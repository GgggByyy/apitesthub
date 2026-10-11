package com.apitesthub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ApiInfoRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 100, message = "name too long")
    private String name;

    @NotBlank(message = "method must not be blank")
    @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH",
             message = "method must be GET/POST/PUT/DELETE/PATCH")
    private String method;

    @NotBlank(message = "url must not be blank")
    @Size(max = 500, message = "url too long")
    private String url;

    /** JSON 字符串，如 {"Content-Type":"application/json"}；可为空 */
    private String headers;

    /** JSON 字符串，查询参数；可为空 */
    private String params;

    /** JSON 字符串，请求体；可为空 */
    private String body;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
