package com.aiplatform.server.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@Tag(name = "系统")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "健康检查（公开）")
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "application", "ai-platform",
                "timestamp", OffsetDateTime.now()
        );
    }
}
