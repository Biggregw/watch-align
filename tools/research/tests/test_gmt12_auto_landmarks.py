import math
import os
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from gmt12_auto_landmarks import (
    _circle_tangent_landmarks, _trim_bezel_band, _pick_dial_circle,
    _polygon_to_triangle_corners, _ticks, _direct, _sequence, _robust_line,
    _first_regularized, _minute_ticks,
)
from human_qc_geometry import Gmt12Geometry, Point, measure_gmt12


def angle_deg(a, b):
    return math.degrees(math.atan2(b[1]-a[1], b[0]-a[0]))


def test_glare_shortened_tick_does_not_manufacture_eight_degree_rotation():
    # Real failure shape observed during blind QC: raw detected 59/1 endpoint
    # heights implied ~8.63 degrees although the physical 12 marker was upright.
    l=(316.0,497.2); c=(336.0,498.4); r=(356.0,503.27)
    assert abs(angle_deg(l,r)) > 8.0
    fixed=_circle_tangent_landmarks(l,c,r,335.0,700.0)
    assert fixed is not None
    fl,fc,fr=fixed
    assert abs(angle_deg(fl,fr)) < 1.0
    assert fc == c
    assert fl[0] == l[0] and fr[0] == r[0]


def test_common_camera_roll_is_preserved_by_circle_tangent_reference():
    # A rolled watch must not be flattened to horizontal. Put 60 on a radius
    # whose local tangent is +7 degrees and verify that orientation survives.
    cx,cy=300.0,700.0
    y60=500.0
    slope=math.tan(math.radians(7.0))
    x60=cx-slope*(y60-cy)
    l=(x60-20,y60-9); c=(x60,y60); r=(x60+20,y60+11)
    fixed=_circle_tangent_landmarks(l,c,r,cx,cy)
    assert fixed is not None
    fl,_,fr=fixed
    assert math.isclose(angle_deg(fl,fr),7.0,abs_tol=.05)


def test_implausible_circle_to_60_geometry_is_rejected():
    # Bad dial-circle/60 associations should become unassessable, not a large
    # confident marker-rotation finding.
    assert _circle_tangent_landmarks((0,0),(100,100),(200,200),0,99) is None


def _geometry_with_triangle_rotation(rotation_deg, triangle_shift_x=0.0, watch_roll_deg=0.0):
    """Synthetic local geometry with independent marker rotation, translation and camera roll."""
    cx,cy=300.0,700.0
    y60=500.0
    tangent=math.radians(watch_roll_deg)
    slope=math.tan(tangent)
    x60=cx-slope*(y60-cy)
    pitch=20.0
    raw_l=(x60-pitch,y60-5.0)
    raw_c=(x60,y60)
    raw_r=(x60+pitch,y60+7.0)
    fixed=_circle_tangent_landmarks(raw_l,raw_c,raw_r,cx,cy)
    assert fixed is not None
    ml,m60,mr=fixed

    width=60.0
    tri_angle=tangent+math.radians(rotation_deg)
    ux,uy=math.cos(tri_angle),math.sin(tri_angle)
    midx=x60+triangle_shift_x
    midy=540.0+slope*(midx-x60)
    tl=Point(midx-.5*width*ux,midy-.5*width*uy)
    tr=Point(midx+.5*width*ux,midy+.5*width*uy)
    # Triangle axis is perpendicular to its top edge and points inward/downward.
    nx,ny=-uy,ux
    tip=Point(midx+70.0*nx,midy+70.0*ny)
    return Gmt12Geometry(tl,tr,tip,Point(*ml),Point(*mr),Point(*m60))


def test_true_two_degree_marker_rotation_survives_tick_regularisation():
    m=measure_gmt12(_geometry_with_triangle_rotation(2.0))
    assert math.isclose(m.rotation_deg,2.0,abs_tol=.05)


def test_marker_rotation_is_relative_to_watch_roll_not_image_horizontal():
    m=measure_gmt12(_geometry_with_triangle_rotation(2.0,watch_roll_deg=7.0))
    assert math.isclose(m.rotation_deg,2.0,abs_tol=.05)


def test_lateral_translation_does_not_turn_into_rotation():
    m=measure_gmt12(_geometry_with_triangle_rotation(0.0,triangle_shift_x=12.0,watch_roll_deg=-5.0))
    assert math.isclose(m.rotation_deg,0.0,abs_tol=.05)
    assert abs(m.horizontal_offset_over_triangle_width) > .10


