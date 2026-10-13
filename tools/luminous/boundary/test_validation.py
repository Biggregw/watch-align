"""Checks for real gate failure modes; no photographs or detector implementation mocked."""
import copy
import json
import math
import tempfile
import unittest
from pathlib import Path
from checks import (CONTROLS, REVIEWED, SAFE_BOUNDARIES, check_reviewed,
                    check_safe_boundary, close, read)
from verify_build import verify_contract, verify_csv_constraints


def photo():
    family,id,h,center,radius,fitted,alignment=REVIEWED[0]
    ideal=[[center[0]+radius*math.cos(a),center[1]+radius*math.sin(a)]
           for a in (i*math.pi/8 for i in range(16))]
    m=dict(hour=h, fit=dict(ideal=ideal,center=list(center),radius_R=radius),
           new_fitted=dict(total_pct=fitted),new_alignment=dict(total_pct=alignment),
           new_observed=None,old_observed=None,observed_polygon=None,observed_usable=False)
    return dict(photo_id=id,markers=[m])


class PhotographicGateTest(unittest.TestCase):
    def check(self,p):
        check_reviewed(p,REVIEWED[0][2],*REVIEWED[0][3:])

    def test_reviewed_geometry_passes(self):
        self.check(photo())

    def test_same_radius_shifted_center_fails(self):
        p=photo();p['markers'][0]['fit']['center'][0]+=.001
        with self.assertRaisesRegex(ValueError,'center changed'):self.check(p)

    def test_same_radius_corrupted_reconstructed_polygon_fails(self):
        p=photo();p['markers'][0]['fit']['ideal'][0][0]+=.001
        with self.assertRaisesRegex(ValueError,'reconstructed circle'):self.check(p)

    def test_fitted_and_alignment_scores_are_independently_checked(self):
        for key in ('new_fitted','new_alignment'):
            with self.subTest(key=key):
                p=photo();p['markers'][0][key]['total_pct']+=.1
                with self.assertRaisesRegex(ValueError,key):self.check(p)

    def test_fitted_geometry_cannot_be_claimed_as_observed(self):
        p=photo();p['markers'][0]['new_observed']={'total_pct':0}
        with self.assertRaisesRegex(ValueError,'OBSERVED footprint'):self.check(p)

    def test_correct_boundary_may_safely_abstain(self):
        before=photo();after=copy.deepcopy(before);m=after['markers'][0]
        m['fit']['ideal']=None
        for key in ('new_fitted','new_alignment','old_fitted','old_alignment'):m[key]=None
        check_safe_boundary(before,after,REVIEWED[0][2])

    def test_false_outer_metal_circle_fails_even_with_unchanged_score(self):
        before=photo();after=copy.deepcopy(before)
        after['markers'][0]['fit']['radius_R']*=1.08
        with self.assertRaisesRegex(ValueError,'expanded onto metal'):
            check_safe_boundary(before,after,REVIEWED[0][2])

    def test_abstention_cannot_leave_alignment_score(self):
        before=photo();after=copy.deepcopy(before);after['markers'][0]['fit']['ideal']=None
        after['markers'][0]['new_fitted']=None
        with self.assertRaisesRegex(ValueError,'unsafe new_alignment'):
            check_safe_boundary(before,after,REVIEWED[0][2])

    def test_duplicate_photographs_cannot_be_silently_overwritten(self):
        with tempfile.TemporaryDirectory() as dest:
            path=Path(dest)/'duplicate.jsonl';line=json.dumps(photo())+'\n';path.write_text(line+line)
            with self.assertRaisesRegex(ValueError,'Duplicate photo'):read(path)

    def test_nonfinite_numbers_do_not_pass_numeric_checks(self):
        self.assertFalse(close(float('inf'),float('inf')))
        self.assertFalse(close(float('nan'),float('nan')))

    def test_old_boolean_report_is_not_build_authorization(self):
        with self.assertRaisesRegex(ValueError,'strengthened full'):
            verify_contract({'validation_passed':True})

    def test_failed_validation_cannot_build(self):
        with self.assertRaisesRegex(ValueError,'unresolved failures'):
            verify_contract({'validation_schema':2,'validation_passed':False,'validation_failures':['metal boundary']})

    def test_passing_boolean_and_counts_without_source_hashes_cannot_build(self):
        report=dict(validation_schema=2,validation_passed=True,validation_failures=[],
                    originals=132,processing_variants=18,exact_original_repeats=3,
                    reviewed_circles_checked=len(REVIEWED),unchanged_reviewed_controls_checked=len(CONTROLS),
                    metal_boundary_regressions_checked=len(SAFE_BOUNDARIES),batgirl_markers_checked=[9,12],
                    noncircular_and_outer_unchanged=True,batgirl_original_verified=True,
                    contradictory_processing_circle_withheld_in_all_7_cases=True)
        with self.assertRaisesRegex(ValueError,'Incomplete source_hashes'):
            verify_contract(report)

    def test_real_failed_csv_cannot_be_overridden_by_a_report_flag(self):
        # Use the committed review evidence when available; the known c14 failure
        # must still fail independently of validation_passed in its JSON report.
        import csv
        from checks import ROOT
        report=ROOT/'docs/research/luminous-boundary-review-2026-10-13'
        if not (report/'original_comparison.csv').exists():self.skipTest('Full photographic review not yet recorded')
        def rows(name):
            with (report/name).open() as f:return list(csv.DictReader(f))
        with self.assertRaisesRegex(ValueError,'c14f8b3abb49c038'):
            verify_csv_constraints(rows('original_comparison.csv'),rows('processing_comparison.csv'),rows('batgirl_comparison.csv'))


if __name__=='__main__':unittest.main()
