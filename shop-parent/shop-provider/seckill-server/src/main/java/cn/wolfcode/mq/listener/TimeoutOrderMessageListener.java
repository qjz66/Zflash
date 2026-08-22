package cn.wolfcode.mq.listener;

import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.TimeoutOrder;
import cn.wolfcode.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@RocketMQMessageListener(
        consumerGroup = "TimeoutOrderMessageGroup",
        topic = MQConstant.ORDER_PAY_TIMEOUT_TOPIC
)
@Component
public class TimeoutOrderMessageListener implements RocketMQListener<TimeoutOrder> {

    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public void onMessage(TimeoutOrder timeoutOrder) {
        log.info("[超时订单消息消费者] 准备开始检查超时订单...");
        orderInfoService.timeoutCheck(timeoutOrder);
        log.info("[超时订单消息消费者] 检查超时订单结束...");

    }
}
