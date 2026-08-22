package cn.wolfcode.handler;

import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.redis.SeckillRedisKey;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.handler.EntryHandler;

@Slf4j
@Component
@CanalTable("t_order_info")
public class OrderInfoHandler implements EntryHandler<OrderInfo> {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void insert(OrderInfo orderInfo) {
        /* 需求：当新增订单时，将订单数据存入 redis，有效时间为 7 天 */
        String realKey = SeckillRedisKey.SECKILL_ORDER_STRING.getRealKey(orderInfo.getOrderNo());
        redisTemplate.opsForValue().set(realKey, JSON.toJSONString(orderInfo),
                SeckillRedisKey.SECKILL_ORDER_STRING.getExpireTime(),
                SeckillRedisKey.SECKILL_ORDER_STRING.getUnit());
        log.info("[订单数据变动处理器] 新增订单数据：{}", orderInfo.toString());
    }

    @Override
    public void update(OrderInfo before, OrderInfo after) {
        /* 需求：当订单数据发生变动时，需要将最新的订单数据存入 redis */
        log.info("[订单数据变动处理器] 更新订单数据-之前：{}", before.toString());
        log.info("[订单数据变动处理器] 更新订单数据-之后：{}", after.toString());
        String realKey = SeckillRedisKey.SECKILL_ORDER_STRING.getRealKey(after.getOrderNo());
        redisTemplate.opsForValue().set(realKey, JSON.toJSONString(after),
                SeckillRedisKey.SECKILL_ORDER_STRING.getExpireTime(),
                SeckillRedisKey.SECKILL_ORDER_STRING.getUnit());
    }

    @Override
    public void delete(OrderInfo orderInfo) {
        log.info("[订单数据变动处理器] 删除订单数据：{}", orderInfo.toString());
        /* 需求：删除订单时，将 redis 中的数据删除 */
        String realKey = SeckillRedisKey.SECKILL_ORDER_STRING.getRealKey(orderInfo.getOrderNo());
        redisTemplate.delete(realKey);
    }
}
