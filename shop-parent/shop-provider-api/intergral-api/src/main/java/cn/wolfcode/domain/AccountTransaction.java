package cn.wolfcode.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户TCC事务控制的日志表
 */
@Setter
@Getter
public class AccountTransaction implements Serializable {
    public static final Integer STATE_TRY = 1;
    public static final Integer STATE_COMMIT = 2;
    public static final Integer STATE_CANCEL = 3;

    public static final Integer TYPE_DECR = 0;
    public static final Integer TYPE_INCR = 1;

    private String txId;//全局事务ID
    private String actionId;//分支事务ID
    private Long userId;//用户ID
    private Date gmtCreated;//事务日志记录创建时间
    private Date gmtModified;//事务日志记录修改时间
    private Long amount;//此次积分变更数值
    private String type;//此次积分变更类型 0减少积分 1增加积分
    private int state = STATE_TRY;//事务状态

    @Override
    public String toString() {
        return "AccountTransaction{" +
                "txId='" + txId + '\'' +
                ", actionId='" + actionId + '\'' +
                ", userId=" + userId +
                ", gmtCreated=" + gmtCreated +
                ", gmtModified=" + gmtModified +
                ", amount=" + amount +
                ", type='" + type + '\'' +
                ", state=" + state +
                '}';
    }
}
