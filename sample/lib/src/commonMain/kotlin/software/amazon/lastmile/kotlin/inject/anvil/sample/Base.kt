package software.amazon.lastmile.kotlin.inject.anvil.sample

import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesAssistedFactory

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
