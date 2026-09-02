package com.example.ecommerce.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigurationTest {

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    void loadsConfiguredMatchingKeyPair() throws Exception {
        RSAKey generated = new RSAKeyGenerator(2048).keyID("configured-key").generate();

        RSAKey loaded = configuration.signingRsaKey(properties(
                encode(generated.toRSAPrivateKey().getEncoded()),
                encode(generated.toRSAPublicKey().getEncoded()),
                true
        ));

        assertThat(loaded.getKeyID()).isEqualTo("configured-key");
        assertThat(loaded.toRSAPublicKey().getModulus())
                .isEqualTo(generated.toRSAPublicKey().getModulus());
    }

    @Test
    void rejectsMismatchedConfiguredKeyPair() throws Exception {
        RSAKey first = new RSAKeyGenerator(2048).generate();
        RSAKey second = new RSAKeyGenerator(2048).generate();

        assertThatThrownBy(() -> configuration.signingRsaKey(properties(
                encode(first.toRSAPrivateKey().getEncoded()),
                encode(second.toRSAPublicKey().getEncoded()),
                true
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Configured JWT RSA key pair does not match");
    }

    @Test
    void rejectsPartialConfiguredKeyPair() throws Exception {
        RSAKey generated = new RSAKeyGenerator(2048).generate();

        assertThatThrownBy(() -> configuration.signingRsaKey(properties(
                encode(generated.toRSAPrivateKey().getEncoded()),
                "",
                false
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Both JWT private and public keys must be configured together");
    }

    @Test
    void rejectsMissingKeyWhenConfigurationIsRequired() {
        assertThatThrownBy(() -> configuration.signingRsaKey(properties("", "", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A configured JWT signing key is required in this environment");
    }

    private JwtProperties properties(String privateKey, String publicKey, boolean requireConfiguredKey) {
        return new JwtProperties(
                "http://localhost:8081",
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                "configured-key",
                privateKey,
                publicKey,
                requireConfiguredKey
        );
    }

    private String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
