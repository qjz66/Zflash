package cn.wolfcode.common.web.interceptor;

import cn.wolfcode.common.constants.CommonConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;


/* 所有通过 Feign 客户端发起的请求都会经过这个拦截器 */
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        /* 标识当前即将要发起的请求为 Feign 接口调用 */
        template.header(CommonConstants.FEIGN_REQUEST_KEY, CommonConstants.FEIGN_REQUEST_TRUE);
    }
}
