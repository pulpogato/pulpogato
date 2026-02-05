package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JwtFactoryTest {

    private static final Long ISSUER = 12345L;
    private static RSAPublicKey publicKey;
    private static String pkcs1Pem;
    private static String pkcs8Pem;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        var keyPair = keyGen.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        var privateKey = (RSAPrivateKey) keyPair.getPrivate();

        pkcs1Pem = toPkcs1Pem(keyPair);
        pkcs8Pem = toPkcs8Pem(privateKey);
    }

    private static String toPkcs1Pem(KeyPair keyPair) throws Exception {
        var writer = new StringWriter();
        try (var pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(keyPair);
        }
        return writer.toString();
    }

    private static String toPkcs8Pem(RSAPrivateKey privateKey) throws Exception {
        var writer = new StringWriter();
        try (var pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(new JcaPKCS8Generator(privateKey, null));
        }
        return writer.toString();
    }

    @Test
    void parsesPkcs1Format() {
        var factory = new JwtFactory(pkcs1Pem, ISSUER);

        // JWT uses seconds precision, so truncate to seconds
        var issuedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var expiresAt = issuedAt.plusSeconds(600);
        var token = factory.create(issuedAt, expiresAt);

        var verifier = JWT.require(Algorithm.RSA256(publicKey)).build();
        var decoded = verifier.verify(token);

        assertThat(decoded.getIssuer()).isEqualTo(ISSUER.toString());
        assertThat(decoded.getIssuedAtAsInstant()).isEqualTo(issuedAt);
        assertThat(decoded.getExpiresAtAsInstant()).isEqualTo(expiresAt);
    }

    @Test
    void parsesPkcs8Format() {
        var factory = new JwtFactory(pkcs8Pem, ISSUER);

        var issuedAt = Instant.now();
        var expiresAt = issuedAt.plusSeconds(600);
        var token = factory.create(issuedAt, expiresAt);

        var verifier = JWT.require(Algorithm.RSA256(publicKey)).build();
        var decoded = verifier.verify(token);

        assertThat(decoded.getIssuer()).isEqualTo(ISSUER.toString());
    }

    @Test
    void createsValidRs256Jwt() {
        var factory = new JwtFactory(pkcs1Pem, ISSUER);

        var issuedAt = Instant.parse("2024-01-01T00:00:00Z");
        var expiresAt = Instant.parse("2024-01-01T00:10:00Z");
        var token = factory.create(issuedAt, expiresAt);

        var decoded = JWT.decode(token);

        assertThat(decoded.getAlgorithm()).isEqualTo("RS256");
        assertThat(decoded.getIssuer()).isEqualTo(ISSUER.toString());
        assertThat(decoded.getIssuedAtAsInstant()).isEqualTo(issuedAt);
        assertThat(decoded.getExpiresAtAsInstant()).isEqualTo(expiresAt);
    }

    @Test
    void throwsExceptionForInvalidPem() {
        assertThatThrownBy(() -> new JwtFactory("not a valid pem", ISSUER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported PEM format");
    }

    @Test
    void throwsExceptionForEmptyPem() {
        assertThatThrownBy(() -> new JwtFactory("", ISSUER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported PEM format");
    }
}
