package dev.tiggo.screenmirror;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionMetricsTest {
    @Test public void durationFreezesAtStopAndNewSessionResetsRate() {
        SessionMetrics stats = new SessionMetrics();
        stats.start(1000);
        assertEquals(15, stats.sample(2000, 15), .01);
        stats.stop(3500);
        assertEquals(2500, stats.durationMs(8000));
        assertEquals(0, stats.sample(9000, 30), .01);
        stats.start(10000);
        assertEquals(0, stats.durationMs(10000));
        assertEquals(8, stats.sample(11000, 8), .01);
    }
    @Test public void noFramesMeansZeroAndCounterResetIsNotNegative() {
        SessionMetrics stats = new SessionMetrics();
        stats.start(100);
        assertEquals(0, stats.sample(1100, 0), .01);
        assertEquals(20, stats.sample(2100, 20), .01);
        assertEquals(0, stats.sample(3100, 2), .01);
    }
    @Test public void singleAppCannotControlEvenWhenItsDimensionsMatchThePhone() {
        assertFalse(SessionMetrics.canForwardInput(true,true,true,false,true,false,true,10));
    }
    @Test public void lockedOrStaleCaptureCannotControl() {
        assertFalse(SessionMetrics.canForwardInput(true,true,true,true,true,true,true,10));
        assertFalse(SessionMetrics.canForwardInput(true,true,true,true,true,false,true,2500));
        assertFalse(SessionMetrics.canForwardInput(true,true,true,true,true,false,true,-1));
        assertTrue(SessionMetrics.canForwardInput(true,true,true,true,true,false,true,100));
    }
    @Test public void formatsLongSessionWithoutWrappingAtOneHour() {
        assertEquals("01:02:03", SessionMetrics.clock(3_723_000));
        assertEquals("00:00:00", SessionMetrics.clock(-10));
    }
}
