package com.xhy.shortlink.project.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/*
* 初始化限流配置
* */
@Component
public class SentinelRuleConfig implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        List<FlowRule> flowRules = new ArrayList<>();
        final FlowRule createOrderRule = new FlowRule();
        createOrderRule.setResource("create_short-link");
        createOrderRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // 每秒 QPS 20
        createOrderRule.setCount(20);
        flowRules.add(createOrderRule);
        FlowRuleManager.loadRules(flowRules);
    }
}
