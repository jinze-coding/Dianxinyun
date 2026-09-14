package com.example.siteplatform.safetycommittee;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@MapperScan(basePackageClasses=CommitteeRecordMapper.class, annotationClass=Mapper.class)
public class CommitteeConfiguration {
    @Bean public ThreadPoolTaskScheduler committeePreviewScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("committee-preview-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
