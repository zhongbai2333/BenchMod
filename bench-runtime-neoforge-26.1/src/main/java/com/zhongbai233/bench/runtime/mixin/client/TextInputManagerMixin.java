package com.zhongbai233.bench.runtime.mixin.client;

import com.mojang.blaze3d.platform.TextInputManager;
import com.zhongbai233.bench.runtime.client.BenchInputIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep text focus/character dispatch intact while suppressing native IME toggles in either direction. */
@Mixin(value = TextInputManager.class, remap = false)
abstract class TextInputManagerMixin {
    @Inject(method = "setIMEInputMode", at = @At("HEAD"), cancellable = true)
    private void modbench$preserveSystemInputMethod(boolean enabled, CallbackInfo callback) {
        if (BenchInputIsolation.active()) callback.cancel();
    }
}
