package io.github.pulpogato.common.client;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.io.IOException;
import java.io.StringReader;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

/**
 * Creates RS256-signed JSON Web Tokens for GitHub App authentication.
 *
 * <p>This factory generates JWTs that follow GitHub's requirements:</p>
 * <ul>
 *   <li>Algorithm: RS256 (RSA with SHA-256)</li>
 *   <li>Issuer (iss): The GitHub App ID or client ID</li>
 * </ul>
 *
 * <p>Supports both PKCS#1 ({@code BEGIN RSA PRIVATE KEY}) and PKCS#8 ({@code BEGIN PRIVATE KEY})
 * PEM formats. The private key is parsed once at construction time.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * var jwtFactory = new JwtFactory(privateKeyPem, "12345");
 * var token = jwtFactory.create(issuedAt, expiresAt);
 * }</pre>
 *
 * @see JwtFilter
 * @see <a href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app">GitHub JWT Documentation</a>
 */
public class JwtFactory {

    private final String issuer;
    private final RSAPrivateKey cachedPrivateKey;

    /**
     * Creates a new JWT factory.
     *
     * @param pem the RSA private key in PEM format (PKCS#1 or PKCS#8)
     * @param gitHubAppId the issuer claim value (typically the GitHub App ID)
     * @throws IllegalArgumentException if the PEM cannot be parsed
     */
    public JwtFactory(String pem, Long gitHubAppId) {
        this.issuer = String.valueOf(gitHubAppId);
        this.cachedPrivateKey = parsePrivateKey(pem);
    }

    private static RSAPrivateKey parsePrivateKey(String pem) {
        try (var reader = new PEMParser(new StringReader(pem))) {
            var pemObject = reader.readObject();
            var converter = new JcaPEMKeyConverter();
            return (RSAPrivateKey)
                    switch (pemObject) {
                        case PEMKeyPair keyPair -> converter.getKeyPair(keyPair).getPrivate();
                        case PrivateKeyInfo keyInfo -> converter.getPrivateKey(keyInfo);
                        case null, default -> throw new IllegalArgumentException("Unsupported PEM format");
                    };
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse PEM private key", e);
        }
    }

    /**
     * Creates a signed JWT with the specified timestamps.
     *
     * @param issuedAt the issued-at (iat) claim value
     * @param expiresAt the expiration (exp) claim value
     * @return the signed JWT string
     */
    public String create(Instant issuedAt, Instant expiresAt) {
        return JWT.create()
                .withIssuer(issuer)
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(Algorithm.RSA256(cachedPrivateKey));
    }
}
