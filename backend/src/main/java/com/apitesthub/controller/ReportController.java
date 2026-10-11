package com.apitesthub.controller;

import com.apitesthub.common.ApiResponse;
import com.apitesthub.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 测试报告：图表数据（前端 ECharts 渲染）+ JSON/HTML 导出。
 */
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/api/tasks/{id}/report")
    public ApiResponse<Map<String, Object>> report(@PathVariable Long id,
                                                   @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(reportService.buildReport(id, userId));
    }

    @GetMapping("/api/tasks/{id}/report/export/json")
    public ResponseEntity<byte[]> exportJson(@PathVariable Long id,
                                             @RequestAttribute("userId") Long userId) {
        byte[] body = reportService.buildReport(id, userId).toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=apitesthub-report-" + id + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @GetMapping("/api/tasks/{id}/report/export/html")
    public ResponseEntity<byte[]> exportHtml(@PathVariable Long id,
                                             @RequestAttribute("userId") Long userId) {
        byte[] body = reportService.buildHtml(id, userId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=apitesthub-report-" + id + ".html")
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(body);
    }
}
