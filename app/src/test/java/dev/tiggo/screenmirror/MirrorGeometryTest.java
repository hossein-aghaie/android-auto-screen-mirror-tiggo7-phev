package dev.tiggo.screenmirror;

import org.junit.Test;
import static org.junit.Assert.*;

public class MirrorGeometryTest {
    @Test public void tallPhoneIsBoundedWithoutDistortion() {
        assertArrayEquals(new int[] {432, 960}, MirrorGeometry.captureSize(1080, 2400));
    }

    @Test public void rotationSwapsBoundsAndSmallCapturesAreNotUpscaled() {
        assertArrayEquals(new int[] {960, 432}, MirrorGeometry.captureSize(2400, 1080));
        assertArrayEquals(new int[] {320, 240}, MirrorGeometry.captureSize(320, 240));
    }

    @Test public void portraitFitsWideHeadUnitWithoutCropping() {
        assertArrayEquals(new float[] {528, 0, 816, 640},
                MirrorGeometry.fit(432, 960, 1344, 640), .01f);
    }

    @Test public void landscapeFitsPortraitWithoutCropping() {
        assertArrayEquals(new float[] {0, 275, 1000, 725},
                MirrorGeometry.fit(960, 432, 1000, 1000), .01f);
    }

    @Test(expected = IllegalArgumentException.class) public void zeroCaptureRejected() {
        MirrorGeometry.captureSize(0, 2400);
    }

    @Test(expected = IllegalArgumentException.class) public void zeroSurfaceRejected() {
        MirrorGeometry.fit(432, 960, 1344, 0);
    }
}
