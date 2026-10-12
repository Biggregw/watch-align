"""Descriptive genuine research statistics. No QC thresholds or overall watch score."""
from collections import Counter,defaultdict
import statistics


def quantile(rows,field,p):
    counts=Counter(r["physical_watch_id"] for r in rows)
    values=sorted((float(r[field]),1/counts[r["physical_watch_id"]]) for r in rows)
    if not values:return ""
    target=p*sum(w for _,w in values); acc=0
    for i,(value,w) in enumerate(values):
        acc+=w
        if acc>=target-1e-12:
            return (value+values[i+1][0])/2 if p==.5 and abs(acc-target)<1e-12 and i+1<len(values) else value
    return values[-1][0]


def tables(rows):
    result=[]
    for family in ("gmt_126710","submariner_124060","submariner_126610"):
        for shape in ("triangle","baton","round"):
            attempted=[r for r in rows if r["family"]==family and r["shape"]==shape]
            usable=[r for r in attempted if r["baseline_eligible"]=="true"]
            n=len({r["physical_watch_id"] for r in usable})
            item=dict(family=family,shape=shape,recorded_watch_groups_attempted=len({r["physical_watch_id"] for r in attempted}),
                      watch_groups_with_usable_area=n,photos_with_usable_area=len({r["photo_id"] for r in usable}),
                      markers_attempted=len(attempted),usable_area_markers=len(usable),withheld_or_identity_excluded_markers=len(attempted)-len(usable),
                      identity_note="Recorded watch groups; inferred and cross-vendor identities not serial-verified; shared-image suspects excluded",
                      percentile_note="Equal weight per recorded watch. P90 if >=20 groups; P95 if >=40. These are sample observations, not factory tolerances.")
            for field in ("extra_pct","missing_pct","mismatch_pct"):
                item["median_"+field]=quantile(usable,field,.5)
            item["p90_mismatch_pct"]=quantile(usable,"mismatch_pct",.9) if n>=20 else ""
            item["p95_mismatch_pct"]=quantile(usable,"mismatch_pct",.95) if n>=40 else ""
            item["max_mismatch_pct"]=max(float(r["mismatch_pct"]) for r in usable) if usable else ""
            result.append(item)
    return result


def watch_marker_summary(rows):
    grouped=defaultdict(list)
    for r in rows:grouped[r["family"],r["physical_watch_id"],r["hour"],r["shape"]].append(r)
    result=[]
    for (family,watch,hour,shape),sample in sorted(grouped.items()):
        usable=[r for r in sample if r["baseline_eligible"]=="true"]
        result.append(dict(family=family,physical_watch_id=watch,hour=hour,shape=shape,photos=len(sample),usable_area_photos=len(usable),
                           mismatch_pct=statistics.median(float(r["mismatch_pct"]) for r in usable) if usable else "",
                           maximum_observed_mismatch_pct=max(float(r["mismatch_pct"]) for r in usable) if usable else "",
                           area_reasons=";".join(sorted({r["area_reason"] for r in sample})),
                           identity_status=sample[0]["identity_status"]))
    return result
