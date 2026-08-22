package cn.wolfcode.mapper;

import cn.wolfcode.domain.Product;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductMapper {
    /**
     * 根据用户传入的id集合查询商品对象信息
     *
     * @param ids
     * @return
     */
    List<Product> queryProductByIds(@Param("ids") Collection<Long> ids);
}
