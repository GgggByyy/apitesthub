package com.apitesthub.engine;

/**
 * 单条断言的结构化表示，序列化为 JSON 存 test_case.assertions：
 * [{"type":"status","op":"eq","expect":200},
 *  {"type":"body_jsonpath","path":"$.code","op":"eq","expect":0},
 *  {"type":"response_time_ms","op":"lt","expect":800}]
 */
public class Assertion {

    /** status | body_jsonpath | response_time_ms */
    private String type;
    /** eq | ne | gt | lt | contains */
    private String op;
    /** 期望值：status/time 为数字，jsonpath 可为数字或字符串 */
    private Object expect;
    /** 仅 body_jsonpath 需要：取值路径，如 $.code */
    private String path;

    public Assertion() {
    }

    public Assertion(String type, String op, Object expect, String path) {
        this.type = type;
        this.op = op;
        this.expect = expect;
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public Object getExpect() {
        return expect;
    }

    public void setExpect(Object expect) {
        this.expect = expect;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
