package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.UsableIntegral;
import cn.wolfcode.service.IUsableIntegralService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("/intergral")
public class IntegralController {
    @Autowired
    private IUsableIntegralService usableIntegralService;

    @RequestMapping("/findIntergralByUserIdForPay")
    public Result<Boolean> findIntergralByUserIdForPay(@RequestParam("userId") Long userId,
                                                       @RequestParam("intergral") Long intergral) {
        UsableIntegral usableIntegral = usableIntegralService.findIntergralByUserIdForPay(userId, intergral);
        return Result.success(usableIntegral != null);
    }

    @RequestMapping("/decrIntergral")
    public Result<Boolean> decrIntergral(@RequestParam("userId") Long userId,
                                         @RequestParam("intergral") Long integral,
                                         @RequestParam("orderNo") String orderNo) {
        log.info("[积分支付] userId={}, integral={}, orderNo={}", userId, integral, orderNo);
        OperateIntergralVo vo = this.toIntegralVo(userId, integral, orderNo);
        // 调用方法
        usableIntegralService.decrIntegralTry(vo, null);
        return Result.success(true);
    }

    private OperateIntergralVo toIntegralVo(Long userId, Long integral, String orderNo) {
        OperateIntergralVo vo = new OperateIntergralVo();
        vo.setInfo("积分支付：" + integral);
        vo.setUserId(userId);
        vo.setValue(integral);
        vo.setPk(orderNo);
        return vo;
    }
}
