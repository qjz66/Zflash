package cn.wolfcode.mq;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;


@Setter
@Getter
public class OrderMQResult implements Serializable {
    private Integer time;//秒杀场次
    private Long seckillId;//秒杀商品id
    private String token;//用户token
    private Long userPhone;//用户手机号
    private String orderNo;//订单编号
    private String msg;//提示消息
    private Integer code;//状态码

    @Override
    public String toString() {
        return "OrderMQResult{" +
                "time=" + time +
                ", seckillId=" + seckillId +
                ", token='" + token + '\'' +
                ", orderNo='" + orderNo + '\'' +
                ", userPhone='" + userPhone + '\'' +
                ", msg='" + msg + '\'' +
                ", code=" + code +
                '}';
    }
}
