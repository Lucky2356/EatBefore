plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

// Static analysis and formatting apply to every module (currently just :app).
allprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)

    detekt {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
        source.setFrom(files("src/main/java", "src/test/java", "src/androidTest/java"))
    }

    // Style rules also live in .editorconfig (for IDEs); spotless gets them explicitly
    // because its ktlint step does not read the root .editorconfig reliably.
    val ktlintRules = mapOf(
        "max_line_length" to "140",
        "ij_kotlin_allow_trailing_comma" to "true",
        "ij_kotlin_allow_trailing_comma_on_call_site" to "true",
        // @Composable functions are PascalCase by Compose convention.
        "ktlint_standard_function-naming" to "disabled",
        // Expression bodies are wrapped by hand where that reads better.
        "ktlint_standard_function-signature" to "disabled",
    )

    spotless {
        kotlin {
            target("src/**/*.kt")
            ktlint(rootProject.libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(rootProject.libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
        }
    }
}
