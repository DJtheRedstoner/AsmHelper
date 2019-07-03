package me.falsehonesty.asmhelper.dsl.writers

import me.falsehonesty.asmhelper.AsmHelper
import me.falsehonesty.asmhelper.dsl.AsmWriter
import me.falsehonesty.asmhelper.dsl.At
import me.falsehonesty.asmhelper.dsl.code.InjectCodeBuilder
import me.falsehonesty.asmhelper.dsl.instructions.Descriptor
import me.falsehonesty.asmhelper.dsl.instructions.InsnListBuilder
import me.falsehonesty.asmhelper.java.NullReturner
import org.jetbrains.annotations.Contract
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import org.objenesis.ObjenesisHelper
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class InjectWriter(
    className: String,
    private val methodName: String,
    private val methodDesc: String,
    private val at: At,
    private val insnListBuilder: (InsnListBuilder.() -> Unit)?,
    private val codeBuilder: (() -> Unit)?
) : AsmWriter(className) {
    override fun transform(classNode: ClassNode) {
        classNode.methods
            .find {
                it.desc == methodDesc && AsmHelper.remapper.remapMethodName(classNode.name, methodName, methodDesc) == it.name
            }
            ?.let { injectInsnList(it, classNode) }
    }

    private fun injectInsnList(method: MethodNode, classNode: ClassNode) {
        val nodes = at.getTargetedNodes(method)

        val instructions = if (insnListBuilder != null) {
            val builder = InsnListBuilder(method)
            insnListBuilder.let { builder.it() }

            builder.build()
        } else {
            val clazz = codeBuilder!!.javaClass
            val clazzName = clazz.name
            val clazzPath = clazzName.replace('.', '/') + ".class"
            val clazzInputStream = clazz.classLoader.getResourceAsStream(clazzPath)

            val clazzReader = ClassReader(clazzInputStream)
            val codeClassNode = ClassNode()
            clazzReader.accept(codeClassNode, ClassReader.SKIP_FRAMES)

            val codeBuilder = InjectCodeBuilder(codeClassNode, classNode, method)

            codeBuilder.codeBlockToInstructions()
        }

        nodes.forEach { insertToNode(method, it, instructions) }
    }

    private fun insertToNode(method: MethodNode, node: AbstractInsnNode, insnList: InsnList) {
        var newNode = node

        if (at.shift < 0) {
            repeat(-at.shift) {
                newNode = node.previous
            }
        } else if (at.shift > 0) {
            repeat(at.shift) {
                newNode = node.next
            }
        }

        if (at.before) {
            method.instructions.insertBefore(newNode, insnList)
        } else {
            method.instructions.insert(newNode, insnList)
        }
    }

    override fun toString(): String {
        return "InjectWriter{className=$className, methodName=$methodName, methodDesc=$methodDesc, at=$at}"
    }

    class Builder {
        lateinit var className: String
        lateinit var methodName: String
        lateinit var methodDesc: String
        lateinit var at: At
        var insnListBuilder: (InsnListBuilder.() -> Unit)? = null
        var codeBuilder: (() -> Unit)? = null

        @Throws(IllegalStateException::class)
        fun build(): AsmWriter {
            return InjectWriter(
                className, methodName, methodDesc,
                at, insnListBuilder, codeBuilder
            )
        }

        fun insnList(config: InsnListBuilder.() -> Unit) {
            this.insnListBuilder = config
        }

        fun code(code: () -> Unit) {
            this.codeBuilder = code
        }

        inline fun <reified T> shadowField(): T = ObjenesisHelper.newInstance(T::class.java)

        inline fun <reified L> shadowListField(): List<L> = shadowField<ArrayList<L>>()

        inline fun <reified R> shadowMethod(): () -> R = { ObjenesisHelper.newInstance(R::class.java) }
    }
}

fun asm(bytecode: InsnListBuilder.() -> Unit) {}