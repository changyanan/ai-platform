package com.aiplatform.tool.executor;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.tool.vo.ToolBeanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ToolBeanRegistry {

    private final ApplicationContext applicationContext;

    public PlatformTool require(String beanName) {
        Map<String, PlatformTool> beans = beans();
        PlatformTool tool = beans.get(beanName);
        if (tool == null) {
            throw BusinessException.badRequest("Java 工具 Bean 不存在：" + beanName);
        }
        return tool;
    }

    public List<ToolBeanVO> listBeans() {
        return beans().entrySet().stream()
                .map(entry -> new ToolBeanVO(
                        entry.getKey(),
                        entry.getValue().getClass().getName(),
                        entry.getValue().getClass().getSimpleName()))
                .sorted(Comparator.comparing(ToolBeanVO::beanName))
                .toList();
    }

    private Map<String, PlatformTool> beans() {
        return applicationContext.getBeansOfType(PlatformTool.class);
    }
}
