package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.feign.fallback.IntergralFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "intergral-service", fallback = IntergralFeignFallback.class)
public interface IntergralFeignApi {

    @RequestMapping("/intergral/findIntergralByUserIdForPay")
    Result<Boolean> findIntergralByUserIdForPay(@RequestParam("userId") Long userId,
                                                @RequestParam("intergral") Long intergral);

    @RequestMapping("/intergral/decrIntergral")
    Result<Boolean> decrIntergral(@RequestParam("userId") Long userId,
                                  @RequestParam("intergral") Long intergral,
                                  @RequestParam("orderNo") String orderNo);

    @RequestMapping("/intergral/refund")
    Result<Boolean> refund(@RequestBody RefundVo refundVo);
}
