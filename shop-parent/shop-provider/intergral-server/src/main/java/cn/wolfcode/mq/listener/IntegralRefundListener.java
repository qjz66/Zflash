package cn.wolfcode.mq.listener;

import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.service.IUsableIntegralService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import cn.wolfcode.mq.MQConstant;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@RocketMQMessageListener(
        consumerGroup = MQConstant.INTEGRAL_REFUND_TX_GROUP,
        topic = MQConstant.INTEGRAL_REFUND_TX_TOPIC
)
public class IntegralRefundListener implements RocketMQListener<RefundVo> {

    @Autowired
    private IUsableIntegralService usableIntegralService;

    @Override
    public void onMessage(RefundVo refundVo) {
        usableIntegralService.doRefund(refundVo);
        log.info("[积分退款]收到积分退款事务");
    }
}
