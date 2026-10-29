package dev.tiggo.screenmirror;

import android.app.Application;

public final class MirrorApplication extends Application {
    private MirrorBridge bridge;
    @Override public void onCreate() {
        super.onCreate();
        bridge = new MirrorBridge(this);
        RotationSession.restore(this);
    }
    MirrorBridge mirror() { return bridge; }
}
