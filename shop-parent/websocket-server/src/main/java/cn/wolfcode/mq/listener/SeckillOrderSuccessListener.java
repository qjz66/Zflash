package cn.wolfcode.mq.listener;

import cn.wolfcode.core.WebSocketServer;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMQResult;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.websocket.Session;
import java.io.IOException;

@Slf4j
@RocketMQMessageListener(
        consumerGroup = "SeckillOrderSuccessGroup",
        topic = MQConstant.ORDER_RESULT_TOPIC,
        selectorType = SelectorType.TAG,
        selectorExpression = MQConstant.ORDER_RESULT_SUCCESS_TAG
)
@Component
public class SeckillOrderSuccessListener implements RocketMQListener<OrderMQResult> {

    @Override
    public void onMessage(OrderMQResult result) {
        log.info("[订单创建成功消费者] 收到订单创建成功消息：{}", result.toString());
        // 去通知用户下单成功
        Session session = WebSocketServer.SESSION_MAP.get(result.getToken());
        int count = 0;
        do {
            if (session != null) {
                // 发送消息给用户
                try {
                    session.getBasicRemote().sendText(JSON.toJSONString(result));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            } else {
                log.warn("[订单创建成功消费者] 用户{}已经下线，无法进行消息通知...", result.getToken());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }while (count++ < 5);
        log.info("[订单创建成功消费者] 订单创建成功消息消费结束--------------");
    }
}
