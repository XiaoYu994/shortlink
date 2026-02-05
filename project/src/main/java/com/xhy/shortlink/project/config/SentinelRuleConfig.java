/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xhy.shortlink.project.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
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
        // 每秒 QPS 200
        createOrderRule.setCount(200); // 建议值：100 - 300
        flowRules.add(createOrderRule);
        FlowRuleManager.loadRules(flowRules);

        // 跳转短链接接口限流
        ParamFlowRule redirectHotParamRule = new ParamFlowRule("short_link_redirect")
                .setParamIdx(0) // The first parameter (shortUri)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(3000); // 建议值：2000 - 5000
        ParamFlowRuleManager.loadRules(java.util.Collections.singletonList(redirectHotParamRule));
    }
}
