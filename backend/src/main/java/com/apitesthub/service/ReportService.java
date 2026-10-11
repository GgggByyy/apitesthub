package com.apitesthub.service;

import com.apitesthub.entity.ExecResult;
import com.apitesthub.entity.ExecTask;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试报告（M5）：任务汇总 + 图表数据 + 慢接口标记 + 导出。
 *
 * 慢接口阈值 800ms：一次响应慢可能是网络抖动，持续超阈值才是风险信号——
 * 当前基于单任务结果标记，趋势对比（连续 N 次）是演进方向。
 */
@Service
public class ReportService {

    static final long SLOW_THRESHOLD_MS = 800;

    private final ExecutionService executionService;

    public ReportService(ExecutionService executionService) {
        this.executionService = executionService;
    }

    public Map<String, Object> buildReport(Long taskId, Long userId) {
        ExecTask task = executionService.getTask(taskId, userId);
        List<ExecResult> results = executionService.listResults(taskId, userId);

        int pass = task.getPassCount() == null ? 0 : task.getPassCount();
        int fail = task.getFailCount() == null ? 0 : task.getFailCount();
        int total = task.getTotalCount() == null ? 0 : task.getTotalCount();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("taskId", taskId);
        report.put("suiteId", task.getSuiteId());
        report.put("triggerType", task.getTriggerType());
        report.put("status", task.getStatus());
        report.put("startedAt", task.getStartedAt());
        report.put("finishedAt", task.getFinishedAt());
        report.put("durationMs", task.getDurationMs());
        report.put("total", total);
        report.put("pass", pass);
        report.put("fail", fail);
        report.put("passRate", total == 0 ? 0 : Math.round(pass * 1000.0 / total) / 10.0);

        // 饼图：通过 / 失败
        Map<String, Object> pie = new LinkedHashMap<>();
        pie.put("pass", pass);
        pie.put("fail", fail);
        report.put("pie", pie);

        // 柱状图：用例耗时（降序 TopN）
        List<Map<String, Object>> bars = new ArrayList<>();
        results.stream()
                .sorted(Comparator.comparingInt(ExecResult::getResponseTimeMs).reversed())
                .limit(15)
                .forEach(r -> {
                    Map<String, Object> bar = new LinkedHashMap<>();
                    bar.put("caseName", r.getCaseName());
                    bar.put("timeMs", r.getResponseTimeMs());
                    bar.put("slow", r.getResponseTimeMs() != null && r.getResponseTimeMs() > SLOW_THRESHOLD_MS);
                    bars.add(bar);
                });
        report.put("bars", bars);

        // 慢接口标记：响应时间超阈值（独立于排序，含全部结果）
        List<Map<String, Object>> slowCases = new ArrayList<>();
        for (ExecResult r : results) {
            if (r.getResponseTimeMs() != null && r.getResponseTimeMs() > SLOW_THRESHOLD_MS) {
                Map<String, Object> slow = new LinkedHashMap<>();
                slow.put("caseId", r.getCaseId());
                slow.put("caseName", r.getCaseName());
                slow.put("timeMs", r.getResponseTimeMs());
                slow.put("passed", r.getPassed() == 1);
                slowCases.add(slow);
            }
        }
        report.put("slowThresholdMs", SLOW_THRESHOLD_MS);
        report.put("slowCases", slowCases);

        // 失败归因明细
        List<Map<String, Object>> failures = new ArrayList<>();
        for (ExecResult r : results) {
            if (r.getPassed() == 0) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("caseName", r.getCaseName());
                f.put("failReason", r.getFailReason());
                failures.add(f);
            }
        }
        report.put("failures", failures);
        return report;
    }

    /** 导出 HTML：自包含单文件报告（内联样式，可直接发邮件/存档） */
    public String buildHtml(Long taskId, Long userId) {
        Map<String, Object> r = buildReport(taskId, userId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bars = (List<Map<String, Object>>) r.get("bars");
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">")
          .append("<title>ApitestHub 报告 #").append(taskId).append("</title>")
          .append("<style>body{font-family:sans-serif;margin:32px;color:#333}")
          .append("h1{border-bottom:2px solid #409eff;padding-bottom:8px}")
          .append("table{border-collapse:collapse;width:100%;margin:16px 0}")
          .append("th,td{border:1px solid #ddd;padding:8px;text-align:left;font-size:14px}")
          .append("th{background:#f5f7fa}.ok{color:#67c23a;font-weight:700}")
          .append(".bad{color:#f56c6c;font-weight:700}.slow{background:#fdf6ec}")
          .append(".meta span{margin-right:24px}</style></head><body>");
        sb.append("<h1>ApitestHub 测试报告 #").append(taskId).append("</h1>");
        sb.append("<div class=\"meta\">")
          .append("<span>状态：<b>").append(r.get("status")).append("</b></span>")
          .append("<span>触发：").append(r.get("triggerType")).append("</span>")
          .append("<span>通过率：<b>").append(r.get("passRate")).append("%</b>（")
          .append(r.get("pass")).append("/").append(r.get("total")).append("）</span>")
          .append("<span>总耗时：").append(r.get("durationMs")).append("ms</span></div>");
        sb.append("<h2>耗时分布（Top").append(bars.size()).append("）</h2><table><tr><th>用例</th><th>耗时</th><th>标记</th></tr>");
        for (Map<String, Object> bar : bars) {
            boolean slow = Boolean.TRUE.equals(bar.get("slow"));
            sb.append("<tr").append(slow ? " class=\"slow\"" : "").append("><td>")
              .append(bar.get("caseName")).append("</td><td>").append(bar.get("timeMs"))
              .append("ms</td><td>").append(slow ? "🐢 慢接口(>" + SLOW_THRESHOLD_MS + "ms)" : "—")
              .append("</td></tr>");
        }
        sb.append("</table>");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) r.get("failures");
        if (!failures.isEmpty()) {
            sb.append("<h2>失败归因</h2><table><tr><th>用例</th><th>原因</th></tr>");
            for (Map<String, Object> f : failures) {
                sb.append("<tr><td>").append(f.get("caseName")).append("</td><td class=\"bad\">")
                  .append(f.get("failReason")).append("</td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }
}
