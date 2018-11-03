import com.github.kaeptmblaubaer1000.smalisignaturepatchgenerator.patchdefparser.Parser
import com.github.kaeptmblaubaer1000.smalisignaturepatchgenerator.patchdefparser.PatchDef
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import org.jetbrains.annotations.Contract
import java.io.File

object PatchDefCompiler {
    @JvmStatic
    fun generate(input: File, output: File) {
        val patchDefs = parseFiles(input).patchDefs
        val reversedPatchDefs = mapOf(*patchDefs.map { Pair(it.value, it.key) }.toTypedArray())
        val signatureVerificationTypes = patchDefs.keys
        if (signatureVerificationTypes.isEmpty()) {
            throw EmptyPatchDefListNotAllowedException("You haven't added any PatchDefs, or they are all invalid. If you *have* added PatchDefs, look above for parser errors")
        }
        val signatureVerificationTypesClassConstructor = FunSpec.constructorBuilder()
                .addParameters(signatureVerificationTypes.map { ParameterSpec.builder(it, Boolean::class).defaultValue("false").build() })

        val signatureVerificationTypesClassName = ClassName("com.github.kaeptmblaubaer1000.smalisignaturepatchgenerator.core.generated", "SignatureVerificationTypes")
        val signatureVerificationTypesClass = TypeSpec.classBuilder(signatureVerificationTypesClassName)
                .primaryConstructor(signatureVerificationTypesClassConstructor
                        .build())
                .addModifiers(KModifier.DATA)
                .addProperties(signatureVerificationTypes.map { PropertySpec.varBuilder(it, Boolean::class).initializer(it).build() })
                .build()

        val methodClassName = ClassName("org.jf.dexlib2.iface", "Method")
        val instructionClassName = ClassName("org.jf.dexlib2.iface.instruction", "Instruction")

        val identificationMethodRewriterClass = TypeSpec.classBuilder(ClassName("com.github.kaeptmblaubaer1000.smalisignaturepatchgenerator.core.generated", "IdentificationMethodRewriter"))
                .superclass(ClassName("org.jf.dexlib2.rewriter", "MethodRewriter"))
                .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter("rewriters", ClassName("org.jf.dexlib2.rewriter", "Rewriters"))
                        .addParameter("signatureVerificationTypes", signatureVerificationTypesClassName)
                        .build())
                .addSuperclassConstructorParameter("rewriters")
                .addProperty(PropertySpec
                        .builder("signatureVerificationTypes", signatureVerificationTypesClassName)
                        .initializer("signatureVerificationTypes")
                        .build())
                .addFunction(FunSpec
                        .builder("rewrite")
                        .addParameter("method", methodClassName)
                        .returns(methodClassName)
                        .addModifiers(KModifier.OVERRIDE)
                        .addCode("try {%>\n")
                        .addCode("when (method.definingClass) {%>\n")
                        .apply {
                            val map = mutableMapOf<String, MutableList<PatchDef>>()
                            for (patchDef in patchDefs.values) {
                                map.getOrPut(patchDef.modifiedClass, ::mutableListOf).add(patchDef)
                            }
                            for ((clazz, patchDefsForClass) in map) {
                                addCode("%1S -> {%>\n", clazz)
                                addCode("when (%1T.getMethodDescriptor(method, true)) {%>\n", ClassName("org.jf.dexlib2.util", "ReferenceUtil"))

                                for (patchDef in patchDefsForClass) {
                                    addCode("%1S -> {%>\n", patchDef.modifiedMethod)

                                    addCode("signatureVerificationTypes.%1N = true\n", reversedPatchDefs[patchDef]!!)

                                    addCode("%<}\n")
                                }

                                addCode("%<}\n")
                                addCode("%<}\n")
                            }
                        }
                        .addCode("else -> throw IgnoreThisMethodException()\n")
                        .addCode("%<}\n")
                        .addCode("%<} catch (e: IgnoreThisMethodException) {\n}\n")
                        .addStatement("return super.rewrite(method)")
                        .build())

                .addType(TypeSpec
                        .classBuilder(ClassName("com.github.kaeptmblaubaer1000.smalisignaturepatchgenerator.core.generated", "IdentificationMethodRewriter", "IgnoreThisMethodException"))
                        .superclass(Exception::class)
                        .addModifiers(KModifier.PRIVATE)
                        .build())
                .build()

