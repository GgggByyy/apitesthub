package com.apitesthub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 套件创建/更新请求。caseIds 的顺序即执行顺序。
 */
public class SuiteRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 100)
    private String name;

    @NotEmpty(message = "caseIds must not be empty")
    private List<Long> caseIds;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Long> getCaseIds() {
        return caseIds;
    }

    public void setCaseIds(List<Long> caseIds) {
        this.caseIds = caseIds;
    }
}
