package cn.wolfcode.feign.fallback;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.feign.IntergralFeignApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IntergralFeignFallback implements IntergralFeignApi {

    @Override
    public Result<Boolean> findIntergralByUserIdForPay(Long userId, Long intergral) {
        log.warn("[积分降级服务] 积分支付查询用户积分失败：userId={}, intergral={}", userId, intergral);
        return Result.success(false);
    }

    @Override
    public Result<Boolean> decrIntergral(Long userId, Long intergral, String orderNo) {
        log.warn("[积分降级服务] 积分支付扣减用户积分是啊比：userId={}, intergral={}, orderNo={}", userId, intergral, orderNo);
        return Result.success(false);
    }
}
