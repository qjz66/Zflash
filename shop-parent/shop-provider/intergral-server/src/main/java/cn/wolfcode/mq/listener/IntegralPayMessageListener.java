package cn.wolfcode.mq.listener;

import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.service.IUsableIntegralService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@RocketMQMessageListener(
        consumerGroup = "IntegralPayMessageGroup",
        topic = "INTEGRAL_PAY_TOPIC"
)
@Component
public class IntegralPayMessageListener implements RocketMQListener<OperateIntergralVo> {

    @Autowired
    private IUsableIntegralService usableIntegralService;

    @Override
    public void onMessage(OperateIntergralVo vo) {
        log.info("[积分支付监听器] 收到积分支付消息，准备开始进行积分支付 orderNo={}, xid={}", vo.getPk(), vo.getInfo());
        usableIntegralService.decrIntegral(vo);
    }
}
