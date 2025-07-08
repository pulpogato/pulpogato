package io.github.pulpogato.test;

import io.github.pulpogato.common.Generated;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
        String reportPath = "build/reports/generated-test-failures.md";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(reportPath))) {
            writer.println("# Generated Test Failures Report");
            writer.println();
            writer.println("Generated at: " + LocalDateTime.now());
            writer.println("Total failures: " + failures.size());
            writer.println();
            
            for (GeneratedTestFailure failure : failures) {
                generateIssueDescription(writer, failure);
            }
            
        } catch (IOException e) {
            System.err.println("Failed to write generated test failure report: " + e.getMessage());
        }
        
        System.out.println("Generated test failure report written to: " + reportPath);
    }
    
    private static void generateIssueDescription(PrintWriter writer, GeneratedTestFailure failure) {
        writer.println(MessageFormat.format("""
                ----
                ## [Schema Inaccuracy] Test failure in {0} for {1}
                
                ### Ignore YAML
                
                ```yaml
                - example: "{2}"
                  reason: "TODO: Diagnose this"
                  versions:
                    - {0}
                ```

                ### Bug Report
                
                This is the JSON ref
                ```
                {2}
                ```
                
                """,
                failure.ghVersion(),
                extractSchemaPath(failure.schemaRef()),
                failure.schemaRef()
        ));

        if (failure.throwable() != null) {
            writer.println(MessageFormat.format("""
                    The example has this
                    ```
                    {0}
                    ```
                    """, failure.throwable().getMessage()));
        }

        writer.println("**Test Details:**");
        writer.println("- Test Class: `" + failure.className() + "`");
        writer.println("- Test Method: `" + failure.methodName() + "`");
        writer.println();
    }
    
    private static String extractSchemaPath(String schemaRef) {
        // Extract the meaningful part of the schema reference
        if (schemaRef.startsWith("#/webhooks/")) {
            String path = schemaRef.substring(2); // Remove "#/"
            int examplesIndex = path.indexOf("/examples");
            if (examplesIndex > 0) {
                path = path.substring(0, examplesIndex);
            }
            return path.replace("/post/requestBody/content/application~1json", "");
        }
        return schemaRef;
    }
    
    private record GeneratedTestFailure(
        String displayName,
        String className,
        String methodName,
        String ghVersion,
        String schemaRef,
        String codeRef,
        Throwable throwable
    ) {}
}