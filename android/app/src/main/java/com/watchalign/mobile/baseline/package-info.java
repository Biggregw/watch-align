/**
 * Compile-safe scaffolding for eventually comparing GMT marker measurements against an empirical
 * genuine reference baseline (see the {@code research/proportional-geometry-knowledge-base}
 * branch's {@code gmt-genuine-baseline-*} research material).
 *
 * <p><strong>Nothing in this package is wired into any production QC path.</strong> No class
 * here is referenced by {@code WatchAlignCoreV13} or any other live report generator, no numeric
 * threshold or tolerance is populated anywhere in this package, and no class makes an
 * authenticity, genuine/replica, or pass/fail determination. See
 * {@code docs/research/gmt-genuine-baseline-android-integration-design.md} for the full design
 * and the exact data this package is waiting on before an implementation can be written.</p>
 *
 * <p>Pipeline shape, each stage a separate type so a verdict can never be produced before all
 * upstream stages have run: {@link com.watchalign.mobile.qc.RawMeasurement} (existing) -&gt;
 * {@link com.watchalign.mobile.baseline.ReferenceComparison} -&gt;
 * {@link com.watchalign.mobile.baseline.Evidence} -&gt;
 * {@link com.watchalign.mobile.baseline.UserFacingFinding}.</p>
 */
package com.watchalign.mobile.baseline;
