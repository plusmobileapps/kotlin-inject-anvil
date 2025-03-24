package software.amazon.lastmile.kotlin.inject.anvil

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

@Target(CLASS)
@Repeatable
public annotation class ContributesAssistedFactory(
    /**
     * The scope in which to include this contributed binding.
     */
    val scope: KClass<*>,
    /**
     * The type that this class is bound to. When injecting [boundType] the concrete class will be
     * this annotated class.
     */
    val boundType: KClass<*> = Unit::class,

    val assistedFactory: KClass<*>,
)
