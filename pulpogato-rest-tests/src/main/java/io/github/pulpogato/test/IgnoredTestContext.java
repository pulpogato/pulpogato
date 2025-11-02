package io.github.pulpogato.test;

import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

import io.github.pulpogato.common.Generated;
import java.lang.reflect.AnnotatedElement;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class IgnoredTestContext implements ExecutionCondition {
    @Override
    @NonNull
    public ConditionEvaluationResult evaluateExecutionCondition(@NonNull ExtensionContext context) {
        AnnotatedElement element = context.getElement().orElse(null);
        return findAnnotation(element, Generated.class)
                .map(this::toResult)
                .orElse(ConditionEvaluationResult.enabled(null));
    }

    private ConditionEvaluationResult toResult(Generated annotation) {
        Optional<String> reason = Optional.ofNullable(IgnoredTests.getCauses().get(annotation.ghVersion()))
                .map(it -> it.get(annotation.schemaRef()));

        return reason.map(ConditionEvaluationResult::disabled).orElseGet(() -> ConditionEvaluationResult.enabled(null));
    }
}
