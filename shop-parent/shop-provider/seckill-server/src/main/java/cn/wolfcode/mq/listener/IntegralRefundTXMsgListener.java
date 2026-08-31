package cn.wolfcode.mq.listener;


import cn.wolfcode.domain.RefundLog;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.transaction.event.TransactionalEventListener;

@RocketMQTransactionListener(txProducerGroup = MQConstant.INTEGRAL_REFUND_TX_GROUP)
@Slf4j
public class IntegralRefundTXMsgListener implements RocketMQLocalTransactionListener {
    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object o) {
        try{
            orderInfoService.IntergralRefundRollback((String) o);
            return RocketMQLocalTransactionState.COMMIT;
        }catch (Exception e){
            log.warn("[事务监听器]执行本地事务出现异常");
            return RocketMQLocalTransactionState.ROLLBACK;
        }

    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        String orderNo = (String) message.getHeaders().get("orderNo");
        try{
            // 基于orderNo 查询退款日志
            RefundLog refundLog = orderInfoService.SelectRefundLogByOrderNo(orderNo);
            if (refundLog != null) {
                return RocketMQLocalTransactionState.COMMIT;
            }

        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
        return RocketMQLocalTransactionState.ROLLBACK;
    }
}
