package me.falsehonesty.asmhelper

import me.falsehonesty.asmhelper.printing.log
import net.minecraft.launchwrapper.IClassTransformer
import net.minecraft.launchwrapper.LaunchClassLoader
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

abstract class BaseClassTransformer : IClassTransformer {
    private var calledSetup = false

    private fun setup() {
        val classLoader = this.javaClass.classLoader as LaunchClassLoader

        classLoader.addTransformerExclusion("kotlin.")
        classLoader.addTransformerExclusion("me.falsehonesty.asmhelper.")
        classLoader.addTransformerExclusion("org.objenesis.")
        classLoader.addTransformerExclusion(this.javaClass.name)

        setup(classLoader)

        makeTransformers()
    }

    /**
     * Enables debug class loading. This means all transformed classes will be printed.
     */
    protected fun debugClassLoading() {
        System.setProperty("legacy.debugClassLoading", "true")
        System.setProperty("legacy.debugClassLoadingSave", "true")
    }

    protected open fun setup(classLoader: LaunchClassLoader) {}

    /**
     * This is where you would place all of your asm helper dsl magic
     *
     */
    abstract fun makeTransformers()

    override fun transform(name: String?, transformedName: String?, basicClass: ByteArray?): ByteArray? {
        if (basicClass == null) return null

        if (!calledSetup) {
            setup()
            calledSetup = true
        }

        AsmHelper.classReplacers[transformedName]?.let { classFile ->
            log("Completely replacing $transformedName with data from $classFile.")

            return loadClassResource(classFile)
        }

        val writers = AsmHelper.asmWriters
            .filter { it.className.replace('/', '.') == transformedName }
            .ifEmpty { return basicClass }

        log("Transforming class $transformedName")

        val classReader = ClassReader(basicClass)
        val classNode = ClassNode()
        classReader.accept(classNode, ClassReader.SKIP_FRAMES)

        writers.forEach {
            log("Applying AsmWriter $it to class $transformedName")

            it.transform(classNode)
        }

        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        try {
            classNode.accept(classWriter)
        } catch (e: Throwable) {
            log("Exception when transforming $transformedName : ${e.javaClass.simpleName}")
            e.printStackTrace()
        }


        return classWriter.toByteArray()
    }

    private fun loadClassResource(name: String): ByteArray {
        return this::class.java.classLoader.getResourceAsStream(name).readBytes()
    }
}
