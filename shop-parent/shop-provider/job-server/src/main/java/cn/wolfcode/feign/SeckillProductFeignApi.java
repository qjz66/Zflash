package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "seckill-service")
public interface SeckillProductFeignApi {

    @RequestMapping("/seckillProduct/selectByTime")
    Result<List<SeckillProductVo>> selectByTime(@RequestParam("time") Integer time);
}
