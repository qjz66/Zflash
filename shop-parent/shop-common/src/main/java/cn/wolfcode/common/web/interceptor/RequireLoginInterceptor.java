package cn.wolfcode.common.web.interceptor;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.redis.CommonRedisKey;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class RequireLoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    public RequireLoginInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断当前请求是否是一个动态请求
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            // TODO 获取了一个 Feign Request Key
            String feignRequest = request.getHeader(CommonConstants.FEIGN_REQUEST_KEY);
            if (!StringUtils.isEmpty(feignRequest)
                    && CommonConstants.FEIGN_REQUEST_FALSE.equals(feignRequest)
                    /* 判断当前请求的方法是否贴有 @RequireLogin 注解 */
                    && handlerMethod.getMethodAnnotation(RequireLogin.class) != null) {
                /* 设置响应类型为 json */
                response.setContentType("application/json;charset=utf-8");
                /* 从请求头中获取 token */
                String token = request.getHeader(CommonConstants.TOKEN_NAME);
                /* 如果 token 为空，直接响应登陆超时 */
                if (StringUtils.isEmpty(token)) {
                    response.getWriter().write(JSON.toJSONString(Result.error(CommonCodeMsg.TOKEN_INVALID)));
                    return false;
                }
                /* 基于 token 从 redis 中获取用户信息 */
                UserInfo userInfo = JSON.parseObject(redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token)), UserInfo.class);
                /* 如果获取到的用户信息为空，直接响应 token 过期 */
                if (userInfo == null) {
                    response.getWriter().write(JSON.toJSONString(Result.error(CommonCodeMsg.TOKEN_INVALID)));
                    return false;
                }

                String ip = request.getHeader(CommonConstants.REAL_IP);
                /* 从请求头中获取本次请求的客户 ip，与上次登陆的用户 ip 做比较，如果不同说明 ip 发生变化，直接响应 ip 改变，需要重新登陆 */
                if (!userInfo.getLoginIp().equals(ip)) {
                    response.getWriter().write(JSON.toJSONString(Result.error(CommonCodeMsg.LOGIN_IP_CHANGE)));
                    return false;
                }
            }
        }
        return true;
    }
}

