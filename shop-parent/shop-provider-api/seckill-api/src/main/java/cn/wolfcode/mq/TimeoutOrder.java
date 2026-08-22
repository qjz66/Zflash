package cn.wolfcode.mq;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;


@Setter
@Getter
public class TimeoutOrder implements Serializable {
    private Integer time;//秒杀场次
    private Long seckillId;//秒杀商品id
    private Long userPhone;//用户手机号
    private String orderNo;//订单编号

    @Override
    public String toString() {
        return "TimeoutOrder{" +
                "time=" + time +
                ", seckillId=" + seckillId +
                ", userPhone=" + userPhone +
                ", orderNo='" + orderNo + '\'' +
                '}';
    }
}
