package com.zhongbai233.bench.runtime.mixin;

import com.zhongbai233.bench.runtime.client.LegacyInputModifiers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve synthetic modifiers for 1.21.1 widgets that query Screen instead of callback args. */
@Mixin(Screen.class)
abstract class ScreenInputModifiersMixin {
    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private static void modbench$shift(CallbackInfoReturnable<Boolean> callback) {
        Boolean down = LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_SHIFT);
        if (down != null) callback.setReturnValue(down);
    }

    @Inject(method = "hasControlDown", at = @At("HEAD"), cancellable = true)
    private static void modbench$control(CallbackInfoReturnable<Boolean> callback) {
        int control = Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL;
        Boolean down = LegacyInputModifiers.keyDown(control);
        if (down != null) callback.setReturnValue(down);
    }

    @Inject(method = "hasAltDown", at = @At("HEAD"), cancellable = true)
    private static void modbench$alt(CallbackInfoReturnable<Boolean> callback) {
        Boolean down = LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_ALT);
        if (down != null) callback.setReturnValue(down);
    }
}
