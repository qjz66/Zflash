package cn.wolfcode.feign.fallback;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.feign.ProductFeignApi;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class ProductFeignFallback implements ProductFeignApi {

    @Override
    public Result<List<Product>> selectProductListByIds(Set<Long> ids) {
        // TODO 选择降级时返回的数据
        return null;
    }
}
