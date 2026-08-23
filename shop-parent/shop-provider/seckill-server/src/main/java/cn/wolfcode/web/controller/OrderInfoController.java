package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.common.web.resolver.RequestUser;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.UserUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    /* 本地标识，避免库存已经没有以后，后续的请求还继续访问 redis */
    public static final Map<Long, Boolean> STOCK_OVER_FLAG_MAP = new ConcurrentHashMap<>();

    @RequireLogin
    @GetMapping("/find")
    public Result<OrderInfo> findById(String orderNo, @RequestHeader(CommonConstants.TOKEN_NAME) String token) {
        OrderInfo orderInfo = orderInfoService.findById(orderNo);
        UserInfo user = UserUtil.getUser(redisTemplate, token);

        if (orderInfo == null || !orderInfo.getUserId().equals(user.getPhone())) {
            throw new BusinessException(SeckillCodeMsg.OP_ERROR);
        }
        return Result.success(orderInfo);
    }

    @RequireLogin
    @PostMapping("/doSeckill")
    public Result<?> doseckill(Long seckillId, Integer time, @RequestUser UserInfo userInfo) {
        // 1. 基于token查询用户信息
        // 已经使用注解得到
        // 2. 基于秒杀id + 场次查询秒杀商品对象
        SeckillProductVo seckillProductVo = seckillProductService.findByIdAndTimeFromRedis(seckillId, time);
        if(seckillProductVo == null) {
            CodeMsg codeMsg = new CodeMsg();
            codeMsg.setCode(500);
            codeMsg.setMsg("[秒杀]秒杀商品查询失败...");
            throw new BusinessException(codeMsg);
        }

        // 3. 判断当前时间是否在秒杀时间范围内
        boolean range = betweenSeckillTime(seckillProductVo);
        if (false) {
            throw new BusinessException(new CodeMsg(501, "[秒杀]当前活动尚未开始..."));
        }
        // 4. 判断库存是否充足
        if(seckillProductVo.getStockCount() <= 0) {
            throw new BusinessException(new CodeMsg(501, "[秒杀]库存不足..."));
        }
        // 5. 判断用户是否已对同一商品下单
        OrderInfo orderInfo = orderInfoService.selectByUserIdAndSecKillId(userInfo.getPhone(), seckillId);
        if(orderInfo != null) {
            throw new BusinessException(new CodeMsg(501, "[秒杀]不能重复下单该商品..."));
        }
        // 6. 创建订单，扣除库存，返回订单id
        String orderNo =  orderInfoService.doSeckill(seckillProductVo, userInfo);

        return Result.success(orderNo);
    }

    private boolean betweenSeckillTime(SeckillProductVo seckillProductVo) {
        Calendar instance = Calendar.getInstance();
        instance.setTime(seckillProductVo.getStartDate());
        instance.set(Calendar.HOUR_OF_DAY, seckillProductVo.getTime());

        // 获取开始时间
        Date startTime = instance.getTime();
        // 获取结束时间
        instance.add(Calendar.HOUR_OF_DAY, 2);
        Date endTime = instance.getTime();

        //当前时间
        long now = System.currentTimeMillis();
        return now >= startTime.getTime() && now < endTime.getTime();
    }

}
