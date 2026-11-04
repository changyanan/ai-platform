package com.aiplatform.prompt.service.impl;

import com.aiplatform.common.ai.TokenEstimator;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.model.adapter.ModelAdapterRegistry;
import com.aiplatform.model.enums.ModelCallType;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.runtime.ModelRuntime;
import com.aiplatform.model.service.ModelCallLogCommand;
import com.aiplatform.model.service.ModelCallLogService;
import com.aiplatform.model.service.ModelRuntimeFactory;
import com.aiplatform.prompt.dto.PromptQuery;
import com.aiplatform.prompt.dto.PromptRequest;
import com.aiplatform.prompt.dto.PromptTestRequest;
import com.aiplatform.prompt.entity.PromptAsset;
import com.aiplatform.prompt.entity.PromptVersion;
import com.aiplatform.prompt.mapper.PromptAssetMapper;
import com.aiplatform.prompt.mapper.PromptVersionMapper;
import com.aiplatform.prompt.service.PromptService;
import com.aiplatform.prompt.vo.PromptTestResultVO;
import com.aiplatform.prompt.vo.PromptVO;
import com.aiplatform.prompt.vo.PromptVariableVO;
import com.aiplatform.prompt.vo.PromptVersionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");

    private final PromptAssetMapper promptAssetMapper;
    private final PromptVersionMapper promptVersionMapper;
    private final ModelRuntimeFactory modelRuntimeFactory;
    private final ModelAdapterRegistry modelAdapterRegistry;
    private final ModelCallLogService modelCallLogService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<PromptVO> page(PromptQuery query) {
        LambdaQueryWrapper<PromptAsset> wrapper = Wrappers.<PromptAsset>lambdaQuery()
                .orderByDesc(PromptAsset::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(PromptAsset::getName, keyword)
                    .or().like(PromptAsset::getDescription, keyword)
                    .or().like(PromptAsset::getContent, keyword));
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(PromptAsset::getCategory, query.getCategory().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PromptAsset::getStatus, query.getStatus().trim().toUpperCase());
        }
        Page<PromptAsset> page = promptAssetMapper.selectPage(
                new Page<>(query.currentPage(), query.pageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PromptVO get(Long id) {
        return toVO(requirePrompt(id));
    }

    @Override
    @Transactional
    public PromptVO create(PromptRequest request) {
        ensureNameAvailable(request.name().trim(), null);
        String variablesJson = detectVariables(request.content(), null);

        PromptAsset prompt = new PromptAsset();
        prompt.setName(request.name().trim());
        prompt.setDescription(normalizeOptional(request.description()));
        prompt.setCategory(request.category().trim());
        prompt.setContent(request.content());
        prompt.setVariablesJson(variablesJson);
        prompt.setCurrentVersion(1);
        prompt.setPublishedVersion(null);
        prompt.setStatus("DRAFT");
        prompt.setCreatedBy(SecurityUtils.currentUserId());
        try {
            promptAssetMapper.insert(prompt);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("Prompt 名称已存在");
        }
        saveVersion(prompt, 1, StringUtils.hasText(request.changeNote())
                ? request.changeNote().trim() : "初始化版本");
        log.info("Prompt {} created by {}", prompt.getId(), SecurityUtils.currentUsername());
        return toVO(promptAssetMapper.selectById(prompt.getId()));
    }

    @Override
    @Transactional
    public PromptVO update(Long id, PromptRequest request) {
        PromptAsset existing = requirePrompt(id);
        ensureNameAvailable(request.name().trim(), id);
        String variablesJson = detectVariables(request.content(), existing.getVariablesJson());
        int nextVersion = (existing.getCurrentVersion() == null ? 1 : existing.getCurrentVersion()) + 1;

        promptAssetMapper.update(null, new LambdaUpdateWrapper<PromptAsset>()
                .eq(PromptAsset::getId, id)
                .set(PromptAsset::getName, request.name().trim())
                .set(PromptAsset::getDescription, normalizeOptional(request.description()))
                .set(PromptAsset::getCategory, request.category().trim())
                .set(PromptAsset::getContent, request.content())
                .set(PromptAsset::getVariablesJson, variablesJson)
                .set(PromptAsset::getCurrentVersion, nextVersion)
                .set(PromptAsset::getStatus, "DRAFT")
                .set(PromptAsset::getUpdatedAt, LocalDateTime.now()));

        PromptAsset updated = promptAssetMapper.selectById(id);
        saveVersion(updated, nextVersion, StringUtils.hasText(request.changeNote())
                ? request.changeNote().trim() : "保存新版本");
        log.info("Prompt {} updated to version {} by {}", id, nextVersion, SecurityUtils.currentUsername());
        return toVO(updated);
    }

    @Override
    public List<PromptVersionVO> versions(Long id) {
        requirePrompt(id);
        return promptVersionMapper.selectList(Wrappers.<PromptVersion>lambdaQuery()
                        .eq(PromptVersion::getPromptId, id)
                        .orderByDesc(PromptVersion::getVersion))
                .stream()
                .map(version -> PromptVersionVO.from(version, parseVariables(version.getVariablesJson())))
                .toList();
    }

    @Override
    @Transactional
    public PromptVO publish(Long id) {
        PromptAsset prompt = requirePrompt(id);
        if (prompt.getCurrentVersion() == null) {
            throw BusinessException.conflict("Prompt 尚未保存版本");
        }
        promptAssetMapper.update(null, new LambdaUpdateWrapper<PromptAsset>()
                .eq(PromptAsset::getId, id)
                .set(PromptAsset::getPublishedVersion, prompt.getCurrentVersion())
                .set(PromptAsset::getStatus, "PUBLISHED")
                .set(PromptAsset::getUpdatedAt, LocalDateTime.now()));
        log.info("Prompt {} v{} published by {}", id, prompt.getCurrentVersion(), SecurityUtils.currentUsername());
        return toVO(promptAssetMapper.selectById(id));
    }

    @Override
    @Transactional
    public PromptVO rollback(Long id, Integer version) {
        PromptAsset prompt = requirePrompt(id);
        PromptVersion target = promptVersionMapper.selectOne(Wrappers.<PromptVersion>lambdaQuery()
                .eq(PromptVersion::getPromptId, id)
                .eq(PromptVersion::getVersion, version)
                .last("LIMIT 1"));
        if (target == null) {
            throw BusinessException.notFound("目标 Prompt 版本不存在");
        }
        int nextVersion = (prompt.getCurrentVersion() == null ? 1 : prompt.getCurrentVersion()) + 1;
        promptAssetMapper.update(null, new LambdaUpdateWrapper<PromptAsset>()
                .eq(PromptAsset::getId, id)
                .set(PromptAsset::getContent, target.getContent())
                .set(PromptAsset::getVariablesJson, target.getVariablesJson())
                .set(PromptAsset::getCurrentVersion, nextVersion)
                .set(PromptAsset::getStatus, "DRAFT")
                .set(PromptAsset::getUpdatedAt, LocalDateTime.now()));
        PromptAsset updated = promptAssetMapper.selectById(id);
        saveVersion(updated, nextVersion, "回滚到版本 v" + version);
        log.info("Prompt {} rolled back to v{} as new v{} by {}", id, version, nextVersion,
                SecurityUtils.currentUsername());
        return toVO(updated);
    }

    @Override
    public PromptTestResultVO test(Long id, PromptTestRequest request) {
        PromptAsset prompt = requirePrompt(id);
        List<PromptVariableVO> variables = parseVariables(prompt.getVariablesJson());
        String rendered = render(prompt.getContent(), variables, request.variables());

        ModelRuntime runtime = modelRuntimeFactory.resolveForExecution(request.modelId());
        if (runtime.modelType() != ModelType.TEXT && runtime.modelType() != ModelType.VISION) {
            throw BusinessException.badRequest("Prompt 测试仅支持文本或视觉模型");
        }

        long start = System.currentTimeMillis();
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;
        boolean estimated = false;
        String output = null;
        String error = null;
        try {
            ChatModel chatModel = modelAdapterRegistry.chatModel(runtime);
            ChatResponse response = chatModel.chat(UserMessage.from(rendered));
            output = response.aiMessage() == null ? "" : response.aiMessage().text();
            TokenUsage usage = response.tokenUsage();
            if (usage != null && usage.inputTokenCount() != null && usage.outputTokenCount() != null) {
                promptTokens = usage.inputTokenCount();
                completionTokens = usage.outputTokenCount();
                totalTokens = usage.totalTokenCount() != null ? usage.totalTokenCount()
                        : promptTokens + completionTokens;
            } else {
                promptTokens = TokenEstimator.estimate(rendered);
                completionTokens = TokenEstimator.estimate(output);
                totalTokens = promptTokens + completionTokens;
                estimated = true;
            }
            long latency = System.currentTimeMillis() - start;
            recordModelCall(prompt, runtime, true, promptTokens, completionTokens, totalTokens,
                    latency, estimated, null);
            return new PromptTestResultVO(rendered, output, runtime.displayName(), promptTokens,
                    completionTokens, totalTokens, estimated, latency);
        } catch (Exception ex) {
            error = safeError(ex);
            long latency = System.currentTimeMillis() - start;
            recordModelCall(prompt, runtime, false, null, null, null, latency, true, error);
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "Prompt 测试失败：" + error);
        }
    }

    @Override
    public List<String> categories() {
        return promptAssetMapper.selectList(Wrappers.<PromptAsset>lambdaQuery()
                        .select(PromptAsset::getCategory)
                        .groupBy(PromptAsset::getCategory)
                        .orderByAsc(PromptAsset::getCategory))
                .stream()
                .map(PromptAsset::getCategory)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void recordModelCall(PromptAsset prompt, ModelRuntime runtime, boolean success,
                                 Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                 long latency, boolean estimated, String errorMessage) {
        modelCallLogService.record(ModelCallLogCommand.builder()
                .traceId(MDC.get("traceId"))
                .userId(SecurityUtils.currentUserId())
                .modelConfigId(runtime.configId())
                .providerCode(runtime.providerCode())
                .modelName(runtime.modelName())
                .requestType(ModelCallType.PROMPT_TEST.name())
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .latencyMs(latency)
                .success(success)
                .errorCode(success ? null : "PROMPT_TEST_FAILED")
                .errorMessage(errorMessage)
                .estimated(estimated)
                .inputPricePerThousand(runtime.inputPricePerThousand())
                .outputPricePerThousand(runtime.outputPricePerThousand())
                .currency(runtime.currency())
                .requestSummary("prompt #" + prompt.getId() + " · " + prompt.getName())
                .build());
    }

    private String detectVariables(String content, String existingVariablesJson) {
        Map<String, PromptVariableVO> existing = new LinkedHashMap<>();
        if (StringUtils.hasText(existingVariablesJson)) {
            for (PromptVariableVO variable : parseVariables(existingVariablesJson)) {
                existing.put(variable.name(), variable);
            }
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        List<PromptVariableVO> variables = new ArrayList<>();
        for (String name : names) {
            PromptVariableVO current = existing.get(name);
            variables.add(current != null ? current
                    : new PromptVariableVO(name, name, true, ""));
        }
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Prompt 变量序列化失败");
        }
    }

    private String render(String content, List<PromptVariableVO> definitions, Map<String, String> provided) {
        Map<String, PromptVariableVO> definitionMap = definitions.stream()
                .collect(java.util.stream.Collectors.toMap(PromptVariableVO::name, v -> v));
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            PromptVariableVO definition = definitionMap.get(name);
            String value = provided == null ? null : provided.get(name);
            if (!StringUtils.hasText(value) && definition != null) {
                value = definition.defaultValue();
            }
            if (!StringUtils.hasText(value) && (definition == null || definition.required())) {
                throw BusinessException.badRequest("缺少 Prompt 变量值：" + name);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(rendered);
        String result = rendered.toString();
        if (VARIABLE_PATTERN.matcher(result).find()) {
            throw BusinessException.badRequest("Prompt 中仍有未解析的变量");
        }
        return result;
    }

    private void saveVersion(PromptAsset prompt, int version, String changeNote) {
        PromptVersion entity = new PromptVersion();
        entity.setPromptId(prompt.getId());
        entity.setVersion(version);
        entity.setContent(prompt.getContent());
        entity.setVariablesJson(prompt.getVariablesJson());
        entity.setChangeNote(changeNote);
        entity.setCreatedBy(SecurityUtils.currentUserId());
        entity.setCreatedAt(LocalDateTime.now());
        promptVersionMapper.insert(entity);
    }

    private void ensureNameAvailable(String name, Long excludeId) {
        LambdaQueryWrapper<PromptAsset> wrapper = Wrappers.<PromptAsset>lambdaQuery()
                .eq(PromptAsset::getName, name);
        if (excludeId != null) {
            wrapper.ne(PromptAsset::getId, excludeId);
        }
        if (promptAssetMapper.selectCount(wrapper) > 0) {
            throw BusinessException.conflict("Prompt 名称已存在");
        }
    }

    private PromptVO toVO(PromptAsset prompt) {
        return new PromptVO(
                prompt.getId(),
                prompt.getName(),
                prompt.getDescription(),
                prompt.getCategory(),
                prompt.getContent(),
                parseVariables(prompt.getVariablesJson()),
                prompt.getCurrentVersion(),
                prompt.getPublishedVersion(),
                prompt.getStatus(),
                prompt.getCreatedAt(),
                prompt.getUpdatedAt()
        );
    }

    private List<PromptVariableVO> parseVariables(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<PromptVariableVO> variables = objectMapper.readValue(json, new TypeReference<>() {
            });
            return variables == null ? List.of() : variables;
        } catch (Exception ex) {
            log.warn("Invalid prompt variables JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    private PromptAsset requirePrompt(Long id) {
        PromptAsset prompt = promptAssetMapper.selectById(id);
        if (prompt == null) {
            throw BusinessException.notFound("Prompt 不存在");
        }
        return prompt;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeError(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        if (!StringUtils.hasText(message)) {
            message = error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
