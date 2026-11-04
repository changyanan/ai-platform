package com.aiplatform.server.chat;

import com.aiplatform.model.entity.ModelConfig;
import com.aiplatform.model.mapper.ModelConfigMapper;
import com.aiplatform.server.user.entity.User;
import com.aiplatform.server.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端验证 SDK -> OpenAI-compatible SSE -> 持久化 -> 监控日志。
 * 测试使用本地 HttpServer 模拟协议端点，不访问公网；真实供应商协议已由同一适配器承载。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatStreamIntegrationTest {

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

    @Autowired
    private StringRedisTemplate redisTemplate;

    private HttpServer stubServer;
    private String username;
    private Long userId;
    private Long modelId;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/v1/chat/completions", exchange -> {
            try {
                // 本测试专用于验证流式协议；OpenAI-compatible 流式请求固定走 SSE。
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
                exchange.sendResponseHeaders(200, 0);
                OutputStream output = exchange.getResponseBody();
                writeChunk(output, "你好，");
                writeChunk(output, "我是流式测试模型。");
                String finalChunk = """
                        {"id":"chatcmpl-test","object":"chat.completion.chunk","created":1690000000,
                         "model":"test-stream-model",
                         "choices":[{"index":0,"delta":{},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":12,"completion_tokens":7,"total_tokens":19}}
                        """.replaceAll("\\s+", " ");
                writeRaw(output, "data: " + finalChunk + "\n\n");
                writeRaw(output, "data: [DONE]\n\n");
                output.flush();
            } catch (Exception ignored) {
                // 测试客户端断开时忽略
            } finally {
                exchange.close();
            }
        });
        stubServer.start();

        username = "sse_test_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Test@123456"));
        user.setDisplayName("SSE Integration User");
        user.setRole("ADMIN");
        user.setEnabled(true);
        user.setMustChangePassword(false);
        user.setTokenVersion(0);
        userMapper.insert(user);
        userId = user.getId();

        accessToken = login();

        ModelConfig config = new ModelConfig();
        config.setProviderId(4L);
        config.setDisplayName("SSE Test Model");
        config.setModelName("test-stream-model");
        config.setModelType("TEXT");
        config.setApiBaseUrl("http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1");
        config.setTemperature(java.math.BigDecimal.valueOf(0.2));
        config.setMaxTokens(256);
        config.setContextWindow(4096);
        config.setCapabilities("[\"TEXT\"]");
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
        if (userId != null) {
            var conversationIds = jdbcTemplate.queryForList(
                    "SELECT id FROM ai_conversation WHERE user_id = ?", Long.class, userId);
            for (Long conversationId : conversationIds) {
                jdbcTemplate.update("DELETE FROM ai_message WHERE conversation_id = ?", conversationId);
                redisTemplate.delete("ai:chat:memory:" + conversationId);
            }
            jdbcTemplate.update("DELETE FROM ai_conversation WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_model_call_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_login_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_user WHERE id = ?", userId);
        }
        if (modelId != null) {
            jdbcTemplate.update("DELETE FROM ai_model_config WHERE id = ?", modelId);
        }
    }

    @Test
    void streamsFromOpenAiCompatibleEndpointAndPersistsRealCallLog() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "modelId", modelId,
                "content", "请简单介绍你自己"
        ));

        MvcResult initial = mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = completed.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(responseBody).contains("event:meta");
        assertThat(responseBody).contains("event:delta");
        assertThat(responseBody).contains("你好，");
        assertThat(responseBody).contains("我是流式测试模型。");
        assertThat(responseBody).contains("event:done");

        Map<String, Object> assistantMessage = jdbcTemplate.queryForMap(
                "SELECT content, status, prompt_tokens, completion_tokens FROM ai_message "
                        + "WHERE conversation_id = (SELECT id FROM ai_conversation WHERE user_id = ?) "
                        + "AND role = 'ASSISTANT' ORDER BY id DESC LIMIT 1", userId);
        assertThat(assistantMessage.get("status")).isEqualTo("COMPLETED");
        assertThat(assistantMessage.get("content").toString()).contains("流式测试模型");
        assertThat(assistantMessage.get("prompt_tokens")).isNotNull();
        assertThat(assistantMessage.get("completion_tokens")).isNotNull();

        Map<String, Object> callLog = jdbcTemplate.queryForMap(
                "SELECT success, data_source, request_type, estimated FROM ai_model_call_log "
                        + "WHERE user_id = ? ORDER BY id DESC LIMIT 1", userId);
        assertThat((Boolean) callLog.get("success")).isTrue();
        assertThat(callLog.get("data_source")).isEqualTo("REAL");
        assertThat(callLog.get("request_type")).isEqualTo("CHAT");

        Long conversationId = jdbcTemplate.queryForObject(
                "SELECT id FROM ai_conversation WHERE user_id = ? LIMIT 1", Long.class, userId);
        assertThat(conversationId).isNotNull();
        assertThat(redisTemplate.hasKey("ai:chat:memory:" + conversationId)).isTrue();
    }

    private void writeChunk(OutputStream output, String content) throws Exception {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        String chunk = "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion.chunk\","
                + "\"created\":1690000000,\"model\":\"test-stream-model\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"" + escaped
                + "\"},\"finish_reason\":null}]}";
        writeRaw(output, "data: " + chunk + "\n\n");
        Thread.sleep(15);
    }

    private void writeRaw(OutputStream output, String data) throws Exception {
        output.write(data.getBytes(StandardCharsets.UTF_8));
        output.flush();
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
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return node.path("data").path("accessToken").asText();
    }
}
