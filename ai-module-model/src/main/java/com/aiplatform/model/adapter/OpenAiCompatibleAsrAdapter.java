package com.aiplatform.model.adapter;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.model.enums.ProviderProtocol;
import com.aiplatform.model.runtime.ModelRuntime;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * OpenAI-compatible 语音转写适配器，调用 POST /audio/transcriptions。
 * 供应商不支持或未配置时由上层明确报错，不返回伪造转写。
 */
@Component
public class OpenAiCompatibleAsrAdapter implements AsrModelAdapter {

    @Override
    public boolean supports(ProviderProtocol protocol) {
        return protocol == ProviderProtocol.OPENAI_COMPATIBLE;
    }

    @Override
    public AsrTranscription transcribe(ModelRuntime runtime, byte[] audio, String filename, String mimeType) {
        if (audio == null || audio.length == 0) {
            throw BusinessException.badRequest("音频内容为空");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(30, runtime.timeoutSeconds())));
        RestClient client = RestClient.builder()
                .baseUrl(normalizeBaseUrl(runtime.baseUrl()))
                .requestFactory(factory)
                .build();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return StringUtils.hasText(filename) ? filename : "audio.bin";
            }
        });
        body.add("model", runtime.modelName());
        body.add("response_format", "verbose_json");

        try {
            RestClient.RequestBodySpec spec = client.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(runtime.apiKey()) && !"not-required".equals(runtime.apiKey())) {
                spec = spec.header("Authorization", "Bearer " + runtime.apiKey());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> response = spec.body(body).retrieve().body(Map.class);
            if (response == null || !StringUtils.hasText(String.valueOf(response.get("text")))) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "ASR 返回内容为空");
            }
            Object duration = response.get("duration");
            Integer durationSeconds = duration instanceof Number number ? number.intValue() : null;
            Object language = response.get("language");
            return new AsrTranscription(
                    String.valueOf(response.get("text")),
                    language == null ? null : String.valueOf(language),
                    durationSeconds
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message != null && message.length() > 300) {
                message = message.substring(0, 300);
            }
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                    "ASR 调用失败：" + (message == null ? ex.getClass().getSimpleName() : message));
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
