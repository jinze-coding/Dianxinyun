package com.example.siteplatform.siteaccess.material;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = MeetingMaterialMapper.class,
        annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MeetingMaterialConfiguration {
    // Keep the ordinary scheduled jobs independent of long-running conversions.
    // Scheduling itself remains controlled by app.scheduling.enabled.
    @Bean(name="taskScheduler")
    @Primary
    @ConditionalOnMissingBean(name="taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("site-scheduler-");
        return scheduler;
    }
    @Bean public ThreadPoolTaskScheduler meetingPreviewScheduler() {
        ThreadPoolTaskScheduler scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("meeting-preview-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false); return scheduler;
    }
}
