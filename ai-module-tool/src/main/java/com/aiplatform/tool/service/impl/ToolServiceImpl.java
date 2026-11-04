package com.aiplatform.tool.service.impl;

import com.aiplatform.common.api.PageResult;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.tool.dto.ToolDebugRequest;
import com.aiplatform.tool.dto.ToolExecutionQuery;
import com.aiplatform.tool.dto.ToolQuery;
import com.aiplatform.tool.dto.ToolRequest;
import com.aiplatform.tool.entity.AiTool;
import com.aiplatform.tool.entity.ToolExecutionLog;
import com.aiplatform.tool.executor.ToolArgumentValidator;
import com.aiplatform.tool.executor.ToolBeanRegistry;
import com.aiplatform.tool.executor.ToolDefinition;
import com.aiplatform.tool.executor.ToolExecutionResult;
import com.aiplatform.tool.mapper.AiToolMapper;
import com.aiplatform.tool.mapper.ToolExecutionLogMapper;
import com.aiplatform.tool.service.ToolExecutionService;
import com.aiplatform.tool.service.ToolService;
import com.aiplatform.tool.vo.ToolBeanVO;
import com.aiplatform.tool.vo.ToolDebugResultVO;
import com.aiplatform.tool.vo.ToolExecutionLogVO;
import com.aiplatform.tool.vo.ToolVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolServiceImpl implements ToolService {

    private final AiToolMapper aiToolMapper;
    private final ToolExecutionLogMapper toolExecutionLogMapper;
    private final ToolBeanRegistry toolBeanRegistry;
    private final ToolArgumentValidator argumentValidator;
    private final ToolExecutionService toolExecutionService;

    @Override
    public PageResult<ToolVO> page(ToolQuery query) {
        LambdaQueryWrapper<AiTool> wrapper = Wrappers.<AiTool>lambdaQuery()
                .orderByAsc(AiTool::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(AiTool::getName, keyword)
                    .or().like(AiTool::getToolCode, keyword)
                    .or().like(AiTool::getDescription, keyword));
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(AiTool::getCategory, query.getCategory().trim());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(AiTool::getEnabled, query.getEnabled());
        }
        Page<AiTool> page = aiToolMapper.selectPage(
                new Page<>(query.currentPage(), query.pageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(ToolVO::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<ToolBeanVO> beans() {
        return toolBeanRegistry.listBeans();
    }

    @Override
    public List<String> categories() {
        return aiToolMapper.selectList(Wrappers.<AiTool>lambdaQuery()
                        .select(AiTool::getCategory)
                        .groupBy(AiTool::getCategory)
                        .orderByAsc(AiTool::getCategory))
                .stream()
                .map(AiTool::getCategory)
                .filter(StringUtils::hasText)
                .toList();
    }

    @Override
    @Transactional
    public ToolVO create(ToolRequest request) {
        String beanName = request.beanName().trim();
        toolBeanRegistry.require(beanName);
        argumentValidator.validateSchema(request.parametersSchema());
        argumentValidator.validateExample(request.exampleArguments());

        AiTool tool = new AiTool();
        tool.setToolCode(request.toolCode().trim());
        tool.setName(request.name().trim());
        tool.setDescription(request.description().trim());
        tool.setParametersSchema(request.parametersSchema().trim());
        tool.setExampleArguments(StringUtils.hasText(request.exampleArguments())
                ? request.exampleArguments().trim() : null);
        tool.setCategory(request.category().trim());
        tool.setBeanName(beanName);
        tool.setEnabled(request.enabled() == null || request.enabled());
        tool.setBuiltIn(false);
        try {
            aiToolMapper.insert(tool);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("工具编码已存在");
        }
        log.info("Tool {} registered by {}", tool.getToolCode(), SecurityUtils.currentUsername());
        return ToolVO.from(aiToolMapper.selectById(tool.getId()));
    }

    @Override
    @Transactional
    public ToolVO update(Long id, ToolRequest request) {
        AiTool existing = requireTool(id);
        if (Boolean.TRUE.equals(existing.getBuiltIn())) {
            if (!existing.getToolCode().equals(request.toolCode().trim())
                    || !existing.getBeanName().equals(request.beanName().trim())) {
                throw BusinessException.badRequest("内置工具不允许修改工具编码和 Java Bean");
            }
        }
        toolBeanRegistry.require(request.beanName().trim());
        argumentValidator.validateSchema(request.parametersSchema());
        argumentValidator.validateExample(request.exampleArguments());

        Long codeCount = aiToolMapper.selectCount(Wrappers.<AiTool>lambdaQuery()
                .eq(AiTool::getToolCode, request.toolCode().trim())
                .ne(AiTool::getId, id));
        if (codeCount > 0) {
            throw BusinessException.conflict("工具编码已存在");
        }

        aiToolMapper.update(null, new LambdaUpdateWrapper<AiTool>()
                .eq(AiTool::getId, id)
                .set(AiTool::getToolCode, request.toolCode().trim())
                .set(AiTool::getName, request.name().trim())
                .set(AiTool::getDescription, request.description().trim())
                .set(AiTool::getParametersSchema, request.parametersSchema().trim())
                .set(AiTool::getExampleArguments, StringUtils.hasText(request.exampleArguments())
                        ? request.exampleArguments().trim() : null)
                .set(AiTool::getCategory, request.category().trim())
                .set(AiTool::getBeanName, request.beanName().trim())
                .set(AiTool::getEnabled, request.enabled() == null || request.enabled()));
        return ToolVO.from(aiToolMapper.selectById(id));
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        requireTool(id);
        aiToolMapper.update(null, Wrappers.<AiTool>lambdaUpdate()
                .eq(AiTool::getId, id)
                .set(AiTool::getEnabled, enabled));
    }

    @Override
    public ToolDebugResultVO debug(Long id, ToolDebugRequest request) {
        AiTool tool = requireTool(id);
        ToolExecutionResult result = toolExecutionService.executeById(
                id,
                request.arguments(),
                SecurityUtils.currentUserId(),
                null,
                false
        );
        return new ToolDebugResultVO(
                result.logId(),
                tool.getToolCode(),
                tool.getName(),
                result.success(),
                result.resultText(),
                result.errorMessage(),
                result.durationMs(),
                result.createdAt()
        );
    }

    @Override
    public PageResult<ToolExecutionLogVO> executions(ToolExecutionQuery query) {
        LambdaQueryWrapper<ToolExecutionLog> wrapper = Wrappers.<ToolExecutionLog>lambdaQuery()
                .orderByDesc(ToolExecutionLog::getId);
        if (query.getToolId() != null) {
            wrapper.eq(ToolExecutionLog::getToolId, query.getToolId());
        }
        if (query.getSuccess() != null) {
            wrapper.eq(ToolExecutionLog::getSuccess, query.getSuccess());
        }
        if (query.getAgentExecutionId() != null) {
            wrapper.eq(ToolExecutionLog::getAgentExecutionId, query.getAgentExecutionId());
        }
        Page<ToolExecutionLog> page = toolExecutionLogMapper.selectPage(
                new Page<>(query.currentPage(), query.pageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(ToolExecutionLogVO::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<ToolDefinition> findEnabledDefinitions(List<Long> toolIds) {
        return findDefinitions(toolIds).stream()
                .filter(ToolDefinition::enabled)
                .toList();
    }

    @Override
    public List<ToolDefinition> findDefinitions(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        List<AiTool> tools = aiToolMapper.selectBatchIds(toolIds);
        Map<Long, AiTool> byId = tools.stream().collect(Collectors.toMap(AiTool::getId, Function.identity()));
        return toolIds.stream()
                .distinct()
                .map(byId::get)
                .filter(tool -> tool != null)
                .map(tool -> new ToolDefinition(tool.getId(), tool.getToolCode(), tool.getName(),
                        tool.getDescription(), tool.getParametersSchema(),
                        Boolean.TRUE.equals(tool.getEnabled())))
                .toList();
    }

    private AiTool requireTool(Long id) {
        AiTool tool = aiToolMapper.selectById(id);
        if (tool == null) {
            throw BusinessException.notFound("工具不存在");
        }
        return tool;
    }
}
