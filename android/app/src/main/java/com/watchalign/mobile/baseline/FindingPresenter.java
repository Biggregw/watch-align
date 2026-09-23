package com.watchalign.mobile.baseline;

/**
 * Stage 3 -&gt; stage 4: turns {@link Evidence} into a {@link UserFacingFinding}.
 *
 * <p>This is the only stage allowed to produce user-facing wording, and it must never introduce
 * a pass/fail or authenticity-scored case -- see {@link UserFacingFinding}'s three sanctioned
 * subclasses. See {@link DefaultFindingPresenter} for the only implementation.</p>
 */
public interface FindingPresenter {
    UserFacingFinding present(Evidence evidence);
}
