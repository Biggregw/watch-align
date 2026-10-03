package com.watchalign.mobile;

/**
 * core.* reason codes the shared Java cores may emit. The generic core owns their meaning; its
 * versioned reason catalogue (measurement contract v2) will be checked against these constants.
 */
final class CoreReasons {
    static final String IMAGE_UNREADABLE="core.image_unreadable";
    static final String ANALYSIS_EXCEPTION="core.analysis_exception";
    static final String STAGE_NOT_APPLICABLE="core.stage_not_applicable";

    private CoreReasons(){}
}
