package com.apitesthub.engine;

import com.apitesthub.entity.ApiInfo;
import com.apitesthub.entity.TestCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 依赖分析器：把套件用例切分成可并行的批次（Stage）。
 *
 * 规则（批次模型）：
 *  - 用例"消费"的变量 = 接口定义 url/headers/params/body 里的 {{var}} 占位符
 *  - 用例"生产"的变量 = extract_rules 提取的 var
 *  - 消费集合 ⊆ 已完成批次解锁的变量 → 进入当前批次（批次内并行）
 *  - 依赖尚未解锁变量（前序链式用例还没跑）→ 开新批次串行等待
 *  - 同批次用例生产的变量在整个批次完成后才解锁（并行执行中彼此不可见）
 *  - 消费了从未被任何用例生产的变量 → 不在规划期报错，执行期 VariableContext 显式失败
 *
 * 效果：链式用例（登录→token→增删改查）天然按序，无依赖用例自动并行。
 */
public class StagePlanner {

    /** 用例与其接口定义/提取规则的合并视图（依赖分析需要看到 {{var}} 与生产的变量） */
    public record CaseWithApi(TestCase testCase, ApiInfo api,
                              java.util.List<VariableContext.ExtractRule> rules) {
    }

    public static List<Stage> plan(List<CaseWithApi> cases) {
        List<Stage> stages = new ArrayList<>();
        Stage current = new Stage(stages.size());
        Set<String> available = new HashSet<>();
        Set<String> pendingProduced = new HashSet<>();

        for (CaseWithApi cwa : cases) {
            Set<String> consumed = consumedVars(cwa);
            Set<String> produced = producedVars(cwa.rules());

            if (!available.containsAll(consumed)) {
                if (!current.isEmpty()) {
                    stages.add(current);
                    available.addAll(pendingProduced);
                    pendingProduced.clear();
                }
                current = new Stage(stages.size());
            }
            current.add(cwa);
            pendingProduced.addAll(produced);
        }
        if (!current.isEmpty()) {
            stages.add(current);
        }
        return stages;
    }

    /** 用例消费的变量：接口定义四个字段里 {{var}} 的并集 */
    public static Set<String> consumedVars(CaseWithApi cwa) {
        ApiInfo api = cwa.api();
        Set<String> vars = new HashSet<>();
        vars.addAll(VariableContext.placeholders(api.getUrl()));
        vars.addAll(VariableContext.placeholders(api.getHeaders()));
        vars.addAll(VariableContext.placeholders(api.getParams()));
        vars.addAll(VariableContext.placeholders(api.getBody()));
        return vars;
    }

    /** 用例生产的变量：extract_rules 里的 var（规则 JSON 已由调用方解析传入） */
    public static Set<String> producedVars(List<VariableContext.ExtractRule> rules) {
        Set<String> vars = new HashSet<>();
        if (rules != null) {
            rules.forEach(r -> vars.add(r.getVar()));
        }
        return vars;
    }

    /** 一个批次：同批用例可并行，批次间串行 */
    public static class Stage {
        private final int index;
        private final List<CaseWithApi> cases = new ArrayList<>();

        Stage(int index) {
            this.index = index;
        }

        void add(CaseWithApi c) {
            cases.add(c);
        }

        boolean isEmpty() {
            return cases.isEmpty();
        }

        public int getIndex() {
            return index;
        }

        public List<CaseWithApi> getCases() {
            return cases;
        }
    }
}
