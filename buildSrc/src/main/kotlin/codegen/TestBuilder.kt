package codegen

import codegen.ext.pascalCase
import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeName
import java.util.TreeMap

object TestBuilder {
    fun buildTest(
        key: String,
        example: String,
        className: TypeName,
    ): MethodSpec {
        val om = ObjectMapper()

        val formatted =
            try {
                val parsed = om.readValue(example, TreeMap::class.java)
                om.writerWithDefaultPrettyPrinter().writeValueAsString(parsed)
            } catch (_: Exception) {
                try {
                    val parsed = om.readValue(example, List::class.java)
                    om.writerWithDefaultPrettyPrinter().writeValueAsString(parsed)
                } catch (_: Exception) {
                    example
                }
            }

        val testUtilsClass = ClassName.get("io.github.pulpogato.test", "TestUtils")

        val ignoredTestsForVersion = IgnoredTests.causes[Context.instance.get().version] ?: mapOf()
        val ignoreCause = ignoredTestsForVersion[Context.getSchemaStackRef()]
        val ignoreAnnotation =
            if (ignoreCause != null) {
                AnnotationSpec.builder(ClassName.get("org.junit.jupiter.api", "Disabled"))
                    .addMember("value", "\$S", ignoreCause)
                    .build()
            } else {
                null
            }

        val methodSpec =
            MethodSpec.methodBuilder("test${key.pascalCase()}")
                .addAnnotation(ClassName.get("org.junit.jupiter.api", "Test"))
                .addAnnotation(Annotations.generated(1))
                .addAnnotations(listOfNotNull(ignoreAnnotation))
                .addException(ClassName.get("com.fasterxml.jackson.core", "JsonProcessingException"))
                .addStatement("\$T input = /* language=JSON */ ${formatted.blockQuote()}", String::class.java)
                .addStatement("var softly = new \$T()", ClassName.get("org.assertj.core.api", "SoftAssertions"))
                .addStatement(
                    "var processed = \$T.parseAndCompare(new \$T<\$T>() {}, input, softly)",
                    testUtilsClass,
                    ClassName.get("com.fasterxml.jackson.core.type", "TypeReference"),
                    className,
                )
                .addStatement("softly.assertThat(processed).isNotNull()")
                .addStatement("softly.assertAll()")
                .build()
        return methodSpec
    }

    private fun String.quote() = "\"$this\""

    private fun String.blockQuote() = "\n${this.replace("\\", "\\\\")}\n".quote().quote().quote()
}