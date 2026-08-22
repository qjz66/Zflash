package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

@FeignClient("pay-service")
public interface AlipayFeignApi {

    @RequestMapping("/alipay/pay")
    Result<String> pay(@RequestBody PayVo payVo);

    @RequestMapping("/alipay/rsaCheck")
    Result<Boolean> rsaCheckV1(@RequestBody Map<String, String> params);

    @RequestMapping("/alipay/refund")
    Result<Boolean> refund(@RequestBody RefundVo refund);

    @RequestMapping("/alipay/queryTradeStatus")
    Result<HashMap<String, String>> queryStatus(@RequestParam("orderNo") String orderNo);
}
