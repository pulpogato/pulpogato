package io.github.pulpogato.common;

import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.time.OffsetDateTime;

/**
 * Custom Jackson module for the Pulpogato project.
 * This module registers custom deserializers for specific types.
 */
public class PulpogatoModule extends SimpleModule {

    /**
     * Constructs a new {@code PulpogatoModule} with the version information from {@link PackageVersion}.
     */
    public PulpogatoModule() {
        super(PackageVersion.VERSION);
    }

    /**
     * Sets up the module by adding custom deserializers.
     *
     * @param context the setup context
     */
    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);

        var deserializer = new SimpleDeserializers();

        deserializer.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());

        context.addDeserializers(deserializer);
    }
}
