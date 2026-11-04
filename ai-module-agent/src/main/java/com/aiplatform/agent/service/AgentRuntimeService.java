package com.aiplatform.agent.service;

import com.aiplatform.agent.dto.AgentDebugRequest;
import com.aiplatform.agent.dto.AgentExecutionQuery;
import com.aiplatform.agent.vo.AgentExecutionDetailVO;
import com.aiplatform.agent.vo.AgentExecutionVO;
import com.aiplatform.common.api.PageResult;

public interface AgentRuntimeService {

    AgentExecutionDetailVO debug(Long agentId, AgentDebugRequest request);

    PageResult<AgentExecutionVO> executions(AgentExecutionQuery query);

    AgentExecutionDetailVO executionDetail(Long executionId);
}
