package com.apitesthub.controller;

import com.apitesthub.common.ApiResponse;
import com.apitesthub.dto.ScheduleJobRequest;
import com.apitesthub.entity.ScheduleJob;
import com.apitesthub.service.ScheduleJobService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 定时回归计划管理：cron 到点自动执行套件（trigger_type=SCHEDULED）。
 */
@RestController
public class ScheduleJobController {

    private final ScheduleJobService scheduleJobService;

    public ScheduleJobController(ScheduleJobService scheduleJobService) {
        this.scheduleJobService = scheduleJobService;
    }

    @GetMapping("/api/projects/{projectId}/schedules")
    public ApiResponse<List<ScheduleJob>> list(@PathVariable Long projectId,
                                               @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(scheduleJobService.listByProject(projectId, userId));
    }

    @PostMapping("/api/projects/{projectId}/schedules")
    public ApiResponse<ScheduleJob> create(@PathVariable Long projectId,
                                           @Valid @RequestBody ScheduleJobRequest req,
                                           @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(scheduleJobService.create(projectId, req, userId));
    }

    @PutMapping("/api/schedules/{id}")
    public ApiResponse<ScheduleJob> update(@PathVariable Long id,
                                           @Valid @RequestBody ScheduleJobRequest req,
                                           @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(scheduleJobService.update(id, req, userId));
    }

    @DeleteMapping("/api/schedules/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestAttribute("userId") Long userId) {
        scheduleJobService.delete(id, userId);
        return ApiResponse.ok();
    }
}
