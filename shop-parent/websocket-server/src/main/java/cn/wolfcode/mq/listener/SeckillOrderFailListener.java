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
        consumerGroup = "SeckillOrderFailGroup",
        topic = MQConstant.ORDER_RESULT_TOPIC,
        selectorType = SelectorType.TAG,
        selectorExpression = MQConstant.ORDER_RESULT_FAIL_TAG
)
@Component
public class SeckillOrderFailListener implements RocketMQListener<OrderMQResult> {

    @Override
    public void onMessage(OrderMQResult result) {
        log.info("[订单创建失败消费者] 收到订单创建失败消息：{}", result.toString());
        // 去通知用户下单失败
        Session session = WebSocketServer.SESSION_MAP.get(result.getToken());
        if (session != null) {
            // 发送消息给用户
            try {
                session.getBasicRemote().sendText(JSON.toJSONString(result));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            log.warn("[订单创建失败消费者] 用户{}已经下线，无法进行消息通知...", result.getToken());
        }
        log.info("[订单创建失败消费者] 订单创建失败消息消费结束--------------");
    }
}
