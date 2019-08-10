package me.falsehonesty.asmhelper.dsl.writers

import me.falsehonesty.asmhelper.AsmHelper
import me.falsehonesty.asmhelper.dsl.AsmWriter
import me.falsehonesty.asmhelper.dsl.At
import me.falsehonesty.asmhelper.dsl.code.CodeBlock
import me.falsehonesty.asmhelper.dsl.code.InjectCodeBuilder
import me.falsehonesty.asmhelper.dsl.instructions.InsnListBuilder
import me.falsehonesty.asmhelper.printing.prettyString
import me.falsehonesty.asmhelper.printing.verbose
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode

class InjectWriter(
    className: String,
    private val methodName: String,
    private val methodDesc: String,
    private val at: At,
    private val insnListBuilder: (InsnListBuilder.() -> Unit)?,
    private val codeBlockClassName: String?
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

        if (nodes.isEmpty()) {
            verbose("$this matched no nodes. For context: ")
            verbose(method.instructions.prettyString())
            return
        }

        verbose("Matched ${nodes.size} nodes.")
        nodes.forEachIndexed { index, abstractInsnNode ->
            verbose("$index.    ${abstractInsnNode.prettyString()}")
        }

        val instructions = if (insnListBuilder != null) {
            val builder = InsnListBuilder(method)
            insnListBuilder.let { builder.it() }

            builder.build()
        } else {
            val clazzPath = codeBlockClassName!!.replace('.', '/') + ".class"
            val clazzInputStream = this.javaClass.classLoader.getResourceAsStream(clazzPath)

            val clazzReader = ClassReader(clazzInputStream)
            val codeClassNode = ClassNode()
            clazzReader.accept(codeClassNode, ClassReader.SKIP_FRAMES)

            val codeBuilder = InjectCodeBuilder(codeClassNode, classNode, method)

            codeBuilder.transformToInstructions()
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
        var codeBlockClassName: String? = null

        @Throws(IllegalStateException::class)
        fun build(): AsmWriter {
            return InjectWriter(
                className, methodName, methodDesc,
                at, insnListBuilder, codeBlockClassName
            )
        }

        fun insnList(config: InsnListBuilder.() -> Unit) {
            this.insnListBuilder = config
        }

        fun codeBlock(code: CodeBlock.() -> Unit) {
            this.codeBlockClassName = code.javaClass.name + "$1"
        }
    }
}

fun asm(bytecode: InsnListBuilder.() -> Unit) {}