package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.feign.ProductFeignApi;
import cn.wolfcode.mapper.SeckillProductMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;


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
    @Autowired
    private RedisScript<Boolean> redisScript;

    @Override
    public List<SeckillProductVo> selectByTime(Integer time) {
        // 1. 基于 time 将对应的秒杀商品列表查询出来
//        List<SeckillProduct> seckillProductList = seckillProductMapper.queryCurrentlySeckillProduct(time);
//
//        if (seckillProductList.size() == 0) {
//            return Collections.emptyList();
//        }
//
//        // 2. 将秒杀商品列表转换为一个商品 id 列表
//        Set<Long> productIdList = new HashSet<>();
//        for (SeckillProduct seckillProduct : seckillProductList) {
//            // 添加商品 id
//            productIdList.add(seckillProduct.getProductId());
//        }
//        // 3. 调用商品服务接口，获取商品列表 Feign
//        Result<List<Product>> productResult = productFeignApi.selectProductListByIds(productIdList);
//        if (productResult == null || productResult.hasError()) {
//            // 如果出现降级，直接抛出异常
//            throw new BusinessException(CommonCodeMsg.ILLEGAL_OPERATION);
//        }
//        // 4. 将商品信息和秒杀商品信息聚合到 vo 对象中，并添加到 vo list
//        Map<Long, Product> tmp = new HashMap<>();
//        for (Product product : productResult.getData()) {
//            tmp.put(product.getId(), product);
//        }
//
//        List<SeckillProductVo> voList = new ArrayList<>();
//        for (SeckillProduct seckillProduct : seckillProductList) {
//            SeckillProductVo vo = new SeckillProductVo();
//            // 将商品信息拷贝到 vo 对象中
//            Product product = tmp.get(seckillProduct.getProductId());
//            BeanUtils.copyProperties(product, vo);
//
//            // 将秒杀商品信息拷贝到 vo 对象中
//            BeanUtils.copyProperties(seckillProduct, vo);
//
//            // 将 vo 添加到集合中
//            voList.add(vo);
//        }
//
//        // 5. 返回 vo list
//        return voList;

        // 1.获取秒杀商品列表
        List<SeckillProduct> seckillProductList = seckillProductMapper.queryCurrentlySeckillProduct(time);
        if(seckillProductList.size() == 0) {
            return Collections.emptyList();
        }
        // 加入redis缓存
        // 2. 组装缓存 Key（基于场次/时间维度）
        String listKey = SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(String.valueOf(time));
        String stockKey = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(String.valueOf(time));

        // 3. 批量组装 Map，避免在 for 循环中频繁进行 Redis 网络 IO（推荐使用 putAll）
        Map<String, String> productMap = new HashMap<>();
        Map<String, String> stockMap = new HashMap<>();

        for (SeckillProduct product : seckillProductList) {
            String field = String.valueOf(product.getId());
            // 缓存商品详情 JSON
            productMap.put(field, JSON.toJSONString(product));
            // 缓存商品库存（作为独立计数字段）
            stockMap.put(field, String.valueOf(product.getStockCount()));
        }

        // 4. 批量写入 Redis Hash
        redisTemplate.opsForHash().putAll(listKey, productMap);
        redisTemplate.opsForHash().putAll(stockKey, stockMap);

        // 5. 设置过期时间（防止冷数据常驻内存，通常设置为场次结束后的若干小时/天）
        redisTemplate.expire(listKey, 1, TimeUnit.DAYS);
        redisTemplate.expire(stockKey, 1, TimeUnit.DAYS);

        // 数据库聚合 通过seckillProduct id集合查询product列表 再将两个集合的信息聚合到seckillProductVO列表中
        // 2.获取product id列表 根据ids查询products
        Set<Long> productsIdList = new HashSet<>();
        for(SeckillProduct seckillProduct : seckillProductList) {
            productsIdList.add(seckillProduct.getProductId());
        }
        Result<List<Product>> productResult = productFeignApi.selectProductListByIds(productsIdList);
        if(productResult == null || productResult.hasError()){
            log.warn("[秒杀查询]Products查询失败...");
            throw new BusinessException(CommonCodeMsg.ILLEGAL_OPERATION);
        }


        // 3.建一个Hash表存<id,product> 将product和seckillProduct聚合
        HashMap<Long, Product> productHashMap = new HashMap<>();
        for(Product product : productResult.getData()) {
            productHashMap.put(product.getId(), product);
        }

        List<SeckillProductVo> voList = new ArrayList<>();
        for(SeckillProduct seckillProduct: seckillProductList) {
            SeckillProductVo seckillProductVo = new SeckillProductVo();
            Product product = productHashMap.get(seckillProduct.getProductId());

            BeanUtils.copyProperties(product, seckillProductVo);
            BeanUtils.copyProperties(seckillProduct, seckillProductVo);

            voList.add(seckillProductVo);
        }

        return voList;
    }

    @Override
    public List<SeckillProductVo> selectByTimeFromRedis(Integer time) {
        // key = seckillProductList:10
        // value = Map<seckillId, voJson>

        List<Object> values = redisTemplate.opsForHash().values(SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time + ""));
        // 如果 redis 中查询为空，就直接返回空集合
        if (values.size() == 0) {
            log.warn("[秒杀查询]：Reids中没有数据，从DB中开查询...");
            return selectByTime(time);
        }

        // 返回的结果
        List<SeckillProduct> seckillProductList = new ArrayList<>();
        for (Object json : values) {
            // 将 json 字符串转换为 vo 对象，并存储到 vo list 中
            seckillProductList.add(JSON.parseObject(json.toString(), SeckillProduct.class));
        }


        Set<Long> productsIdList = new HashSet<>();
        for(SeckillProduct seckillProduct : seckillProductList) {
            productsIdList.add(seckillProduct.getProductId());
        }
        Result<List<Product>> productResult = productFeignApi.selectProductListByIds(productsIdList);
        if(productResult == null || productResult.hasError()){
            log.warn("[秒杀查询]Products查询失败...");
            throw new BusinessException(CommonCodeMsg.ILLEGAL_OPERATION);
        }


        // 3.建一个Hash表存<id,product> 将product和seckillProduct聚合
        HashMap<Long, Product> productHashMap = new HashMap<>();
        for(Product product : productResult.getData()) {
            productHashMap.put(product.getId(), product);
        }

        List<SeckillProductVo> voList = new ArrayList<>();
        for(SeckillProduct seckillProduct: seckillProductList) {
            SeckillProductVo seckillProductVo = new SeckillProductVo();
            Product product = productHashMap.get(seckillProduct.getProductId());
//            log.info("product name :",product.getProductName());
            BeanUtils.copyProperties(product, seckillProductVo);
            BeanUtils.copyProperties(seckillProduct, seckillProductVo);

            voList.add(seckillProductVo);
        }

