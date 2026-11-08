package com.zhongbai233.bench.runtime.mixin.client;

import com.mojang.blaze3d.platform.TextInputManager;
import com.zhongbai233.bench.runtime.client.BenchInputIsolation;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDL_Rect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve logical text focus while benchmark runs avoid mutating the host SDL input method. */
@Mixin(value = TextInputManager.class, remap = false)
abstract class TextInputManagerMixin {
    @Redirect(method = "startTextInput(Ljava/lang/Object;)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/sdl/SDLKeyboard;SDL_StartTextInput(J)Z"))
    private boolean modbench$startTextInput(long window) {
        return BenchInputIsolation.active() || SDLKeyboard.SDL_StartTextInput(window);
    }

    @Redirect(method = "startTextInput(Ljava/lang/Object;)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/sdl/SDLKeyboard;SDL_ClearComposition(J)Z"))
    private boolean modbench$clearComposition(long window) {
        return BenchInputIsolation.active() || SDLKeyboard.SDL_ClearComposition(window);
    }

    @Redirect(method = "stopTextInput()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/sdl/SDLKeyboard;SDL_StopTextInput(J)Z"))
    private boolean modbench$stopTextInput(long window) {
        return BenchInputIsolation.active() || SDLKeyboard.SDL_StopTextInput(window);
    }

    @Redirect(method = "applyTextInputArea()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/sdl/SDLKeyboard;SDL_SetTextInputArea(JLorg/lwjgl/sdl/SDL_Rect$Buffer;I)Z"))
    private boolean modbench$setTextInputArea(long window, SDL_Rect.Buffer area, int cursor) {
        return BenchInputIsolation.active() || SDLKeyboard.SDL_SetTextInputArea(window, area, cursor);
    }
}
