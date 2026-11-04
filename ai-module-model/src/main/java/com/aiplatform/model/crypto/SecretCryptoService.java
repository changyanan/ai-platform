package com.aiplatform.model.crypto;

import com.aiplatform.model.config.SecretCryptoProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 模型 API Key 服务端加密。AES-256-GCM，随机 12 字节 IV，输出带版本前缀的 Base64。
 * 明文密钥只在模型调用瞬间存在于内存，不写日志、不返回前端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecretCryptoService {

    private static final String VERSION_PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretCryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;

    @PostConstruct
    void init() {
        String masterKey = properties.getMasterKey();
        if (masterKey == null || masterKey.length() < 16) {
            throw new IllegalStateException("AI_PLATFORM_MASTER_KEY 至少需要 16 个字符");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("无法初始化 API Key 加密密钥", ex);
        }
        if (System.getenv("AI_PLATFORM_MASTER_KEY") == null) {
            log.warn("正在使用内置演示主密钥加密模型 API Key，生产部署必须设置 AI_PLATFORM_MASTER_KEY");
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("待加密内容不能为空");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("API Key 加密失败", ex);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            throw new IllegalArgumentException("密文不能为空");
        }
        if (!encryptedText.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("不支持的密文版本");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText.substring(VERSION_PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文格式不正确");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("API Key 解密失败，请检查主密钥是否发生变化", ex);
        }
    }

    /**
     * 只展示不可逆的尾号提示，绝不返回密钥内容。
     */
    public String mask(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        String trimmed = plaintext.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, Math.min(4, trimmed.length())) + "****"
                + trimmed.substring(trimmed.length() - 4);
    }
}
