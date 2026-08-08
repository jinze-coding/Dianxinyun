package com.example.siteplatform.siteaccess.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisitorDataCryptoServiceTest {

    @Test
    void encryptsWithRandomNonceAndDecryptsOriginalValue() {
        VisitorDataCryptoService crypto = new VisitorDataCryptoService("", local());
        String plaintext = "synthetic-sensitive-value";

        String first = crypto.encrypt(plaintext);
        String second = crypto.encrypt(plaintext);

        assertThat(first).startsWith("v1:").doesNotContain(plaintext);
        assertThat(second).isNotEqualTo(first);
        assertThat(crypto.decrypt(first)).isEqualTo(plaintext);
        assertThat(crypto.decrypt(second)).isEqualTo(plaintext);
        assertThat(crypto.digest(plaintext)).hasSize(64).isEqualTo(crypto.digest(plaintext));
    }

    @Test
    void rejectsTamperedCiphertextWithoutLeakingItsContent() {
        VisitorDataCryptoService crypto = new VisitorDataCryptoService("", local());
        String ciphertext = crypto.encrypt("synthetic-phone-value");
        String tampered = ciphertext.substring(0, ciphertext.length() - 1)
                + (ciphertext.endsWith("A") ? "B" : "A");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> crypto.decrypt(tampered));
        assertThat(exception.getMessage()).isEqualTo("外访敏感数据解密失败");
        assertThat(exception.getMessage()).doesNotContain("synthetic-phone-value");
    }

    @Test
    void productionRequiresExplicitKeyOfAtLeastThirtyTwoBytes() {
        assertThrows(IllegalStateException.class,
                () -> new VisitorDataCryptoService("", production()));
        assertThrows(IllegalStateException.class,
                () -> new VisitorDataCryptoService("too-short", production()));
        assertDoesNotThrow(() -> new VisitorDataCryptoService(
                "visitor-data-production-key-with-at-least-32-bytes", production()));
    }

    private MockEnvironment local() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        return environment;
    }

    private MockEnvironment production() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
