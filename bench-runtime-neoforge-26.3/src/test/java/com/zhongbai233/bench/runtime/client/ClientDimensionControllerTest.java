package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ClientDimensionControllerTest {
    @Test
    void mapsConfiguredDimensionsToVanillaKeys() {
        assertSame(Level.OVERWORLD, ClientDimensionController.targetDimension("overworld"));
        assertSame(Level.NETHER, ClientDimensionController.targetDimension("the_nether"));
        assertSame(Level.END, ClientDimensionController.targetDimension("the_end"));
    }
}
