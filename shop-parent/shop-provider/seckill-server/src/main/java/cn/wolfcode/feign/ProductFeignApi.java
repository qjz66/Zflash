package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.feign.fallback.ProductFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

@FeignClient(name = "product-service", fallback = ProductFeignFallback.class)
public interface ProductFeignApi {

    @RequestMapping("/product/selectProductListByIds")
    Result<List<Product>> selectProductListByIds(@RequestParam("ids") Set<Long> ids);
}
