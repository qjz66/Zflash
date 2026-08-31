package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.domain.*;
import cn.wolfcode.mapper.AccountLogMapper;
import cn.wolfcode.mapper.AccountTransactionMapper;
import cn.wolfcode.mapper.UsableIntegralMapper;
import cn.wolfcode.service.IUsableIntegralService;
import cn.wolfcode.web.msg.IntergralCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
public class UsableIntegralServiceImpl implements IUsableIntegralService {
    @Autowired
    private UsableIntegralMapper usableIntegralMapper;
    @Autowired
    private AccountTransactionMapper accountTransactionMapper;
    @Autowired
    private AccountLogMapper accountLogMapper;

    private static final String TAG = "[积分支付]";

    @Override
    public void decrIntegralTry(OperateIntergralVo vo, BusinessActionContext context) {
        String branchId = context != null ? context.getBranchId() + "" : "-1";
        log.info("{} 扣除积分，预留资源操作：xid={}, branchId={}, vo={}", TAG, context.getXid(), branchId, vo.toString());
        // 1. 封装账户事物记录对象
        AccountTransaction at = this.buildAccountTransaction(vo.getUserId(), vo.getValue(),
                AccountTransaction.TYPE_DECR, AccountTransaction.STATE_TRY, context);
        try {
            // 2. 插入事物对象
            accountTransactionMapper.insert(at);
            // 3. 如果插入成功，说明没有进行空回滚，资源预留（冻结积分）
            usableIntegralMapper.freezeIntergral(vo.getUserId(), vo.getValue());
        } catch (Exception e) {
            log.info("[积分支付] 预留资源失败，插入事物记录对象出现异常", e);
            throw new BusinessException(IntergralCodeMsg.OP_INTERGRAL_ERROR);
        }
    }

    private AccountTransaction buildAccountTransaction(Long userId, Long integral, Integer type, Integer state, BusinessActionContext context) {
        AccountTransaction at = new AccountTransaction();
        at.setTxId(context.getXid());
        at.setActionId(context.getBranchId() + "");
        at.setAmount(integral);
        at.setState(state);
        at.setType(type + "");
        at.setUserId(userId);
        return at;
    }

    @Override
    public void decrIntegralCommit(BusinessActionContext context) {
        log.info("{} xid={}, branchId={}, 准备执行积分扣除确认（Confirm）操作...", TAG, context.getXid(), context.getBranchId());
        // 1. 查询事物记录表，判断是否为空
        AccountTransaction transaction = accountTransactionMapper.get(context.getXid(), context.getBranchId() + "");
        // 2. 如果为空，直接抛出异常（try 还没有执行）
        if (transaction == null) {
            throw new BusinessException(IntergralCodeMsg.OP_INTERGRAL_ERROR);
        }
        // 3. 判断状态是否为初始化（try），如果不是，判断是否为已提交（是否已经执行过 commit 方法）
        // 4. 如果不是初始化，且已经执行过 commit，直接结束方法（幂等性保障）
        if (AccountTransaction.STATE_COMMIT.equals(transaction.getState())) {
            log.info("{} 当前已经执行过 commit 方法，无需再次执行", TAG);
            return;
        }
        // 5. 如果不是初始化，且状态不是 commit，意味着已经执行过回滚（流程异常），抛出异常
        if (!AccountTransaction.STATE_TRY.equals(transaction.getState())) {
            log.error("{} 积分扣除事物流程异常，当前操作已经被回滚，不能再执行 commit 操作: tx={}", TAG, transaction.toString());
            throw new BusinessException(IntergralCodeMsg.OP_INTERGRAL_ERROR);
        }
        // 6. 如果是初始化，执行 commit 的业务
        // 6.1 扣除冻结金额，记录积分操作日志，将事物记录状态修改为已提交
        String json = context.getActionContext("operateIntegralVo").toString();
        OperateIntergralVo operateIntegralVo = JSON.parseObject(json, OperateIntergralVo.class);
        // 扣除冻结金额
        usableIntegralMapper.commitChange(operateIntegralVo.getUserId(), operateIntegralVo.getValue());
        // 增加积分日志
        this.addIntegralLog(operateIntegralVo, AccountLog.TYPE_DECR);
        // 修改事物记录状态
        accountTransactionMapper.updateAccountTransactionState(
                context.getXid(),
                context.getBranchId() + "",
                AccountTransaction.STATE_COMMIT,
                AccountTransaction.STATE_TRY
        );
    }

