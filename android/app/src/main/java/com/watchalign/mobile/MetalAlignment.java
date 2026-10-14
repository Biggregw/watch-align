package com.watchalign.mobile;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Outer-metal ALIGNMENT of the triangle and the batons, alongside the luminous result (owner, 14 Oct 2026: a 12 whose
 * metal surround sits high while its lume is nearly in place must be reported against the metal, not the lume).
 *
 * Reuses the existing Alpha107 outer-metal measurement (Alpha94: outermost bright-to-dark edge of each side, the same
 * edge definition as its genuine reference) and its findings (Alpha99Findings: deviation from the genuine-calibrated
 * nominal, which carries the known systematic reference offset of the 12, against the largest genuine watch, with
 * 3 x the genuine photo-to-photo uncertainty as the allowance and the existing visibility and edge-consistency caps).
 * Position and rotation only; the 12's side-angle (shape) measure and area scoring are not used.
 *
 * A component is a defect only when Alpha107 would call it CLEAR: beyond every genuine watch by more than the
 * allowance, and visible. Merely crossing the yellow reference is never a defect. A reading beyond every genuine watch
 * but within the allowance is reported as "Worth a look" (a borderline note), never as a defect.
 *
 * The 12's radial ("track") reading had no photo-to-photo allowance, so Alpha107 capped it at worth a look. Its
 * allowance now comes from alignment/metal_alignment.properties (twelve_radial: pooled within-watch SD of the genuine
 * repeat photos the app would assess, with k = 2 as in every luminous decision;
 * tools/research/alignment_only/twelve_radial_uncertainty.py). Without that file it stays capped. The other metal
 * components keep Alpha107's established k = 3.
 */
final class MetalAlignment {
    static final String FILE="alignment/metal_alignment.properties";
    private MetalAlignment(){}

    /** twelve_radial.R / .px from the model's metal alignment file; null when absent. */
    static Map<String,Double> load(ModelSpec.Assets assets,ModelSpec model){
        try{return parse(ModelSpec.read(assets,model.dir+"/"+FILE));}catch(IOException|RuntimeException e){return null;}
    }
    static Map<String,Double> parse(String text)throws IOException{
        Properties p=new Properties();p.load(new StringReader(text));
        Map<String,Double> m=new java.util.HashMap<>();
        for(String k:p.stringPropertyNames())try{m.put(k,Double.parseDouble(p.getProperty(k).trim()));}catch(NumberFormatException ignored){}
        return m;
    }

    /** Adds the metal results to each triangle / baton marker and settles the marker's outcome. Display text only for
     *  inconclusive readings; a CLEAR metal component makes the marker "Defect detected". */
    static void apply(AlignmentAnalysis.Output o,Alpha99Findings.Summary s,Alpha94MarkerMeasurement.Report r,ModelSpec model,
                      ModelReference ref,Map<String,Double> extra){
        if(o==null||s==null||r==null||ref==null||o.photoReason!=null)return;
        double R=r.dialRadiusPx;
        for(AlignmentDecision.Marker m:o.markers){
            if(m.shape==ModelSpec.Shape.ROUND)continue;
            Alpha99Findings.Finding f=null;
            for(Alpha99Findings.Finding x:s.all)if(!x.dialWide&&x.hour==m.hour&&(x.shape==Alpha99Findings.Shape.TRIANGLE||x.shape==Alpha99Findings.Shape.BATON))f=x;
            if(f==null)continue;
            if(f.status==Alpha99Findings.Status.NOT_ASSESSED){m.metalNotes.add("Outer metal not assessed: "+f.reason+".");settle(m);continue;}
            ModelSpec.Marker spec=model.atHour(m.hour);
            double lever=0.5*Math.max(spec.outerEdgeR()-spec.innerR(),2*spec.halfWidth());
            double[] er=LuminousAlignment.radial(m.hour),et=LuminousAlignment.tangential(m.hour);
            Alpha97TwelveReadout.Result t=m.shape==ModelSpec.Shape.TRIANGLE?Alpha97TwelveReadout.from(r,ref):null;
            Alpha94MarkerMeasurement.Marker mm=r.atHour(m.hour);
            for(Alpha99Findings.Measure x:f.measures){
                Alpha99Findings.Measure use=x;
                AlignmentDecision.Issue i=new AlignmentDecision.Issue();i.metal=true;i.rpx=R;
                switch(x.name){
                    case "lateral":   // the 12 left / right
                        i.param=LuminousAlignment.Param.TANGENTIAL;
                        double sgn=t!=null&&Double.isFinite(t.lateralPx)&&t.lateralPx<0?-1:1;
                        i.deviation=sgn*x.value;i.screen=new double[]{sgn*et[0],sgn*et[1]};break;
                    case "track": {   // the 12 towards the minute track, with its photo-to-photo allowance
                        i.param=LuminousAlignment.Param.RADIAL;i.deviation=x.value;i.screen=er.clone();
                        double sig=sigmaRadial(extra,R);
                        Double kk=extra==null?null:extra.get("twelve_radial.k");
                        use=new Alpha99Findings.Measure(x.name,x.value,x.genuineMax,sig,kk!=null&&kk>0?kk:x.k,x.n,x.unit,x.shortValue,x.sentence);
                        use.visibleBar=x.visibleBar;
                        if(!Double.isFinite(sig))use.cap("no photo-to-photo allowance for the 12's distance from the minute track");
                        break;}
                    case "centreline": case "rotation": {
                        i.param=LuminousAlignment.Param.ROTATION;
                        double sg=rotationSign(x,t,mm,ref,m.hour);i.deviation=sg*x.value;break;}
                    case "position": {
                        i.param=mm!=null&&Double.isFinite(mm.localRadialPx)&&Math.abs(mm.localRadialPx)>=Math.abs(mm.localTangentialPx)
                                ?LuminousAlignment.Param.RADIAL:LuminousAlignment.Param.TANGENTIAL;
                        i.deviation=x.value;
                        if(mm!=null&&Double.isFinite(mm.localRadialPx)){double a=mm.localRadialPx,b=mm.localTangentialPx,l=Math.hypot(a,b);
                            i.screen=l>0?new double[]{(a*er[0]+b*et[0])/l,(a*er[1]+b*et[1])/l}:null;}
                        break;}
                    default:continue;      // side angle (shape), not alignment
                }
                i.limit=x.genuineMax;i.uncertainty=use.hasAllowance()?use.allowance():Double.NaN;
                double toR=i.param==LuminousAlignment.Param.ROTATION?Math.toRadians(1)*lever:1;
                i.excessR=(x.value-x.genuineMax-(use.hasAllowance()?use.allowance():0))*toR;
                Alpha99Findings.Status st=use.status();
                if(st==Alpha99Findings.Status.CLEAR)m.metal.add(i);
                else if(st==Alpha99Findings.Status.WORTH){
                    m.metalBorderline=true;
                    m.metalNotes.add(String.format(Locale.US,"Worth a look (not confirmed): the outer metal reads %s, beyond every genuine watch measured (%s; genuine up to %s), but the difference is within this photo's measurement uncertainty. A sharper original photo may settle it. Do not enlarge this photo to try: enlarging adds no detail, and this check cannot yet tell an enlarged photo from a sharper one.",
                            words(i),fmt(x.value,x.unit,R),fmt(x.genuineMax,x.unit,R)));}
            }
            settle(m);
        }
    }

