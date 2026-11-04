package com.aiplatform.agent.service;

import com.aiplatform.agent.dto.AgentQuery;
import com.aiplatform.agent.dto.AgentRequest;
import com.aiplatform.agent.vo.AgentVO;
import com.aiplatform.agent.vo.AgentVersionVO;
import com.aiplatform.common.api.PageResult;

import java.util.List;

public interface AgentService {

    PageResult<AgentVO> page(AgentQuery query);

    AgentVO get(Long id);

    AgentVO create(AgentRequest request);

    AgentVO update(Long id, AgentRequest request);

    void delete(Long id);

    void setEnabled(Long id, boolean enabled);

    List<AgentVersionVO> versions(Long id);
}
