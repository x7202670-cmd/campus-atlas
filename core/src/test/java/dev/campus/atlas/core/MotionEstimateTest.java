package dev.campus.atlas.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class MotionEstimateTest {
    @Test public void movingNorthDerivesSpeedAndBearing() {
        MotionEstimate motion = new MotionEstimate();
        assertEquals(0f, motion.update(new Point(0, 0), 1_000_000_000L).speedMetersPerSecond, 0f);
        MotionEstimate.Result result = motion.update(new Point(0.0001, 0), 2_000_000_000L);
        assertTrue(result.speedMetersPerSecond > 10 && result.speedMetersPerSecond < 12);
        assertEquals(0f, result.bearingDegrees, 0.01f);
    }
    @Test public void repeatedPointDoesNotInventMotion() {
        MotionEstimate motion = new MotionEstimate();
        motion.update(new Point(30, 120), 1_000_000_000L);
        MotionEstimate.Result result = motion.update(new Point(30, 120), 2_000_000_000L);
        assertEquals(0f, result.speedMetersPerSecond, 0f);
        assertFalse(result.hasBearing);
    }
    @Test public void resetAndLargeJumpClearHistory() {
        MotionEstimate motion = new MotionEstimate();
        motion.update(new Point(30, 120), 1_000_000_000L);
        assertEquals(0f, motion.update(new Point(31, 120), 2_000_000_000L).speedMetersPerSecond, 0f);
        motion.reset();
        assertEquals(0f, motion.update(new Point(30, 120), 3_000_000_000L).speedMetersPerSecond, 0f);
    }
    @Test public void bearingNeverRoundsUpTo360() {
        MotionEstimate motion = new MotionEstimate();
        // Move north with an infinitesimally small westward deviation (bearing very close to 360°)
        motion.update(new Point(30.0, 120.0), 1_000_000_000L);
        MotionEstimate.Result result = motion.update(new Point(30.001, 119.99999999999), 2_000_000_000L);
        assertTrue(result.bearingDegrees >= 0f && result.bearingDegrees < 360f);
    }
}
