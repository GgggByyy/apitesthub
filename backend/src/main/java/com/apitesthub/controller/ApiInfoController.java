package com.apitesthub.controller;

import com.apitesthub.common.ApiResponse;
import com.apitesthub.dto.ApiInfoRequest;
import com.apitesthub.dto.TryResult;
import com.apitesthub.entity.ApiInfo;
import com.apitesthub.service.ApiInfoService;
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
 * 接口定义管理。嵌套路由 /projects/{pid}/apis 表达"接口属于项目"的从属关系；
 * 单个接口操作直接用 /api/apis/{id} 避免路径过长。
 */
@RestController
public class ApiInfoController {

    private final ApiInfoService apiInfoService;

    public ApiInfoController(ApiInfoService apiInfoService) {
        this.apiInfoService = apiInfoService;
    }

    @GetMapping("/api/projects/{projectId}/apis")
    public ApiResponse<List<ApiInfo>> list(@PathVariable Long projectId,
                                           @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(apiInfoService.listByProject(projectId, userId));
    }

    @PostMapping("/api/projects/{projectId}/apis")
    public ApiResponse<ApiInfo> create(@PathVariable Long projectId,
                                       @Valid @RequestBody ApiInfoRequest req,
                                       @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(apiInfoService.create(projectId, req, userId));
    }

    @PutMapping("/api/apis/{id}")
    public ApiResponse<ApiInfo> update(@PathVariable Long id,
                                       @Valid @RequestBody ApiInfoRequest req,
                                       @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(apiInfoService.update(id, req, userId));
    }

    @DeleteMapping("/api/apis/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestAttribute("userId") Long userId) {
        apiInfoService.delete(id, userId);
        return ApiResponse.ok();
    }

    /** 发送试一下：后端代理调用该接口并返回响应（执行引擎打底） */
    @PostMapping("/api/apis/{id}/try")
    public ApiResponse<TryResult> tryApi(@PathVariable Long id,
                                         @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(apiInfoService.tryApi(id, userId));
    }
}
