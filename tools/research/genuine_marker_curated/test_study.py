import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from prepare import choose_one_per_watch
from study_stats import tables,quantile,watch_marker_summary


def row(watch="a",hour="9",value="2",eligible="true"):
    return dict(family="gmt_126710",physical_watch_id=watch,hour=hour,shape="baton",photo_id=watch+hour,
                baseline_eligible=eligible,area_reason="measured" if eligible=="true" else "withheld",identity_status="recorded_listing_group",
                mismatch_pct=value,extra_pct=value,missing_pct="0")


class CuratedStudyTest(unittest.TestCase):
    def test_selection_is_one_original_per_recorded_watch_without_scores(self):
        a=dict(family="gmt_126710",physical_watch_id="a",source_width=1000,source_height=2000,sha256="a")
        b=dict(a,source_width=1500,source_height=1500,sha256="b")
        c=dict(a,physical_watch_id="b")
        choose_one_per_watch([a,b,c])
        self.assertEqual("false",a["selected_for_measurement"]);self.assertEqual("true",b["selected_for_measurement"]);self.assertEqual("true",c["selected_for_measurement"])

    def test_identity_or_edge_excluded_scores_do_not_define_distribution(self):
        t=tables([row(),row(watch="b",value="99",eligible="false")])
        b=next(r for r in t if r["family"]=="gmt_126710" and r["shape"]=="baton")
        self.assertEqual(2,b["max_mismatch_pct"]);self.assertEqual(1,b["watch_groups_with_usable_area"])

    def test_equal_watch_weight_and_even_median(self):
        a=[row(value="2"),row(hour="6",value="4"),row(watch="b",value="8")]
        self.assertEqual(6,quantile(a,"mismatch_pct",.5))

    def test_sparse_groups_have_no_tail_percentiles(self):
        self.assertTrue(all(r["p90_mismatch_pct"]==r["p95_mismatch_pct"]=="" for r in tables([row()])))

    def test_watch_summary_remains_per_hour_and_retains_maximum(self):
        a=watch_marker_summary([row(value="2"),row(value="8"),row(hour="6",value="1")])
        self.assertEqual(2,len(a));nine=next(r for r in a if r["hour"]=="9")
        self.assertEqual(8,nine["maximum_observed_mismatch_pct"])


if __name__=="__main__":unittest.main()
