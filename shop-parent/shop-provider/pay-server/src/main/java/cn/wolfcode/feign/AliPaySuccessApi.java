package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PaySuccessVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient("seckill-service")
public interface AliPaySuccessApi {
    @RequestMapping("/orderPay/paySuccess")
    Result<String> paySuccess(@RequestBody PaySuccessVo paySuccessVo);
}