//        log.info(voList.get(0).getProductName());

        return voList;
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
        SeckillProductVo seckillProductVo =  JSON.parseObject(json.toString(), SeckillProductVo.class);
        Set<Long> ids = new HashSet<>();
        ids.add(seckillProductVo.getProductId());
        Result<List<Product>> productResult = productFeignApi.selectProductListByIds(ids);
        if (productResult == null || productResult.hasError() || productResult.getData() == null) {
            log.error("[秒杀商品查询] 远程获取商品基础信息失败 productId={}", seckillProductVo.getProductId());
            return seckillProductVo; // 或者根据业务需要抛出异常/返回 null
        }

        // 4. 将基础商品信息 (商品名称、图片、原价、描述等) 合并/填充到 VO 中
        Long id = seckillProductVo.getId();
        List<Product> products = productResult.getData();
        Product product = products.get(0);
        // 方式 A：如果 VO 内部嵌套了 Product 对象，直接 set
        // seckillProductVo.setProduct(productVo);

        // 方式 B：如果是扁平化字段，使用 BeanUtils 拷贝属性（注意避免覆盖秒杀专属价格 seckillPrice）
        BeanUtils.copyProperties(product, seckillProductVo);
        seckillProductVo.setId(id);


        return seckillProductVo;
    }

    @Override
    public void decrStockCount(SeckillProductVo vo) {
        String key = "seckill:product:stockcount:" + vo.getTime() + ":" + vo.getId();
        String threadId = IdGenerateUtil.get().nextId()+"";
        try {
            // 尝试获取锁
            int count = 0;
            do {
                Boolean res = redisTemplate.execute(redisScript, Collections.singletonList(key), threadId, "10");
                if(res) {
                    break;
                }

                if((count++) > 5) {
                    throw new BusinessException(new CodeMsg(501,"[秒杀]减库存失败..."));
                }
                Thread.sleep(10);
            }while (true);

            // 查库存先扣除 MySQL 库存
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
        } catch (BusinessException e) {
            throw e;                       // 业务异常原样抛出，ControllerAdvice 会返回"您来晚了"
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            String value = redisTemplate.opsForValue().get(key);
            if(threadId.equals(value)) {
                redisTemplate.delete(key);
            }

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
