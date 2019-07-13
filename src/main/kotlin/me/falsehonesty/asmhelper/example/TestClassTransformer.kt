package me.falsehonesty.asmhelper.example

import me.falsehonesty.asmhelper.BaseClassTransformer
import me.falsehonesty.asmhelper.dsl.*
import me.falsehonesty.asmhelper.dsl.instructions.*
import me.falsehonesty.asmhelper.dsl.writers.AccessType
import me.falsehonesty.asmhelper.dsl.writers.asm
import net.minecraft.util.ChatComponentText
import net.minecraft.util.IChatComponent

class TestClassTransformer : BaseClassTransformer() {
    override fun makeTransformers() {
        injectCountField()
        injectCountPrint()
//        injectDrawSplashScreen()

        world()
    }

    private fun injectCountPrint() = inject {
        className = "net.minecraft.client.gui.GuiNewChat"
        methodName = "printChatMessage"
        methodDesc = "(Lnet/minecraft/util/IChatComponent;)V"
        at = At(InjectionPoint.HEAD)

        codeBlock {
            val deleteChatLine = shadowMethod<Unit, Int>()
            val printChatMessageWithOptionalDeletion = shadowMethod<Unit, IChatComponent, Int>()
            val local1 = shadowLocal<IChatComponent>()

            code {
                deleteChatLine(1337)

                if (local1.unformattedText.contains("ee")) {
                    printChatMessageWithOptionalDeletion(local1, 1337)

                    // TODO: provide api to return from target method
                    asm {
                        methodReturn()
                    }
                }
            }
        }
    }

    private fun injectCountField() = applyField {
        className = "net.minecraft.client.gui.GuiNewChat"
        accessTypes = listOf(AccessType.PRIVATE)
        fieldName = "testMessagesSent"
        fieldDesc = "I"
        initialValue = 0
    }

    private fun injectSuper() = inject {
        className = "net.minecraft.entity.EntityLivingBase"
        methodName = "getLook"
        methodDesc = "(F)Lnet/minecraft/util/Vec3;"
        at = At(InjectionPoint.HEAD)

        insnList {
            aload(0)
            instanceof("net/minecraft/entity/EntityLivingBase")

            field(FieldAction.GET_STATIC, "xxx", "xxx", "xxx")

            ifClause(JumpCondition.NOT_EQUAL, JumpCondition.NOT_EQUAL) {
                aload(0)
                fload(1)
                invoke(InvokeType.SPECIAL, "net/minecraft/entity/Entity", "getLook", "(F)Lnet/minecraft/util/Vec3;")
                areturn()
            }
        }
    }

//    private fun injectDrawSplashScreen() = overwrite {
//        className = "net.minecraft.client.Minecraft"
//        methodName = "drawSplashScreen"
//        methodDesc = "(Lnet/minecraft/client/renderer/texture/TextureManager;)V"
//
//        insnList {
//            invokeKOBjectFunction(
//                "me/falsehonesty/asmhelper/example/TestHelper",
//                "drawSplash",
//                "()V"
//            )
//
//            methodReturn()
//        }
//    }
}

object TestObj {
    fun printWhenChatted(messages: Int) {
        println("$messages printed so far.")
    }

    fun doTHing(b: Boolean) {
        println("open? $b")
    }
}