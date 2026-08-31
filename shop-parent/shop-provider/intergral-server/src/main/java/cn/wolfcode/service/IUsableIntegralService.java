package cn.wolfcode.service;

import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.domain.UsableIntegral;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

@LocalTCC
public interface IUsableIntegralService {
    UsableIntegral findIntergralByUserIdForPay(Long userId, Long intergral);

    void decrIntegral(Long userId, Long intergral, String orderNo);

    void decrIntegral(OperateIntergralVo vo);

    /**
     * TCC 事物：资源预留方法
     *
     * @param vo
     */
    @TwoPhaseBusinessAction(
            name = "decrIntegralTry",
            commitMethod = "decrIntegralCommit",
            rollbackMethod = "decrIntegralRollback"
    )
    void decrIntegralTry(
            @BusinessActionContextParameter(paramName = "operateIntegralVo") OperateIntergralVo vo,
            BusinessActionContext context);

    /**
     * TCC 事物：提交事物方法（Confirm）
     *
     * @param context
     */
    void decrIntegralCommit(BusinessActionContext context);

    /**
     * TCC 事物：回滚事物方法
     *
     * @param context
     */
    void decrIntegralRollback(BusinessActionContext context);

    Boolean doRefund(RefundVo refundVo);
}
