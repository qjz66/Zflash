package cn.wolfcode.mq.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;

@Slf4j
public class DefaultSendCallBack implements SendCallback {
    private String tag;

    public DefaultSendCallBack(String tag) {
        this.tag = tag;
    }

    @Override
    public void onSuccess(SendResult sendResult) {
        log.info("[{}] 消息发送成功， 消息id = {}",tag,sendResult.getMsgId());
    }

    @Override
    public void onException(Throwable throwable) {
        log.warn("[{}] 消息发送失败...",tag);
    }

}
