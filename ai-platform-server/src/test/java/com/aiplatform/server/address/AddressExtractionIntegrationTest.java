package com.aiplatform.server.address;

import com.aiplatform.model.entity.ModelConfig;
import com.aiplatform.model.mapper.ModelConfigMapper;
import com.aiplatform.server.user.entity.User;
import com.aiplatform.server.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证地址识别接口：真实 MockMvc → LangChain4j → OpenAI-compatible 模型桩。
 * 重点断言识别结果直接返回，且请求过程不新增任何业务/模型调用数据库记录。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AddressExtractionIntegrationTest {

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
    private String username;
    private Long userId;
    private Long modelId;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/v1/chat/completions", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                String content = objectMapper.writeValueAsString(Map.of(
                        "receiverName", "张三",
                        "phone", "+86 138-0000-1234",
                        "province", "浙江省",
                        "city", "杭州市",
                        "district", "西湖区",
                        "street", "文三路",
                        "detailAddress", "5楼",
                        "fullAddress", "浙江省杭州市西湖区文三路5楼",
                        "confidence", 0.96
                ));
                Map<String, Object> message = Map.of("role", "assistant", "content", content);
                Map<String, Object> choice = Map.of(
                        "index", 0, "message", message, "finish_reason", "stop");
                Map<String, Object> body = Map.of(
                        "id", "chatcmpl-address",
                        "object", "chat.completion",
                        "created", 1690000000,
                        "model", "address-stub",
                        "choices", java.util.List.of(choice),
                        "usage", Map.of("prompt_tokens", 120, "completion_tokens", 40,
                                "total_tokens", 160));
                byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            } finally {
                exchange.close();
            }
        });
        stubServer.start();

        username = "address_it_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Test@123456"));
        user.setDisplayName("Address Integration User");
        user.setRole("ADMIN");
        user.setEnabled(true);
        user.setMustChangePassword(false);
        user.setTokenVersion(0);
        userMapper.insert(user);
        userId = user.getId();
        accessToken = login();

        ModelConfig config = new ModelConfig();
        config.setProviderId(4L);
        config.setDisplayName("Address Stub Model");
        config.setModelName("address-stub");
        config.setModelType("TEXT");
        config.setApiBaseUrl("http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1");
        config.setTemperature(BigDecimal.valueOf(0.1));
        config.setMaxTokens(512);
        config.setContextWindow(8192);
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
            jdbcTemplate.update("DELETE FROM ai_login_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_user WHERE id = ?", userId);
        }
        if (modelId != null) {
            jdbcTemplate.update("DELETE FROM ai_model_config WHERE id = ?", modelId);
        }
    }

    @Test
    void extractsAddressAndDoesNotWriteDatabase() throws Exception {
        Long before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_model_call_log WHERE user_id = ?", Long.class, userId);

        mockMvc.perform(post("/api/address/extract")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", "张三 13800001234 浙江省杭州市西湖区文三路90号5楼",
                                "modelId", modelId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiverName").value("张三"))
                .andExpect(jsonPath("$.data.phone").value("13800001234"))
                .andExpect(jsonPath("$.data.province").value("浙江省"))
                .andExpect(jsonPath("$.data.city").value("杭州市"))
                .andExpect(jsonPath("$.data.district").value("西湖区"))
                .andExpect(jsonPath("$.data.street").value("文三路"))
                .andExpect(jsonPath("$.data.detailAddress").value("90号5楼"))
                .andExpect(jsonPath("$.data.fullAddress").value("浙江省杭州市西湖区文三路90号5楼"))
                .andExpect(jsonPath("$.data.confidence").value(0.96));

        Long after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_model_call_log WHERE user_id = ?", Long.class, userId);
        assertThat(after).isEqualTo(before);
    }

    private String login() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", "Test@123456"
        ));
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asText();
    }
}
