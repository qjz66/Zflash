package cn.wolfcode.mq.listener;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMQResult;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.mq.TimeoutOrder;
import cn.wolfcode.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@RocketMQMessageListener(
        consumerGroup = "SeckillPendingOrderGroup",
        topic = MQConstant.ORDER_PENDING_TOPIC
)
@Component
public class SeckillPendingOrderMessageListener implements RocketMQListener<OrderMessage> {

    @Autowired
    private IOrderInfoService orderInfoService;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(OrderMessage orderMessage) {
        String dest = MQConstant.ORDER_RESULT_SUCCESS_DEST;
        OrderMQResult result = new OrderMQResult();
        try {
            BeanUtils.copyProperties(orderMessage, result);
            log.info("[秒杀订单消费者] 收到秒杀订单前置消息：{}", orderMessage.toString());
            log.info("[秒杀订单消费者] 准备开始创建订单-------------------------------------------");
            // 调用秒杀订单服务直接创建秒杀订单
            String orderNo = orderInfoService.doSeckill(orderMessage.getTime(), orderMessage.getSeckillId(), orderMessage.getToken());
            log.info("[秒杀订单消费者] 订单编号：{}", orderNo);
            log.info("[秒杀订单消费者] 创建订单完成-------------------------------------------");
            // 创建订单成功
            result.setOrderNo(orderNo);
            result.setCode(Result.SUCCESS_CODE);
            result.setMsg("订单创建成功");
            // 发送延迟消息处理超时未支付订单
            TimeoutOrder timeoutOrder = new TimeoutOrder();
            BeanUtils.copyProperties(orderMessage, timeoutOrder);
            timeoutOrder.setOrderNo(orderNo);
            /* 基于 messageBuilder 构建一个 message 对象 */
            Message<TimeoutOrder> message = MessageBuilder.withPayload(timeoutOrder).build();
            // 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
            /* String destination, Message<?> message, SendCallback sendCallback, long timeout, int delayLevel */
            rocketMQTemplate.asyncSend(
                    MQConstant.ORDER_PAY_TIMEOUT_TOPIC,
                    message,
                    new SendResultMessageCallback(timeoutOrder.toString()),
                    3000,
                    MQConstant.ORDER_PAY_TIMEOUT_DELAY_LEVEL
            );
        } catch (BusinessException e) {
            // 业务失败
            e.printStackTrace();
            dest = MQConstant.ORDER_RESULT_FAIL_DEST;
            // 异常回滚操作
            this.fallback(result, e.getCodeMsg(), orderMessage);
        } catch (Exception e) {
            // 系统异常
            e.printStackTrace();
            dest = MQConstant.ORDER_RESULT_FAIL_DEST;

            // 异常回滚操作
            this.fallback(result, new CodeMsg(Result.ERROR_CODE, Result.ERROR_MESSAGE), orderMessage);
        }

        // 发送订单创建成功消息
        rocketMQTemplate.asyncSend(dest, result, new SendResultMessageCallback(result.toString()));
    }

    private void fallback(OrderMQResult result, CodeMsg codeMsg, OrderMessage orderMessage) {
        result.setCode(codeMsg.getCode());
        result.setMsg(codeMsg.getMsg());

        // 回滚 redis 操作
        orderInfoService.rollbackSeckillProduct(orderMessage.getTime(), orderMessage.getSeckillId(), orderMessage.getUserPhone());

        // 发送清除本地标识消息
        rocketMQTemplate.asyncSend(MQConstant.CANCEL_SECKILL_OVER_SIGN_TOPIC, /* 消息主题 */
                orderMessage.getSeckillId() + "", /* 消息内容：秒杀 id（字符串类型） */
                new SendResultMessageCallback(orderMessage.getSeckillId() + "") /* 回调 */
        );
    }

    public static class SendResultMessageCallback implements SendCallback {

        private String msg;

        public SendResultMessageCallback(String msg) {
            this.msg = msg;
        }

        @Override
        public void onSuccess(SendResult sendResult) {
            log.info("[秒杀订单消费者] 订单结果消息发送成功:{}", msg);
        }

        @Override
        public void onException(Throwable throwable) {
            log.warn("[秒杀订单消费者] 订单结果消息发送失败:{}", msg);
            log.error("[秒杀订单消费者] 订单消息发送出现异常：", throwable);
        }
    }
}
