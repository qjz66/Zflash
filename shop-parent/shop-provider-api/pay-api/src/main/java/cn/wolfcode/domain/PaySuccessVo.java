package cn.wolfcode.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaySuccessVo {
    private String outTradeNo;
    private String tradeNo;
    private String totalAmount;
}
