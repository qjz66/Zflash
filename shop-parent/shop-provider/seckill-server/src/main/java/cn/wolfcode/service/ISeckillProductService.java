package cn.wolfcode.service;

import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;

import java.util.List;


public interface ISeckillProductService {

    /**
     * 根据时间查询秒杀商品信息
     *
     * @param time
     * @return
     */
    List<SeckillProductVo> selectByTime(Integer time);

    /**
     * 根据时间查询秒杀商品信息，从redis查
     *
     * @param time
     * @return
     */
    List<SeckillProductVo> selectByTimeFromRedis(Integer time);

    /**
     * 根据秒杀id+场次查询秒杀商品信息
     *
     * @param seckillId
     * @param time
     * @return
     */
    SeckillProductVo findByIdAndTimeFromRedis(Long seckillId, Integer time);

    /**
     * 扣秒杀商品库存
     *
     * @param vo
     */
    void decrStockCount(SeckillProductVo vo);

    SeckillProduct findByIdAndTime(Long seckillId, Integer time);

    void incrStockCount(Long seckillId);
}
