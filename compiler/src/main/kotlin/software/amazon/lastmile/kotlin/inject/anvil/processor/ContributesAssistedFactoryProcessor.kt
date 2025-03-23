@file:OptIn(KspExperimental::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.ContributesAssistedFactory
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.argumentOfTypeAt
import software.amazon.lastmile.kotlin.inject.anvil.decapitalize
import software.amazon.lastmile.kotlin.inject.anvil.requireQualifiedName
import kotlin.reflect.KClass

internal class ContributesAssistedFactoryProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
) : SymbolProcessor, ContextAware {

    private val anyFqName = Any::class.requireQualifiedName()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(ContributesAssistedFactory::class)
            .filterIsInstance<KSClassDeclaration>()
            .onEach {
                checkIsPublic(it)
                checkHasScope(it)
            }
            .forEach {
                generateComponentInterface(it)
            }

        return emptyList()
    }

    @Suppress("LongMethod")
    private fun generateComponentInterface(clazz: KSClassDeclaration) {
        val componentClassName = ClassName(LOOKUP_PACKAGE, clazz.safeClassName)

        val constructor = clazz.getConstructors().firstOrNull { constructor ->
            constructor.parameters.any { it.isAnnotationPresent(Assisted::class) }
        } ?: throw IllegalArgumentException(
            "No constructor with @Assisted found in ${clazz.simpleName.asString()}"
        )

        val annotations = clazz.findAnnotationsAtLeastOne(ContributesAssistedFactory::class)
        checkNoDuplicateBoundTypes(clazz, annotations)
        checkReplacesHasSameScope(clazz, annotations)

        val boundTypes = annotations
            .map {
                GeneratedFunction(
                    boundType = boundType(clazz, it),
                    assistedFactory = assistedFactoryFromAnnotation(it),
                    multibinding = false,
                )
            }
            .distinctBy {
                // The bound type of the assisted factory.
                it.bindingMethodReturnType.canonicalName
            }

        val fileSpec = FileSpec.builder(componentClassName)
            .apply {
                boundTypes.forEach { function ->
                    addImport(function.bindingMethodReturnType.packageName, function.bindingMethodReturnType.simpleName)
                    addImport(function.assistedFactoryReturnType.packageName, function.assistedFactoryReturnType.simpleName)
                }
            }
            .addType(
                TypeSpec
                    .interfaceBuilder(componentClassName)
                    .addOriginatingKSFile(clazz.requireContainingFile())
                    .addOriginAnnotation(clazz)
                    .addFunctions(
                        boundTypes.map { function ->
                            val multibindingSuffix = if (function.multibinding) {
                                "Multibinding"
                            } else {
                                ""
                            }
                            FunSpec
                                .builder(
                                    "provide${clazz.innerClassNames()}" +
                                        function.bindingMethodReturnType.simpleName +
                                        multibindingSuffix,
                                )
                                .addAnnotation(Provides::class)
                                .apply {
                                    if (function.multibinding) {
                                        addAnnotation(IntoSet::class)
                                    }
                                }
                                .apply {
                                    val parameters = constructor.parameters
                                        .filter { it.isAnnotationPresent(Assisted::class) }
                                        .map { param ->
                                        val paramName = param.name!!.asString()
                                        val paramType = param.type.resolve().toClassName()
                                        val paramAnnotations =
                                            param.annotations.map { annotation ->
                                                annotation.toAnnotationSpec()
                                            }
                                        ParameterSpec.builder(paramName, paramType).apply {
                                            paramAnnotations.forEach { addAnnotation(it) }
                                        }.build()
                                    }
                                    parameters.forEach { addParameter(it) }
                                    addParameter(
                                        ParameterSpec.builder(
                                            "realFactory",
                                            LambdaTypeName.get(
                                                parameters = parameters
                                                    .map { it.type }
                                                    .toTypedArray(),
                                                returnType = clazz.toClassName(),
                                            )
                                        ).build()
                                    )
                                    addStatement(
                                        """
                                            return object : ${function.assistedFactoryReturnType.simpleName} {
                                                override fun ${function.assistedFactoryFunctionName}(${parameters.joinToString("\n") { "${it.name}: ${it.type}" }}): ${function.bindingMethodReturnType.simpleName} {
                                                    return realFactory(${parameters.joinToString { it.name }})
                                                }
                                            }
                                        """.trimIndent()
                                    )
                                }
                                .returns(function.assistedFactoryReturnType)
                                .build()
                        },
                    )
                    .build(),
            )
            .build()

        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    private fun checkNoDuplicateBoundTypes(
        clazz: KSClassDeclaration,
        annotations: List<KSAnnotation>,
    ) {
        annotations
            .mapNotNull { boundTypeFromAnnotation(it) }
            .map { it.declaration.requireQualifiedName() }
            .takeIf { it.isNotEmpty() }
            ?.reduce { previous, next ->
                check(previous != next, clazz) {
                    "The same type should not be contributed twice: $next."
                }

                previous
            }
    }

    private fun boundTypeFromAnnotation(annotation: KSAnnotation): KSType? {
        return annotation.arguments.firstOrNull { it.name?.asString() == "boundType" }
            ?.let { it.value as? KSType }
            ?.takeIf {
                it.declaration.requireQualifiedName() != Unit::class.requireQualifiedName()
            }
    }

    private fun assistedFactoryFromAnnotation(annotation: KSAnnotation): KSType {
        return annotation.arguments.firstOrNull { it.name?.asString() == "assistedFactory" }
            ?.let { it.value as? KSType }
            ?.takeIf {
                it.declaration.requireQualifiedName() != Unit::class.requireQualifiedName()
            } ?: throw IllegalArgumentException(
                "Assisted factory type must be specified in the @ContributesAssistedFactory annotation."
            )
    }

    @Suppress("ReturnCount")
    private fun boundType(
        clazz: KSClassDeclaration,
        annotation: KSAnnotation,
    ): KSType {
        boundTypeFromAnnotation(annotation)?.let { return it }

        // The bound type is not defined in the annotation, let's inspect the super types.
        val superTypes = clazz.superTypes
            .map { it.resolve() }
            .filter { it.declaration.requireQualifiedName() != anyFqName }
            .toList()

        when (superTypes.size) {
            0 -> {
                val message = "The bound type could not be determined for " +
                    "${clazz.simpleName.asString()}. There are no super types."
                logger.error(message, clazz)
                throw IllegalArgumentException(message)
            }

            1 -> {
                return superTypes.single()
            }

            else -> {
                val message = "The bound type could not be determined for " +
                    "${clazz.simpleName.asString()}. There are multiple super types: " +
                    superTypes.joinToString { it.declaration.simpleName.asString() } +
                    "."
                logger.error(message, clazz)
                throw IllegalArgumentException(message)
            }
        }
    }

    private fun KSClassDeclaration.findAnnotationsAtLeastOne(
        annotation: KClass<out Annotation>,
    ): List<KSAnnotation> {
        return findAnnotations(annotation).also {
            check(it.isNotEmpty(), this) {
                "Couldn't find the @${annotation.simpleName} annotation for $this."
            }
        }
    }

    private inner class GeneratedFunction(
        boundType: KSType,
        assistedFactory: KSType,
        val multibinding: Boolean,
    ) {
        val bindingMethodReturnType: ClassName by lazy {
            boundType.toClassName()
        }
        val assistedFactoryReturnType: ClassName by lazy {
            assistedFactory.toClassName()
        }

        val assistedFactoryFunctionName: String by lazy {
            val classDeclaration = assistedFactory.declaration as? KSClassDeclaration ?: throw IllegalArgumentException(
                "Assisted factory type must be a class."
            )
            classDeclaration.getAllFunctions()
                .map(KSFunctionDeclaration::simpleName)
                .map { it.asString() }
                .first()
        }

        fun getFunctionNamesFromInterface(type: KSType): List<String> {
            val classDeclaration = type.declaration as? KSClassDeclaration ?: return emptyList()
            return classDeclaration.getAllFunctions()
                .map(KSFunctionDeclaration::simpleName)
                .map { it.asString() }
                .toList()
        }
    }
}
