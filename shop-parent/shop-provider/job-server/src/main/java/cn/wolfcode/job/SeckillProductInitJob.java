package cn.wolfcode.job;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.feign.SeckillProductFeignApi;
import cn.wolfcode.redis.JobRedisKey;
import com.alibaba.fastjson.JSON;
import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.api.simple.SimpleJob;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Setter
@Getter
/* 每次读取时重新从 nacos 中读取 */
@RefreshScope
@Slf4j
public class SeckillProductInitJob implements SimpleJob {

    @Value("${jobCron.initSeckillProduct}")
    private String cron;
    @Value("${sharding.parameters.initSeckillProduct}")
    private String shardingParameter;
    @Value("${sharding.count.initSeckillProduct}")
    private Integer shardCount;

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private SeckillProductFeignApi seckillProductFeignApi;

    @Override
    public void execute(ShardingContext ctx) {
        doWork(ctx.getShardingParameter());
    }

    private void doWork(String shardingParameter) {
        log.info("----------------------------------执行秒杀商品初始化任务-开始----------------------------------");
        // 1. 远程调用秒杀服务接口获取秒杀商品vo列表
        // 正常调用：正常返回
        // 远程服务报错：被统一异常捕获，返回一个异常的 Result
        Result<List<SeckillProductVo>> result = seckillProductFeignApi.selectByTime(Integer.valueOf(shardingParameter));
        if (result.hasError()) {
            log.error("[定时任务] 初始化秒杀商品数据异常, 远程获取商品信息出错：{}", JSON.toJSONString(result));
            return;
        }
        // 2. 遍历列表保存到 redis 中，用 hash 存储，key = 前缀+time，field=seckillId,value=voJson
        List<SeckillProductVo> list = result.getData();
        if (list.size() == 0) {
            log.warn("[定时任务] 查询到当天的秒杀商品数据为空。");
            return;
        }
        for (SeckillProductVo vo : list) {
            log.info("[定时任务] 缓存秒杀商品信息：id={}, title={}, stockCount={}", vo.getId(), vo.getProductTitle(), vo.getStockCount());
            redisTemplate.opsForHash().put(
                    JobRedisKey.SECKILL_PRODUCT_LIST.getRealKey(shardingParameter), /* 基于场次作为唯一 key */
                    vo.getId() + "", /* hash 中的key == 秒杀商品id */
                    JSON.toJSONString(vo)/* vo json 字符串 */
            );
            redisTemplate.opsForHash().put(
                    JobRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(shardingParameter), /* 基于场次作为唯一 key */
                    vo.getId() + "", /* hash 中的key == 秒杀商品id */
                    vo.getStockCount() + "" /* 库存值 */
            );
        }
        log.info("----------------------------------执行秒杀商品初始化任务-结束----------------------------------");
    }


}
