package com.aiplatform.model.crypto;

import com.aiplatform.model.config.SecretCryptoProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCryptoServiceTest {

    private SecretCryptoService service(String masterKey) {
        SecretCryptoProperties properties = new SecretCryptoProperties();
        properties.setMasterKey(masterKey);
        SecretCryptoService crypto = new SecretCryptoService(properties);
        crypto.init();
        return crypto;
    }

    @Test
    void encryptsWithRandomIvAndNeverStoresPlaintext() {
        SecretCryptoService crypto = service("unit-test-master-key-0123456789abcdef");
        String plain = "sk-super-secret-api-key-value";

        String first = crypto.encrypt(plain);
        String second = crypto.encrypt(plain);

        assertThat(first).startsWith("v1:");
        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain(plain);
        assertThat(crypto.decrypt(first)).isEqualTo(plain);
        assertThat(crypto.decrypt(second)).isEqualTo(plain);
        assertThat(crypto.mask(plain)).isEqualTo("sk-s****alue");
    }

    @Test
    void differentMasterKeyCannotDecrypt() {
        String encrypted = service("unit-test-master-key-0123456789abcdef").encrypt("sk-test-value-123456");
        SecretCryptoService other = service("another-long-master-key-fedcba9876543210");

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    void rejectsTooShortMasterKey() {
        assertThatThrownBy(() -> service("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少需要 16");
    }
}
