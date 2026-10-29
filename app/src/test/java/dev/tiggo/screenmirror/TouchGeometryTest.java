package dev.tiggo.screenmirror;

import org.junit.Test;
import static org.junit.Assert.*;

public class TouchGeometryTest {
    @Test public void letterboxTapMapsToPhoneCenter() {
        float[] box = MirrorGeometry.fit(1080, 2400, 1344, 640);
        assertArrayEquals(new float[]{540,1200}, MirrorGeometry.mapPoint(672,320,box,1080,2400), .01f);
    }
    @Test public void blackBarsNeverGeneratePhoneTap() {
        float[] box = MirrorGeometry.fit(1080,2400,1344,640);
        assertNull(MirrorGeometry.mapPoint(10,320,box,1080,2400));
        assertNull(MirrorGeometry.mapPoint(box[2],320,box,1080,2400));
        assertNull(MirrorGeometry.mapPoint(Float.NaN,320,box,1080,2400));
    }
    @Test public void fillCropsWithoutDistortingAndMapsVisibleCenter() {
        float[] box = MirrorGeometry.placement(2400,1080,1344,720,true);
        assertTrue(box[0] < 0);
        assertArrayEquals(new float[]{1200,540},MirrorGeometry.mapPoint(672,360,box,2400,1080),.01f);
    }
    @Test public void landscapeHighQualityIsBounded() {
        assertArrayEquals(new int[]{1280,576},MirrorGeometry.captureSize(2400,1080,1280));
        assertArrayEquals(new int[]{324,720},MirrorGeometry.captureSize(1080,2400,720));
    }
}
