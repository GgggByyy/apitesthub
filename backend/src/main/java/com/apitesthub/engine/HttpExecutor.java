package com.apitesthub.engine;

import com.apitesthub.dto.TryResult;
import com.apitesthub.entity.ApiInfo;
import com.apitesthub.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * HTTP 执行单元：按 ApiInfo 定义构造真实请求并发出，采集 状态码/耗时/响应头/响应体。
 * 由 M1 的"发送试一下"重构而来——试调与执行引擎共用同一执行单元，行为一致。
 */
@Component
public class HttpExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** OkHttpClient 线程安全，单例复用连接池（避免每次请求重建 TCP 连接） */
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public TryResult execute(ApiInfo api) {
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

    /** 请求快照：落库 exec_result，报告页可回看"实际发出了什么" */
    public String buildRequestSnapshot(ApiInfo api) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("method", api.getMethod());
        snapshot.put("url", buildUrlWithParams(api));
        snapshot.put("headers", parseJsonMap(api.getHeaders()));
        snapshot.put("body", api.getBody());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (IOException e) {
            return "{}";
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
