package cn.wolfcode.config;

import cn.wolfcode.job.SeckillProductInitJob;
import cn.wolfcode.job.UserCacheJob;
import cn.wolfcode.util.ElasticJobUtil;
import com.dangdang.ddframe.job.lite.config.LiteJobConfiguration;
import com.dangdang.ddframe.job.lite.spring.api.SpringJobScheduler;
import com.dangdang.ddframe.job.reg.base.CoordinatorRegistryCenter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessJobConfig {
    @Bean(initMethod = "init")
    public SpringJobScheduler initUserCacheJob(CoordinatorRegistryCenter registryCenter, UserCacheJob userCacheJob) {
        LiteJobConfiguration jobConfiguration = ElasticJobUtil.createDefaultSimpleJobConfiguration(userCacheJob.getClass(), userCacheJob.getCron());
        SpringJobScheduler springJobScheduler = new SpringJobScheduler(userCacheJob, registryCenter, jobConfiguration);
        return springJobScheduler;
    }

    @Bean(initMethod = "init")
    public SpringJobScheduler initSeckillProductJob(CoordinatorRegistryCenter registryCenter, SeckillProductInitJob seckillProductInitJob) {
        LiteJobConfiguration jobConfiguration =
                ElasticJobUtil.createJobConfiguration(
                        seckillProductInitJob.getClass(), /* 任务类型 */
                        seckillProductInitJob.getCron(), /* 定时任务表达式 */
                        seckillProductInitJob.getShardCount(), /* 分片数量 */
                        seckillProductInitJob.getShardingParameter(), /* 分片规则 */
                        false);

        SpringJobScheduler springJobScheduler = new SpringJobScheduler(seckillProductInitJob, registryCenter, jobConfiguration);
        return springJobScheduler;
    }
}
