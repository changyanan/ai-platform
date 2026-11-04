package com.aiplatform.tool.service;

import com.aiplatform.common.api.PageResult;
import com.aiplatform.tool.dto.ToolDebugRequest;
import com.aiplatform.tool.dto.ToolExecutionQuery;
import com.aiplatform.tool.dto.ToolQuery;
import com.aiplatform.tool.dto.ToolRequest;
import com.aiplatform.tool.executor.ToolDefinition;
import com.aiplatform.tool.vo.ToolBeanVO;
import com.aiplatform.tool.vo.ToolDebugResultVO;
import com.aiplatform.tool.vo.ToolExecutionLogVO;
import com.aiplatform.tool.vo.ToolVO;

import java.util.List;

public interface ToolService {

    PageResult<ToolVO> page(ToolQuery query);

    List<ToolBeanVO> beans();

    List<String> categories();

    ToolVO create(ToolRequest request);

    ToolVO update(Long id, ToolRequest request);

    void setEnabled(Long id, boolean enabled);

    ToolDebugResultVO debug(Long id, ToolDebugRequest request);

    PageResult<ToolExecutionLogVO> executions(ToolExecutionQuery query);

    List<ToolDefinition> findEnabledDefinitions(List<Long> toolIds);

    List<ToolDefinition> findDefinitions(List<Long> toolIds);
}
