package com.apitesthub.service;

import com.apitesthub.dto.ProjectRequest;
import com.apitesthub.entity.ApiInfo;
import com.apitesthub.entity.Project;
import com.apitesthub.exception.BizException;
import com.apitesthub.mapper.ApiInfoMapper;
import com.apitesthub.mapper.ProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ApiInfoMapper apiInfoMapper;

    public ProjectService(ProjectMapper projectMapper, ApiInfoMapper apiInfoMapper) {
        this.projectMapper = projectMapper;
        this.apiInfoMapper = apiInfoMapper;
    }

    /**
     * 归属校验：查不到或不属于当前用户统一抛 404（不暴露资源是否存在）。
     */
    public Project getOwnedProject(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getOwnerId().equals(userId)) {
            throw new BizException(404, "project not found");
        }
        return project;
    }

    public List<Project> list(Long userId) {
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getOwnerId, userId)
                .orderByDesc(Project::getId));
    }

    public Project create(ProjectRequest req, Long userId) {
        Project project = new Project();
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        project.setOwnerId(userId);
        projectMapper.insert(project);
        return project;
    }

    public Project update(Long projectId, ProjectRequest req, Long userId) {
        Project project = getOwnedProject(projectId, userId);
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        projectMapper.updateById(project);
        return project;
    }

    /**
     * 删除项目同时删除其下全部接口定义（接口脱离项目无意义，级联删除）。
     * 加事务：两步删除要么都成功要么都回滚，防止出现"项目没了接口还在"的孤儿数据。
     */
    @Transactional
    public void delete(Long projectId, Long userId) {
        getOwnedProject(projectId, userId);
        apiInfoMapper.delete(new LambdaQueryWrapper<ApiInfo>()
                .eq(ApiInfo::getProjectId, projectId));
        projectMapper.deleteById(projectId);
    }
}
