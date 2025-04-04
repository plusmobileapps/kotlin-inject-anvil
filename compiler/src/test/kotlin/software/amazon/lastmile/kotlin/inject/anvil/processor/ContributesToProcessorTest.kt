@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.compile
import software.amazon.lastmile.kotlin.inject.anvil.componentInterface
import software.amazon.lastmile.kotlin.inject.anvil.generatedComponent
import software.amazon.lastmile.kotlin.inject.anvil.inner
import software.amazon.lastmile.kotlin.inject.anvil.origin

class ContributesToProcessorTest {

    @Test
    fun `a component interface is generated in the lookup package for a contributed component interface`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

            @ContributesTo(Unit::class)
            interface ComponentInterface
            """,
        ) {
            val generatedComponent = componentInterface.generatedComponent

            assertThat(generatedComponent.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(generatedComponent.interfaces).containsExactly(componentInterface)
            assertThat(generatedComponent.origin).isEqualTo(componentInterface)
        }
    }

    @Test
    fun `a component interface is generated in the lookup package for a contributed inner component interface`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

            interface ComponentInterface {
                @ContributesTo(Unit::class)
                interface Inner
            }
            """,
        ) {
            val generatedComponent = componentInterface.inner.generatedComponent

            assertThat(generatedComponent.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(generatedComponent.interfaces).containsExactly(componentInterface.inner)
            assertThat(generatedComponent.origin).isEqualTo(componentInterface.inner)
        }
    }

    @Test
    fun `a contributed component interface must be public`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

            @ContributesTo(Unit::class)
            private interface ComponentInterface
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains("Contributed component interfaces must be public.")
        }
    }

    @Test
    fun `only interfaces can be contributed`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

            @ContributesTo(Unit::class)
            abstract class ComponentInterface
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains("Only interfaces can be contributed.")
        }
    }

    @Test
    fun `a replaced component must use the same scope`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

            interface Base

            @ContributesTo(AppScope::class)
            interface Component1

            @ContributesTo(Unit::class, replaces = [Component1::class])
            interface Component2
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Replaced types must use the same scope. software.amazon.test." +
                    "Component2 uses scope Unit, but tries to replace software.amazon.test." +
                    "Component1 using scope AppScope.",
            )
        }
    }

    @Test
    fun `a replaced component must use the same scope without named parameter`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

            interface Base

            @ContributesTo(AppScope::class)
            interface Component1

            @ContributesTo(Unit::class, [Component1::class])
            interface Component2
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Replaced types must use the same scope. software.amazon.test." +
                    "Component2 uses scope Unit, but tries to replace software.amazon.test." +
                    "Component1 using scope AppScope.",
            )
        }
    }
}
