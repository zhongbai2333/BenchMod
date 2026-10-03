package com.zhongbai233.bench.api.graphics;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsMigrationFixturesTest {
    @Test void everySceneHasStableUniqueIdentityAndRgbaOracle() {
        var ids = new HashSet<String>();
        for (var scene : GraphicsMigrationScene.values()) {
            assertTrue(ids.add(scene.id()));
            byte[] bytes = GraphicsMigrationFixtures.expected(scene, 602263);
            assertEquals(scene.width() * scene.height() * 4, bytes.length);
            assertTrue(GraphicsMigrationOracle.compare(bytes, bytes, scene.tolerance()).passed());
            assertEquals(64, GraphicsMigrationOracle.sha256(bytes).length());
        }
        assertEquals(6, ids.size());
    }
    @Test void paddedOffsetBottomUpRowsAreRepackedWithoutChangingBuffer() {
        var source = ByteBuffer.allocate(32);
        source.position(3);
        source.put(new byte[]{7,8,9,10,11,12,99,99,1,2,3,4,5,6});
        source.position(3).limit(17);
        assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12}, GraphicsMigrationFixtures.packRows(source,3,2,2,8,true));
        assertEquals(3,source.position()); assertEquals(17,source.limit());
        assertArrayEquals(new byte[]{7,8,9,10,11,12,1,2,3,4,5,6}, GraphicsMigrationFixtures.packRows(source,3,2,2,8,false));
    }
    @Test void packingRejectsInvalidAndTruncatedRows() {
        assertThrows(IllegalArgumentException.class, () -> GraphicsMigrationFixtures.packRows(ByteBuffer.allocate(20),3,2,4,11,false));
        assertThrows(IllegalArgumentException.class, () -> GraphicsMigrationFixtures.packRows(ByteBuffer.allocate(23),3,2,4,12,false));
        assertThrows(IllegalArgumentException.class, () -> GraphicsMigrationFixtures.packRows(ByteBuffer.allocate(20),0,2,4,12,false));
        assertThrows(ArithmeticException.class, () -> GraphicsMigrationFixtures.packRows(ByteBuffer.allocate(20),Integer.MAX_VALUE,2,4,12,false));
    }
    @Test void oracleDetectsChannelSwizzleAlphaFlipAndStaleSeed() {
        byte[] a = GraphicsMigrationFixtures.rgba(17,9,602263), b = a.clone();
        b[0] = a[1]; b[1] = a[0];
        assertFalse(GraphicsMigrationOracle.compare(a,b,0).passed());
        b = a.clone(); b[3] ^= 127;
        assertEquals(1, GraphicsMigrationOracle.compare(a,b,0).badPixels());
        assertFalse(GraphicsMigrationOracle.compare(a, GraphicsMigrationFixtures.packRows(ByteBuffer.wrap(a),17,9,4,68,true),0).passed());
        assertFalse(Arrays.equals(a, GraphicsMigrationFixtures.rgba(17,9,602264)));
    }
    @Test void toleranceIsPerChannelAndAllPixelsMatter() {
        byte[] a={0,0,0,(byte)255}, b={2,0,0,(byte)255};
        assertTrue(GraphicsMigrationOracle.compare(a,b,2).passed());
        var d=GraphicsMigrationOracle.compare(a,b,1);
        assertEquals(1,d.badPixels()); assertEquals(0,d.firstBadPixel()); assertEquals(2,d.maxChannelError());
        assertEquals(.5,d.meanAbsoluteError());
        assertThrows(IllegalArgumentException.class, () -> GraphicsMigrationOracle.compare(a,new byte[3],2));
        assertThrows(IllegalArgumentException.class, () -> GraphicsMigrationOracle.compare(a,b,-1));
    }
}
