package cn.wolfcode.mq.listener;

import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.web.controller.OrderInfoController;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@RocketMQMessageListener(
        consumerGroup = "CancelLocalSignMessageGroup",
        topic = MQConstant.CANCEL_SECKILL_OVER_SIGN_TOPIC,
        /* 设置消息模式为广播模式 */
        messageModel = MessageModel.BROADCASTING
)
@Component
public class CancelLocalSignMessageListener implements RocketMQListener<String> {

    @Override
    public void onMessage(String seckillIdStr) {
        log.debug("[清除本地标识消费者] 清除本地标识开始：----------------------------------------");
        OrderInfoController.STOCK_OVER_FLAG_MAP.put(Long.valueOf(seckillIdStr), false);
        log.info("[清除本地标识消费者] 正在设置{}本地标识为 false", seckillIdStr);
        log.debug("[清除本地标识消费者] 清除本地标识结束：----------------------------------------");
    }
}
