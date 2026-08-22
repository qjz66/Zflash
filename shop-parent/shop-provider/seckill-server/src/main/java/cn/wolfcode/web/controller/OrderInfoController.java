package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.UserUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    /* 本地标识，避免库存已经没有以后，后续的请求还继续访问 redis */
    public static final Map<Long, Boolean> STOCK_OVER_FLAG_MAP = new ConcurrentHashMap<>();

    @RequireLogin
    @GetMapping("/find")
    public Result<OrderInfo> findById(String orderNo, @RequestHeader(CommonConstants.TOKEN_NAME) String token) {
        OrderInfo orderInfo = orderInfoService.findById(orderNo);
        UserInfo user = UserUtil.getUser(redisTemplate, token);

        if (orderInfo == null || !orderInfo.getUserId().equals(user.getPhone())) {
            throw new BusinessException(SeckillCodeMsg.OP_ERROR);
        }
        return Result.success(orderInfo);
    }

    @RequireLogin
    @PostMapping("/doSeckill")
    public Result<String> doSeckill(@RequestHeader(CommonConstants.TOKEN_NAME) String token, Long seckillId, Integer time) {
        // 1. 判断用户是否登陆
        // 2. 判断参数是否合法
        if (seckillId == null || time == null) {
            throw new BusinessException(SeckillCodeMsg.OP_ERROR);
        }

        /*
         * 本地标识判断
         *  */
        Boolean stockOverFlag = STOCK_OVER_FLAG_MAP.get(seckillId);
        if (stockOverFlag != null && stockOverFlag) {
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }

        // 3. 基于秒杀商品 id 获取秒杀商品对象，如果对象不存在，也说明是非法操作
        SeckillProductVo vo = seckillProductService.findByIdAndTimeFromRedis(seckillId, time);
        if (vo == null) {
            throw new BusinessException(SeckillCodeMsg.OP_ERROR);
        }
        // 4. 判断是否在活动时间范围内
        /* TODO 完成以后打开注释 */
        /*if (!DateUtil.isLegalTime(vo.getStartDate(), time)) {
            throw new BusinessException(SeckillCodeMsg.OVER_TIME_ERROR);
        }*/
        // 5. 判断库存是否 > 0
        /*if (vo.getStockCount() <= 0) {
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }*/
        String stockCountRealKey = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time + "");
        /* 返回剩余的库存数量 */
        Long remainStockCount = redisTemplate.opsForHash().increment(stockCountRealKey, vo.getId() + "", -1);
        if (remainStockCount < 0) {
            /* 只要当前库存是不足的，都往 map 中存储 */
            STOCK_OVER_FLAG_MAP.put(seckillId, true);
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        // 6. 判断用户是否重复下单
        UserInfo user = UserUtil.getUser(redisTemplate, token);
        /*String realKey = SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(time + "");
        Boolean isMember = redisTemplate.opsForSet().isMember(realKey, user.getPhone() + ":" + seckillId);*/
        String seckillOrderKey = SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey(time + "");
        String userOrderHashKey = user.getPhone() + ":" + seckillId;
        /* 如果之前用户没有下过单，才能够 put 成功，结果会返回 true */
        Boolean putIfAbsent = redisTemplate.opsForHash().putIfAbsent(seckillOrderKey, userOrderHashKey, "1");
        /* 如果结果为 false，说明用户已经下过单了 */
        if (!putIfAbsent) {
            // 说明已经下过单
            throw new BusinessException(SeckillCodeMsg.REPEAT_SECKILL);
        }
        // 7. 开始秒杀流程，减库存、创建秒杀订单、存储下单成功标识，返回订单编号
        // String orderNo = orderInfoService.doSeckill(vo, user);
        // 异步发送下单消息
        OrderMessage message = new OrderMessage(time, seckillId, token, user.getPhone());
        rocketMQTemplate.asyncSend(MQConstant.ORDER_PENDING_TOPIC, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("[秒杀下单] 发送下单消息成功：status={}, messageId={}", sendResult.getSendStatus(), sendResult.getMsgId());
                log.info("[秒杀下单] 消息内容：{}", message.toString());
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("[秒杀下单] 发送下单消息失败：", throwable);
            }
        });
        return Result.success("正在下单中，请稍后");
    }
}
