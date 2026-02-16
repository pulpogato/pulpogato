package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.FileInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * <p>This integration test verifies that the {@link JwtFilter} can successfully generate a JWT and authenticate with the GitHub API.
 * By default, this test will be skipped unless the following environment variables are set:</p>
 * <ul>
 *     <li><code>GITHUB_BASE_URL</code>: The base URL for the
 *     GitHub API (e.g., <code>https://api.github.com</code> or a GitHub Enterprise URL).</li>
 *     <li><code>GITHUB_APP_ID</code>: The numeric ID of the GitHub App to authenticate as.</li>
 *     <li><code>GITHUB_APP_PRIVATE_KEY_PATH</code>: The file path to the PEM-encoded private key for the GitHub App.</li>
 * </ul>
 *
 * <p>When the test is run with the necessary environment variables, it will attempt to connect to the GitHub API using the {@link JwtFilter} and retrieve information about the authenticated GitHub App. The test asserts that the response contains expected fields such as "slug", "description", "created_at", "installations_count", and "permissions".</p>
 * <p>Examples</p>
 * <pre>{@code
 *   GITHUB_BASE_URL=https://github.example.com/api/v3 \
 *      GITHUB_APP_ID=157 \
 *      GITHUB_APP_PRIVATE_KEY_PATH=$HOME/Downloads/jenkins.2025-09-30.private-key.pem \
 *      ./gradlew :pulpogato-common:test
 *   GITHUB_BASE_URL=https://api.github.com \
 *      GITHUB_APP_ID=42 \
 *      GITHUB_APP_PRIVATE_KEY_PATH=$HOME/Downloads/jenkins.2025-09-30.private-key.pem \
 *      ./gradlew :pulpogato-common:test
 * }</pre>
 */
class JwtFilterIntegrationTest {
    @Test
    void testGitHubConnection() throws Exception {
        String githubBaseUrl = System.getenv("GITHUB_BASE_URL");
        String githubAppIdString = System.getenv("GITHUB_APP_ID");
        String githubAppPrivateKeyPath = System.getenv("GITHUB_APP_PRIVATE_KEY_PATH");
        String githubJwtRetries = System.getenv("GITHUB_JWT_RETRIES");
        int attempts = githubJwtRetries == null ? 1 : Integer.parseInt(githubJwtRetries);

        assumeThat(githubBaseUrl).as("GITHUB_BASE_URL is not set").isNotNull().isNotEmpty();

        assumeThat(githubAppIdString).as("GITHUB_APP_ID is not set").isNotNull().isNotEmpty();
        long githubAppId = Long.parseLong(githubAppIdString);

        assumeThat(githubAppPrivateKeyPath)
                .as("GITHUB_APP_PRIVATE_KEY_PATH is not set")
                .isNotNull()
                .isNotEmpty();

        String privateKeyPem;
        try (var pemStream = new FileInputStream(githubAppPrivateKeyPath)) {
            assumeThat(pemStream)
                    .as("GITHUB_APP_PRIVATE_KEY_PATH does not exist")
                    .isNotNull();
            privateKeyPem = new String(pemStream.readAllBytes());
        }

        var jwtFactory = new JwtFactory(privateKeyPem, githubAppId);
        var jwtFilter = JwtFilter.builder().jwtFactory(jwtFactory).build();

        var webClient =
                WebClient.builder().baseUrl(githubBaseUrl).filter(jwtFilter).build();
        assertThat(webClient).isNotNull();

        for (int i = 0; i < attempts; i++) {
            if (i > 0) {
                Thread.sleep(30_000);
            }
            var app = webClient
                    .get()
                    .uri("/app")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            assertThat(app)
                    .isNotNull()
                    .contains("slug", "description", "created_at", "installations_count", "permissions");
        }
    }
}
