package com.apitesthub.controller;

import com.apitesthub.common.ApiResponse;
import com.apitesthub.entity.ExecResult;
import com.apitesthub.entity.ExecTask;
import com.apitesthub.service.ExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务执行与查询。M2 为同步执行：请求发出后等全部用例跑完返回任务号；
 * M4 将引入 WebSocket 实时推送 + Redis 状态缓存。
 */
@RestController
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    /** 执行套件（同步），返回完成的任务（含通过/失败统计） */
    @PostMapping("/api/suites/{id}/execute")
    public ApiResponse<ExecTask> execute(@PathVariable Long id,
                                         @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(executionService.executeSuite(id, userId));
    }

    @GetMapping("/api/tasks")
    public ApiResponse<List<ExecTask>> list(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(executionService.listTasks(userId));
    }

    @GetMapping("/api/tasks/{id}")
    public ApiResponse<ExecTask> get(@PathVariable Long id,
                                     @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(executionService.getTask(id, userId));
    }

    @GetMapping("/api/tasks/{id}/results")
    public ApiResponse<List<ExecResult>> results(@PathVariable Long id,
                                                 @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(executionService.listResults(id, userId));
    }
}
