package com.apitesthub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 定时回归计划：cron 到点自动触发对应套件（trigger_type=SCHEDULED）。
 */
@TableName("schedule_job")
public class ScheduleJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long suiteId;

    private String cronExpr;

    private Integer enabled;

    private LocalDateTime lastFireTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
    }

    public String getCronExpr() {
        return cronExpr;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getLastFireTime() {
        return lastFireTime;
    }

    public void setLastFireTime(LocalDateTime lastFireTime) {
        this.lastFireTime = lastFireTime;
    }
}
