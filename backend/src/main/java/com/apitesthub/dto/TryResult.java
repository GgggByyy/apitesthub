package com.apitesthub.dto;

import java.util.Map;

/**
 * "发送试一下"的代理调用结果（后续执行引擎的单步执行结果同构）。
 */
public class TryResult {

    private final int status;
    private final long timeMs;
    private final Map<String, String> headers;
    private final String body;

    public TryResult(int status, long timeMs, Map<String, String> headers, String body) {
        this.status = status;
        this.timeMs = timeMs;
        this.headers = headers;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}
