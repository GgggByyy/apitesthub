package com.apitesthub.service;

import com.apitesthub.dto.ScheduleJobRequest;
import com.apitesthub.entity.Project;
import com.apitesthub.entity.ScheduleJob;
import com.apitesthub.entity.TestSuite;
import com.apitesthub.exception.BizException;
import com.apitesthub.mapper.ProjectMapper;
import com.apitesthub.mapper.ScheduleJobMapper;
import com.apitesthub.mapper.TestSuiteMapper;
import com.apitesthub.scheduler.SuiteScheduleJob;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时回归计划管理（M5）：schedule_job 表 CRUD + Quartz 动态注册/删除。
 *
 * 幂等与并发（面试点）：
 *  - cron 到点触发与手动触发可能撞车 → 复用 M4 分布式锁（executeSuite 内置），
 *    拿不到锁的触发直接失败，不会重复执行同一套件；
 *  - @DisallowConcurrentExecution 防 Quartz 自身对同一 Job 的并发触发；
 *  - 单机内存注册 + 数据库持久配置：应用重启时重新注册全部 enabled 计划
 *    （Quartz 的 JDBC JobStore 是集群方案，单机内存调度够用且好讲）。
 */
@Service
public class ScheduleJobService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleJobService.class);
    private static final String GROUP = "suite-schedule";

    private final ScheduleJobMapper jobMapper;
    private final SuiteService suiteService;
    private final ExecutionService executionService;
    private final ProjectService projectService;
    private final TestSuiteMapper suiteMapper;
    private final ProjectMapper projectMapper;
    private final Scheduler scheduler;

    public ScheduleJobService(ScheduleJobMapper jobMapper, SuiteService suiteService,
                              ExecutionService executionService, ProjectService projectService,
                              TestSuiteMapper suiteMapper, ProjectMapper projectMapper,
                              Scheduler scheduler) {
        this.jobMapper = jobMapper;
        this.suiteService = suiteService;
        this.executionService = executionService;
        this.projectService = projectService;
        this.suiteMapper = suiteMapper;
        this.projectMapper = projectMapper;
        this.scheduler = scheduler;
    }

    private ScheduleJob getOwnedJob(Long jobId, Long userId) {
        ScheduleJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(404, "schedule job not found");
        }
        suiteService.getOwnedSuite(job.getSuiteId(), userId);
        return job;
    }

    public List<ScheduleJob> listByProject(Long projectId, Long userId) {
        projectService.getOwnedProject(projectId, userId);
        List<Long> suiteIds = suiteService.listByProject(projectId, userId)
                .stream().map(s -> s.getId()).toList();
        if (suiteIds.isEmpty()) {
            return List.of();
        }
        return jobMapper.selectList(new LambdaQueryWrapper<ScheduleJob>()
                .in(ScheduleJob::getSuiteId, suiteIds)
                .orderByDesc(ScheduleJob::getId));
    }

    public ScheduleJob create(Long projectId, ScheduleJobRequest req, Long userId) {
        projectService.getOwnedProject(projectId, userId);
        validateCron(req.getCronExpr());
        suiteService.getOwnedSuite(req.getSuiteId(), userId);
        ScheduleJob job = new ScheduleJob();
        job.setSuiteId(req.getSuiteId());
        job.setCronExpr(req.getCronExpr());
        job.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        jobMapper.insert(job);
        if (job.getEnabled() == 1) {
            registerQuartz(job);
        }
        return job;
    }

    public ScheduleJob update(Long jobId, ScheduleJobRequest req, Long userId) {
        ScheduleJob job = getOwnedJob(jobId, userId);
        validateCron(req.getCronExpr());
        suiteService.getOwnedSuite(req.getSuiteId(), userId);
        removeQuartz(jobId);
        job.setSuiteId(req.getSuiteId());
        job.setCronExpr(req.getCronExpr());
        job.setEnabled(req.getEnabled() == null ? job.getEnabled() : req.getEnabled());
        jobMapper.updateById(job);
        if (job.getEnabled() == 1) {
            registerQuartz(job);
        }
        return job;
    }

    public void delete(Long jobId, Long userId) {
        getOwnedJob(jobId, userId);
        removeQuartz(jobId);
        jobMapper.deleteById(jobId);
    }

    /** Quartz 到点回调：更新触发时间并以套件归属人身份执行（trigger_type=SCHEDULED） */
    public void fire(Long jobId) {
        ScheduleJob job = jobMapper.selectById(jobId);
        if (job == null || job.getEnabled() != 1) {
            return;
        }
        Long ownerUserId = resolveOwner(job.getSuiteId());
        if (ownerUserId == null) {
            return;
        }
        job.setLastFireTime(LocalDateTime.now());
        jobMapper.updateById(job);
        try {
            executionService.executeSuite(job.getSuiteId(), ownerUserId, "SCHEDULED");
            log.info("scheduled suite {} fired, owner={}", job.getSuiteId(), ownerUserId);
        } catch (BizException e) {
            // 409 等：上一次触发还在执行（分布式锁生效）——定时器的正常幂等表现
            log.info("scheduled fire skipped: {}", e.getMessage());
        }
    }

    /** 系统内部解析套件归属人（不经用户归属校验，直接查表） */
    private Long resolveOwner(Long suiteId) {
        TestSuite suite = suiteMapper.selectById(suiteId);
        if (suite == null) {
            return null;
        }
        Project project = projectMapper.selectById(suite.getProjectId());
        return project == null ? null : project.getOwnerId();
    }

    private void validateCron(String cron) {
        if (!CronExpression.isValidExpression(cron)) {
            throw new BizException(400, "invalid quartz cron: " + cron
                    + " (expected e.g. '0 * * * * ?' for every minute)");
        }
    }

    private void registerQuartz(ScheduleJob job) {
        try {
            JobDetail detail = JobBuilder.newJob(SuiteScheduleJob.class)
                    .withIdentity(jobKey(job.getId()), GROUP)
                    .usingJobData("jobId", job.getId())
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger_" + job.getId(), GROUP)
                    .withSchedule(CronScheduleBuilder.cronSchedule(job.getCronExpr()))
                    .build();
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            throw new BizException(500, "register quartz job failed: " + e.getMessage());
        }
    }

    private void removeQuartz(Long jobId) {
        try {
            scheduler.deleteJob(JobKey.jobKey("job_" + jobId, GROUP));
        } catch (SchedulerException e) {
            log.warn("delete quartz job failed: {}", e.getMessage());
        }
    }

    private static String jobKey(Long jobId) {
        return "job_" + jobId;
    }

    /** 应用启动后重新注册全部 enabled 计划（内存调度器不持久，配置在库里） */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAllOnStartup() {
        List<ScheduleJob> jobs = jobMapper.selectList(
                new LambdaQueryWrapper<ScheduleJob>().eq(ScheduleJob::getEnabled, 1));
        for (ScheduleJob job : jobs) {
            try {
                registerQuartz(job);
                log.info("registered schedule job {} (suite={}, cron={})",
                        job.getId(), job.getSuiteId(), job.getCronExpr());
            } catch (Exception e) {
                log.error("register job {} failed: {}", job.getId(), e.getMessage());
            }
        }
    }
}
