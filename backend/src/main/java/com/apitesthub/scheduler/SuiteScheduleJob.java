package com.apitesthub.scheduler;

import com.apitesthub.service.ScheduleJobService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 套件定时触发 Job：cron 到点 → ScheduleJobService.fire(jobId)。
 *
 * @DisallowConcurrentExecution：同一 Job 不会并发触发（Quartz 层防重入）；
 * 跨触发源（手动 vs 定时撞车）由 executeSuite 内的分布式锁兜底——两层幂等。
 * Spring Boot 的 Quartz 自动配置使用 SpringBeanJobFactory，@Autowired 可用。
 */
@DisallowConcurrentExecution
public class SuiteScheduleJob implements Job {

    @Autowired
    private ScheduleJobService scheduleJobService;

    @Override
    public void execute(JobExecutionContext context) {
        Long jobId = context.getJobDetail().getJobDataMap().getLong("jobId");
        scheduleJobService.fire(jobId);
    }
}
