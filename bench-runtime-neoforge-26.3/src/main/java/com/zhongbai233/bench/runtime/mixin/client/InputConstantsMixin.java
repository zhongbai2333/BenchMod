package com.zhongbai233.bench.runtime.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.zhongbai233.bench.runtime.client.BenchInputIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guard the SDL cursor boundary even if a caller bypasses {@code MouseHandler}. */
@Mixin(value = InputConstants.class, remap = false)
abstract class InputConstantsMixin {
    @Inject(method = {"grabMouse", "releaseMouse"}, at = @At("HEAD"), cancellable = true)
    private static void modbench$preventNativeCursorWarp(Window window, double x, double y,
                                                         CallbackInfo callback) {
        if (BenchInputIsolation.active()) callback.cancel();
    }
}