def _band_with_bright_ring(h=109,w=392,ring_rows=(14,40),ring_frac=0.35,tick_frac=0.11):
    """Synthetic minute-track search band shaped like the real failure: a
    generous top margin reaches a bright metal bezel/rehaut ring before the
    dark dial and its minute ticks. Row brightness is expressed directly as
    the fraction of that row's pixels above the _trim_bezel_band '>140'
    threshold, since that is the only signal the function reads."""
    band=np.full((h,w),60,dtype=np.uint8)  # dark dial background everywhere
    r0,r1=ring_rows
    band[r0:r1,:int(w*ring_frac)]=200  # bright bezel ring rows
    band[r1:,:int(w*tick_frac)]=200    # dimmer, narrower minute-tick evidence
    return band


def test_trim_bezel_band_skips_bright_metal_ring_above_ticks():
    # Real failure shape: a bright bezel/rehaut ring inside a generously
    # sized search band pulled the Otsu threshold high enough that the much
    # thinner, dimmer minute ticks below it did not register at all (0-3
    # tick candidates against ~9 physically visible ticks). Trimming the
    # band past the ring recovered a full, direct 59/60/1 detection.
    gray=np.zeros((200,500),dtype=np.uint8)
    x0,y0,x1,y1=50,20,442,129
    gray[y0:y1,x0:x1]=_band_with_bright_ring()
    trimmed=_trim_bezel_band(gray,x0,y0,x1,y1)
    assert trimmed>y0+30  # past the 14-40 bright ring, with margin
    assert trimmed<y0+60  # but nowhere near the far (dial) end of the band


def test_trim_bezel_band_leaves_band_untouched_without_a_bright_ring():
    # No bezel content in the searched band at all (e.g. the margin above
    # the triangle did not reach the bezel in this photo) -- must not trim
    # away real, needed search area on a purely defensive assumption.
    gray=np.full((200,500),60,dtype=np.uint8)
    x0,y0,x1,y1=50,20,450,129
    gray[y0:y1,x0+10:x0+30]=200  # a little tick-like brightness, not a ring
    assert _trim_bezel_band(gray,x0,y0,x1,y1)==y0


def test_trim_bezel_band_is_safe_on_a_short_band():
    gray=np.zeros((60,200),dtype=np.uint8)
    assert _trim_bezel_band(gray,10,10,190,18)==10


def test_trim_bezel_band_does_not_mistake_a_single_tick_spike_for_a_ring():
    # Real failure shape: a search band with NO bezel content at all, but a
    # single-row bright-fraction spike (adjacent minute ticks curving into
    # the same row across a wide band) as tall as a real bezel row. Treating
    # any tall spike as bezel discarded the very tick evidence being
    # searched for. A run must be many rows tall, not one, to count.
    gray=np.zeros((200,500),dtype=np.uint8)
    x0,y0,x1,y1=50,20,442,129
    band=np.full((109,392),0,dtype=np.uint8)   # dark everywhere...
    band[38:39,:]=200                          # ...except a single bright row
    gray[y0:y1,x0:x1]=band
    assert _trim_bezel_band(gray,x0,y0,x1,y1)==y0


def test_pick_dial_circle_prefers_hough_top_rank_near_ties():
    # Real failure shape observed on a QC photo: the correct dial circle was
    # Hough's own rank-0 (strongest accumulator) candidate, but a smaller,
    # off-dial circle around the hands hub -- sampling purely black interior
    # with no hands/index/text to dilute it, and sitting closer to the frame
    # centre -- won the old un-penalised content re-score by a hair (457.0
    # vs 455.1) despite being geometrically wrong (visually confirmed).
    correct=(0, 545.0, 989.0, 393.0, 224.0, 181.0)   # rank, x, y, r, dark, d
    wrong=(2, 597.0, 1273.0, 385.0, 185.0, 117.0)
    x,y,r=_pick_dial_circle([correct,wrong])
    assert (x,y,r)==(545.0,989.0,393.0)


def test_pick_dial_circle_still_lets_a_much_stronger_content_match_win():
    # The rank penalty breaks near-ties; it must not make Hough's rank an
    # absolute override regardless of how much better another candidate is.
    weak_top_rank=(0, 300.0, 300.0, 100.0, 50.0, 400.0)   # small, bright, far off-centre
    much_better=(3, 300.0, 300.0, 400.0, 240.0, 5.0)      # large, dark, centred
    x,y,r=_pick_dial_circle([weak_top_rank,much_better])
    assert (x,y,r)==(300.0,300.0,400.0)


def test_pick_dial_circle_empty_is_none():
    assert _pick_dial_circle([]) is None