    /** CLEAR metal components join the marker; the luminous dominant issue keeps the lead when there is one. */
    static void settle(AlignmentDecision.Marker m){
        if(m.metal.isEmpty())return;
        m.metal.sort((a,b)->Double.compare(b.excessR,a.excessR));
        if(m.outcome==AlignmentDecision.Outcome.DEFECT){m.secondary.addAll(m.metal);return;}
        m.outcome=AlignmentDecision.Outcome.DEFECT;m.primary=m.metal.get(0);
        for(int k=1;k<m.metal.size();k++)m.secondary.add(m.metal.get(k));
        m.reason="";
    }

    /** Note: the pixel term falls as the dial radius in pixels grows. It assumes those pixels carry real detail; an
     *  enlarged (resampled) photo is not detected and would be judged as a sharper one (known limitation, documented). */
    static double sigmaRadial(Map<String,Double> extra,double R){
        if(extra==null)return Double.NaN;
        Double a=extra.get("twelve_radial.R"),b=extra.get("twelve_radial.px");
        if(a==null&&b==null)return Double.NaN;
        return Math.max(a==null?0:a,b==null||!(R>0)?0:b/R);
    }

    private static double rotationSign(Alpha99Findings.Measure x,Alpha97TwelveReadout.Result t,Alpha94MarkerMeasurement.Marker mm,ModelReference ref,int hour){
        if(x.name.equals("centreline")&&t!=null&&Double.isFinite(t.centrelineDeg))return t.centrelineDeg<0?-1:1;
        if(mm!=null&&Double.isFinite(mm.rotationDeg)){ModelSpec.Marker sp=mm.spec;
            double nom=sp==null?0:ref.nominal(sp.key+"_rot");return mm.rotationDeg-(Double.isFinite(nom)?nom:0)<0?-1:1;}
        return 1;
    }

    private static String words(AlignmentDecision.Issue i){
        if(i.param==LuminousAlignment.Param.ROTATION)return "rotated "+(i.deviation>0?"clockwise":"anticlockwise");
        return "positioned too "+AlignmentDecision.words(i.screen);
    }
    private static String fmt(double v,String unit,double R){
        return unit.equals("deg")?String.format(Locale.US,"%.1f°",v):String.format(Locale.US,"%.2f%% of the dial radius, about %.1f px",100*v,v*R);
    }

    /** Technical lines for the collapsed panel. */
    static void technical(StringBuilder s,AlignmentDecision.Marker m){
        for(AlignmentDecision.Issue i:m.metal)
            s.append(String.format(Locale.US,"\n  outer metal %s: %s, genuine max %s, allowance %s -> CLEAR",AlignmentDecision.name(i.param),
                    AlignmentAnalysis.fmt(i.param,i.deviation),AlignmentAnalysis.fmt(i.param,i.limit),AlignmentAnalysis.fmt(i.param,i.uncertainty)));
        for(String n:m.metalNotes)s.append("\n  ").append(n);
    }
}
