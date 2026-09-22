pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Descarga un JDK 17 (toolchain) si la maquina no lo tiene, en vez de fallar.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "lupita"

// Un modulo por linea. Las fronteras entre ellos las hace cumplir :tools:boundaries (falla el
// build si se rompen) -- si anades un modulo nuevo, clasificalo alli o el build fallara a proposito.
include(":app")
include(":core:model")
include(":orchestrator")
include(":capability:privacy")
include(":capability:screen")
include(":capability:context")
include(":capability:text")
include(":capability:image")
include(":capability:ocr")
include(":capability:web")
include(":ui:overlay")
include(":ui:app")
include(":ui:theme")
include(":tools:boundaries")
