package com.apitesthub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ScheduleJobRequest {

    @NotNull(message = "suiteId must not be null")
    private Long suiteId;

    /** Quartz cron：秒 分 时 日 月 周，如 0 * * * * ? 表示每分钟 */
    @NotBlank(message = "cronExpr must not be blank")
    private String cronExpr;

    private Integer enabled;

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
}
