package com.aiplatform.agent.service.impl;

import com.aiplatform.agent.dto.AgentQuery;
import com.aiplatform.agent.dto.AgentRequest;
import com.aiplatform.agent.entity.Agent;
import com.aiplatform.agent.entity.AgentVersion;
import com.aiplatform.agent.mapper.AgentMapper;
import com.aiplatform.agent.mapper.AgentVersionMapper;
import com.aiplatform.agent.service.AgentService;
import com.aiplatform.agent.vo.AgentVO;
import com.aiplatform.agent.vo.AgentVersionVO;
import com.aiplatform.agent.vo.ToolBindingVO;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.model.entity.ModelConfig;
import com.aiplatform.model.enums.ModelCapability;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.service.ModelRuntimeFactory;
import com.aiplatform.tool.executor.ToolDefinition;
import com.aiplatform.tool.service.ToolService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final AgentVersionMapper agentVersionMapper;
    private final ModelRuntimeFactory modelRuntimeFactory;
    private final ToolService toolService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<AgentVO> page(AgentQuery query) {
        LambdaQueryWrapper<Agent> wrapper = Wrappers.<Agent>lambdaQuery()
                .orderByAsc(Agent::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Agent::getName, keyword)
                    .or().like(Agent::getDescription, keyword));
        }
        if (query.getEnabled() != null) {
            wrapper.eq(Agent::getEnabled, query.getEnabled());
        }
        if (query.getModelId() != null) {
            wrapper.eq(Agent::getModelId, query.getModelId());
        }
        Page<Agent> page = agentMapper.selectPage(
                new Page<>(query.currentPage(), query.pageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AgentVO get(Long id) {
        return toVO(requireAgent(id));
    }

    @Override
    @Transactional
    public AgentVO create(AgentRequest request) {
        ValidatedAgent validated = validate(request);
        Agent agent = new Agent();
        agent.setName(request.name().trim());
        agent.setDescription(normalizeOptional(request.description()));
        agent.setSystemPrompt(request.systemPrompt().trim());
        agent.setModelId(request.modelId());
        agent.setToolIds(validated.toolIdsJson());
        agent.setMaxToolCalls(request.maxToolCalls());
        agent.setMaxExecutionSeconds(request.maxExecutionSeconds());
        agent.setMaxCost(validated.maxCost());
        agent.setEnabled(request.enabled() == null || request.enabled());
        agent.setIsPreset(false);
        agent.setCurrentVersion(1);
        agent.setCreatedBy(SecurityUtils.currentUserId());
        agentMapper.insert(agent);

        saveVersion(agent, 1, StringUtils.hasText(request.changeNote())
                ? request.changeNote().trim() : "创建 Agent");
        log.info("Agent {} created by {}", agent.getId(), SecurityUtils.currentUsername());
        return toVO(agentMapper.selectById(agent.getId()));
    }

    @Override
    @Transactional
    public AgentVO update(Long id, AgentRequest request) {
        Agent existing = requireAgent(id);
        ValidatedAgent validated = validate(request);
        int nextVersion = (existing.getCurrentVersion() == null ? 1 : existing.getCurrentVersion()) + 1;

        agentMapper.update(null, new LambdaUpdateWrapper<Agent>()
                .eq(Agent::getId, id)
                .set(Agent::getName, request.name().trim())
                .set(Agent::getDescription, normalizeOptional(request.description()))
                .set(Agent::getSystemPrompt, request.systemPrompt().trim())
                .set(Agent::getModelId, request.modelId())
                .set(Agent::getToolIds, validated.toolIdsJson())
                .set(Agent::getMaxToolCalls, request.maxToolCalls())
                .set(Agent::getMaxExecutionSeconds, request.maxExecutionSeconds())
                .set(Agent::getMaxCost, validated.maxCost())
                .set(Agent::getEnabled, request.enabled() == null || request.enabled())
                .set(Agent::getCurrentVersion, nextVersion)
                .set(Agent::getUpdatedAt, LocalDateTime.now()));

        Agent updated = agentMapper.selectById(id);
        saveVersion(updated, nextVersion, StringUtils.hasText(request.changeNote())
                ? request.changeNote().trim() : "更新 Agent");
        log.info("Agent {} updated to version {} by {}", id, nextVersion, SecurityUtils.currentUsername());
        return toVO(updated);
    }

    @Override
    public void delete(Long id) {
        Agent agent = requireAgent(id);
        agentMapper.deleteById(id);
        log.info("Agent {} ({}) soft-deleted by {}", id, agent.getName(), SecurityUtils.currentUsername());
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        requireAgent(id);
        agentMapper.update(null, Wrappers.<Agent>lambdaUpdate()
                .eq(Agent::getId, id)
                .set(Agent::getEnabled, enabled));
    }

    @Override
    public List<AgentVersionVO> versions(Long id) {
        requireAgent(id);
        return agentVersionMapper.selectList(Wrappers.<AgentVersion>lambdaQuery()
                        .eq(AgentVersion::getAgentId, id)
                        .orderByDesc(AgentVersion::getVersion))
                .stream()
                .map(AgentVersionVO::from)
                .toList();
    }

    private void saveVersion(Agent agent, int version, String changeNote) {
        AgentVersion snapshot = new AgentVersion();
        snapshot.setAgentId(agent.getId());
        snapshot.setVersion(version);
        snapshot.setName(agent.getName());
        snapshot.setDescription(agent.getDescription());
        snapshot.setSystemPrompt(agent.getSystemPrompt());
        snapshot.setModelId(agent.getModelId());
        snapshot.setToolIds(agent.getToolIds());
        snapshot.setMaxToolCalls(agent.getMaxToolCalls());
        snapshot.setMaxExecutionSeconds(agent.getMaxExecutionSeconds());
        snapshot.setMaxCost(agent.getMaxCost());
        snapshot.setChangeNote(changeNote);
        snapshot.setCreatedBy(SecurityUtils.currentUserId());
        snapshot.setCreatedAt(LocalDateTime.now());
        agentVersionMapper.insert(snapshot);
    }

    private ValidatedAgent validate(AgentRequest request) {
        ModelConfig model = modelRuntimeFactory.findConfig(request.modelId());
        if (model == null) {
            throw BusinessException.badRequest("所选模型不存在或已删除");
        }
        ModelType type;
        try {
            type = ModelType.valueOf(model.getModelType());
        } catch (Exception ex) {
            throw BusinessException.badRequest("模型类型不合法");
        }
        if (type != ModelType.TEXT && type != ModelType.VISION) {
            throw BusinessException.badRequest("Agent 只能绑定文本或视觉模型");
        }

        List<Long> toolIds = request.toolIds() == null ? List.of()
                : request.toolIds().stream().filter(id -> id != null).distinct().toList();
        if (!toolIds.isEmpty()) {
            List<ToolDefinition> definitions = toolService.findDefinitions(toolIds);
            if (definitions.size() != toolIds.size()) {
                throw BusinessException.badRequest("部分绑定工具不存在或已被删除");
            }
            boolean supportsTools = modelRuntimeFactory.parseCapabilities(model.getCapabilities())
                    .contains(ModelCapability.TOOL_CALLING.name());
            if (!supportsTools) {
                throw BusinessException.badRequest("当前模型未声明 TOOL_CALLING 能力，无法绑定工具");
            }
        }

        BigDecimal maxCost = request.maxCost();
        if (maxCost != null && maxCost.compareTo(BigDecimal.ZERO) <= 0) {
            maxCost = null;
        }
        if (maxCost != null && (model.getInputPricePer1k() == null || model.getOutputPricePer1k() == null)) {
            throw BusinessException.badRequest("设置成本上限前，请先为模型配置输入/输出单价");
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(toolIds);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "工具绑定序列化失败");
        }
        return new ValidatedAgent(json, maxCost);
    }

    private AgentVO toVO(Agent agent) {
        ModelConfig model = modelRuntimeFactory.findConfig(agent.getModelId());
        List<Long> toolIds = parseToolIds(agent.getToolIds());
        List<ToolBindingVO> tools = toolService.findDefinitions(toolIds).stream()
                .map(tool -> new ToolBindingVO(tool.id(), tool.toolCode(), tool.name(), tool.enabled()))
                .toList();
        return new AgentVO(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                agent.getSystemPrompt(),
                agent.getModelId(),
                model == null ? "模型已删除" : model.getDisplayName(),
                toolIds,
                tools,
                agent.getMaxToolCalls(),
                agent.getMaxExecutionSeconds(),
                agent.getMaxCost(),
                Boolean.TRUE.equals(agent.getEnabled()),
                Boolean.TRUE.equals(agent.getIsPreset()),
                agent.getCurrentVersion(),
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }

    private List<Long> parseToolIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Long> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            log.warn("Invalid tool ids JSON for agent: {}", ex.getMessage());
            return List.of();
        }
    }

    private Agent requireAgent(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw BusinessException.notFound("Agent 不存在");
        }
        return agent;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ValidatedAgent(String toolIdsJson, BigDecimal maxCost) {
    }
}
