package me.falsehonesty.asmhelper

import me.falsehonesty.asmhelper.dsl.AsmWriter
import me.falsehonesty.asmhelper.remapping.DeobfRemapper
import me.falsehonesty.asmhelper.remapping.ForgeRemapper
import me.falsehonesty.asmhelper.remapping.NotchRemapper
import me.falsehonesty.asmhelper.remapping.Remapper
import net.minecraft.launchwrapper.Launch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.lang.Exception

object AsmHelper {
    val classReplacers = mutableMapOf<String, String>()
    val asmWriters = mutableListOf<AsmWriter>()

    val remapper: Remapper
    val logger: Logger = LogManager.getLogger("AsmHelper")
    
    internal var fieldMaps = mapOf<String, String>()
    internal var methodMaps = mapOf<String, String>()

    init {
        val fmlDeobf = try { Launch.blackboard["fml.deobfuscatedEnvironment"] as Boolean } catch (e: Exception) { null }

        remapper = if (fmlDeobf != null) {
            if (fmlDeobf) DeobfRemapper() else ForgeRemapper()
        } else {
            val deobf = System.getProperty("asmhelper.deobf", "false")!!.toBoolean()

            if (deobf) DeobfRemapper() else NotchRemapper()
        }

        println("Selected the $remapper remapper")
    }
}
