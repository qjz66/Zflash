package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.feign.ProductFeignApi;
import cn.wolfcode.mapper.SeckillProductMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;


@Slf4j
@Service
public class SeckillProductServiceImpl implements ISeckillProductService {

    @Autowired
    private SeckillProductMapper seckillProductMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private ProductFeignApi productFeignApi;

    @Override
    public List<SeckillProductVo> selectByTime(Integer time) {
        // 1. 基于 time 将对应的秒杀商品列表查询出来
        List<SeckillProduct> seckillProductList = seckillProductMapper.queryCurrentlySeckillProduct(time);

        if (seckillProductList.size() == 0) {
            return Collections.emptyList();
        }

        // 2. 将秒杀商品列表转换为一个商品 id 列表
        Set<Long> productIdList = new HashSet<>();
        for (SeckillProduct seckillProduct : seckillProductList) {
            // 添加商品 id
            productIdList.add(seckillProduct.getProductId());
        }
        // 3. 调用商品服务接口，获取商品列表 Feign
        Result<List<Product>> productResult = productFeignApi.selectProductListByIds(productIdList);
        if (productResult == null || productResult.hasError()) {
            // 如果出现降级，直接抛出异常
            throw new BusinessException(CommonCodeMsg.ILLEGAL_OPERATION);
        }
        // 4. 将商品信息和秒杀商品信息聚合到 vo 对象中，并添加到 vo list
        Map<Long, Product> tmp = new HashMap<>();
        for (Product product : productResult.getData()) {
            tmp.put(product.getId(), product);
        }

        List<SeckillProductVo> voList = new ArrayList<>();
        for (SeckillProduct seckillProduct : seckillProductList) {
            SeckillProductVo vo = new SeckillProductVo();
            // 将商品信息拷贝到 vo 对象中
            Product product = tmp.get(seckillProduct.getProductId());
            BeanUtils.copyProperties(product, vo);

            // 将秒杀商品信息拷贝到 vo 对象中
            BeanUtils.copyProperties(seckillProduct, vo);

            // 将 vo 添加到集合中
            voList.add(vo);
        }

        // 5. 返回 vo list
        return voList;
    }

    @Override
    public List<SeckillProductVo> selectByTimeFromRedis(Integer time) {
        // key = seckillProductList:10
        // value = Map<seckillId, voJson>

        List<Object> values = redisTemplate.opsForHash().values(SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time + ""));
        // 如果 redis 中查询为空，就直接返回空集合
        if (values.size() == 0) {
            return selectByTime(time);
        }

        // 返回的结果
        List<SeckillProductVo> result = new ArrayList<>();
        for (Object json : values) {
            // 将 json 字符串转换为 vo 对象，并存储到 vo list 中
            result.add(JSON.parseObject(json.toString(), SeckillProductVo.class));
        }
        return result;
    }

    @Override
    public SeckillProductVo findByIdAndTimeFromRedis(Long seckillId, Integer time) {
        // key = seckillProductList:10
        // value = Map<seckillId, voJson>
        Object json = redisTemplate.opsForHash().get(SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time + ""), seckillId + "");
        if (json == null) {
            log.warn("[秒杀商品查询] 查询秒杀商品详情信息失败 seckillId={}, time={}", seckillId, time);
            return null;
        }
        return JSON.parseObject(json.toString(), SeckillProductVo.class);
    }

    @Override
    public void decrStockCount(SeckillProductVo vo) {
        // 先扣除 MySQL 库存
        int ret = seckillProductMapper.decrStock(vo.getId());
        if (ret > 0) {
            // 扣除 redis 库存
            vo.setStockCount(vo.getStockCount() - 1);
            String realKey = SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(vo.getTime() + "");
            redisTemplate.opsForHash().put(realKey, vo.getId() + "", JSON.toJSONString(vo));
        } else {
            // 乐观锁生效
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
    }

    @Override
    public SeckillProduct findByIdAndTime(Long seckillId, Integer time) {
        return seckillProductMapper.selectByIdAndTime(seckillId, time);
    }

    @Override
    public void incrStockCount(Long seckillId) {
        seckillProductMapper.incrStock(seckillId);
    }
}
