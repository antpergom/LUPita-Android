package com.antoniopg.lupita.tools.boundaries

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoundariesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(path: String, content: String) {
        val f = File(tmp.root, path)
        f.parentFile.mkdirs()
        f.writeText(content)
    }

    private fun dirOf(module: String) = module.trim(':').replace(':', '/')

    private fun settings(vararg modules: String) =
        write("settings.gradle.kts", modules.joinToString("\n") { "include(\"$it\")" })

    private fun gradle(module: String, body: String = "") = write("${dirOf(module)}/build.gradle.kts", body)

    private fun source(module: String, body: String, under: String = "src/main/kotlin") =
        write("${dirOf(module)}/$under/Foo.kt", body)

    private fun messages() = check(tmp.root).map { "${it.module}: ${it.message}" }

    @Test
    fun `a clean layout has no violations`() {
        settings(":app", ":core:model", ":capability:text", ":ui:overlay")
        gradle(":core:model")
        gradle(":capability:text", """implementation(project(":core:model"))""")
        gradle(":ui:overlay", """implementation(project(":core:model"))""")
        gradle(":app", """implementation(project(":ui:overlay")); implementation(project(":capability:text"))""")
        source(":core:model", "package x\nimport kotlin.collections.List\n")

        assertEquals(emptyList<String>(), messages())
    }

    @Test
    fun `core model importing android is a violation`() {
        settings(":core:model")
        source(":core:model", "package x\nimport android.content.Context\n")

        val m = messages()
        assertEquals(1, m.size)
        assertTrue(m[0], m[0].startsWith(":core:model: importa Android"))
    }

    @Test
    fun `androidx counts as android`() {
        settings(":capability:text")
        source(":capability:text", "import androidx.room.Entity\n")

        assertEquals(1, messages().size)
    }

    @Test
    fun `the screen capability is the declared exception and may import android`() {
        settings(":capability:screen")
        source(":capability:screen", "import android.accessibilityservice.AccessibilityService\n")

        assertEquals(emptyList<String>(), messages())
    }

    @Test
    fun `a capability cannot depend on the ui`() {
        settings(":capability:text", ":ui:overlay")
        gradle(":capability:text", """implementation(project(":ui:overlay"))""")

        val m = messages()
        assertEquals(1, m.size)
        assertTrue(m[0], m[0].contains(":ui:overlay"))
    }

    @Test
    fun `a capability cannot depend on another capability`() {
        settings(":capability:text", ":capability:image")
        gradle(":capability:text", """api(project(":capability:image"))""")

        assertEquals(1, messages().size)
    }

    @Test
    fun `a ui module cannot depend on another ui module`() {
        settings(":ui:overlay", ":ui:app")
        gradle(":ui:app", """implementation(project(":ui:overlay"))""")

        assertEquals(1, messages().size)
    }

    @Test
    fun `core model depends on nothing`() {
        settings(":core:model", ":core:capability")
        gradle(":core:model", """implementation(project(":core:capability"))""")

        assertTrue(messages().any { it.startsWith(":core:model: no puede depender de ningun modulo") })
    }

    @Test
    fun `an unclassified module fails closed`() {
        settings(":misc")

        val m = messages()
        assertEquals(1, m.size)
        assertTrue(m[0], m[0].contains("sin clasificar"))
    }

    @Test
    fun `generated code under a build folder is ignored`() {
        settings(":core:model")
        source(":core:model", "import android.os.Build\n", under = "src/main/kotlin/build")

        assertEquals(emptyList<String>(), messages())
    }

    @Test
    fun `missing settings file is reported and not silently accepted`() {
        assertEquals(1, messages().size)
    }

    @Test
    fun `parsers extract modules and project dependencies`() {
        assertEquals(
            listOf(":a", ":b:c", ":x"),
            parseModules("include(\":a\")\ninclude(\":b:c\", \":x\")"),
        )
        assertEquals(
            listOf(":core:model", ":ui:app"),
            parseProjectDeps("""api(project(":core:model")); testImplementation(project(":ui:app"))"""),
        )
    }
}
