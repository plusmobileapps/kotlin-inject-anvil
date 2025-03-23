@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.compile
import software.amazon.lastmile.kotlin.inject.anvil.generatedComponent
import software.amazon.lastmile.kotlin.inject.anvil.inner
import software.amazon.lastmile.kotlin.inject.anvil.isAnnotatedWith
import software.amazon.lastmile.kotlin.inject.anvil.isNotAnnotatedWith
import software.amazon.lastmile.kotlin.inject.anvil.origin

class ContributesAssistedFactoryProcessorTest {
    @Test
    fun `a component interface is generate with assisted factory`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesAssistedFactory
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Assisted

            interface Base

            @Inject
            @ContributesAssistedFactory(
                scope = Unit::class,
                boundType = Base::class,
                assistedFactory = BaseFactory::class,
            )
            class Impl(
                @Assisted val id: String,
            ) : Base   

            interface BaseFactory {
                fun create(id: String): Base
            }
            """
        ) {
            val component = impl.generatedComponent

            assertThat(component.simpleName).isEqualTo("InjectImpl")
        }
    }

    private val JvmCompilationResult.base: Class<*>
        get() = classLoader.loadClass("software.amazon.test.Base")

    private val JvmCompilationResult.base2: Class<*>
        get() = classLoader.loadClass("software.amazon.test.Base2")

    private val JvmCompilationResult.impl: Class<*>
        get() = classLoader.loadClass("software.amazon.test.Impl")

    private val JvmCompilationResult.factoryImpl: Class<*>
        get() = classLoader.loadClass("software.amazon.test.BaseFactoryImpl")

    private val JvmCompilationResult.impl2: Class<*>
        get() = classLoader.loadClass("software.amazon.test.Impl2")
}
