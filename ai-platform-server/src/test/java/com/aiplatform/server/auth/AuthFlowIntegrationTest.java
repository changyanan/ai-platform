package com.aiplatform.server.auth;

import com.aiplatform.server.user.entity.User;
import com.aiplatform.server.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证主链路的集成测试：真实 Spring Security 过滤器、MySQL 与 Flyway。
 * 测试运行在独立 ai_platform_test 库中，结束时事务回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void wrongPasswordDoesNotRevealAccountDetails() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"not-exists","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void firstLoginMustChangePasswordAndOldTokenIsInvalidated() throws Exception {
        String username = "it_admin_" + UUID.randomUUID().toString().substring(0, 8);
        String initialPassword = "Initial@123";
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        user.setDisplayName("Integration Admin");
        user.setRole("ADMIN");
        user.setEnabled(true);
        user.setMustChangePassword(true);
        user.setTokenVersion(0);
        userMapper.insert(user);

        String loginBody = objectMapper.writeValueAsString(
                java.util.Map.of("username", username, "password", initialPassword));
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();
        String initialToken = readToken(loginResult);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + initialToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        String changeBody = objectMapper.writeValueAsString(java.util.Map.of(
                "currentPassword", initialPassword,
                "newPassword", "Changed@2026",
                "confirmPassword", "Changed@2026"));
        MvcResult changeResult = mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + initialToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andReturn();
        String changedToken = readToken(changeResult);
        assertThat(changedToken).isNotEqualTo(initialToken);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + changedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 密码修改会使旧 tokenVersion 失效，旧令牌不可继续使用。
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + initialToken))
                .andExpect(status().isUnauthorized());
    }

    private String readToken(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }
}
