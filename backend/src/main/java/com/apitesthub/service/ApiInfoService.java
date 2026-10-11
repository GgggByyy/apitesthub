package com.apitesthub.service;

import com.apitesthub.dto.ApiInfoRequest;
import com.apitesthub.dto.TryResult;
import com.apitesthub.entity.ApiInfo;
import com.apitesthub.entity.Project;
import com.apitesthub.exception.BizException;
import com.apitesthub.mapper.ApiInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 接口定义管理 + "发送试一下"（后端代理调用被测接口）。
 * 试调是 M2 执行引擎的第一块砖：构造请求 → 发出 → 采集 状态码/耗时/响应 的流程与引擎单步执行同构。
 */
@Service
public class ApiInfoService {

    private final ApiInfoMapper apiInfoMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    /** OkHttpClient 线程安全，单例复用连接池（避免每次请求重建 TCP 连接） */
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public ApiInfoService(ApiInfoMapper apiInfoMapper, ProjectService projectService,
                          ObjectMapper objectMapper) {
        this.apiInfoMapper = apiInfoMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
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
        apiMapperInsert(api);
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

    private void apiMapperInsert(ApiInfo api) {
        api.setUpdatedAt(java.time.LocalDateTime.now());
        apiInfoMapper.insert(api);
    }

    /** 字段拷贝 + 保存前校验 headers/params/body 是合法 JSON */
    private void applyRequest(ApiInfo api, ApiInfoRequest req) {
        api.setName(req.getName());
        api.setMethod(req.getMethod().toUpperCase());
        api.setUrl(req.getUrl());
        api.setHeaders(requireJsonOrBlank(req.getHeaders(), "headers"));
        api.setParams(requireJsonOrBlank(req.getParams(), "params"));
        api.setBody(req.getBody());
        api.setUpdatedAt(java.time.LocalDateTime.now());
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

    /**
     * 发送试一下：按定义构造真实 HTTP 请求发出，采集状态码/耗时/响应头/响应体。
     * 失败（连接拒绝/超时）抛 502——上游系统问题的语义。
     */
    public TryResult tryApi(Long apiId, Long userId) {
        ApiInfo api = getOwnedApi(apiId, userId);

        String url = buildUrlWithParams(api);
        Request.Builder builder = new Request.Builder().url(url);

        Map<String, String> headers = parseJsonMap(api.getHeaders());
        headers.forEach(builder::header);

        String method = api.getMethod().toUpperCase();
        RequestBody requestBody = null;
        if (!"GET".equals(method)) {
            String contentType = headers.getOrDefault("Content-Type", "application/json; charset=utf-8");
            String bodyStr = api.getBody() == null ? "" : api.getBody();
            requestBody = RequestBody.create(bodyStr, MediaType.parse(contentType));
        }
        Request request = builder.method(method, requestBody).build();

        long start = System.currentTimeMillis();
        try (Response response = httpClient.newCall(request).execute()) {
            long cost = System.currentTimeMillis() - start;
            String respBody = response.body() != null ? response.body().string() : "";
            if (respBody.length() > 10_000) {
                respBody = respBody.substring(0, 10_000) + "\n...(truncated, total "
                        + respBody.length() + " chars)";
            }
            Map<String, String> respHeaders = new LinkedHashMap<>();
            response.headers().forEach(p -> respHeaders.put(p.getFirst(), p.getSecond()));
            return new TryResult(response.code(), cost, respHeaders, respBody);
        } catch (IOException e) {
            throw new BizException(502, "request failed: " + e.getMessage());
        }
    }

    /** url + params(JSON) 拼接查询串，值做 URL 编码 */
    private String buildUrlWithParams(ApiInfo api) {
        Map<String, String> params = parseJsonMap(api.getParams());
        if (params.isEmpty()) {
            return api.getUrl();
        }
        String query = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return api.getUrl() + (api.getUrl().contains("?") ? "&" : "?") + query;
    }

    private Map<String, String> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> result.put(k, v == null ? "" : String.valueOf(v)));
            return result;
        } catch (IOException e) {
            throw new BizException(400, "invalid JSON: " + json);
        }
    }
}
