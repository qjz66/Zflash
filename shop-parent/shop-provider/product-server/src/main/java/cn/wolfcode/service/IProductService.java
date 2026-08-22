package cn.wolfcode.service;

import cn.wolfcode.domain.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public interface IProductService {

    /**
     * 根据 id 列表查询商品列表
     *
     * @param ids
     * @return
     */
    List<Product> selectProductListByIds(ArrayList<Long> ids);
}
