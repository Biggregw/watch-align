# GMT genuine-baseline → Android QC integration design

Branch: `design/gmt-genuine-baseline-android-integration` (based on
`research/proportional-geometry-knowledge-base`, not merged into it or
into `main`).

Status: design + compile-safe scaffolding only. **No populated
tolerances, thresholds, or scores.** No production QC behaviour is
changed by this branch. The running `GMT genuine marker baseline`
workflow (`.github/workflows/gmt-genuine-baseline.yml`, producing
`docs/research/gmt-genuine-baseline-results/**`) was not touched,
restarted, or interfered with while this work was done.

## 1. Purpose and non-goals

Purpose: prepare Watch Align so that, once the empirical genuine
126710BLNR marker baseline (currently being computed by the research
branch's bounded run) exists, it has a clean, typed, already-tested path
into the Android QC report — without inventing what that baseline says
in advance.

Explicit non-goals (restating the task's constraints as design
constraints, not just a checklist):

- Do not alter `Gmt126710BlnrMaster.java` (master geometry) or any pose
  estimation class (`MinuteTrackDialFinder`, `MinuteTrackPoseValidator`,
  `PerspectiveGmtOverlay`, `DialProjectiveRefiner`).
- Do not change `GmtMarkerQcRepair`'s detector sanity limits (±4°/±8%R)
  or any other detector threshold.
- Do not invent Rolex manufacturing tolerances. Every numeric band in
  this design is a placeholder (`Optional.empty()` / documented as
  "populated later from the research branch's output").
- Do not create an authenticity score, a genuine/replica classifier, or
  an overall watch score. The output vocabulary (§6) deliberately has no
  slot for one.
- Do not use the replica control set (§4 of the research material) to
  define what genuine geometry should look like. It is a *sensitivity*
  test set only, evaluated against a genuine-derived baseline; it never
  contributes to that baseline.
- Do not wire anything into `WatchAlignCoreV13`'s live report path. The
  new code is additive, unreferenced by production classes, and proven
  only via its own unit tests.

## 2. Current-state audit

### 2.1 Where GMT QC actually runs today

`WatchAlignCoreV13.analyse(...)` (`WatchAlignCoreV13.java:34-64`) is the
live orchestrator:

- L38: `canonicalGmt = CanonicalGmtGeometryAnalyzer.supports(modelRef)`
  — true for any `126710`-prefixed model. Only `.supports()` is called;
  `CanonicalGmtGeometryAnalyzer.analyse(...)` itself is **never invoked
  from this class**.
- L41-42: pose is built (`MinuteTrackRescueOverlay.build`) and
  `geometryTrusted = perspective.automaticAccepted` is the single gate
  for whether any marker QC is shown at all.
- L45-47: when trusted, `GmtMarkerQcRepair.repair(...)` is the one
  detector that actually produces the 12/6/9/round-marker lines shown to
  the user on a GMT model. When not trusted, marker QC is suppressed
  entirely (no partial/degraded output).
- L52-55 (the **non**-GMT branch only): `ReferenceDistributionAnalyzer`
  (a second, independent median/MAD-baseline consumer) feeds
  `QcSummaryFormatter.prependSummary(...)`. GMT models never reach this
  branch, so GMT reports have no top-level ranked summary pass at all
  today — they are `perspectiveReport + qcReport` concatenated directly
  (L48-51).

### 2.2 A median/MAD baseline layer already exists, but isn't wired to GMT

`GenuineBaselineStats.java` (41 lines) is a small, already-unit-tested
(`GenuineBaselineStatsTest.java`) robust-statistics primitive:

```java
static final class Summary {
    final int n; final double median; final double mad; final double sigma;
    double tolerance(double floor){ return Math.max(floor, 3.0*sigma); }
    double delta(double value){ return value-median; }
    double robustZ(double value,double floorSigma){ return delta(value)/Math.max(floorSigma,sigma); }
}
static Summary summarize(double[] values,int count){ ... } // median, MAD, sigma=1.4826*MAD
static int severity(double value,Summary baseline,double floorTolerance){ ... } // 0/1/2
```

Two analyzers already consume it:

- `CanonicalGmtGeometryAnalyzer.analyse(Bitmap watch, List<Bitmap> references, String modelRef)`
  (`CanonicalGmtGeometryAnalyzer.java:46`) — computes `Summary` objects
  **on the fly, per analysis call, from whatever `references` bitmaps
  the caller passes in** (`summarize(aa,an)` at L77,
  `summarize(rr,rn)` at L91) — i.e. its "genuine baseline" is not a
  precomputed multi-watch empirical population, it is whatever handful
  of reference images (often just one) the current screen happens to
  supply. It is unreferenced by `WatchAlignCoreV13`.
- `ReferenceDistributionAnalyzer` — same `GenuineBaselineStats`
  primitive, wired only into the non-GMT branch (§2.1).

**Implication for this design**: the integration work is mostly
*finishing* an already-started pattern, not inventing one. But the
empirical, multi-watch, `n`/p10/p90/repeatability-aware baseline this
task asks for is a different (richer, precomputed, offline-built) object
than what `CanonicalGmtGeometryAnalyzer` builds ad hoc today — §5's data
model is deliberately a separate, loadable artifact rather than a
per-call `Summary`, and does not reuse `GenuineBaselineStats.Summary` as
its storage type (it needs p10/p90 and a validation-status field that
type doesn't have). Where useful, the new code depends on
`GenuineBaselineStats` rather than duplicating its median/MAD math.

### 2.3 Master geometry: a single-photo template

`Gmt126710BlnrMaster.java` (79 lines, package-private) hardcodes one
ideal marker geometry per hour, explicitly derived from **one** official
catalogue photo (comment block, L20-30: round-marker radii measured
across the 8 round markers, one excluded as an outlier, "the mean of the
remaining seven is +2.865%R" folded into `ROUND_CENTER_R = 0.751`).
`angleForHour(int hour)` (L33ish) is an exact 30°/hour grid with no
tolerance. This is a direct Java port of `tools/watch_align_py/master.py`
(same constant names/values). A declarative JSON mirror exists at
`android/app/src/main/assets/templates/126710BLNR-master-v1.json` but is
not loaded by any runtime code — documentation/tooling-parity only.

`GmtMarkerQcRepairTest.v5MarkerCentresMatchTheOfficialCatalogueCalibration`
pins these exact constants as a regression guard. This is exactly the
gap the empirical baseline is meant to eventually address — but that is
future work requiring the completed baseline (§8), not this branch.

### 2.4 Pose: two different tilt models coexist

- `MinuteTrackDialFinder` fits an **affine** ellipse (`RotatedRect`) +
  roll from minute-track ticks. `GmtMarkerQcRepair.measure()` builds its
  marker positions from exactly this ellipse+roll (`GmtMarkerQcRepair.java:216-224`),
  undistorted via `PerspectiveGmtOverlay.undoEllipseDistortion` — a pure
  per-axis division by the ellipse's own radii
  (`PerspectiveGmtOverlay.java:201-209`), i.e. an affine (elliptical)
  tilt correction only.
- `PerspectiveGmtOverlay` / `DialProjectiveRefiner` additionally fit a
  full **projective homography** with explicit non-affine terms (h31,
  h32 logged at `PerspectiveGmtOverlay.java:172-173`), and it is
  *this* pose's acceptance (`automaticAccepted`) that gates
  `geometryTrusted` in `WatchAlignCoreV13.java:42` — i.e. whether marker
  QC is shown at all.
- Marker QC itself never consumes the projectively-refined homography.
  See §3.2.

### 2.5 No typed QC-verdict vocabulary exists yet

The only real enum anywhere in the QC surface is
`qc.QcModuleResult.Confidence { LOW, MEDIUM, HIGH }`
(`qc/QcModuleResult.java:14-18`). Everywhere else "verdicts" are bare
`int` severities (0/1/2 — `QcExtendedMath`, `CanonicalGmtGeometryAnalyzer`)
or ranked free-text strings (`QcExtendedAnalyzer.Finding{double priority; String text;}`,
`QcSummaryFormatter`'s substring-based `"major"`/`"minor"` classification
of report text, `QcSummaryFormatter.java:78-82`). None of this is a
sealed/typed finding.

The `qc` package (`QcModule<I>`, `QcModuleResult`, `RawMeasurement`) is a
**clean, already-unit-tested, currently-unreferenced-outside-its-own-
package contract** explicitly documented as deliberately containing "no
pass/fail or genuine/replica interpretation" (`RawMeasurement.java:8`)
and "no model-specific pass/fail logic — classification belongs in the
assessment layer" (`QcModuleResult.java:10-11`). Per
`android/validation/gmt12_control_study/README.md`, this contract was
ported from an orphaned branch as reusable scaffolding, with its
concrete modules deliberately left unported. **This is the intended, and
best available, landing spot for new code** — it is where §6's pipeline
stages are added, as a sibling package (`com.watchalign.mobile.baseline`)
that *depends on* `qc.RawMeasurement` as its stage-1 input type rather
than duplicating it.

## 3. Weaknesses found (deliverable C)

These are read-only findings; nothing below was changed as part of this
branch, per the task's constraints.

### 3.1 Silent-ish fallback to master/expected geometry on detection failure

`GmtMarkerQcRepair` itself gets this right: when `measureProjectedMarker`
can't isolate a plausible component, or when the angular/radial sanity
bounds are exceeded, it returns `measured=false` and the report says
*"not confidently isolated inside projected master ROI; no positional/
orientation value reported"* (`GmtMarkerQcRepair.java:169-173`). No
substitution happens.

`QcExtendedAnalyzer`'s date-axis check does not follow this pattern:

```java
// QcExtendedAnalyzer.java:254-255
TrackAnchor anchor=detectMinuteTrackAnchor(bgr,cx,cy,dr,expectedAngle);
double localAngle=anchor!=null?anchor.angleDeg:expectedAngle;   // falls back to MASTER expectation
```

The caveat is disclosed as a text suffix (`" [local marker not isolated;
fitted dial axis used]"`, L142), but the *numeric* value computed from
`localAngle` is still fed to `dateAxisSeverity` and ranked as a finding
exactly as if it were a real measurement (L145-146). The detector's own
"not detected" signal (`detectMinuteTrackAnchor` returns `null` below a
contrast threshold, L272-276) is clean; it's the caller that substitutes
the master value rather than propagating "unknown" through to the
finding layer.

### 3.2 Perspective mistaken for a marker defect

Marker QC (`GmtMarkerQcRepair.measure()`) computes every angular/radial
residual from the **affine** ellipse+roll pose (§2.4), never from the
projectively-refined homography that the rest of the pipeline trusts
enough to gate `geometryTrusted` on. Genuine central-perspective
foreshortening beyond what an ellipse-squash models — which is exactly
what `DialProjectiveRefiner` exists to correct — is therefore not
corrected in the numbers that become the reported marker deviation, and
would appear as apparent marker displacement. The only real guard
present is the ±4°/±8%R detector sanity check
(`GmtMarkerQcRepair.java:248-250`, explicitly commented as "detector
sanity limits, not QC tolerances") — anything *inside* that range is
reported with no separate accounting for residual pose error vs. a real
geometric deviation.

`QcExtendedMath.perspectiveAllowsFineQc(tilt)` (tilt<14°) exists and
gates the legacy 12/6/9/date/cyclops path in `QcExtendedAnalyzer` with an
`"(advisory: ...)"` suffix, but neither `GmtMarkerQcRepair` nor
`CanonicalGmtGeometryAnalyzer` use any tilt-based advisory downgrade on
their own numbers.

### 3.3 Correlated measurements counted as independent evidence

`QcExtendedAnalyzer.analyse`'s 12/6/9 loop
(`QcExtendedAnalyzer.java:89-112`) pushes a **position** finding and a
**body-rotation** finding as two separate `Finding` objects into one
ranked list, both derived from the same detected component of the same
ROI:

```java
// position (L97-98) and rotation (L106-107) — same marker, same ROI, two ranked findings
findings.add(new Finding(QcExtendedMath.findingPriority(sev,angular), ...));
findings.add(new Finding(QcExtendedMath.findingPriority(os,orient)+25.0, ...));
```

The same pattern recurs for the date complication: `axisSeverity`
(aperture vs. local minute-track anchor) and `numeralSeverity` (numeral
centring within the aperture) both derive from the same detected
aperture box (`d.apertureBox`), plus a third, `cyclops`, correlated to
the same aperture position — three separate findings from one underlying
displacement (L145-148, L192-193).

`CanonicalGmtGeometryAnalyzer` computes angular and radial severity for
one marker against two separate baseline distributions and combines with
`Math.max` (`CanonicalGmtGeometryAnalyzer.java:100`) — better than
summing two correlated numbers, but they are still reported as two
separate lines a future evidence-aggregator could double-count. Its
`mirrorPair(hour)` pooling (L149-161) also pools a *single reference
photo's* two mirror-marker readings into one baseline sample — those two
readings share that photo's own dial-detection, lighting and rotation
estimate, so they are not fully independent draws; `radialVerdict=rs.n>=3`
(L94) can therefore overstate how many independent observations actually
back a given `n`.

### 3.4 Reported precision the detector can't support

- `GmtMarkerQcRepair.detailLine`: `"angular offset %+.2f°, radial %+.2f%% R"`
  — two-decimal precision on measurements whose own acceptance gate
  tolerates up to `TOP_PHASE_LIMIT_DEG=3.10°` roll error
  (`MinuteTrackDialFinder.java:27`) and whose sanity bounds are ±4°/±8%R.
- `CanonicalGmtGeometryAnalyzer.java:112-114`: the same two-decimal
  degree/percent pattern, on top of a master constant itself derived
  from one calibration photo.
- `QcExtendedAnalyzer.java:171`: one-decimal-percent date-magnification
  precision, even though `QcExtendedMath.magnificationMatchSeverity`
  is hardcoded to always return severity 0 with a comment explaining the
  signal isn't reliable enough to grade yet — display precision
  contradicts the code's own admitted confidence.
- `PerspectiveGmtOverlay.java:170`: two-decimal-pixel pose-residual
  precision from a search whose own grid steps by 0.005 scale / 0.10°
  roll in its finest pass (`MinuteTrackDialFinder.java:296-297`) —
  coarser than the precision printed.

## 4. Metric mapping: research metrics ↔ Android

Research metric names (per `gmt-genuine-baseline-validation-plan.md`
§3 and `build_gmt_genuine_baseline.py`'s `marker_features()`, all keyed
`h{hour:02d}.<name>`) against what Android currently has:

| Research metric | Android today | Gap / note |
|---|---|---|
| `centre_r` (absolute canonical radial position of marker centre, hour-axis frame) | **Not stored.** `GmtMarkerQcRepair.measure()` computes an equivalent intermediate, `normalizedRadius = hypot(corrected.x, corrected.y)` (`GmtMarkerQcRepair.java:236-238`), where `corrected` is the pose-undistorted, dial-centre-relative pixel position — but this is immediately collapsed into `radialOffsetPctR(normalizedRadius, hour)`, a **master-relative percentage**, and the absolute value is discarded. | **This is the concrete integration point for item 1**: capturing `corrected` (or `normalizedRadius`) before it collapses into a master-relative delta is where absolute empirical `centre_r`/`centre_t` would enter. |
| `centre_t` (tangential offset in the local hour-axis frame) | **Not stored as a linear offset.** Android reports `angular` (`GmtMarkerQcRepair.java:239-241`), a full clock-angle residual in **degrees** against the master's expected angle — conceptually related but a different representation (angular vs. linear-canonical) computed a different way (compared directly to master, not decomposed from an absolute position first). | Would need the same `corrected` point, rotated into the hour-axis frame and expressed as a linear (not angular) canonical offset, to match the research definition exactly. |
| `outer_r`, `inner_r` | **No general equivalent for round markers.** `Candidate.outwardExtentLocal` (`GmtMarkerQcRepair.java:82`) exists but is only populated/used for hour 12 (`triangleOutwardDeltaPctR`, L253-254) — round markers (1,2,4,5,7,8,10,11) have no outer/inner radial extent extraction in the current Android detector at all. | Real gap. Python's `marker_consensus.py` extracts these for round markers; Android's `GmtMarkerQcRepair.measureProjectedMarker` would need an equivalent extension, out of scope for this branch. |
| `radial_span` (= `outer_r - inner_r`) | Not derivable until `outer_r`/`inner_r` exist. | Same gap. |
| `outer_t`, `inner_t` | Not stored (same gap as `outer_r`/`inner_r`). | Same gap. |
| `area_norm` | `Candidate.areaNorm` (`GmtMarkerQcRepair.java:82,308`) — direct match, already computed, just not surfaced past `Candidate` into `MarkerDiagnostic`/the report. | Straightforward to surface once a measurement-output stage exists. |
| `angular_residual_deg` | `MarkerDiagnostic.angularDeg` — analogous concept (both are angular placement residuals), but research's version comes from `marker_consensus_analysis.angular_analysis`'s peer-corridor-based residual, a different algorithm than Android's direct expected-vs-observed clock-angle difference. Treat as *analogous*, not verified-identical, until cross-checked numerically. | No code gap; a semantics note for whoever wires the comparison. |
| `axis_residual_deg` | `MarkerDiagnostic.bodyRotationDeg` / `Candidate.rotationDeg` — analogous (both are shape/orientation residuals from a PCA-style fit of the blob), same caveat as above (research's is normalised against a peer/expected axis via `principal_axis_residual_deg`; Android's is the raw blob PCA angle). | Same: analogous, not verified-identical. |
| `h12.stage3_*` (the Stage 3 hybrid-normalised 12-triangle features: `apex_r_simple`, `centre_r_projective`, `base_r_projective`, `axis_incidence_canonical`, `centroid_tangential_offset_canonical`) | **No Android equivalent at all.** These exist only in the frozen Python Stage 3 engine (`experiment/gmt-proportional-geometry-v1:tools/watch_align_py/gmt_proportional_features.py`), which is what `gmt-genuine-baseline.yml` pulls in for the research-side baseline build. Android's 12-triangle handling (`GmtMarkerQcRepair`'s hour-12 case, `triangleOutwardDeltaPctR`) is a materially simpler, single-metric model. | Largest single feature-family gap between the research engine and Android. Not attempted in this branch — flagged for a future, separate design pass once the Stage 3 approach is itself validated against a broader population (see the Stage 3 write-up's own caveats). |
| `segmentation_confidence` | No direct numeric confidence field on `MarkerDiagnostic`; closest existing concept is `qc.QcModuleResult.Confidence` (LOW/MEDIUM/HIGH), unused in production, or the boolean `measured` flag. | A future `RawMeasurement`-based module could carry this explicitly; see §6. |

## 5. Data model: empirical genuine reference profile

New package: `com.watchalign.mobile.baseline` (sibling to `qc` and
`profile`). All classes below are added by this branch, are immutable,
**compile-safe, and hold no populated values** — every constructor
requires explicit arguments (no committed 126710BLNR numbers anywhere),
and the only instances built by shipped code are empty/placeholder ones
built by tests.

- **`MetricKey`** — identity of one metric: `String markerId` (e.g.
  `"h12"`, `"global"`) + `String metricName` (e.g. `"centre_r"`),
  matching the research branch's `h{hour}.{metric}` convention so a
  future loader can parse `gmt-genuine-baseline-results/baseline_watch_level.csv`
  (columns: `marker, feature, n, median, mad, p10, p90, min, max`)
  directly into this key without a translation table.
- **`ValidationStatus`** — enum, exactly the four categories from
  `gmt-genuine-baseline-validation-plan.md` §7: `SUPPORTED`,
  `PROMISING`, `DIAGNOSTIC_ONLY`, `REJECT`. No metric ships with a
  status baked in; every `MetricBaseline` requires one explicitly.
- **`MetricBaseline`** — per-metric empirical summary: `n`
  (independent-watch count), `median`, `mad`, `p10`, `p90`,
  `repeatabilityError` (median within-watch absolute deviation, nullable
  — not every metric will have repeated-photo evidence yet),
  `missingRate` (fraction of attempted images where this metric could
  not be measured), `ValidationStatus`. All numeric fields are
  `double`/`Double` with finite-value validation in the constructor,
  mirroring `RawMeasurement`'s existing validation style.
- **`GenuineReferenceProfile`** — the loadable artifact: `profileId`,
  `sourceDescription` (free text — provenance/branch/commit, never a
  claim of manufacturing authority), `Map<MetricKey, MetricBaseline>`.
  Ships with a documented `EMPTY` constant and no loader wired to any
  asset yet (§8 lists exactly what has to exist first).

## 6. Interface design: measurement → comparison → evidence → finding

Four stages, four types, one direction. Each stage's output type is the
next stage's only input — nothing downstream can reach back into an
earlier stage's raw inputs, and nothing upstream needs to know how a
later stage will classify anything. This is deliberately more layers
than `GmtMarkerQcRepair`'s current design (which goes straight from
pixels to a master-relative percentage string in one pass) so that a
verdict-shaped decision is never made at measurement time.

1. **Measurement** — `qc.RawMeasurement` (existing, reused as-is: `id,
   value, unit`, no interpretation). Stage-1 producers are existing or
   future detector code (e.g. a future `GmtMarkerQcRepair` extension
   surfacing `Candidate.areaNorm`/absolute `centre_r` per §4); this
   branch adds no new producer.
2. **Reference comparison** — new `baseline.ReferenceComparison`:
   `MetricKey, double observedValue, MetricBaseline baseline, Double
   signedDeviation (= observed - median), Double standardizedDeviation`
   (nullable: only populated when `baseline.repeatabilityError` is
   present and non-zero, i.e. never divide by an absent noise floor).
   Pure arithmetic; contains no wording, no severity, no colour.
3. **Evidence** — new `baseline.Evidence`: wraps one
   `ReferenceComparison` plus the metric's own `ValidationStatus` and a
   `DetectorConfidence` (reuses `qc.QcModuleResult.Confidence`) into a
   single `EvidenceStrength` enum: `NONE`, `WEAK`, `MODERATE`, `STRONG`
   — deliberately not a probability or score, just an ordered
   qualitative bucket, and deliberately independent of the finding
   wording chosen in stage 4 (a `STRONG` evidence value does not by
   itself pick which of the three finding categories applies — that
   still depends on `ValidationStatus`, matching §7).
4. **User-facing finding** — new `baseline.UserFacingFinding`, an
   abstract class with exactly the cases the task requires, no
   pass/fail case exists to fall into:
   - `MeasurableDeviation` (metric is `SUPPORTED`, evidence is at least
     `MODERATE`) — carries the `Evidence` for display, no adjective
     stronger than "deviation," no authenticity language.
   - `Inconclusive` (metric is `PROMISING` or `DIAGNOSTIC_ONLY`, or
     evidence is `WEAK`/`NONE` despite a `SUPPORTED` metric) — carries a
     free-text reason.
   - `DetectorConfidenceInsufficient` (measurement stage itself reported
     low/no confidence, or the metric could not be measured at all) —
     carries the same reason vocabulary as `GmtMarkerQcRepair`'s
     existing "not confidently isolated" message, so it can eventually
     reuse that exact string rather than inventing new wording.

Interfaces enforcing the boundaries (so a future implementation cannot
accidentally skip a stage):

```java
public interface ReferenceComparator {
    ReferenceComparison compare(RawMeasurement measurement, MetricBaseline baseline);
}
public interface EvidenceClassifier {
    Evidence classify(ReferenceComparison comparison, ValidationStatus status, QcModuleResult.Confidence detectorConfidence);
}
public interface FindingPresenter {
    UserFacingFinding present(Evidence evidence);
}
```

No class implements these yet. They exist so the eventual
implementation is forced through all four stages by the type system,
and so each stage can be unit-tested (per the `GenuineBaselineStatsTest`/
`CanonicalGmtGeometryAnalyzerTest` pattern already established in this
codebase, §2.5) independently of the others and independently of
OpenCV/Bitmap.

## 7. Exact Android integration points (deliverable A, item 1)

In priority order, for when the genuine baseline exists and this design
is implemented:

1. **`GmtMarkerQcRepair.measure()`, right after `corrected` is computed**
   (`GmtMarkerQcRepair.java:236-238`, before it collapses into
   `radial`/`angular` master-relative deltas) — the one place the
   absolute, pose-corrected marker position already exists in memory
   and is currently thrown away. This is where a `RawMeasurement` for
   absolute `centre_r`/`centre_t` would be captured.
2. **A new stage-1 producer method**, not a change to
   `MarkerDiagnostic`, so `GmtMarkerQcRepair`'s existing report-string
   contract (and its regression test,
   `GmtMarkerQcRepairTest.rewriteReport`) is untouched. `MarkerDiagnostic`
   keeps meaning what it means today; the new empirical-comparison
   surface is additive.
3. **`WatchAlignCoreV13.java:45-47`** (the `canonicalGmt` branch) is
   where a future `qcReport` could be extended with a new "genuine
   baseline comparison" section, analogous to how `perspectiveReport`
   and `qcReport` are already concatenated — again additive, not a
   replacement of `GmtMarkerQcRepair.repair(...)`'s existing output.
4. **`GenuineReferenceProfile` loading** belongs beside
   `WatchProfileLoader.java` (`profile/` package) conceptually, but
   actual asset loading is out of scope until §8's data exists —
   building a loader for a file that doesn't exist yet would be
   guessing at its schema.
5. **`CanonicalGmtGeometryAnalyzer`** is the one existing class already
   shaped like a consumer of a `GenuineBaselineStats.Summary`; it is
   worth revisiting once the new profile format is populated, to decide
   whether it should be refactored onto `GenuineReferenceProfile`/
   `MetricBaseline` or left as the simpler ad hoc reference-image
   comparator it is today (§2.2's distinction matters here — they solve
   different problems).

## 8. Build/test (deliverable D)

New files, all under `android/app/src/main/java/com/watchalign/mobile/baseline/`
and `android/app/src/test/java/com/watchalign/mobile/baseline/`:

- `MetricKey.java`, `ValidationStatus.java`, `MetricBaseline.java`,
  `GenuineReferenceProfile.java`, `ReferenceComparison.java`,
  `Evidence.java`, `EvidenceStrength.java`, `UserFacingFinding.java`
  (+ its three subclasses), `ReferenceComparator.java`,
  `EvidenceClassifier.java`, `FindingPresenter.java`.
- Unit tests for each concrete class's construction/validation/
  immutability contract, following `QcModuleResultTest`/
  `RawMeasurementTest`'s existing pattern.

**Environment limitation encountered**: `./gradlew :app:testDebugUnitTest
:app:assembleDebug` (the standard commands per `AGENTS.md`) could not
complete in this sandbox. Two independent network dependencies are
blocked by this environment's egress policy:

1. The committed `gradle/gradle-daemon-jvm.properties`
   (`toolchainVendor=adoptium`, `toolchainVersion=17`) requires Gradle to
   auto-provision an Eclipse Temurin JDK 17 for the daemon itself. This
   sandbox has no network route to the Adoptium download host, and the
   locally-installed JDK 17 (Ubuntu's own `openjdk-17-jdk` package,
   `IMPLEMENTOR="Ubuntu"`) does not satisfy the strict vendor match, so
   Gradle refuses to start at all (`ToolchainDownloadException` /
   `ToolchainProvisioningException`).
2. Once that is bypassed locally, dependency resolution for the Android
   Gradle Plugin itself fails: `dl.google.com` is explicitly denied by
   this sandbox's proxy (`connect_rejected`, gateway 403), and the local
   Gradle module cache is empty (no prior run has ever populated it in
   this container), so `com.android.application:8.7.3` cannot be
   fetched from any configured repository.

Neither is caused by anything in this branch -- `gradle-daemon-jvm.
properties`, `build.gradle`, and every dependency version are untouched.
**This file was never modified in git**: to check whether the JDK issue
alone was the only blocker, it was copied aside, temporarily removed
from the working tree, tested against locally, and restored byte-for-
byte before anything was staged (`diff` against the backup showed no
difference). `git status` confirms it shows no diff in this branch.

**What was actually validated**, since the Android Gradle Plugin could
not be fetched: `com.watchalign.mobile.qc` (the existing, pre-this-
branch package) and the new `com.watchalign.mobile.baseline` package
have **zero Android/OpenCV imports** -- both are plain Java, `java.util.*`
only. This made a real, independent compile+test pass possible using
the JDK 17 already on disk and the JUnit 4.13.2 jar bundled inside the
Gradle 8.14.5 distribution itself (`~/.gradle/wrapper/dists/.../lib/
junit-4.13.2.jar`), entirely outside Gradle/AGP:

```
javac -d <out> app/src/main/java/com/watchalign/mobile/qc/*.java \
                app/src/main/java/com/watchalign/mobile/baseline/*.java
  -> compiles cleanly, no warnings, no errors.

javac -d <out> -cp <out>:junit:hamcrest \
                app/src/test/java/com/watchalign/mobile/baseline/*.java
  -> compiles cleanly.

java -cp <out>:junit:hamcrest org.junit.runner.JUnitCore \
  com.watchalign.mobile.baseline.MetricKeyTest \
  com.watchalign.mobile.baseline.MetricBaselineTest \
  com.watchalign.mobile.baseline.GenuineReferenceProfileTest \
  com.watchalign.mobile.baseline.ReferenceComparisonTest \
  com.watchalign.mobile.baseline.EvidenceTest \
  com.watchalign.mobile.baseline.UserFacingFindingTest
  -> JUnit version 4.13.2 ... OK (32 tests)

java -cp <out>:junit:hamcrest org.junit.runner.JUnitCore \
  com.watchalign.mobile.qc.QcModuleResultTest \
  com.watchalign.mobile.qc.RawMeasurementTest
  -> JUnit version 4.13.2 ... OK (9 tests)   (pre-existing tests, run
     unmodified alongside the new package as a regression check)
```

All 32 new tests pass; all 9 pre-existing `qc`-package tests continue to
pass unmodified. This is real compile+test evidence for every new class
in this branch, obtained without touching the Gradle toolchain
configuration -- but it is **not** a substitute for a real
`:app:assembleDebug`/`:app:testDebugUnitTest` run, since it does not
exercise Gradle's own dependency graph, R8/resource processing, or any
class that does depend on Android/OpenCV (i.e. everything outside the
new `baseline` package and the existing `qc` package). Whoever picks
this branch up on a machine with normal network access should run the
standard `AGENTS.md` commands once before relying on this further; they
were not skipped by choice.

## 9. Waiting for genuine baseline

Nothing below exists yet. Each line names exactly what has to land, and
where, before `GenuineReferenceProfile` can be populated or any class in
§6 can be implemented (not just designed).

**From the currently-running `GMT genuine marker baseline` workflow**
(commits to `research/proportional-geometry-knowledge-base` under
`docs/research/gmt-genuine-baseline-results/`, per
`.github/workflows/gmt-genuine-baseline.yml` -- not touched by this
branch):

1. `summary.json` -- must show the run completed (not timed out) and
   report `independent_low_tilt_watches` -- the plan's own acceptance
   checklist (§8 of `gmt-genuine-baseline-validation-plan.md`) requires
   confirming this before anything downstream is trusted.
2. `source_status.csv` -- per-source fetch/pose/measurement counts, to
   audit provenance coverage across the 22-source manifest (dealer /
   auction-house / CPO source classes) before treating any resulting
   band as broad enough.
3. `per_image_measurements.csv` and `per_physical_watch_medians.csv` --
   the raw and per-watch-collapsed measurements for every metric in §4's
   table (`centre_r`, `centre_t`, `outer_r`, `inner_r`, `radial_span`,
   `outer_t`, `inner_t`, `area_norm`, `angular_residual_deg`,
   `axis_residual_deg`, plus the `h12.stage3_*` family and the date-
   centring diagnostics).
4. `baseline_watch_level.csv` -- the `marker, feature, n, median, mad,
   p10, p90, min, max` table this maps almost directly onto
   `MetricBaseline`'s `n`/`median`/`mad`/`p10`/`p90` fields (`min`/`max`
   are diagnostic-only per the plan and are intentionally not fields on
   `MetricBaseline`).
5. `baseline_by_source_class.csv` -- needed to check for provenance/
   photography bias (established-dealer vs. auction-house vs. CPO)
   before treating the pooled baseline as source-independent.

**Not yet computed by anything, needed to fill `MetricBaseline`'s
remaining fields:**

6. **Repeatability error** (`MetricBaseline.repeatabilityError`) --
   `gmt-genuine-baseline-validation-plan.md` §5 requires this (median
   within-watch absolute deviation across repeated photos) but it is not
   one of the columns the builder script currently writes; it needs a
   follow-up pass over `per_image_measurements.csv`, grouped by
   `physical_watch_id`, restricted to watches with >1 usable low-tilt
   image.
7. **Missing/detection-failure rate** (`MetricBaseline.missingRate`) --
   similarly not a direct column; needs a follow-up pass comparing
   attempted vs. measured counts per metric from `source_status.csv` +
   `per_image_measurements.csv`.

**From the (not yet started) replica control-set evaluation** --
required before any metric can be marked `ValidationStatus.SUPPORTED`
(the plan's §6-7 explicitly gate that classification on labelled-defect
separation, not on genuine-side repeatability alone):

8. The blind per-control table described in
   `gmt-replica-control-set-protocol.md`'s "Required outputs" section
   (predeclared metric, genuine-reference deviation, repeatability
   margin, expected-direction match) for the 9 seed controls in
   `gmt-replica-ground-truth-control-set.csv` plus the further examples
   queued in `gmt-replica-control-set-expansion-2026-09-23.md` -- this
   is explicitly listed as the "next action after genuine baseline
   completes" in that same document, i.e. it has an acknowledged
   dependency on item 1-5 above and has not been started.
9. `gmt-genuine-baseline-validation-report.md` -- named as the concrete
   "next deliverable" in `gmt-genuine-baseline-validation-plan.md` §9;
   this is where each candidate metric actually gets assigned one of
   `SUPPORTED` / `PROMISING` / `DIAGNOSTIC_ONLY` / `REJECT`. Until this
   exists, every `MetricBaseline` this design could build would have to
   default to `DIAGNOSTIC_ONLY` at best, and `UserFacingFinding.
   MeasurableDeviation` could never legitimately be constructed for any
   metric.

Until items 1-9 exist, the correct state of every class in this branch
is exactly what it is today: compiling, tested against placeholder
values, and referenced by nothing in production.