def test_polygon_to_triangle_corners_survives_a_hand_crossing_an_edge():
    # Real failure shape: a watch hand crossed the triangle's right edge in
    # the photo, leaving the hull's polygon approximation with a 4th vertex
    # along that edge instead of simplifying to a clean 3-vertex triangle
    # (anti-aliasing/colour bleed at the hand's boundary, not a real
    # corner). The detector reported no triangle at all even though the
    # top-left, top-right and tip corners were still plainly the shape's
    # three extreme points. cx/r below match the real photo's dial circle.
    poly=np.array([[471.0,775.0],[520.0,776.0],[517.0,824.0],[503.0,855.0]])
    corners=_polygon_to_triangle_corners(poly,6,497.4,448.68)
    assert corners is not None
    left,right,tip=corners
    assert tuple(left)==(471.0,775.0)
    assert tuple(right)==(520.0,776.0)
    assert tuple(tip)==(503.0,855.0)   # the actual bottommost point, not the
                                        # 3rd-smallest-y vertex (517,824)


def test_polygon_to_triangle_corners_rejects_too_many_vertices():
    # A genuinely non-triangular blob must still be rejected -- the vertex
    # allowance exists for a handful of minor edge artefacts, not arbitrary
    # shapes.
    poly=np.array([[float(i),float(i%3)] for i in range(10)])
    assert _polygon_to_triangle_corners(poly,6,0.0,100.0) is None


def test_polygon_to_triangle_corners_rejects_implausible_shape():
    # A 4-vertex polygon that isn't triangle-like (extreme width/height
    # ratio) must still fail the existing plausibility checks.
    poly=np.array([[0.0,0.0],[500.0,1.0],[10.0,3.0],[250.0,2.0]])
    assert _polygon_to_triangle_corners(poly,6,250.0,1000.0) is None


def test_trim_bezel_band_scales_with_band_height_not_fixed_pixel_rows():
    # MIN_BEZEL_RUN_ROWS/TRIM_MARGIN_ROWS used to be fixed pixel-row counts,
    # calibrated only against the one real photo (band height 109) that
    # exposed the original bezel-washout bug. That made the trim behave very
    # differently on a much lower-resolution photo -- a fixed 15-row margin
    # can consume most of a 55-row band, as real image-10-scale bands do.
    # The trim fraction of the band must now stay roughly constant across
    # scales instead of being dominated by an absolute row count.
    def band_with_ring(h, w, ring_rows, ring_frac=.35, tick_frac=.11):
        band=np.full((h,w),60,dtype=np.uint8)
        r0,r1=ring_rows
        band[r0:r1,:int(w*ring_frac)]=200
        band[r1:,:int(w*tick_frac)]=200
        return band

    gray=np.zeros((200,500),dtype=np.uint8)
    x0,y0,x1,y1=50,20,442,129   # h=109, the reference scale from earlier tests
    gray[y0:y1,x0:x1]=band_with_ring(109,392,(14,40))
    trimmed_ref=_trim_bezel_band(gray,x0,y0,x1,y1)
    frac_ref=(trimmed_ref-y0)/(y1-y0)

    gray2=np.zeros((100,250),dtype=np.uint8)
    x0b,y0b,x1b,y1b=25,10,221,65   # h=55, roughly image-10's real band scale
    gray2[y0b:y1b,x0b:x1b]=band_with_ring(55,196,(7,20))
    trimmed_half=_trim_bezel_band(gray2,x0b,y0b,x1b,y1b)
    frac_half=(trimmed_half-y0b)/(y1b-y0b)

    assert math.isclose(frac_ref,frac_half,abs_tol=.04)


def test_ticks_extracts_plausible_tick_shapes_and_rejects_noise():
    # Real proportions observed in image 11 (r=411.72): ticks ~3-4px wide,
    # ~13-15px tall, pitch ~28.5px. A wide/short blob (bezel text stroke)
    # and a 1px speck (compression noise) must not be reported as ticks.
    r=411.72
    mask=np.zeros((30,700),dtype=np.uint8)
    cv2.rectangle(mask,(150,5),(153,18),255,-1)
    cv2.rectangle(mask,(178,6),(180,19),255,-1)
    cv2.rectangle(mask,(207,5),(210,19),255,-1)
    cv2.rectangle(mask,(300,10),(330,16),255,-1)  # w=31 h=7, rejected: h<1.2*w
    mask[10,400]=255                              # 1x1 speck, rejected: area<4

    out=_ticks(mask,x0=1000,y0=2000,r=r)
    assert len(out)==3
    xs=[round(o[0],1) for o in out]
    assert xs==[1152.0,1179.5,1209.0]
    for cx,y_bottom,w,h,area in out:
        assert 2000<y_bottom<2030


# Real tick candidates extracted from image 11's minute-track band (Otsu
# mask, y0=767), r=411.72, triangle top-edge midpoint mid=509.5. Spans
# roughly minute 56 through 2.
_REAL_TICKS_IMG11 = [
    (396.0, 812, 6.0, 10.0, 22.0), (423.0, 804, 6.0, 13.0, 26.0),
    (451.0, 797, 4.0, 13.0, 23.0), (479.5, 792, 3.0, 13.0, 28.0),
    (508.0, 791, 4.0, 15.0, 46.0), (536.5, 792, 3.0, 13.0, 22.0),
    (565.0, 796, 4.0, 13.0, 24.0), (592.5, 804, 5.0, 14.0, 27.0),
    (620.0, 812, 6.0, 11.0, 22.0),
]
_MID_IMG11 = 509.5
_R_IMG11 = 411.72


