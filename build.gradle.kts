plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val architectureCheck = tasks.register("architectureCheck") {
    group = "verification"
    description = "Checks Clean Architecture package import direction."

    doLast {
        val sourceRoot = file("app/src/main/java/com/echelon/console")
        val forbiddenImports = mapOf(
            "/domain/" to listOf("android.", "androidx.", ".application.", ".data.", ".presentation."),
            "/application/usecase/" to listOf("android.", "androidx.", ".data.", ".presentation."),
            "/data/" to listOf(".presentation."),
            "/presentation/" to listOf(".data."),
        )
        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { sourceFile ->
                val normalizedPath = "/${sourceFile.relativeTo(sourceRoot).invariantSeparatorsPath}"
                val rules = forbiddenImports.entries.firstOrNull { (folder, _) ->
                    normalizedPath.contains(folder)
                } ?: return@flatMap emptySequence()
                sourceFile.readLines().asSequence()
                    .mapIndexedNotNull { index, line ->
                        val importLine = line.trim()
                        if (importLine.startsWith("import ") && rules.value.any(importLine::contains)) {
                            "${sourceFile.relativeTo(projectDir)}:${index + 1}: $importLine"
                        } else {
                            null
                        }
                    }
            }
            .toList()

        check(violations.isEmpty()) {
            "Clean Architecture import violations:\n${violations.joinToString("\n")}"
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("architectureCheck"))
    }
}

tasks.register("check") {
    group = "verification"
    dependsOn(architectureCheck, ":app:check")
}