    @Override
    public void decrIntegralRollback(BusinessActionContext context) {
        log.info("{} xid={}, branchId={}, 准备执行积分支付回滚（Cancel）操作...", TAG, context.getXid(), context.getBranchId());
        // 1. 先查询事物记录，判断是否有记录（是否执行过 try）
        AccountTransaction transaction = accountTransactionMapper.get(context.getXid(), context.getBranchId() + "");
        String json = context.getActionContext("operateIntegralVo").toString();
        OperateIntergralVo vo = JSON.parseObject(json, OperateIntergralVo.class);
        // 2. 如果有记录，还得判断状态是否是初始化（try）
        if (transaction != null) {
            log.info("{} 已经执行初始化，进行正常回滚操作 tx={}", TAG, transaction.toString());
            // 2.1 如果不是初始化，判断是否是回滚操作，如果是回滚操作，意味着已经执行过 rollback，直接结束方法，幂等性保障
            if (AccountTransaction.STATE_CANCEL.equals(transaction.getState())) {
                log.info("{} 当前已经执行过 rollback 方法，无需再次执行", TAG);
                return;
            }
            // 2.2 如果不是初始化，并且状态不是回滚，意味着执行过 confirm（已提交），流程异常，抛出异常
            if (!AccountTransaction.STATE_TRY.equals(transaction.getState())) {
                log.error("{} 积分扣除事物流程异常，当前操作已经提交了，不能再执行 rollback 操作: tx={}", TAG, transaction.toString());
                throw new BusinessException(IntergralCodeMsg.OP_INTERGRAL_ERROR);
            }
            // 2.3 如果是初始化，直接执行回滚草操作，返还预留资源（冻结金额解冻）
            usableIntegralMapper.unFreezeIntergral(vo.getUserId(), vo.getValue());
            // 3.4 将事物记录状态修改为已回滚状态
            accountTransactionMapper.updateAccountTransactionState(
                    context.getXid(),
                    context.getBranchId() + "",
                    AccountTransaction.STATE_CANCEL,
                    AccountTransaction.STATE_TRY
            );
        } else {
            log.info("{} 没有进行初始化操作，先执行回滚操作，进行空回滚 txId={}, branchId={}", TAG, context.getXid(), context.getBranchId());
            try {
                // 3. 如果没有记录，插入事物控制记录，状态为已回滚
                AccountTransaction at = this.buildAccountTransaction(
                        vo.getUserId(), vo.getValue(), AccountTransaction.TYPE_DECR, AccountTransaction.STATE_CANCEL, context);
                // 3.1 如果插入失败，说明此时 try 已经插入了记录，直接抛出异常
                accountTransactionMapper.insert(at);
                // 3.2 如果插入成功，说明 try 未执行，但执行了 rollback，执行空回滚操作
                // 无需做任何操作
            } catch (Exception e) {
                log.warn("{} 执行空回滚时，try 提前执行，空回滚失败 txid={}, branchId={}", TAG, context.getXid(), context.getBranchId());
                log.error("空回滚异常信息：", e);
                throw new BusinessException(IntergralCodeMsg.OP_INTERGRAL_ERROR);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean doRefund(RefundVo refundVo) {
        // 1.基于订单编号查询支付流水 类型必须为扣款
        AccountLog accountLog = accountLogMapper.selectByOutTradeNoAndType(refundVo.getOutTradeNo(), AccountLog.TYPE_DECR);
        // 2.如果类型为扣款进行退款
        return null;
    }


    @Override
    public UsableIntegral findIntergralByUserIdForPay(Long userId, Long intergral) {
        return usableIntegralMapper.selectIntegralByUserIdForPay(userId, intergral);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void decrIntegral(Long userId, Long integral, String orderNo) {
        // 1. 通过乐观锁来扣除用户积分（保证用户积分充足）
        OperateIntergralVo vo = new OperateIntergralVo();
        vo.setPk(orderNo);
        vo.setValue(integral);
        vo.setUserId(userId);
        vo.setInfo("其他扣除积分");
        this.decrIntegral(vo);
    }

    private void addIntegralLog(OperateIntergralVo vo, Integer type) {
        AccountLog log = new AccountLog();
        log.setAmount(vo.getValue());
        log.setInfo(vo.getInfo());
        log.setPkValue(vo.getPk());
        log.setType(type);
        accountLogMapper.insert(log);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void decrIntegral(OperateIntergralVo vo) {
        String info = vo.getInfo();
        // 1. 通过乐观锁来扣除用户积分（保证用户积分充足）
        int row = usableIntegralMapper.decrIntergral(vo.getUserId(), vo.getValue());
        if (row == 0) {
            throw new BusinessException(IntergralCodeMsg.INTERGRAL_NOT_ENOUGH);
        }
        // 2. 增加积分操作日志
        this.addIntegralLog(vo, AccountLog.TYPE_DECR);
    }
}
