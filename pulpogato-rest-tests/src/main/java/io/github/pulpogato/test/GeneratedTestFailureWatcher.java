package io.github.pulpogato.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pulpogato.common.Generated;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GeneratedTestFailureWatcher implements TestWatcher {
    
    private static final List<GeneratedTestFailure> failures = new ArrayList<>();
    
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        Optional<Method> testMethod = context.getTestMethod();
        if (testMethod.isPresent()) {
            Generated generated = findGeneratedAnnotation(testMethod.get(), context.getTestClass().orElse(null));
            if (generated != null) {
                String displayName = context.getDisplayName();
                String className = context.getTestClass().map(Class::getName).orElse("Unknown");
                String methodName = testMethod.get().getName();
                
                GeneratedTestFailure failure = new GeneratedTestFailure(
                    displayName,
                    className,
                    methodName,
                    generated.ghVersion(),
                    generated.schemaRef(),
                    generated.codeRef(),
                    cause
                );
                
                synchronized (failures) {
                    failures.add(failure);
                }
                
                System.out.println("Generated test failed: " + displayName + " (Schema: " + generated.schemaRef() + ")");
            }
        }
    }
    
    private Generated findGeneratedAnnotation(Method testMethod, Class<?> testClass) {
        // Check method first
        Generated methodGenerated = testMethod.getAnnotation(Generated.class);
        if (methodGenerated != null) {
            return methodGenerated;
        }
        
        // Check enclosing classes
        Class<?> currentClass = testClass;
        while (currentClass != null) {
            Generated classGenerated = currentClass.getAnnotation(Generated.class);
            if (classGenerated != null) {
                return classGenerated;
            }
            currentClass = currentClass.getEnclosingClass();
        }
        
        return null;
    }
    
    // Called when JVM shuts down
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (failures) {
                if (!failures.isEmpty()) {
                    generateReport();
                }
            }
        }));
    }
    
    private static void generateReport() {
        String reportPath = "build/reports/generated-test-failures.jsonl";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(reportPath))) {
            for (GeneratedTestFailure failure : failures) {
                var data = Map.ofEntries(
                        Map.entry("ghVersion", failure.ghVersion),
                        Map.entry("exampleRef", failure.exampleRef()),
                        Map.entry("schemaRef", failure.getSchemaRef()),
                        Map.entry("message", failure.getFieldMessage()),
                        Map.entry("snippet", failure.getSnippet())
                );
                writer.println(new ObjectMapper().writeValueAsString(data));
            }
            
        } catch (IOException e) {
            System.err.println("Failed to write generated test failure report: " + e.getMessage());
        }
        
        System.out.println("Generated test failure report written to: " + reportPath);
    }

    private record GeneratedTestFailure(
        String displayName,
        String className,
        String methodName,
        String ghVersion,
        String exampleRef,
        String codeRef,
        Throwable throwable
    ) {
        public String getSchemaRef() {
            var stackTrace = throwable.getStackTrace();
            for (var element : stackTrace) {
                var className = element.getClassName();
                try {
                    var clazz = Class.forName(className);
                    var classAnnotation = clazz.getAnnotation(Generated.class);
                    if (classAnnotation != null && !classAnnotation.schemaRef().isEmpty()) {
                        return classAnnotation.schemaRef();
                    }
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            return "unknown";
        }

        public String getSnippet() {
            if (throwable instanceof UnrecognizedPropertyExceptionWrapper e) {
                return e.getSnippet();
            } else {
                return "No snippet available.";
            }
        }

        public String getFieldMessage() {
            if (throwable instanceof UnrecognizedPropertyExceptionWrapper e) {
                return e.getFieldMessage();
            } else {
                return throwable.getMessage();
            }
        }
    }
}