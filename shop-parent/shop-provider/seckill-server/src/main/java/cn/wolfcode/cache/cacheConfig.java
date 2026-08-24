package cn.wolfcode.cache;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import javax.persistence.criteria.CriteriaBuilder;

@Slf4j
@Configuration
public class cacheConfig {
    @Bean
    public RedisScript<Boolean> redisScript() {
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(Boolean.class);
        redisScript.setLocation(new ClassPathResource("META-INF/scripts/redis_lock.lua"));
        return redisScript;
    }
}
