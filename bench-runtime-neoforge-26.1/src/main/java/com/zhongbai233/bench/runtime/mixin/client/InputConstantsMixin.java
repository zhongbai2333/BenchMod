package com.zhongbai233.bench.runtime.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.zhongbai233.bench.runtime.client.BenchInputIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guard the common native boundary even if a caller bypasses MouseHandler. */
@Mixin(value = InputConstants.class, remap = false)
abstract class InputConstantsMixin {
    @Inject(method = "grabOrReleaseMouse", at = @At("HEAD"), cancellable = true)
    private static void modbench$preventNativeCursorWarp(Window window, int mode, double x, double y,
                                                        CallbackInfo callback) {
        if (BenchInputIsolation.active()) callback.cancel();
    }
}
