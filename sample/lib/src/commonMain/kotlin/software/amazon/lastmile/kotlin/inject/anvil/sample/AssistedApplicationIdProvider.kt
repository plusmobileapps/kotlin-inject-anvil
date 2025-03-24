package software.amazon.lastmile.kotlin.inject.anvil.sample

import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.AssistedFactory
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesAssistedFactory
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

interface AssistedApplicationIdProvider {
    val id: String
}

/**
 * This is what you would need to do manually before to get an assisted injection factory.
 */
@AssistedFactory
interface AssistedApplicationIdProviderImplFactory {
    fun create(tag: String): AssistedApplicationIdProviderImpl
}

// @Inject
// @ContributesBinding(AppScope::class, AssistedApplicationIdProviderFactory::class)
// @SingleIn(AppScope::class)
// class DefaultAssistedApplicationIdProviderFactory(
//    private val realFactory: (tag: String) -> AssistedApplicationIdProviderImpl
// ) : AssistedApplicationIdProviderFactory {
//    override fun create(id: String): AssistedApplicationIdProvider = realFactory(id)
// }
//
// @ContributesTo(AppScope::class)
// @SingleIn(AppScope::class)
// interface ApplicationIdProviderFactoryComponent {
//    @Provides
//    fun providesFactory(
//        @Assisted id: String,
//        realFactory: (tag: String) -> AssistedApplicationIdProviderImpl,
//    ): AssistedApplicationIdProvider
// }

/**
 * What needs to be bound.
 */
interface AssistedApplicationIdProviderFactory {
    fun createTheThing(tag: String): AssistedApplicationIdProvider
}

@Inject
@ContributesAssistedFactory(
    scope = AppScope::class,
    boundType = AssistedApplicationIdProvider::class,
    assistedFactory = AssistedApplicationIdProviderFactory::class,
)
@SingleIn(AppScope::class)
class AssistedApplicationIdProviderImpl(
    @Assisted val tag: String,
    val applicationIdProvider: ApplicationIdProvider,
) : AssistedApplicationIdProvider {
    override val id: String
        get() = "$tag: ${applicationIdProvider.appId}"
}

@Inject
class Foo(
    val factory: AssistedApplicationIdProviderFactory,
) {
    fun bar() {
        val assisted = factory.createTheThing("example")
        println(assisted.id)
    }
}
