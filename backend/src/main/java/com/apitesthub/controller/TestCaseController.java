package com.apitesthub.controller;

import com.apitesthub.common.ApiResponse;
import com.apitesthub.dto.TestCaseRequest;
import com.apitesthub.entity.TestCase;
import com.apitesthub.service.TestCaseService;
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
 * 测试用例管理：用例 = 接口选择 + 断言集合 + 执行顺序。
 */
@RestController
public class TestCaseController {

    private final TestCaseService caseService;

    public TestCaseController(TestCaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/api/projects/{projectId}/cases")
    public ApiResponse<List<TestCase>> list(@PathVariable Long projectId,
                                            @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(caseService.listByProject(projectId, userId));
    }

    @PostMapping("/api/projects/{projectId}/cases")
    public ApiResponse<TestCase> create(@PathVariable Long projectId,
                                        @Valid @RequestBody TestCaseRequest req,
                                        @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(caseService.create(projectId, req, userId));
    }

    @PutMapping("/api/cases/{id}")
    public ApiResponse<TestCase> update(@PathVariable Long id,
                                        @Valid @RequestBody TestCaseRequest req,
                                        @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(caseService.update(id, req, userId));
    }

    @DeleteMapping("/api/cases/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestAttribute("userId") Long userId) {
        caseService.delete(id, userId);
        return ApiResponse.ok();
    }
}
