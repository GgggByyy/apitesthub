package com.apitesthub.controller;

import com.apitesthub.common.ApiResponse;
import com.apitesthub.dto.ProjectRequest;
import com.apitesthub.entity.Project;
import com.apitesthub.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目管理。RESTful：资源用名词复数 /projects，方法表达动作，状态码表达结果。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<List<Project>> list(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(projectService.list(userId));
    }

    @PostMapping
    public ApiResponse<Project> create(@Valid @RequestBody ProjectRequest req,
                                       @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(projectService.create(req, userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<Project> update(@PathVariable Long id,
                                       @Valid @RequestBody ProjectRequest req,
                                       @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(projectService.update(id, req, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestAttribute("userId") Long userId) {
        projectService.delete(id, userId);
        return ApiResponse.ok();
    }
}
