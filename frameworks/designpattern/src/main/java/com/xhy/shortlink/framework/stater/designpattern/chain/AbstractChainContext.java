package com.xhy.shortlink.framework.stater.designpattern.chain;

import com.xhy.shortlink.framework.starter.bases.ApplicationContextHolder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 抽象责任链上下文
 */
public final class AbstractChainContext<T> implements CommandLineRunner {

    private final Map<String, List<AbstractChainHandler>> abstractChainHandlerContainer =  new HashMap<>();

    /**
     * 责任链组件执行
     *
     * @param mark         责任链组件标识
     * @param requestParam 请求参数
     */
    public void handler(String mark, T requestParam) {
        final List<AbstractChainHandler> abstractChainHandlers = abstractChainHandlerContainer.get(mark);
        if (CollectionUtils.isEmpty(abstractChainHandlers)) {
            throw new RuntimeException(String.format("[%s] Chain of Responsibility ID is undefined.", mark));
        }
        for (AbstractChainHandler handler : abstractChainHandlers) {
            boolean continueChain = handler.handler(requestParam);
            if (!continueChain) {
                break;
            }
        }
    }

    @Override
    public void run(String... args) throws Exception {
        ApplicationContextHolder.getBeansOfType(AbstractChainHandler.class)
                .forEach((beanName, bean) -> {
                    List<AbstractChainHandler> abstractChainHandlers = abstractChainHandlerContainer.get(bean.mark());
                    if (CollectionUtils.isEmpty(abstractChainHandlers)) {
                        abstractChainHandlers = new ArrayList<>();
                    }
                    abstractChainHandlers.add(bean);
                    List<AbstractChainHandler> actualAbstractChainHandlers = abstractChainHandlers.stream()
                            .sorted(Comparator.comparing(Ordered::getOrder))
                            .collect(Collectors.toList());
                    abstractChainHandlerContainer.put(bean.mark(), actualAbstractChainHandlers);
                });
    }
}
