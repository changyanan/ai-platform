package com.aiplatform.server.p2;

import com.aiplatform.model.entity.ModelConfig;
import com.aiplatform.model.mapper.ModelConfigMapper;
import com.aiplatform.server.user.entity.User;
import com.aiplatform.server.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2 管理与执行链路集成测试：真实 MockMvc + MySQL + LangChain4j AI Services，
 * 模型端点由本地 HttpServer 模拟，用于精确产生一次 Tool Calling 与最终回答。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class P2AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ModelConfigMapper modelConfigMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpServer stubServer;
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final java.util.concurrent.atomic.AtomicBoolean repeatToolCalls =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private String username;
    private Long userId;
    private Long modelId;
    private Long agentId;
    private Long executionId;
    private Long promptId;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/v1/chat/completions", this::handleChatCompletion);
        stubServer.start();
        modelCalls.set(0);
        repeatToolCalls.set(false);

        username = "p2_it_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Test@123456"));
        user.setDisplayName("P2 Integration Admin");
        user.setRole("ADMIN");
        user.setEnabled(true);
        user.setMustChangePassword(false);
        user.setTokenVersion(0);
        userMapper.insert(user);
        userId = user.getId();
        accessToken = login();

        ModelConfig config = new ModelConfig();
        config.setProviderId(4L);
        config.setDisplayName("P2 Agent Stub Model");
        config.setModelName("test-agent-model");
        config.setModelType("TEXT");
        config.setApiBaseUrl("http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1");
        config.setTemperature(java.math.BigDecimal.valueOf(0.1));
        config.setMaxTokens(512);
        config.setContextWindow(8192);
        config.setCapabilities("[\"TEXT\",\"TOOL_CALLING\"]");
        config.setEnabled(true);
        config.setIsDefault(false);
        config.setLastTestStatus("UNTESTED");
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        modelConfigMapper.insert(config);
        modelId = config.getId();
    }

    @AfterEach
    void tearDown() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
        if (agentId != null) {
            jdbcTemplate.update("DELETE FROM ai_tool_execution_log WHERE agent_execution_id IN "
                    + "(SELECT id FROM ai_agent_execution WHERE agent_id = ?)", agentId);
            jdbcTemplate.update("DELETE FROM ai_agent_step WHERE execution_id IN "
                    + "(SELECT id FROM ai_agent_execution WHERE agent_id = ?)", agentId);
            jdbcTemplate.update("DELETE FROM ai_agent_execution WHERE agent_id = ?", agentId);
            jdbcTemplate.update("DELETE FROM ai_agent_version WHERE agent_id = ?", agentId);
            jdbcTemplate.update("DELETE FROM ai_agent WHERE id = ?", agentId);
        }
        if (promptId != null) {
            jdbcTemplate.update("DELETE FROM ai_prompt_version WHERE prompt_id = ?", promptId);
            jdbcTemplate.update("DELETE FROM ai_prompt WHERE id = ?", promptId);
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM ai_tool_execution_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_model_call_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_login_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_user WHERE id = ?", userId);
        }
        if (modelId != null) {
            jdbcTemplate.update("DELETE FROM ai_model_config WHERE id = ?", modelId);
        }
    }

    @Test
    void agentCallsRealJavaToolThroughLangChain4jAndRecordsEveryStep() throws Exception {
        Map<String, Object> agentPayload = new LinkedHashMap<>();
        agentPayload.put("name", "P2 订单分析 Agent");
        agentPayload.put("description", "集成测试：真实工具调用");
        agentPayload.put("systemPrompt", "你是订单分析助手。必须调用 query_order 工具查询订单后再回答。");
        agentPayload.put("modelId", modelId);
        agentPayload.put("toolIds", List.of(2));
        agentPayload.put("maxToolCalls", 3);
        agentPayload.put("maxExecutionSeconds", 30);
        agentPayload.put("maxCost", null);
        agentPayload.put("enabled", true);
        agentPayload.put("changeNote", "P2 集成测试");

        MvcResult createResult = mockMvc.perform(post("/api/admin/agents")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(agentPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(1))
                .andReturn();
        agentId = read(createResult).path("data").path("id").asLong();

        MvcResult debugResult = mockMvc.perform(post("/api/admin/agents/" + agentId + "/debug")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("input", "请查询订单 SO20260001 的状态。"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.execution.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.execution.toolCallCount").value(1))
                .andExpect(jsonPath("$.data.execution.modelCallCount").value(2))
                .andExpect(jsonPath("$.data.execution.tokenEstimated").value(false))
                .andReturn();

        JsonNode root = read(debugResult);
        JsonNode execution = root.path("data").path("execution");
        executionId = execution.path("id").asLong();
        assertThat(execution.path("outputText").asText()).contains("已发货");
        assertThat(execution.path("totalTokens").asInt()).isEqualTo(294);

        JsonNode steps = root.path("data").path("steps");
        assertThat(steps.findValuesAsText("stepType"))
                .contains("MODEL_REQUEST", "MODEL_RESPONSE", "TOOL_REQUEST", "TOOL_RESULT");
        JsonNode toolRequest = null;
        for (JsonNode step : steps) {
            if ("TOOL_REQUEST".equals(step.path("stepType").asText())) {
                toolRequest = step;
            }
        }
        assertThat(toolRequest).isNotNull();
        assertThat(toolRequest.path("toolCode").asText()).isEqualTo("query_order");
        assertThat(toolRequest.path("argumentsJson").asText()).contains("SO20260001");

        Map<String, Object> executionRow = jdbcTemplate.queryForMap(
                "SELECT status, tool_call_count, model_call_count, total_tokens FROM ai_agent_execution WHERE id = ?",
                executionId);
        assertThat(executionRow.get("status")).isEqualTo("SUCCESS");
        assertThat(((Number) executionRow.get("tool_call_count")).intValue()).isEqualTo(1);
        assertThat(((Number) executionRow.get("model_call_count")).intValue()).isEqualTo(2);

        Map<String, Object> toolLog = jdbcTemplate.queryForMap(
                "SELECT success, tool_code, agent_execution_id FROM ai_tool_execution_log "
                        + "WHERE agent_execution_id = ? ORDER BY id DESC LIMIT 1", executionId);
        assertThat((Boolean) toolLog.get("success")).isTrue();
        assertThat(toolLog.get("tool_code")).isEqualTo("query_order");

        List<Map<String, Object>> modelLogs = jdbcTemplate.queryForList(
                "SELECT success, data_source, request_type FROM ai_model_call_log "
                        + "WHERE user_id = ? AND request_type = 'AGENT' ORDER BY id", userId);
        assertThat(modelLogs).hasSize(2);
        assertThat(modelLogs).allSatisfy(log -> {
            assertThat((Boolean) log.get("success")).isTrue();
            assertThat(log.get("data_source")).isEqualTo("REAL");
        });
        assertThat(modelCalls.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void agentMarksExecutionAsLimitExceededWhenToolBudgetIsExhausted() throws Exception {
        repeatToolCalls.set(true);
        Map<String, Object> agentPayload = new LinkedHashMap<>();
        agentPayload.put("name", "P2 限额 Agent");
        agentPayload.put("description", "集成测试：工具次数限额");
        agentPayload.put("systemPrompt", "必须持续调用 query_order 工具。");
        agentPayload.put("modelId", modelId);
        agentPayload.put("toolIds", List.of(2));
        agentPayload.put("maxToolCalls", 1);
        agentPayload.put("maxExecutionSeconds", 30);
        agentPayload.put("maxCost", null);
        agentPayload.put("enabled", true);
        agentPayload.put("changeNote", "限额测试");

        MvcResult createResult = mockMvc.perform(post("/api/admin/agents")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(agentPayload)))
                .andExpect(status().isOk())
                .andReturn();
        agentId = read(createResult).path("data").path("id").asLong();

        MvcResult debugResult = mockMvc.perform(post("/api/admin/agents/" + agentId + "/debug")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("input", "请持续查询订单 SO20260001。"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = read(debugResult);
        JsonNode execution = root.path("data").path("execution");
        assertThat(execution.path("status").asText()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(execution.path("toolCallCount").asInt()).isLessThanOrEqualTo(1);
        assertThat(execution.path("errorMessage").asText()).contains("最大工具调用次数");
        assertThat(root.path("data").path("steps").findValuesAsText("stepType")).contains("ERROR");
    }

    @Test
    void monitorOverviewMarksSimulatedDataAndKeepsUnknownCostExplicit() throws Exception {
        mockMvc.perform(get("/api/admin/monitor/overview")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.simulatedCalls").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(22)))
                .andExpect(jsonPath("$.data.summary.unknownCostCalls").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.trend.length()").value(7))
                .andExpect(jsonPath("$.data.types.length()").value(
                        org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/admin/monitor/calls")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("page", "1")
                        .param("size", "5")
                        .param("dataSource", "SIMULATED")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].dataSource").value("SIMULATED"))
                .andExpect(jsonPath("$.data.records[0].costKnown").value(false));
    }

    @Test
    void promptVersionsPublishAndRollbackAreAuditable() throws Exception {
        Map<String, Object> createPayload = Map.of(
                "name", "P2 IT Prompt " + UUID.randomUUID().toString().substring(0, 6),
                "description", "版本测试",
                "category", "集成测试",
                "content", "你好 {{name}}，请回答：{{question}}",
                "changeNote", "初始化"
        );
        MvcResult createResult = mockMvc.perform(post("/api/admin/prompts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(1))
                .andExpect(jsonPath("$.data.variables.length()").value(2))
                .andReturn();
        promptId = read(createResult).path("data").path("id").asLong();

        Map<String, Object> updatePayload = Map.of(
                "name", createPayload.get("name"),
                "description", "版本测试",
                "category", "集成测试",
                "content", "你好 {{name}}，欢迎来到 {{company}}，请回答：{{question}}",
                "changeNote", "新增公司变量"
        );
        mockMvc.perform(put("/api/admin/prompts/" + promptId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(2))
                .andExpect(jsonPath("$.data.variables.length()").value(3));

        mockMvc.perform(post("/api/admin/prompts/" + promptId + "/publish")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedVersion").value(2))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/admin/prompts/" + promptId + "/rollback/1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(3))
                .andExpect(jsonPath("$.data.variables.length()").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/admin/prompts/" + promptId + "/versions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].version").value(3))
                .andExpect(jsonPath("$.data[0].changeNote").value("回滚到版本 v1"));
    }

    @Test
    void calculatorExecutesRealJavaAndDocumentSearchFailsHonestly() throws Exception {
        MvcResult calculator = mockMvc.perform(post("/api/admin/tools/4/debug")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "arguments", Map.of("expression", "(1280*3+960)/4 + sqrt(16)")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andReturn();
        JsonNode calculatorData = read(calculator).path("data");
        assertThat(calculatorData.path("result").asText()).contains("\"result\":\"1204\"");

        mockMvc.perform(post("/api/admin/tools/6/debug")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "arguments", Map.of("query", "权限模型", "topK", 3)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.error").value(
                        org.hamcrest.Matchers.containsString("知识库检索尚未就绪")));

        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_tool_execution_log WHERE user_id = ?", Integer.class, userId);
        assertThat(logCount).isEqualTo(2);
    }

    private void handleChatCompletion(HttpExchange exchange) {
        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            modelCalls.incrementAndGet();
            boolean toolResultPresent = requestBody.contains("tool_call_id")
                    || requestBody.contains("\"role\":\"tool\"");
            String response = repeatToolCalls.get() || !toolResultPresent
                    ? toolCallRequest() : finalAnswer();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (Exception ex) {
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (Exception ignored) {
                // 测试桩关闭时忽略
            }
        } finally {
            exchange.close();
        }
    }

    private String toolCallRequest() throws Exception {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "query_order");
        function.put("arguments", objectMapper.writeValueAsString(Map.of("orderNo", "SO20260001")));
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "call_p2_1");
        toolCall.put("type", "function");
        toolCall.put("function", function);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", "");
        message.put("tool_calls", List.of(toolCall));
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "tool_calls");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "chatcmpl-p2-tool");
        body.put("object", "chat.completion");
        body.put("created", 1690000000);
        body.put("model", "test-agent-model");
        body.put("choices", List.of(choice));
        body.put("usage", Map.of("prompt_tokens", 100, "completion_tokens", 20, "total_tokens", 120));
        return objectMapper.writeValueAsString(body);
    }

    private String finalAnswer() throws Exception {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", "订单 SO20260001 已发货（本地演示数据）。");
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "chatcmpl-p2-final");
        body.put("object", "chat.completion");
        body.put("created", 1690000001);
        body.put("model", "test-agent-model");
        body.put("choices", List.of(choice));
        body.put("usage", Map.of("prompt_tokens", 150, "completion_tokens", 24, "total_tokens", 174));
        return objectMapper.writeValueAsString(body);
    }

    private String login() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", "Test@123456"
        ));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).path("data").path("accessToken").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
