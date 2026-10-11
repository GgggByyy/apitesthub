package com.apitesthub.engine;

import com.apitesthub.exception.BizException;
import com.jayway.jsonpath.JsonPath;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务级变量上下文（M3 变量体系）。
 *
 * 设计要点（面试高频）：
 * 1. ConcurrentHashMap 而非 HashMap：批次内多个工作线程会并发读写同一任务的上下文，
 *    HashMap 并发写会丢数据（1.7 头插还会成环）；CHM 1.8 用 CAS + 桶头 synchronized。
 * 2. 作为方法参数在任务闭包内传递，而不用 ThreadLocal：线程池的线程是复用的，
 *    ThreadLocal 不清理会把上一个任务的变量带给下一个任务（串数据）；
 *    Context 生命周期 = 任务生命周期，参数传递天然隔离、随任务消亡。
 * 3. 未定义变量显式失败（不静默保留原文）：测试平台必须把"变量没拿到"暴露出来。
 */
public class VariableContext {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");

    private final Map<String, String> vars = new ConcurrentHashMap<>();

    public void put(String name, String value) {
        vars.put(name, value);
    }

    public String get(String name) {
        return vars.get(name);
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(vars);
    }

    /** 收集一段文本里消费的全部变量名（{{var}} 占位符） */
    public static java.util.Set<String> placeholders(String text) {
        java.util.Set<String> names = new java.util.HashSet<>();
        if (text == null || text.isEmpty()) {
            return names;
        }
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /** 把 {{var}} 替换为上下文值；遇到未定义变量抛业务异常（显式失败） */
    public String render(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = vars.get(m.group(1));
            if (value == null) {
                throw new BizException(400, "undefined variable " + m.group());
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 按提取规则从响应体提取变量存入上下文（链式调用的关键一步） */
    public void extract(String responseBody, Iterable<ExtractRule> rules) {
        if (rules == null) {
            return;
        }
        for (ExtractRule rule : rules) {
            Object value = JsonPath.read(responseBody, rule.getSource());
            vars.put(rule.getVar(), value == null ? "" : String.valueOf(value));
        }
    }

    /** 变量提取规则：$.data.token -> token */
    public static class ExtractRule {
        private String source;
        private String var;

        public ExtractRule() {
        }

        public ExtractRule(String source, String var) {
            this.source = source;
            this.var = var;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getVar() {
            return var;
        }

        public void setVar(String var) {
            this.var = var;
        }
    }
}
