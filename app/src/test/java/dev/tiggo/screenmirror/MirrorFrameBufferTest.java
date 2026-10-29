package dev.tiggo.screenmirror;

import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class MirrorFrameBufferTest {
    @Test public void acceptsMissingLastRowPaddingAndDoesNotChangeSourcePosition() {
        ByteBuffer input = ByteBuffer.wrap(new byte[] {1,2,3,4,0,0,0,0,5,6,7,8});
        input.position(3);
        ByteBuffer output = new MirrorFrameBuffer().forBitmap(input, 8, 1, 2);
        assertEquals(16, output.remaining());
        assertEquals(3, input.position());
        assertEquals(5, output.get(8));
        assertEquals(0, output.get(15));
    }

    @Test public void completePlaneNeedsNoPadding() {
        ByteBuffer input = ByteBuffer.wrap(new byte[] {1,2,3,4});
        assertEquals(4, new MirrorFrameBuffer().forBitmap(input, 4, 1, 1).remaining());
    }

    @Test(expected = IllegalArgumentException.class) public void missingActualPixelsRejected() {
        new MirrorFrameBuffer().forBitmap(ByteBuffer.allocate(11), 8, 1, 2);
    }
}
