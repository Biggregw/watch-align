#!/usr/bin/env python3
"""Generate an instrumented, renamed copy; never edit the production fitter.

Each insertion must match exactly once. MarkerAudit also checks numerical parity
against the real pipeline. The trace records the weights used in the last solve,
separately from the weights updated afterwards for quality reporting.
"""
import argparse
from pathlib import Path


def generate(source, destination):
    text = Path(source).read_text().replace("MetalFit", "TracedMetalFit")
    changes = [
        ("    static final class Fit {", """    static final class Step {
        double[][] poly, jacobian; Edges edges;
        double[] residual, weights, solveWeights, delta; int[] indices;
    }
    static final class Fit {
        final List<Step> steps=new ArrayList<>();"""),
        ("            e=edges(img,poly,rpx,round,hands);", """            e=edges(img,poly,rpx,round,hands);
            Step trace=new Step();trace.poly=poly;trace.edges=e;f.steps.add(trace);"""),
        ("                x=wls(J,r,w,np);", """                trace.solveWeights=w.clone();
                x=wls(J,r,w,np);"""),
        ("            tx+=x[0];ty+=x[1];if(rotate)th+=x[2];", """            trace.jacobian=J;trace.residual=res;trace.weights=w;
            trace.delta=x;trace.indices=idx;
            tx+=x[0];ty+=x[1];if(rotate)th+=x[2];"""),
    ]
    for old, new in changes:
        if text.count(old) != 1:
            raise ValueError("Trace anchor changed: " + old)
        text = text.replace(old, new)
    dest = Path(destination)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(text)


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("source")
    p.add_argument("destination")
    a = p.parse_args()
    generate(a.source, a.destination)
