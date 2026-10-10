package com.watchalign.redditqc;

/** A successful Reddit metadata fetch proves the thread can be processed even if it has no direct photos. */
final class CollectorTestGate {
    static boolean canCollect(int metadataThreadsCompleted, boolean stopped) {
        return metadataThreadsCompleted > 0 && !stopped;
    }
    private CollectorTestGate() {}
}
