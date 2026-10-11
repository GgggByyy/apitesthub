package com.apitesthub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 用例创建/更新请求。assertions 为 JSON 数组字符串，
 * 服务端会反序列化校验每条断言的 type/op 合法性。
 */
public class TestCaseRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 100)
    private String name;

    @NotNull(message = "apiId must not be null")
    private Long apiId;

    private Integer sortOrder;

    /** JSON 数组，如 [{"type":"status","op":"eq","expect":200}]；可为空 = 恒通过 */
    private String assertions;

    private Integer enabled;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getApiId() {
        return apiId;
    }

    public void setApiId(Long apiId) {
        this.apiId = apiId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getAssertions() {
        return assertions;
    }

    public void setAssertions(String assertions) {
        this.assertions = assertions;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
