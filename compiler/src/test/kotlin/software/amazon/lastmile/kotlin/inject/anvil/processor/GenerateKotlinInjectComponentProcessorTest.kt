@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.tschuchort.compiletesting.JvmCompilationResult
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.compile
import software.amazon.lastmile.kotlin.inject.anvil.componentInterface
import software.amazon.lastmile.kotlin.inject.anvil.inner
import software.amazon.lastmile.kotlin.inject.anvil.kotlinInjectComponent
import software.amazon.lastmile.kotlin.inject.anvil.newComponent
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility

class GenerateKotlinInjectComponentProcessorTest {

    @Test
    fun `the kotlin-inject component is generated with merged components`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            @ContributesBinding(AppScope::class)
            class Impl(string: String) : Base

            @ContributesTo(AppScope::class)
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            interface ComponentInterface {
                val base: Base
            }
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isTrue()

            assertThat(component::class.companionObject).isNotNull()
        }
    }

    @Test
    fun `the kotlin-inject component is generated with merged components for an inner class`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            @ContributesBinding(AppScope::class)
            class Impl(string: String) : Base

            @ContributesTo(AppScope::class)
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            interface ComponentInterface {
                @MergeComponent(AppScope::class)
                @SingleIn(AppScope::class)
                interface Inner {
                    val base: Base
                }
            }
            """,
        ) {
            val component = componentInterface.inner.kotlinInjectComponent.newComponent<Any>()

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isTrue()

            assertThat(component::class.companionObject).isNotNull()
        }
    }

    @Test
    fun `the kotlin-inject component is generated with merged components for an abstract class`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            @ContributesBinding(AppScope::class)
            class Impl(string: String) : Base

            @ContributesTo(AppScope::class)
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            abstract class ComponentInterface {
                abstract val base: Base
            }
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isTrue()

            assertThat(component::class.companionObject).isNotNull()
        }
    }

    @Test
    fun `an abstract class supports parameters`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.ForScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            @ContributesBinding(AppScope::class)
            class Impl(@ForScope(AppScope::class) val string: String, val int: Int) : Base {
                override fun toString(): String = string + int
            }

            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            abstract class ComponentInterface(
                @get:Provides @get:ForScope(AppScope::class) val string: String,
                @get:Provides val int: Int,
            ) {
                abstract val base: Base
            }
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>("", 5)

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isTrue()

            assertThat(component::class.companionObject).isNotNull()
        }
    }

    @Test
    fun `an abstract class supports component parameters`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.ForScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            abstract class ComponentInterface(
                @Component val childComponent: ChildComponent,
            ) {
                abstract val string: String
            }

            @Component
            abstract class ChildComponent {
                @Provides
                fun provideString(): String = "test"
            }

            """,
        ) {
            val childComponent = classLoader.loadClass("software.amazon.test.ChildComponent")
                .newComponent<Any>()

            val component = componentInterface.kotlinInjectComponent.createFunction
                .invoke(null, componentInterface.kotlinInjectComponent::class, childComponent)

            val implValue = component::class.java.methods
                .single { it.name == "getString" }
                .invoke(component)

            assertThat(implValue).isEqualTo("test")
        }
    }

    @Test
    fun `internal visibility for component interface is supported for an abstract class`() {
        compile(
            """
            package software.amazon.test
                            
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn


            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            internal abstract class ComponentInterface
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            assertThat(component::class.visibility).isEqualTo(KVisibility.INTERNAL)
            assertThat(component::class.companionObject).isNotNull()
        }
    }

    @Test
    fun `internal visibility for component interface is supported`() {
        compile(
            """
            package software.amazon.test
                            
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn


            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            internal interface ComponentInterface
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            assertThat(component::class.visibility).isEqualTo(KVisibility.INTERNAL)
            assertThat(component::class.companionObject).isNotNull()
        }
    }

    @Test
    fun `the kotlin-inject component is generated with merged components without a scope`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @ContributesBinding(AppScope::class)
            class Impl(string: String) : Base

            @ContributesTo(AppScope::class)
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @MergeComponent(AppScope::class)
            interface ComponentInterface {
                val base: Base
            }
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isTrue()

            assertThat(component::class.companionObject).isNotNull()
        }
    }

    @Test
    fun `excluded types are excluded in the final component`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            @ContributesBinding(AppScope::class)
            class Impl(string: String) : Base
            
            @ContributesTo(AppScope::class)
            interface ImplComponent {
                val base: Base
            }

            @MergeComponent(AppScope::class, exclude = [ImplComponent::class])
            @SingleIn(AppScope::class)
            interface ComponentInterface
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            assertThat(component::class.java.methods.map { it.name }).doesNotContain("getBase")

            assertThat(component::class.companionObject).isNotNull()
        }
    }

    @Test
    fun `a function is generated to create a component`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            @ContributesBinding(AppScope::class)
            class Impl(string: String) : Base

            @ContributesTo(AppScope::class)
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            interface ComponentInterface {
                val base: Base
            }
            """,
        ) {
            // Note that this invokes the generated function and verifies that
            // KClass<ComponentInterface> is the receiver type.
            val component = componentInterface.kotlinInjectComponent.createFunction
                .invoke(null, componentInterface.kotlin)

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isTrue()

            assertThat(component::class.companionObject).isNotNull()
        }
    }

    @Test
    fun `a function is generated to create a component with parameters`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.ForScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            @ContributesBinding(AppScope::class)
            class Impl(@ForScope(AppScope::class) val string: String, val int: Int) : Base {
                override fun toString(): String = string + int
            }

            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            abstract class ComponentInterface(
                @get:Provides @get:ForScope(AppScope::class) val string: String,
                @get:Provides val int: Int,
            ) {
                abstract val base: Base
            }
            """,
        ) {
            // Note that this invokes the generated function and verifies that
            // KClass<ComponentInterface> is the receiver type.
            val component = componentInterface.kotlinInjectComponent.createFunction
                .invoke(null, componentInterface.kotlin, "hello", 6)

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isTrue()
            assertThat(implValue?.toString()).isEqualTo("hello6")

            assertThat(component::class.companionObject).isNotNull()
        }
    }

    private val JvmCompilationResult.impl: Class<*>
        get() = classLoader.loadClass("software.amazon.test.Impl")

    private val Class<*>.createFunction: Method
        get() = classLoader.loadClass("${canonicalName}Kt").methods.single { it.name == "create" }

    private val KClass<*>.companionObject: Field?
        get() = java.fields.single { it.name == "Companion" }
}
