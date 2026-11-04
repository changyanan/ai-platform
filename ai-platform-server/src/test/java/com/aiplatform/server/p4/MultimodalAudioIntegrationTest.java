package com.aiplatform.server.p4;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 P4 音频路由：真实 multipart 上传 → FileAsset 魔数校验 → ASR 适配器
 * → OpenAI-compatible /audio/transcriptions → 任务持久化与模型调用日志。
 * 本地 HttpServer 只用来验证协议；真实生产环境由兼容供应商的 ASR 模型承担。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultimodalAudioIntegrationTest {

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
        stubServer.createContext("/v1/audio/transcriptions", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                byte[] bytes = """
                        {"text":"您好，我想查询订单状态。","language":"zh","duration":1.2}
                        """.getBytes(StandardCharsets.UTF_8);
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

        username = "p4_asr_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Test@123456"));
        user.setDisplayName("P4 ASR User");
        user.setRole("ADMIN");
        user.setEnabled(true);
        user.setMustChangePassword(false);
        user.setTokenVersion(0);
        userMapper.insert(user);
        userId = user.getId();
        accessToken = login();

        ModelConfig config = new ModelConfig();
        config.setProviderId(4L);
        config.setDisplayName("ASR Stub Model");
        config.setModelName("whisper-test");
        config.setModelType("ASR");
        config.setApiBaseUrl("http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1");
        config.setTemperature(java.math.BigDecimal.ZERO);
        config.setCapabilities("[\"ASR\"]");
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
            jdbcTemplate.update("DELETE FROM ai_multimodal_task WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_model_call_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_file_asset WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_login_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM ai_user WHERE id = ?", userId);
        }
        if (modelId != null) {
            jdbcTemplate.update("DELETE FROM ai_model_config WHERE id = ?", modelId);
        }
    }

    @Test
    void audioTaskCallsAsrAdapterAndPersistsRealCallLog() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sample.wav", "audio/wav", wavBytes());
        var request = multipart("/api/multimodal/tasks")
                .file(file)
                .param("taskType", "AUDIO_TRANSCRIBE")
                .param("modelId", String.valueOf(modelId))
                .header("Authorization", "Bearer " + accessToken);
        var result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.fileCategory").value("AUDIO"))
                .andExpect(jsonPath("$.data.resultText").value(
                        org.hamcrest.Matchers.containsString("订单状态")))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long taskId = root.path("data").path("id").asLong();
        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT status, task_type FROM ai_multimodal_task WHERE id = ?", taskId);
        assertThat(task.get("status")).isEqualTo("SUCCESS");
        assertThat(task.get("task_type")).isEqualTo("AUDIO_TRANSCRIBE");

        Map<String, Object> call = jdbcTemplate.queryForMap(
                "SELECT request_type, success, data_source FROM ai_model_call_log "
                        + "WHERE user_id = ? ORDER BY id DESC LIMIT 1", userId);
        assertThat(call.get("request_type")).isEqualTo("ASR");
        assertThat((Boolean) call.get("success")).isTrue();
        assertThat(call.get("data_source")).isEqualTo("REAL");
    }

    private byte[] wavBytes() {
        int sampleRate = 8000;
        short channels = 1;
        short bits = 16;
        int dataSize = sampleRate * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort(channels);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * channels * bits / 8);
        buffer.putShort((short) (channels * bits / 8));
        buffer.putShort(bits);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataSize);
        return buffer.array();
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
