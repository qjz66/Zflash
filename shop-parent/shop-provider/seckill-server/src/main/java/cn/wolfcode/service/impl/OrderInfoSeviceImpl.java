package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.*;
import cn.wolfcode.feign.AlipayFeignApi;
import cn.wolfcode.feign.IntergralFeignApi;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.TimeoutOrder;
import cn.wolfcode.mq.listener.SeckillPendingOrderMessageListener;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Order;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by wolfcode
 */
@Slf4j
@RefreshScope
@Service
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private AlipayFeignApi alipayFeignApi;
    @Autowired
    private IntergralFeignApi intergralFeignApi;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String doSeckill(SeckillProductVo vo, Long phone) {
        // 1. 查库存再扣库存（redis、MySQL）
        seckillProductService.decrStockCount(vo.getId(), vo.getTime());
        // 2. 生成秒杀订单
        String orderNo = this.createOrder(vo, phone);
        // 3. 记录用户下单成功标识
        /*String realKey = SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(vo.getTime() + "");
        redisTemplate.opsForSet().add(realKey, user.getPhone() + ":" + vo.getId());*/
        return orderNo;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String doSeckill(Integer time, Long seckillId, Long phone) {
        SeckillProductVo vo = seckillProductService.findByIdAndTimeFromRedis(seckillId, time);
        //UserInfo user = UserUtil.getUser(redisTemplate, token);
        return doSeckill(vo, phone);
    }

    @Override
    public void rollbackSeckillProduct(Integer time, Long seckillId, Long userPhone) {
        SeckillProduct sp = seckillProductService.findByIdAndTime(seckillId, time);
        // 1. 回补库存预减
        redisTemplate.opsForHash().put(
                SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time + ""), /* 基于场次作为唯一 key */
                seckillId + "", /* hash 中的key == 秒杀商品id */
                sp.getStockCount() + ""); /* 库存值 */

        // 2. 回补 vo 中的库存
        SeckillProductVo vo = seckillProductService.findByIdAndTimeFromRedis(seckillId, time);
        vo.setStockCount(sp.getStockCount());
        redisTemplate.opsForHash().put(
                SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time + ""), /* 基于场次作为唯一 key */
                vo.getId() + "", /* hash 中的key == 秒杀商品id */
                JSON.toJSONString(vo)/* vo json 字符串 */
        );

        String userOrderHashKey = userPhone + ":" + seckillId;
        // 3. 删除重复下单标识
        redisTemplate.opsForHash().delete(
                SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey(time + ""),
                userOrderHashKey
        );
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void timeoutCheck(TimeoutOrder timeoutOrder) {
        // 1. 根据订单编号得到订单对象
        OrderInfo orderInfo = orderInfoMapper.find(timeoutOrder.getOrderNo());
        if (orderInfo == null) {
            log.warn("[订单超时检查] 查询订单信息有误：{}", timeoutOrder.toString());
            return;
        }
        log.info("[订单超时检查] 查询到超时订单对象：{}", orderInfo.toString());
        // 2. 检查订单状态是否为未支付
        if (OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())) {
            // 3. 如果是未支付，就进行回滚操作
            // 修改状态
            int row = orderInfoMapper.updateCancelStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_TIMEOUT);
            if (row == 0) {
                // 1. 刚好在修改前，用户支付成功  --> 不应该回补库存
                // 2. 刚好在修改前，用户手动取消  --> 不应该回补库存
                log.warn("[订单超时检查] 修改订单状态失败，当前订单状态已经不是未支付:{}", timeoutOrder.toString());
                return;
            }

            // 库存回滚操作
            this.stockCountRollback(timeoutOrder.getSeckillId(), timeoutOrder.getTime(), timeoutOrder.getUserPhone());
            log.info("[订单超时检查] 超时订单回滚成功......");
            return;
        }

        log.info("[订单超时检查] 订单状态不是未支付状态：{}", orderInfo.getStatus());
    }

    private void stockCountRollback(Long seckillId, Integer time, Long userPhone) {
        // 数据库中秒杀商品的库存 + 1
        seckillProductService.incrStockCount(seckillId);
        // 回滚 redis 的数据
        this.rollbackSeckillProduct(time, seckillId, userPhone);
        // 发送清除本地标识消息
        rocketMQTemplate.asyncSend(MQConstant.CANCEL_SECKILL_OVER_SIGN_TOPIC, /* 消息主题 */
                seckillId + "", /* 消息内容：秒杀 id（字符串类型） */
                new SeckillPendingOrderMessageListener.SendResultMessageCallback(seckillId + "") /* 回调 */
        );
    }

    @Override
    public OrderInfo findById(String orderNo) {
        OrderInfo orderInfo;
        String json = redisTemplate.opsForValue().get(SeckillRedisKey.SECKILL_ORDER_STRING.getRealKey(orderNo));
        if (StringUtils.hasText(json)) {
            orderInfo = JSON.parseObject(json, OrderInfo.class);
        } else {
            orderInfo = orderInfoMapper.find(orderNo);
        }
        return orderInfo;
    }

    @Override
    public String alipay(String orderNo) {
        //OrderInfo orderInfo = this.findById(orderNo);
        ///* 如果查询不到订单，或者订单状态不为未支付，都抛出操作异常 */
        //if (orderInfo == null || !OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())) {
        //    throw new BusinessException(SeckillCodeMsg.OP_ERROR);
        //}
        //// 封装 支付对象
        //PayVo pay = this.buildPayVo(orderInfo);
        //// 调用支付服务接口进行支付
        //Result<String> result = alipayFeignApi.pay(pay);
        //if (result == null || result.hasError()) {
        //    throw new BusinessException(SeckillCodeMsg.PAY_FAILED_ERROR);
        //}
        //// 返回发起支付结果
        //return result.getData();
        // 1.基于订单号查询订单对象
        OrderInfo orderInfo = this.findById(orderNo);
        // 2.验证订单状态是否为未支付

        if (orderInfo == null || !OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())) {
            throw new BusinessException(SeckillCodeMsg.OP_ERROR);

        }
        PayVo payVo = this.buildPayVo(orderInfo);
        Result<String> res = alipayFeignApi.pay(payVo);

        if(res == null || res.hasError()) {
            throw new BusinessException(SeckillCodeMsg.PAY_FAILED_ERROR);
        }

        return res.getData();
    }

    @Override
    public void paySuccess(String totalAmount, String tradeNo, String orderNo) {
        // 保证幂等性
        // update t_order_info set status = success where orderNo = #{orderNo} and status = 0
        // 1. 修改订单状态
        //int row = orderInfoMapper.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAY_TYPE_ONLINE);
        //if (row > 0) {
        //    // 2. 增加支付流水记录
        //    this.addPayLog(orderNo, OrderInfo.PAY_TYPE_ONLINE, totalAmount, tradeNo);
        //}

        // 1.查询订单对象 确保订单信息无错误
        OrderInfo orderInfo = this.findById(orderNo);
        if (orderInfo == null) {
            throw new BusinessException(new CodeMsg(500601,"订单信息错误，查询为空..."));
        }

        // 2.判断订单金额
        if(!orderInfo.getSeckillPrice().toString().equals(totalAmount)) {
            throw new BusinessException(new CodeMsg(500601,"订单信息错误，金额不正确..."));
        }

        // 3.使用订单状态保证幂等性
        int row = orderInfoMapper.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAY_TYPE_ONLINE);
        if (row > 0) {
            this.addPayLog(orderNo, OrderInfo.PAY_TYPE_ONLINE, totalAmount, tradeNo);
        }
    }

    private void addPayLog(String orderNo, Integer payType, String totalAmount, String tradeNo) {
        PayLog payLog = new PayLog();
        String notifyTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        payLog.setNotifyTime(notifyTime);
        payLog.setOutTradeNo(orderNo);
        payLog.setPayType(payType);
        payLog.setTotalAmount(totalAmount);
        payLog.setTradeNo(tradeNo);
        payLogMapper.insert(payLog);
    }

    @Override
    public void refund(String orderNo) {
        OrderInfo orderInfo = this.findById(orderNo);
        /* 查不到订单对象，或者订单状态不是已付款状态，都提示出错 */
        if (orderInfo == null || !OrderInfo.STATUS_ACCOUNT_PAID.equals(orderInfo.getStatus())) {
            throw new BusinessException(SeckillCodeMsg.OP_ERROR);
        }

        // 1. 判断订单的支付类型
        if (OrderInfo.PAY_TYPE_ONLINE.equals(orderInfo.getPayType())) {
            log.info("[退款操作] 准备进行支付宝退款业务：{}", orderInfo.toString());
            // 2. 如果是线上支付，就远程调用支付宝接口来进行退款
            String reason = "用户自主退款";
            RefundVo vo = new RefundVo();
            vo.setOutTradeNo(orderNo);
            vo.setRefundAmount(orderInfo.getSeckillPrice() + "");
            vo.setRefundReason(reason);
            Result<Boolean> result = alipayFeignApi.refund(vo);
            if (result == null || result.hasError() || !result.getData()) {
                // 退款失败
                throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
            }
            // 2.1 退款成功还需要修改订单状态为已退款、追加退款记录
            orderInfoMapper.changeRefundStatus(orderNo, OrderInfo.STATUS_REFUND);
            // 追加退款记录
            this.addRefundLog(orderInfo, reason);
            // 2.2 如果支付宝退款成功，回补库存（数据库库存、库存预减、vo 库存）、本地标识
            this.stockCountRollback(orderInfo.getSeckillId(), orderInfo.getSeckillTime(), orderInfo.getUserId());
        } else if (OrderInfo.PAY_TYPE_INTERGRAL.equals(orderInfo.getPayType())) {
            log.info("[退款操作] 准备进行积分退款业务：{}", orderInfo.toString());
            // 3. 如果是积分支付，就调用积分服务进行退款
        }
    }

    @Override
    public void syncStatus(String orderNo) {
        // 1. 调用远程支付接口，查询支付宝支付状态
        Result<HashMap<String, String>> result = alipayFeignApi.queryStatus(orderNo);
        if (result == null || result.hasError()) {
            log.warn("[同步支付状态] 查询支付宝交易状态失败：{}", result);
            return;
        }
        // 2. 判断状态是否支付成功，如果是支付成功，直接调用支付成功方法，更新订单状态，以及增支付日志
        HashMap<String, String> data = result.getData();
        if ("TRADE_SUCCESS".equals(data.get("trade_status"))) {
            // 订单已经支付成功
            String trade_no = data.get("trade_no");
            String total_amount = data.get("total_amount");
            // 将订单更新为成功状态
            this.paySuccess(total_amount, trade_no, orderNo);
        }
    }

    /* TM 标识开始事物的地方 */
    @GlobalTransactional(rollbackFor = Exception.class)
    @Override
    public void intergralPay(String orderNo) {
        // 1. 根据订单编号 + 状态查询订单信息
        OrderInfo orderInfo = this.findById(orderNo);
        // 2. 判断订单是否能查询到，如果查询不到，说明订单状态异常
        if (orderInfo == null || !OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())) {
            throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
        }
        // 3. 基于用户id + 订单所需积分查询积分服务，获取用户积分信息
        Result<Boolean> intergralResult = intergralFeignApi.findIntergralByUserIdForPay(orderInfo.getUserId(), orderInfo.getIntergral());
        // 4. 判断用户积分是否能获取到，如果获取不到，直接提示积分不足
        if (intergralResult == null || intergralResult.hasError() || !intergralResult.getData()) {
            throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
        }
        // 5. 修改订单状态为已支付
        // 6. 修改订单支付类型为积分支付，并设置支付时间
        int row = orderInfoMapper.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAY_TYPE_INTERGRAL);
        if (row == 0) {
            throw new BusinessException(SeckillCodeMsg.INTERGRAL_PAY_FAILED_ERROR);
        }
        // 7. 增加积分支付日志
        this.addPayLog(orderNo, OrderInfo.PAY_TYPE_ONLINE, orderInfo.getIntergral() + "", "-1");
        // 8. 调用积分服务扣除用户积分，增加积分扣除日志
        // intergralFeignApi.decrIntergral(orderInfo.getUserId(), orderInfo.getIntergral(), orderNo);
        OperateIntergralVo vo = new OperateIntergralVo();
        vo.setInfo(RootContext.getXID());
        vo.setPk(orderNo);
        vo.setUserId(orderInfo.getUserId());
        vo.setValue(orderInfo.getIntergral());
        // 通过发送异步消息扣除积分
        intergralFeignApi.decrIntergral(orderInfo.getUserId(), orderInfo.getIntergral(), orderNo);