        val coreInstructionRewriterClass = TypeSpec.classBuilder("CoreInstructionRewriter")
                .superclass(ClassName("org.jf.dexlib2.rewriter", "InstructionRewriter"))
                .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter("rewriters", ClassName("org.jf.dexlib2.rewriter", "Rewriters"))
                        .addParameter("signatureVerificationTypes", signatureVerificationTypesClassName)
                        .build())
                .addSuperclassConstructorParameter("rewriters")
                .addProperty(PropertySpec
                        .builder("signatureVerificationTypes", signatureVerificationTypesClassName)
                        .initializer("signatureVerificationTypes")
                        .build())
                .addFunction(FunSpec
                        .builder("rewrite")
                        .addParameter("instruction", instructionClassName)
                        .returns(instructionClassName)
                        .addModifiers(KModifier.OVERRIDE)
                        .addCode("if (instruction !is %T || instruction !is %T) return super.rewrite(instruction)\n", ClassName("org.jf.dexlib2.iface.instruction", "ReferenceInstruction"), ParameterizedTypeName.get(ClassName("com.github.kaeptmblaubaer1000.smalisignaturepatchgenerator.core", "Wrapped"), WildcardTypeName.subtypeOf(ANY)))
                        .addCode("when (instruction.methodName) {%>\n")
                        .apply {
                            for (patchDef in patchDefs.values) {
                                addCode("%1S -> {%>\n", "${patchDef.modifiedClass}->${patchDef.modifiedMethod}")

                                addCode("when (((instruction as %T).reference as %T)) {%>\n", ClassName("org.jf.dexlib2.iface.instruction", "ReferenceInstruction"), ClassName("org.jf.dexlib2.iface.reference", "MethodReference"))

                                addCode("%<}\n")
                                reversedPatchDefs[patchDef]!!

                                addCode("%<}\n")
                            }

                        }
                        .addCode("else -> return super.rewrite(instruction)\n")
                        .addCode("%<}\n")
                        .addStatement("return super.rewrite(instruction)")
                        .build())
                .build()


        val fileSpec = FileSpec.builder("com.github.kaeptmblaubaer1000.smalisignaturepatchgenerator.core.generated", "CompiledPatchDef")
                .addType(identificationMethodRewriterClass)
                .addType(signatureVerificationTypesClass)
                .addType(coreInstructionRewriterClass)
                .addProperty(PropertySpec
                        .builder("signatureVerificationTypesHumanNames", ParameterizedTypeName.get(Map::class, String::class, String::class))
                        .initializer(CodeBlock.builder()
                                .add("mapOf(\n")
                                .apply {
                                    val patchDefList = patchDefs.toList()
                                    for ((name, patchDef) in patchDefList.dropLast(1)) {
                                        add("%1S to %2S,\n", name, patchDef.humanName)
                                    }
                                    for ((name, patchDef) in patchDefList.takeLast(1)) {
                                        add("%1S to %2S\n", name, patchDef.humanName)
                                    }
                                }
                                .add("%<%<)\n%>%>")
                                .build())
                        .build())
                .addFunction(FunSpec
                        .builder("verifySelectedSignatureVerificationTypes")
                        .addParameter("available", signatureVerificationTypesClassName)
                        .addParameter("selected", signatureVerificationTypesClassName)
                        .returns(Boolean::class)
                        .addCode("return when {%>\n")
                        .apply {
                            for (signatureVerificationType in signatureVerificationTypes) {
                                addCode("!available.%1N && selected.%1N -> false\n", signatureVerificationType)
                            }
                        }
                        .addCode("else -> true\n")
                        .addCode("%<}\n")
                        .addAnnotation(AnnotationSpec
                                .builder(Contract::class)
                                .addMember("pure = true")
                                .build())
                        .build())
                .build()
        fileSpec.writeTo(output)
    }

    class EmptyPatchDefListNotAllowedException : Exception {
        constructor(message: String?, cause: Throwable?) : super(message, cause)
        constructor(message: String?) : super(message)
        constructor(cause: Throwable?) : super(cause)
        constructor() : super()
    }

    fun parseFiles(dir: File): Parser {
        val parser = Parser()
        for (file: File in dir.walk().filter { it.isFile }) {
            parser.parse(file.readText(Charsets.UTF_8))
        }
        return parser
    }
}
