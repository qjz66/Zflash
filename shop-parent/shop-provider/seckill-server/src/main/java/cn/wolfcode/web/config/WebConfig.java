package cn.wolfcode.web.config;

import cn.wolfcode.common.web.interceptor.FeignRequestInterceptor;
import cn.wolfcode.common.web.interceptor.RequireLoginInterceptor;
import cn.wolfcode.common.web.resolver.UserInfoMethodArgumentResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;


@Configuration
public class WebConfig implements WebMvcConfigurer {
    // 配置线程池用来做异步servlet
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 1. 设置自定义的线程池
        configurer.setTaskExecutor(mvcTaskExecutor());
        // 2. 设置全局异步请求超时时间（毫秒），可选
        configurer.setDefaultTimeout(3000);
    }

    @Bean
    public ThreadPoolTaskExecutor mvcTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：池中始终存活的线程数[reference:3]
        executor.setCorePoolSize(16);
        // 最大线程数：池中允许的最大线程数[reference:5]
        executor.setMaxPoolSize(200);
        // 队列容量：当核心线程满时，任务存放的队列大小[reference:6]
        executor.setQueueCapacity(200);
        // 线程空闲存活时间[reference:7]
        executor.setKeepAliveSeconds(60);
        // 线程名称前缀
        executor.setThreadNamePrefix("mvc-async-");
        // 初始化线程池
        executor.initialize();
        return executor;
    }

    @Bean
    public RequireLoginInterceptor requireLoginInterceptor(StringRedisTemplate redisTemplate) {
        return new RequireLoginInterceptor(redisTemplate);
    }

    @Bean
    public FeignRequestInterceptor feignRequestInterceptor() {
        return new FeignRequestInterceptor();
    }

    @Autowired
    private RequireLoginInterceptor requireLoginInterceptor;

    @Bean
    public UserInfoMethodArgumentResolver userInfoMethodArgumentResolver(){
        return new UserInfoMethodArgumentResolver();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requireLoginInterceptor)
                .addPathPatterns("/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(userInfoMethodArgumentResolver());
    }
}