//        rocketMQTemplate.asyncSend(MQConstant.INTEGRAL_PAY_TOPIC, vo,
//                new SeckillPendingOrderMessageListener.SendResultMessageCallback(vo.toString()));
    }

    @Override
    public OrderInfo selectByUserIdAndSecKillId(Long phone, Long seckillId) {
        return orderInfoMapper.selectByUserIdAndSecKillId(phone,seckillId);
    }

    private void addRefundLog(OrderInfo orderInfo, String reason) {
        RefundLog refundLog = new RefundLog();
        refundLog.setOutTradeNo(orderInfo.getOrderNo());
        refundLog.setRefundAmount(orderInfo.getSeckillPrice() + "");
        refundLog.setRefundReason(reason);
        refundLog.setRefundTime(new Date());
        refundLog.setRefundType(orderInfo.getPayType());
        this.refundLogMapper.insert(refundLog);
    }

    private PayVo buildPayVo(OrderInfo orderInfo) {
        PayVo payVo = new PayVo();
        payVo.setBody("秒杀商品：" + orderInfo.getProductName());
        payVo.setOutTradeNo(orderInfo.getOrderNo());
        payVo.setSubject(orderInfo.getProductName());
        payVo.setTotalAmount(orderInfo.getSeckillPrice() + "");
        return payVo;
    }

    private String createOrder(SeckillProductVo vo, Long phone) {
        Date now = new Date();
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCreateDate(now);
        String orderNo = IdGenerateUtil.get().nextId() + "";
        orderInfo.setOrderNo(orderNo);
        orderInfo.setProductCount(1);
        orderInfo.setProductId(vo.getProductId());
        orderInfo.setProductImg(vo.getProductImg());
        orderInfo.setProductName(vo.getProductName());
        orderInfo.setProductPrice(vo.getProductPrice());
        orderInfo.setSeckillDate(vo.getStartDate());
        orderInfo.setSeckillTime(vo.getTime());
        orderInfo.setSeckillId(vo.getId());
        orderInfo.setSeckillPrice(vo.getSeckillPrice());
        orderInfo.setStatus(OrderInfo.STATUS_ARREARAGE);
        orderInfo.setUserId(phone);
        // 保存订单
        orderInfoMapper.insert(orderInfo);
        return orderNo;
    }
}
