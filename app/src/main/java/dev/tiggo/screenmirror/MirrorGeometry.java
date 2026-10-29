package dev.tiggo.screenmirror;

/** Bounds memory use and letterboxes without cropping private phone content. */
final class MirrorGeometry {
    static final int MAX_CAPTURE_EDGE = 960;

    static int[] captureSize(int width, int height) {
        return captureSize(width, height, MAX_CAPTURE_EDGE);
    }

    static int[] captureSize(int width, int height, int maxEdge) {
        requireSize(width, height);
        if (maxEdge < 1 || maxEdge > 1280) throw new IllegalArgumentException("Invalid capture limit");
        double scale = Math.min(1.0, (double) maxEdge / Math.max(width, height));
        return new int[] { Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale)) };
    }

    static float[] fit(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        return placement(sourceWidth, sourceHeight, targetWidth, targetHeight, false);
    }

    static float[] placement(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight, boolean fill) {
        requireSize(sourceWidth, sourceHeight);
        requireSize(targetWidth, targetHeight);
        float scale = fill ? Math.max((float) targetWidth / sourceWidth, (float) targetHeight / sourceHeight)
                : Math.min((float) targetWidth / sourceWidth, (float) targetHeight / sourceHeight);
        float width = sourceWidth * scale;
        float height = sourceHeight * scale;
        float left = (targetWidth - width) / 2;
        float top = (targetHeight - height) / 2;
        return new float[] { left, top, left + width, top + height };
    }

    static float[] mapPoint(float x, float y, float[] box, int phoneWidth, int phoneHeight) {
        requireSize(phoneWidth, phoneHeight);
        if (!Float.isFinite(x) || !Float.isFinite(y) || box == null
                || x < box[0] || y < box[1] || x >= box[2] || y >= box[3]
                || box[2] <= box[0] || box[3] <= box[1]) return null;
        return new float[] {Math.min(phoneWidth - 1f, (x - box[0]) * phoneWidth / (box[2] - box[0])),
                Math.min(phoneHeight - 1f, (y - box[1]) * phoneHeight / (box[3] - box[1]))};
    }

    private static void requireSize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid display size");
    }
}
