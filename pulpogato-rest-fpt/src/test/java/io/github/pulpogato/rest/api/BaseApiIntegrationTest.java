package io.github.pulpogato.rest.api;

import io.github.pulpogato.test.BaseIntegrationTest;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

public class BaseApiIntegrationTest extends BaseIntegrationTest {

    @Override
    protected @NonNull Optional<Package> getTestResourceRootPackage() {
        return Optional.of(BaseApiIntegrationTest.class.getPackage());
    }
}
