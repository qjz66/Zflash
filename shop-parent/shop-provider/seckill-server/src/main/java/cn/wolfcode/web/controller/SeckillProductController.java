package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.service.ISeckillProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/seckillProduct")
@Slf4j
public class SeckillProductController {

    @Autowired
    private ISeckillProductService seckillProductService;

    @RequestMapping("/find")
    public Result<SeckillProductVo> findById(Integer time, Long seckillId) {
        return Result.success(seckillProductService.findByIdAndTimeFromRedis(seckillId, time));
    }

    @RequestMapping("/selectByTime")
    public Result<List<SeckillProductVo>> selectByTime(Integer time) {
        return Result.success(seckillProductService.selectByTime(time));
    }

    @RequestMapping("/queryByTime")
    public Result<List<SeckillProductVo>> queryByTime(Integer time) {
        return Result.success(seckillProductService.selectByTimeFromRedis(time));
    }
}
