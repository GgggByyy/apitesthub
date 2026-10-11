package com.apitesthub.engine;

import com.apitesthub.dto.TryResult;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 断言评估器（纯逻辑、无依赖注入，M6 单元测试的核心靶子）。
 * 三类断言各守一道防线：
 *  - status          ：协议层——接口活着且按预期应答
 *  - body_jsonpath   ：业务层——返回的业务码/字段值符合预期（200 但 code!=0 也能抓出来）
 *  - response_time_ms：性能层——响应时间的冒烟上限
 */
public class AssertEvaluator {

    /**
     * @return 全部断言通过返回 null；否则返回失败原因（拼接每条失败断言）
     */
    public static String evaluate(TryResult result, List<Assertion> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            return null;
        }
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < assertions.size(); i++) {
            Assertion a = assertions.get(i);
            String reason = evaluateOne(result, a);
            if (reason != null) {
                failures.add("assertion[" + i + "] " + describe(a) + " -> " + reason);
            }
        }
        return failures.isEmpty() ? null : String.join("; ", failures);
    }

    private static String evaluateOne(TryResult result, Assertion a) {
        switch (a.getType() == null ? "" : a.getType()) {
            case "status":
                return compare(BigDecimal.valueOf(result.getStatus()), a, "status=" + result.getStatus());
            case "response_time_ms":
                return compare(BigDecimal.valueOf(result.getTimeMs()), a,
                        "responseTime=" + result.getTimeMs() + "ms");
            case "body_jsonpath":
                return evalJsonPath(result, a);
            default:
                return "unknown assertion type: " + a.getType();
        }
    }

    private static String evalJsonPath(TryResult result, Assertion a) {
        Object actual;
        try {
            actual = JsonPath.read(result.getBody(), a.getPath());
        } catch (PathNotFoundException e) {
            return "path not found: " + a.getPath();
        } catch (Exception e) {
            return "jsonpath eval failed: " + e.getMessage();
        }
        if (a.getOp().equals("contains")) {
            String actualStr = String.valueOf(actual);
            String expectStr = String.valueOf(a.getExpect());
            return actualStr.contains(expectStr) ? null
                    : "actual=" + actualStr + " not contains " + expectStr;
        }
        BigDecimal actualNum = toBigDecimal(actual);
        BigDecimal expectNum = toBigDecimal(a.getExpect());
        if (actualNum != null && expectNum != null) {
            return compare(actualNum, a, "actual=" + actualNum);
        }
        // 字符串比较（仅 eq/ne 有意义）
        boolean eq = String.valueOf(actual).equals(String.valueOf(a.getExpect()));
        if (a.getOp().equals("eq")) {
            return eq ? null : "actual=" + actual + ", expect=" + a.getExpect();
        }
        if (a.getOp().equals("ne")) {
            return eq ? "actual=" + actual + " should not equal " + a.getExpect() : null;
        }
        return "op " + a.getOp() + " not supported for string values";
    }

    private static String compare(BigDecimal actual, Assertion a, String actualDesc) {
        BigDecimal expect = toBigDecimal(a.getExpect());
        if (expect == null) {
            return "expect is not numeric: " + a.getExpect();
        }
        int cmp = actual.compareTo(expect);
        boolean pass = switch (a.getOp()) {
            case "eq" -> cmp == 0;
            case "ne" -> cmp != 0;
            case "gt" -> cmp > 0;
            case "lt" -> cmp < 0;
            default -> false;
        };
        if (pass) {
            return null;
        }
        return actualDesc + ", expect " + a.getOp() + " " + expect;
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String describe(Assertion a) {
        if ("body_jsonpath".equals(a.getType())) {
            return a.getType() + "(" + a.getPath() + ") " + a.getOp() + " " + a.getExpect();
        }
        return a.getType() + " " + a.getOp() + " " + a.getExpect();
    }
}
