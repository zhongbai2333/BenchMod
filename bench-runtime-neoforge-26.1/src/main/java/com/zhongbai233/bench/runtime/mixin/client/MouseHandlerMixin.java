package com.zhongbai233.bench.runtime.mixin.client;

import com.zhongbai233.bench.runtime.client.BenchInputIsolation;
import net.minecraft.client.MouseHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve vanilla GUI closing while benchmark input never captures or recenters the cursor. */
@Mixin(value = MouseHandler.class, remap = false)
abstract class MouseHandlerMixin {
    @Shadow private boolean mouseGrabbed;
    @Shadow private double xpos;
    @Shadow private double ypos;

    @Redirect(method = "grabMouse", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MouseHandler;xpos:D", opcode = Opcodes.PUTFIELD))
    private void modbench$keepCursorX(MouseHandler mouse, double center) {
        if (!BenchInputIsolation.active()) xpos = center;
    }

    @Redirect(method = "grabMouse", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MouseHandler;ypos:D", opcode = Opcodes.PUTFIELD))
    private void modbench$keepCursorY(MouseHandler mouse, double center) {
        if (!BenchInputIsolation.active()) ypos = center;
    }

    @Inject(method = "grabMouse", at = @At("RETURN"))
    private void modbench$leaveCursorFreeAfterGuiClose(CallbackInfo callback) {
        // Vanilla temporarily sets this flag before setScreen(null), preventing recursive grabs.
        if (BenchInputIsolation.active()) mouseGrabbed = false;
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void modbench$releaseWithoutNativeCursorWrite(CallbackInfo callback) {
        if (BenchInputIsolation.active()) {
            mouseGrabbed = false;
            callback.cancel();
        }
    }
}
