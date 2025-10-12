# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Pulpogato is a Java client library for GitHub's REST and GraphQL APIs, supporting multiple GitHub versions including FPT (free/public), GHEC (Enterprise Cloud), and various GHES (Enterprise Server) versions.

## Architecture

The project uses a multi-module Gradle structure:
- **pulpogato-common**: Shared types and utilities
- **pulpogato-rest-{version}**: REST API clients for each GitHub version
- **pulpogato-graphql-{version}**: GraphQL clients for each GitHub version  
- **pulpogato-rest-tests**: Test utilities and base test classes
- **gradle/pulpogato-rest-codegen**: Custom Gradle plugin for REST code generation

Code generation is core to the project:
- REST clients are generated from GitHub's OpenAPI specifications
- GraphQL clients are generated using Netflix DGS from GitHub's GraphQL schemas
- Each variant (fpt, ghec, ghes-X.XX) generates separate artifacts

## Development Commands

### Build plugin
```bash
/gradlew --project-dir gradle/pulpogato-rest-codegen check
```

### Build
```bash
./gradlew build
```

### Build on low-memory/low-CPU systems
```bash
./gradlew build --max-workers=2
```

### Run tests
```bash
# All tests
./gradlew test

# Specific module tests
./gradlew :pulpogato-rest-fpt:test

# Single test class
./gradlew :pulpogato-rest-fpt:test --tests UsersApiIntegrationTest
```

### Update GitHub API schema version
```bash
./update-schema-version.sh
```

### Integration test requirements
- Set `GITHUB_TOKEN` environment variable
- For GHES testing, also set `GITHUB_HOST` and `GITHUB_PORT`

## Key Gradle Configuration

- Java 21 toolchain required
- Parallel builds enabled (`org.gradle.parallel=true`)
- Custom JVM args for compilation with module exports
- Memory allocated: 4GB (`-Xmx4g`)
- GitHub API version tracked in `gradle.properties` as `gh.api.version`

## Testing Approach

- Integration tests use VCR-style HTTP recording (YAML tapes in `src/test/resources/tapes/`)
- Webhook tests capture real HTTP requests in `pulpogato-rest-tests/src/main/resources/webhooks/`
- Automatically generated tests from OpenAPI examples
- JUnit 5 platform with Mockito agent for test execution