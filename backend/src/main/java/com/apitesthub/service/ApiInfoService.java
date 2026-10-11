package com.apitesthub.service;

import com.apitesthub.dto.ApiInfoRequest;
import com.apitesthub.dto.TryResult;
import com.apitesthub.entity.ApiInfo;
import com.apitesthub.engine.HttpExecutor;
import com.apitesthub.exception.BizException;
import com.apitesthub.mapper.ApiInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 接口定义管理。"发送试一下"自 M2 起委托 engine.HttpExecutor 执行——
 * 试调与执行引擎共用同一执行单元，保证"试调看到的"和"回归跑到的"行为完全一致。
 */
@Service
public class ApiInfoService {

    private final ApiInfoMapper apiInfoMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final HttpExecutor httpExecutor;

    public ApiInfoService(ApiInfoMapper apiInfoMapper, ProjectService projectService,
                          ObjectMapper objectMapper, HttpExecutor httpExecutor) {
        this.apiInfoMapper = apiInfoMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
        this.httpExecutor = httpExecutor;
    }

    private ApiInfo getOwnedApi(Long apiId, Long userId) {
        ApiInfo api = apiInfoMapper.selectById(apiId);
        if (api == null) {
            throw new BizException(404, "api not found");
        }
        projectService.getOwnedProject(api.getProjectId(), userId);
        return api;
    }

    public List<ApiInfo> listByProject(Long projectId, Long userId) {
        projectService.getOwnedProject(projectId, userId);
        return apiInfoMapper.selectList(new LambdaQueryWrapper<ApiInfo>()
                .eq(ApiInfo::getProjectId, projectId)
                .orderByAsc(ApiInfo::getId));
    }

    public ApiInfo create(Long projectId, ApiInfoRequest req, Long userId) {
        projectService.getOwnedProject(projectId, userId);
        ApiInfo api = new ApiInfo();
        applyRequest(api, req);
        api.setProjectId(projectId);
        apiInfoMapper.insert(api);
        return api;
    }

    public ApiInfo update(Long apiId, ApiInfoRequest req, Long userId) {
        ApiInfo api = getOwnedApi(apiId, userId);
        applyRequest(api, req);
        apiInfoMapper.updateById(api);
        return api;
    }

    public void delete(Long apiId, Long userId) {
        getOwnedApi(apiId, userId);
        apiInfoMapper.deleteById(apiId);
    }

    /** 字段拷贝 + 保存前校验 headers/params/body 是合法 JSON */
    private void applyRequest(ApiInfo api, ApiInfoRequest req) {
        api.setName(req.getName());
        api.setMethod(req.getMethod().toUpperCase());
        api.setUrl(req.getUrl());
        api.setHeaders(requireJsonOrBlank(req.getHeaders(), "headers"));
        api.setParams(requireJsonOrBlank(req.getParams(), "params"));
        api.setBody(req.getBody());
        api.setUpdatedAt(LocalDateTime.now());
    }

    private String requireJsonOrBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
            return value;
        } catch (IOException e) {
            throw new BizException(400, field + " is not valid JSON object: " + e.getMessage());
        }
    }

    /** 发送试一下：后端代理调用该接口并返回响应 */
    public TryResult tryApi(Long apiId, Long userId) {
        ApiInfo api = getOwnedApi(apiId, userId);
        return httpExecutor.execute(api);
    }
}