def test_direct_finds_the_real_59_60_1_triple():
    d=_direct(_REAL_TICKS_IMG11,_MID_IMG11,_R_IMG11)
    assert d is not None
    score,l,c,rr=d
    assert l==(479.5,792,3.0,13.0,28.0)
    assert c==(508.0,791,4.0,15.0,46.0)
    assert rr==(536.5,792,3.0,13.0,22.0)


def test_sequence_reconstructs_60_from_surrounding_evidence_when_missing():
    # Real regression case for the "reconstruct poorly-defined ticks from
    # good ones" capability: drop the actual 60 candidate entirely and
    # confirm the pitch model recovers its position from 59/1/58/2 alone,
    # closely matching the real observed x=508.0.
    t_missing_60=[q for q in _REAL_TICKS_IMG11 if q[0]!=508.0]
    s=_sequence(t_missing_60,_MID_IMG11,_R_IMG11)
    assert s is not None
    score,l,c,rr,ninf=s
    assert math.isclose(c[0],508.0,abs_tol=1.0)
    assert ninf==1   # exactly one position (60) had to be inferred


def test_sequence_rejects_evidence_confined_to_one_side_of_60():
    # Ticks observed only to one side of 60 (e.g. 1,2,3,4...) must not be
    # accepted as a self-consistent local reference: extrapolating the 60
    # position from one-sided evidence alone is exactly the unconstrained
    # guess this function is designed to refuse.
    t_one_side=[q for q in _REAL_TICKS_IMG11 if q[0]>508.0]
    assert _sequence(t_one_side,_MID_IMG11,_R_IMG11) is None


def test_robust_line_excludes_a_single_drastic_outlier():
    k=[-2,-1,0,1,2]
    y=[802.0,792.0,791.0,999.0,796.0]   # index 3 (k=1) is a drastic outlier
    y0,slope,keep=_robust_line(k,y)
    assert list(keep)==[True,True,True,False,True]
    assert abs((y0+slope*1)-999.0)>50   # fit is not dragged toward the outlier


def test_first_regularized_skips_a_bad_best_scored_candidate_for_a_good_one():
    # Real fallback-priority bug: _minute_ticks used to take only the single
    # best-scored direct candidate; if THAT one failed circle-tangent
    # regularisation (bad circle/60 geometry), the whole direct family was
    # discarded even when a second, lower-ranked direct candidate was
    # perfectly good. _first_regularized must keep trying candidates in
    # score order until one actually passes.
    cx,cy=300.0,1000.0
    bad=(0.1,(480.0,500.0),(500.0,500.0),(520.0,500.0))    # best score, bad geometry
    good=(1.0,(280.0,500.0),(300.0,500.0),(320.0,500.0))   # worse score, valid geometry

    # Confirm the premise: the naive "just take min-by-score" approach fails.
    assert _circle_tangent_landmarks(bad[1],bad[2],bad[3],cx,cy) is None
    assert _circle_tangent_landmarks(good[1],good[2],good[3],cx,cy) is not None

    result=_first_regularized([bad,good],cx,cy)
    assert result is not None
    (l,c,rr),rest=result
    assert (l,c,rr)==((280.0,500.0),(300.0,500.0),(320.0,500.0))
    assert rest==()


def test_first_regularized_empty_is_none():
    assert _first_regularized([],300.0,1000.0) is None


def test_minute_ticks_end_to_end_direct_detection_on_synthetic_dial():
    # Closes the coverage gap for _minute_ticks itself: a synthetic but
    # geometrically plausible dial band (real tick proportions, r=400) drawn
    # with cv2 so the full mask -> ticks -> direct -> circle-tangent path
    # runs for real, not just its individual pieces in isolation.
    gray=np.zeros((320,450),dtype=np.uint8)
    r=400.0
    for x in (140,170,200,230,260):
        cv2.line(gray,(x,200),(x,224),255,3)
    tl=Point(180,270); tr=Point(220,270)
    cx,cy=200.0,670.0

    result=_minute_ticks(gray,cx,cy,r,tl,tr)
    assert result is not None
    ml,mr,m60,inferred,n=result
    assert math.isclose(ml.x,170.5,abs_tol=.5)
    assert math.isclose(m60.x,200.5,abs_tol=.5)
    assert math.isclose(mr.x,230.5,abs_tol=.5)
    assert inferred is False
