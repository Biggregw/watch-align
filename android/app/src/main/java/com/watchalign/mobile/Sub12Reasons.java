package com.watchalign.mobile;

/**
 * sub12.* reason codes of the 12-series Submariner family core. Java emits codes only through these
 * constants; the family's versioned reason catalogue (measurement contract v2) will be checked
 * against them. A code is never repurposed: a changed meaning is a new code.
 */
final class Sub12Reasons {
    // Region stage (photo).
    static final String DIAL_NOT_FOUND="sub12.dial_not_found";
    static final String DIAL_SEED_LOW_CONFIDENCE="sub12.dial_seed_low_confidence";
    static final String DIAL_EDGE_FIT_FAILED="sub12.dial_edge_fit_failed";
    // Geometry stage flags, also cited by the gates they withhold.
    static final String DIAL_EDGE_NOT_REPRODUCIBLE="sub12.dial_edge_not_reproducible";
    static final String DIAL_MANUAL_CIRCLE="sub12.dial_manual_circle";
    // 12 triangle: raw production.
    static final String TRIANGLE_NOT_FOUND="sub12.triangle_not_found";
    static final String MINUTE_TICK_NOT_FOUND="sub12.minute_tick_not_found";
    static final String LUME_OUTLINE_ONLY="sub12.lume_outline_only";
    static final String MINUTE_TRACK_NOT_FOUND="sub12.minute_track_not_found";
    // 12 triangle: reliability chain, in the analyser's order.
    static final String TRIANGLE_TOO_SMALL="sub12.triangle_too_small";
    static final String HAND_AT_TWELVE="sub12.hand_at_twelve";
    static final String TRIANGLE_RESIZE_OUTLINE_CHANGED="sub12.triangle_resize_outline_changed";
    static final String ROTATION_RESIZE_UNSTABLE="sub12.rotation_resize_unstable";
    static final String GAP_RESIZE_UNSTABLE="sub12.gap_resize_unstable";
    static final String CENTRING_RESIZE_UNSTABLE="sub12.centring_resize_unstable";
    // Round markers.
    static final String ROUND_MARKERS_INSUFFICIENT="sub12.round_markers_insufficient";
    static final String ROUND_MARKERS_NOT_REPEATABLE="sub12.round_markers_not_repeatable";
    // Batons (subject baton:3, baton:6 or baton:9).
    static final String BATON_NOT_FOUND="sub12.baton_not_found";
    static final String BATON_LOW_CONFIDENCE="sub12.baton_low_confidence";
    static final String BATON_HAND="sub12.baton_hand";
    static final String BATON_WRONG_PLACE="sub12.baton_wrong_place";
    static final String BATON_GEOMETRY_INCOMPLETE="sub12.baton_geometry_incomplete";
    static final String BATON_RESULT_MISSING="sub12.baton_result_missing";
    static final String BATON_RESIZE_NOT_REPEATABLE="sub12.baton_resize_not_repeatable";
    // Relations between landmarks.
    static final String RELATION_DEGENERATE="sub12.relation_degenerate";
    /** A message without a registered code (hand-built result or a coding defect); never valid in a contract. */
    static final String UNCODED="sub12.uncoded";

    private Sub12Reasons(){}

    static String batonSubject(String label){return "baton:"+label;}

    /** The registered code for a baton status set by the analyser's own gate; null for FOUND. */
    static String batonStatusCode(Sub124060QcAnalyzer.Status s){
        if(s==null)return UNCODED;
        switch(s){
            case FOUND:return null;
            case NOT_FOUND:return BATON_NOT_FOUND;
            case LOW_CONFIDENCE:return BATON_LOW_CONFIDENCE;
            case HAND:return BATON_HAND;
            case WRONG_PLACE:return BATON_WRONG_PLACE;
            default:return UNCODED;
        }
    }

    /** Codes naming a baton's own detection status (every status except FOUND). */
    static final String[] BATON_STATUS_CODES={BATON_NOT_FOUND,BATON_LOW_CONFIDENCE,BATON_HAND,BATON_WRONG_PLACE};
}
