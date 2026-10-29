package dev.tiggo.screenmirror;

import java.nio.ByteBuffer;

/** Some ImageReaders omit only the unused trailing row padding, which is valid. */
final class MirrorFrameBuffer {
    private ByteBuffer padded;

    ByteBuffer forBitmap(ByteBuffer source, int rowStride, int width, int height) {
        if (width <= 0 || height <= 0 || rowStride < width * 4 || rowStride % 4 != 0)
            throw new IllegalArgumentException("Invalid RGBA layout");
        int fullBytes = Math.multiplyExact(rowStride, height);
        int required = fullBytes - (rowStride - width * 4);
        ByteBuffer data = source.duplicate();
        data.rewind();
        if (data.remaining() < required) throw new IllegalArgumentException("Incomplete RGBA plane");
        if (data.remaining() >= fullBytes) return data;
        if (padded == null || padded.capacity() != fullBytes) padded = ByteBuffer.allocateDirect(fullBytes);
        padded.clear();
        padded.put(data);
        while (padded.hasRemaining()) padded.put((byte) 0);
        padded.flip();
        return padded;
    }

    void clear() { padded = null; }
}
