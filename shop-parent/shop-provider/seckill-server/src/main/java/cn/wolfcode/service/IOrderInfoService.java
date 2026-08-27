package cn.wolfcode.service;


import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.TimeoutOrder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Created by wolfcode
 */
public interface IOrderInfoService {

    @Transactional(rollbackFor = Exception.class)
    String doSeckill(SeckillProductVo vo, Long phone);

    @Transactional(rollbackFor = Exception.class)
    String doSeckill(Integer time, Long seckillId, Long phone);

    /**
     * 秒杀商品 redis 回滚
     *
     * @param time
     * @param seckillId
     * @param userPhone
     */
    void rollbackSeckillProduct(Integer time, Long seckillId, Long userPhone);

    /**
     * 检查订单是否超时
     *
     * @param timeoutOrder
     */
    void timeoutCheck(TimeoutOrder timeoutOrder);

    /**
     * 基于订单编号查询订单对象
     *
     * @param orderNo
     * @return
     */
    OrderInfo findById(String orderNo);

    /**
     * 调用支付服务对订单进行支付宝支付
     *
     * @param orderNo
     * @return
     */
    String alipay(String orderNo);

    /**
     * 订单支付成功调用接口
     *
     * @param totalAmount
     * @param tradeNo     支付宝交易号
     * @param orderNo     订单编号
     */
    void paySuccess(String totalAmount, String tradeNo, String orderNo);

    /**
     * 退款操作
     *
     * @param orderNo
     */
    void refund(String orderNo);

    /**
     * 同步支付宝状态
     *
     * @param orderNo
     */
    void syncStatus(String orderNo);

    /**
     * 积分支付
     *
     * @param orderNo
     */
    void intergralPay(String orderNo);

    OrderInfo selectByUserIdAndSecKillId(Long phone, Long seckillId);

}
