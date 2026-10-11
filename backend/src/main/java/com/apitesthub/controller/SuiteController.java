package com.apitesthub.controller;

import com.apitesthub.common.ApiResponse;
import com.apitesthub.dto.SuiteRequest;
import com.apitesthub.entity.TestSuite;
import com.apitesthub.service.SuiteService;
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
 * 测试套件管理：用例的有序集合，回归的基本单位。
 */
@RestController
public class SuiteController {

    private final SuiteService suiteService;

    public SuiteController(SuiteService suiteService) {
        this.suiteService = suiteService;
    }

    @GetMapping("/api/projects/{projectId}/suites")
    public ApiResponse<List<TestSuite>> list(@PathVariable Long projectId,
                                             @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(suiteService.listByProject(projectId, userId));
    }

    @PostMapping("/api/projects/{projectId}/suites")
    public ApiResponse<TestSuite> create(@PathVariable Long projectId,
                                         @Valid @RequestBody SuiteRequest req,
                                         @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(suiteService.create(projectId, req, userId));
    }

    @PutMapping("/api/suites/{id}")
    public ApiResponse<TestSuite> update(@PathVariable Long id,
                                         @Valid @RequestBody SuiteRequest req,
                                         @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(suiteService.update(id, req, userId));
    }

    @DeleteMapping("/api/suites/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestAttribute("userId") Long userId) {
        suiteService.delete(id, userId);
        return ApiResponse.ok();
    }
}
