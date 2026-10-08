package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Alpha99 evidence for the results screen: Alpha98's genuine-reference comparisons (unchanged reference, unchanged
 * measurements), with
 *  1. a per-marker interference gate: every individual marker (12, 6, 9 and each round) must pass
 *     Alpha99MarkerInterference before it can become a finding; a hand crossing or touching it -> not assessed;
 *  2. three evidence states for anything outside the measured genuine range:
 *       WORTH A LOOK   beyond every genuine reference watch, but by no more than K x sigma, where sigma is the
 *                      single-photo measurement uncertainty of that feature (the model's ModelReference: photo-to-photo spread
 *                      of the same genuine watch). Measurement error could account for it.
 *       CLEAR          beyond the genuine maximum by more than K x sigma.
 *     The genuine range itself is not widened: range and uncertainty (both in ModelReference) are separate. A feature without
 *     an uncertainty estimate can be outside the range but never CLEAR.
 *  3. round markers judged one by one and named by hour.
 * Wording compares measurements with genuine watches; it never says genuine / fake.
 */
final class Alpha99Findings {
    enum Status{CLEAR,WORTH,NOT_ASSESSED,WITHIN}
    enum Shape{BATON,TRIANGLE,ROUND,RING,DATE}

    static final String DISCLAIMER="These results compare this photo's measurements with genuine watches. They are not an authenticity verdict.";
    static final String INTERFERENCE_NOTE="Close-ups are shown so you can check for hands, reflections, dust or other interference.";

    /** One measured quantity compared with the genuine reference. */
    /** Measures added in Alpha101 (no Alpha98 counterpart): regression checks against Alpha98 skip them. */
    static final java.util.Set<String> ALPHA101_MEASURES=new java.util.HashSet<>(java.util.Arrays.asList("size","dial size"));
    /** True when the finding is outside only because of Alpha101 measures. */
    static boolean outsideOnlyByNewMeasures(Finding f){
        boolean any=false;
        for(Measure m:f.measures)if(m.outside()){if(!ALPHA101_MEASURES.contains(m.name))return false;any=true;}
        return any;
    }

    static final class Measure {
        final String name;final double value,genuineMax,sigma,k;final int n;final String unit;
        /** short phrase for the tile, e.g. "1.7° CCW" / detail sentence start, e.g. "It is rotated 1.7° anticlockwise". */
        final String shortValue,sentence;
        /** Owner decision 2026-10-08: never a clear finding on its own (e.g. a 12 side angle without a matching
         *  centreline or position change); at most worth a look. */
        boolean capAtWorth;
        /** Why capAtWorth was set; shown in the detail view. */
        String capReason="";
        /** @param k the model's K: clear when the excess exceeds k x sigma (NaN: nothing can be clear). */
        Measure(String name,double value,double genuineMax,double sigma,double k,int n,String unit,String shortValue,String sentence){
            this.name=name;this.value=value;this.genuineMax=genuineMax;this.sigma=sigma;this.k=k;this.n=n;this.unit=unit;this.shortValue=shortValue;this.sentence=sentence;
        }
        boolean outside(){return Double.isFinite(value)&&Double.isFinite(genuineMax)&&Alpha98Findings.beyond(value,genuineMax);}
        double excess(){return value-genuineMax;}
        boolean hasAllowance(){return Double.isFinite(sigma)&&sigma>0&&Double.isFinite(k)&&k>0;}
        double allowance(){return k*sigma;}
        Status status(){
            if(!outside())return Status.WITHIN;
            if(capAtWorth)return Status.WORTH;
            return hasAllowance()&&excess()>allowance()?Status.CLEAR:Status.WORTH;
        }
        /** excess in units of sigma (for ranking); infinite-less when there is no allowance. */
        double strength(){return hasAllowance()?excess()/sigma:0;}
    }

    static final class Finding {
        final String key,title;Status status=Status.WITHIN;
        final List<Measure> measures=new ArrayList<>();
        String reason="",shortReason="";
        /** a not-assessed marker whose close-up shows why (hand / glare): it gets a tile. */
        boolean visual;
        /** set when a whole group was not assessed for one shared reason (e.g. "round markers"): counted once. */
        String group;
        /** a whole-dial measurement shown as a tile but without a badge at one marker (e.g. round lume-plot size). */
        boolean dialWide;
        /** Close-up region in canonical dial units (dial radius 1, 12 at the top) and what outline to draw. */
        double cx,cy,half;Shape shape;int hour;
        /** short name used in the "within" line, e.g. "4" or "date window". */
        final String shortName;
        Finding(String key,String title,String shortName){this.key=key;this.title=title;this.shortName=shortName;}

        Measure strongest(){
            Measure b=null;
            for(Measure m:measures)if(m.outside()&&(b==null||rank(m)>rank(b)))b=m;
            return b;
        }
        private static double rank(Measure m){return (m.status()==Status.CLEAR?1e6:0)+m.strength()+(m.excess()/Math.max(1e-12,Math.abs(m.genuineMax)));}

        String statusLabel(){
            switch(status){case CLEAR:return "CLEAR FINDING";case WORTH:return "WORTH A LOOK";case NOT_ASSESSED:return "NOT ASSESSED";default:return "WITHIN RANGE";}
        }
        /** One short line for the tile. */
        String shortLine(){
            if(status==Status.NOT_ASSESSED)return shortReason;
            if(status==Status.WITHIN)return "within measured genuine range";
            Measure m=strongest();
            return m.shortValue+"; genuine up to "+fmt(m.genuineMax,m.unit);
        }
        /** Detail sentences for the larger evidence view. */
        List<String> detail(){
            List<String> out=new ArrayList<>();
            if(status==Status.NOT_ASSESSED){out.add("Not assessed: "+reason+".");return out;}
            for(Measure m:measures){
                if(!m.outside())continue;
                String s=m.sentence+". The furthest of "+m.n+" genuine reference watches reads "+fmt(m.genuineMax,m.unit)+".";
                if(m.capAtWorth)s+=" "+m.capReason;
                else if(!m.hasAllowance())s+=" No measurement-uncertainty estimate exists for this feature, so it is not rated as a clear finding.";
                else if(m.status()==Status.CLEAR)s+=String.format(Locale.US," The excess (%s) is more than %.0f times the photo-to-photo spread measured on genuine watches (%s), so measurement error alone is unlikely to explain it.",
                        fmt(m.excess(),m.unit),m.k,fmt(m.sigma,m.unit));
                else s+=String.format(Locale.US," The excess (%s) is within %.0f times the photo-to-photo spread measured on genuine watches (%s), so photo or measurement error could account for it.",
                        fmt(m.excess(),m.unit),m.k,fmt(m.sigma,m.unit));
                out.add(s);
            }
            for(Measure m:measures)if(!m.outside()&&Double.isFinite(m.value))
                out.add(m.sentence+" - within the measured genuine range (furthest genuine "+fmt(m.genuineMax,m.unit)+").");
            return out;
        }
        String text(){
            if(status==Status.NOT_ASSESSED)return title+": not assessed - "+reason+".";
            if(status==Status.WITHIN)return title+": within the measured genuine range.";
            return title+": "+statusLabel()+" - "+shortLine();
        }
    }

    static final class Summary {
        final List<Finding> all=new ArrayList<>();
        List<Finding> with(Status st){List<Finding> o=new ArrayList<>();for(Finding f:all)if(f.status==st)o.add(f);return o;}
        List<Finding> clear(){return with(Status.CLEAR);}
        List<Finding> worth(){return with(Status.WORTH);}
        List<Finding> within(){return with(Status.WITHIN);}
        List<Finding> notAssessed(){return with(Status.NOT_ASSESSED);}
        /** Tiles: clear findings, then worth a look, then not-assessed markers whose close-up shows the reason. */
        List<Finding> tiles(){
            List<Finding> o=new ArrayList<>(clear());o.addAll(worth());
            for(Finding f:notAssessed())if(f.visual)o.add(f);
            return o;
        }
        List<String> headlineLines(){
            List<String> h=new ArrayList<>();int c=clear().size(),w=worth().size(),na=notAssessedCount();
            if(c>0)h.add(c+" clear alignment finding"+(c==1?"":"s"));
            if(w>0)h.add(w+(c>0?" other":"")+(w==1?" measurement is":" measurements are")+" worth a look");
            if(c==0&&w==0)h.add("No measured feature is outside the measured genuine range");
            if(na>0)h.add(na+(na==1?" feature":" features")+" could not be assessed");
            return h;
        }
        String headline(){return String.join("\n",headlineLines());}
        /** Not-assessed features, a group withheld for one shared reason counting once. */
        int notAssessedCount(){
            java.util.Set<String> groups=new java.util.HashSet<>();int n=0;
            for(Finding f:notAssessed()){if(f.group==null)n++;else if(groups.add(f.group))n++;}
            return n;
        }
        String withinLine(){
            StringBuilder b=new StringBuilder();
            for(Finding f:within()){if(b.length()>0)b.append(", ");b.append(f.shortName);}
            return b.length()==0?"":"Within measured genuine range: "+b;
        }
        /** Not-assessed features without a tile, grouped by reason. */
        String notAssessedLine(){
            Map<String,List<String>> g=new java.util.LinkedHashMap<>();
            for(Finding f:notAssessed())if(!f.visual){List<String> l=g.computeIfAbsent(f.shortReason,k->new ArrayList<>());
                String name=f.group!=null?f.group:f.shortName;if(!l.contains(name))l.add(name);}
            StringBuilder b=new StringBuilder();
            for(Map.Entry<String,List<String>> e:g.entrySet()){if(b.length()>0)b.append(" · ");b.append(String.join(", ",e.getValue())).append(" (").append(e.getKey()).append(")");}
            return b.length()==0?"":"Not assessed: "+b;
        }
    }

    private Alpha99Findings(){}

    /**
     * @param checks Alpha99MarkerInterference per hour (12 and every Alpha94 hour). Null or missing -> those markers are
     *               not assessed (fail closed).
     */
    static Summary build(Alpha94MarkerMeasurement.Report r,Alpha98DateWindow.Result date,Map<Integer,Alpha99MarkerInterference.Check> checks,
                         ModelSpec model,ModelReference ref){
        Summary s=new Summary();
        double R=r==null?Double.NaN:r.dialRadiusPx;
        for(ModelSpec.Marker mk:model.markers)if(mk.shape==ModelSpec.Shape.TRIANGLE)s.all.add(twelve(r,check(checks,mk.hour),R,mk,ref));
        for(ModelSpec.Marker mk:model.withShape(ModelSpec.Shape.BATON))s.all.add(baton(r,mk,R,check(checks,mk.hour),ref));
        s.all.addAll(rounds(r,R,checks,model,ref));
        if(!model.withShape(ModelSpec.Shape.ROUND).isEmpty())s.all.add(roundsSize(r,R,checks,model,ref));
        s.all.add(ring(r,R,ref));
        if(model.date!=null)s.all.add(date(date,R,r!=null&&r.ring!=null&&r.ring.usable,ref,model.date));
        return s;
    }

    /** The model has no genuine reference (or no uncertainty) for this feature yet: never a finding. */
    private static boolean noReference(Finding f){
        f.status=Status.NOT_ASSESSED;f.reason=Alpha98Findings.NO_REFERENCE;f.shortReason="no genuine reference yet";f.visual=false;return true;
    }
    /** Family sigma in the photo's terms: max(sigma_R, sigma_px / R) for positions, max(sigma_deg, sigma_degR / R) for
     *  angles; NaN when the model has no uncertainty for the family. */
    static double sigmaPos(ModelReference ref,String fam,double R){return sigmaR(ref.sigma(fam,"R"),ref.sigma(fam,"px"),R);}
    static double sigmaAng(ModelReference ref,String fam,double R){return sigmaDeg(ref.sigma(fam,"deg"),ref.sigma(fam,"degR"),R);}

    private static Alpha99MarkerInterference.Check check(Map<Integer,Alpha99MarkerInterference.Check> c,int hour){return c==null?null:c.get(hour);}

    /** Interference gate: null -> withheld (could not be checked); not clean -> withheld with its reason. */
    private static boolean gate(Finding f,Alpha99MarkerInterference.Check c){
        if(c!=null&&c.clean)return true;
        f.status=Status.NOT_ASSESSED;f.visual=true;
        if(c==null){f.reason="its surroundings could not be checked for hands";f.shortReason="not checked";f.visual=false;}
        else if(Alpha99MarkerInterference.GLARE.equals(c.reason)){f.reason="reflection or glare around this marker";f.shortReason="glare around marker";}
        else if(Alpha99MarkerInterference.HAND.equals(c.reason)){f.reason="a hand crosses or touches this marker";f.shortReason="hand crosses marker";}
        else{f.reason=c.reason;f.shortReason="not checked";f.visual=false;}
        return false;
    }

    private static void withheldByMeasurement(Finding f,String raw){
        f.status=Status.NOT_ASSESSED;f.reason=Alpha98Findings.reasonFor(raw);
        String s=raw==null?"":raw.toLowerCase(Locale.US);
        if(s.contains("hand")||s.contains("occlu")){f.reason="a hand crosses or touches this marker";f.shortReason="hand crosses marker";f.visual=true;}
        else{f.shortReason="edge not clear";f.visual=false;}
    }

    static double sigmaR(double sR,double sPx,double R){return Double.isFinite(R)&&R>0&&Double.isFinite(sPx)?Math.max(sR,sPx/R):sR;}
    /** Angles: an angle's error is a pixel edge error over the feature's length (proportional to R), so a small photo
     *  gets at least sigma_degR / R. */
    static double sigmaDeg(double sDeg,double sDegR,double R){return Double.isFinite(R)&&R>0&&Double.isFinite(sDegR)?Math.max(sDeg,sDegR/R):sDeg;}

    // ------------------------------------------------------------------ features
    static Finding twelve(Alpha94MarkerMeasurement.Report r,Alpha99MarkerInterference.Check c,double R,ModelSpec.Marker spec,ModelReference ref){
        int hour=spec.hour;
        Finding f=new Finding(spec.key,hour+" o'clock",""+hour);
        double a=Math.toRadians(hour*30.0);f.cx=hour%12==0?0:Math.sin(a)*0.75;f.cy=hour%12==0?-0.75:-Math.cos(a)*0.75;
        f.half=0.22;f.shape=Shape.TRIANGLE;f.hour=hour;
        if(ref.triangle==null&&noReference(f))return f;
        if(!gate(f,c))return f;
        Alpha97TwelveReadout.Result t=Alpha97TwelveReadout.from(r,ref);
        if(!t.usable){withheldByMeasurement(f,t.reason);return f;}
        int n=ref.triangle.nWatches;String fam=spec.key;double k=ref.kSigma;
        f.measures.add(new Measure("centreline",Math.abs(t.centrelineDeg),Alpha98Findings.max(ref.triangle.centrelineDeg),
                sigmaAng(ref,fam+"_centreline",R),k,n,"deg",
                String.format(Locale.US,"tilted %.1f° %s",Math.abs(t.centrelineDeg),Alpha98Findings.cw(t.centrelineDeg)),
                String.format(Locale.US,"It points %.1f° %s of the genuine direction",Math.abs(t.centrelineDeg),Alpha98Findings.cw(t.centrelineDeg))));
        boolean left=Math.abs(t.leftSideDeg)>=Math.abs(t.rightSideDeg);double sd=left?t.leftSideDeg:t.rightSideDeg;
        f.measures.add(new Measure("sides",t.sidesDeg,Alpha98Findings.max(ref.triangle.sidesDeg),
                sigmaAng(ref,fam+"_sides",R),k,n,"deg",
                String.format(Locale.US,"%s side angled %.1f° %s",left?"left":"right",Math.abs(sd),Alpha98Findings.cw(sd)),
                String.format(Locale.US,"Its %s side is angled %.1f° %s",left?"left":"right",Math.abs(sd),Alpha98Findings.cw(sd))));
        f.measures.add(new Measure("lateral",Math.abs(t.lateralR),Alpha98Findings.max(ref.triangle.lateralR),
                sigmaPos(ref,fam+"_lateral",R),k,n,"R",
                String.format(Locale.US,"shifted %s by %.2f%% of the dial",t.lateralPx<0?"left":"right",100*Math.abs(t.lateralR)),
                String.format(Locale.US,"It sits %.1f px to the %s (%.2f%% of the dial radius)",Math.abs(t.lateralPx),t.lateralPx<0?"left":"right",100*Math.abs(t.lateralR))));
        // Alpha98 rule for the 12: outside when no genuine watch reads at least as far (Alpha97TwelveReadout counts)
        if(t.atLeastCentreline>0)neutralise(f,"centreline");
        if(t.atLeastSides>0)neutralise(f,"sides");
        if(t.atLeastLateral>0)neutralise(f,"lateral");
        // side angle only (centreline and position within range): worth a look at most (owner decision 2026-10-08; a
        // genuine SWE studio photo read its right side 4.1 deg off with centreline and position in range)
        boolean corroborated=false;
        for(Measure x:f.measures)if(!x.name.equals("sides")&&x.outside())corroborated=true;
        if(!corroborated)for(Measure x:f.measures)if(x.name.equals("sides")){x.capAtWorth=true;
            x.capReason="Only the side angle reads outside: the triangle's direction and position are within the genuine range."
                    +" Studio lighting can move a side edge without the marker moving, so a side angle on its own is shown as worth a look, not a clear finding.";}
        // Alpha101 edge consistency: a real rotation turns both sides together; when the two sides disagree by more than
        // genuine triangles ever do, one edge is affected by lighting or blur, and every angle read from those edges
        // (centreline, sides) is at most worth a look. Position (lateral) is unaffected.
        double agree=ref.limit(fam+"_sides_agreement"),dis=Math.abs(t.leftSideDeg-t.rightSideDeg);
        if(Double.isFinite(agree)&&dis>agree)for(Measure x:f.measures)if(x.name.equals("sides")||x.name.equals("centreline")){
            x.capAtWorth=true;
            x.capReason=String.format(Locale.US,"The triangle's two sides disagree by %.1f° (on genuine watches they agree within %.1f°), which points to lighting or blur on one edge rather than a turned marker, so its angles are shown as worth a look, not a clear finding.",dis,agree);}
        settle(f);return f;
    }

    /** Replace a measure with one that is never outside (keeps the 12 on its Alpha98 'at least as far' counts). */
    private static void neutralise(Finding f,String name){
        for(int i=0;i<f.measures.size();i++){Measure m=f.measures.get(i);
            if(m.name.equals(name)&&m.outside())f.measures.set(i,new Measure(m.name,Math.min(m.value,m.genuineMax),m.genuineMax,m.sigma,m.k,m.n,m.unit,m.shortValue,m.sentence));}
    }

    static Finding baton(Alpha94MarkerMeasurement.Report r,ModelSpec.Marker spec,double R,Alpha99MarkerInterference.Check c,ModelReference ref){
        int hour=spec.hour;String key=spec.key;
        Finding f=new Finding(key,hour+" o'clock",""+hour);
        double a=Math.toRadians(hour*30.0);f.cx=Math.sin(a)*spec.centreR;f.cy=-Math.cos(a)*spec.centreR;
        f.half=0.22;f.shape=Shape.BATON;f.hour=hour;
        if((!ref.has(key+"_rot")||!ref.has(key+"_off")||!Double.isFinite(ref.nominal(key+"_rot")))&&noReference(f))return f;
        if(!gate(f,c))return f;
        Alpha94MarkerMeasurement.Marker m=r==null?null:r.atHour(hour);
        if(m==null||!m.usable){withheldByMeasurement(f,m==null?"unavailable":m.reason);return f;}
        double[] rotRef=ref.far(key+"_rot");
        double[] offRef=ref.far(key+"_off");
        double nom=ref.nominal(key+"_rot");
        double sRot=sigmaAng(ref,key+"_rot",R);
        double sOff=sigmaPos(ref,key+"_off",R);double k=ref.kSigma;
        double rot=m.rotationDeg-nom;
        f.measures.add(new Measure("rotation",Math.abs(rot),Alpha98Findings.max(rotRef),sRot,k,rotRef.length,"deg",
                String.format(Locale.US,"rotated %.1f° %s",Math.abs(rot),Alpha98Findings.cw(rot)),
                String.format(Locale.US,"It is rotated %.1f° %s",Math.abs(rot),Alpha98Findings.cw(rot))));
        double off=m.localOffsetPx/R;
        f.measures.add(new Measure("position",off,Alpha98Findings.max(offRef),sOff,k,offRef.length,"R",
                String.format(Locale.US,"shifted %s by %.2f%% of the dial",towards(m),100*off),
                String.format(Locale.US,"It sits %.1f px out of place relative to the other markers (%.2f%% of the dial radius, mostly %s)",
                        m.localOffsetPx,100*off,Alpha98Findings.direction(m))));
        settle(f);return f;
    }

    static List<Finding> rounds(Alpha94MarkerMeasurement.Report r,double R,Map<Integer,Alpha99MarkerInterference.Check> checks,
                                ModelSpec model,ModelReference ref){
        List<Finding> out=new ArrayList<>();
        int usable=0;
        if(r!=null)for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind)&&m.usable&&Double.isFinite(m.localOffsetPx))usable++;
        boolean hasRef=ref.has("rounds_off");
        int n=Alpha98Findings.matchedCount(ref.radius("rounds_off"),R);
        double lim=Alpha98Findings.matchedMax(ref.far("rounds_off"),ref.radius("rounds_off"),R);
        double sOff=sigmaPos(ref,"rounds_off",R);
        int sizeN=ref.has("round_size_rel")?Alpha98Findings.matchedCount(ref.radius("round_size_rel"),R):0;
        double sizeLim=Alpha98Findings.matchedMax(ref.far("round_size_rel"),ref.radius("round_size_rel"),R);
        for(ModelSpec.Marker mk:model.withShape(ModelSpec.Shape.ROUND)){
            int h=mk.hour;
            Finding f=new Finding("round"+h,h+" o'clock",""+h);
            double a=Math.toRadians(h*30.0);f.cx=Math.sin(a)*mk.centreR;f.cy=-Math.cos(a)*mk.centreR;
            f.half=0.17;f.shape=Shape.ROUND;f.hour=h;out.add(f);
            if(!hasRef){noReference(f);f.group="round markers";continue;}
            if(r==null||usable<5){f.status=Status.NOT_ASSESSED;f.reason="too few round markers could be measured cleanly";f.shortReason="too few clean markers";f.group="round markers";continue;}
            if(n<Alpha98Findings.MIN_MATCHED){f.status=Status.NOT_ASSESSED;f.reason="the photo's resolution is too low to compare round markers with genuine photos";f.shortReason="resolution too low";f.group="round markers";continue;}
            if(!gate(f,check(checks,h)))continue;
            Alpha94MarkerMeasurement.Marker m=r.atHour(h);
            if(m==null||!m.usable||!Double.isFinite(m.localOffsetPx)){withheldByMeasurement(f,m==null?"unavailable":m.reason);continue;}
            double off=m.localOffsetPx/R;
            f.measures.add(new Measure("position",off,lim,sOff,ref.kSigma,n,"R",
                    String.format(Locale.US,"shifted %s by %.2f%% of the dial",towards(m),100*off),
                    String.format(Locale.US,"The %d o'clock marker sits %.1f px out of place relative to the other markers (%.2f%% of the dial radius, mostly %s); compared with genuine photos of similar or lower resolution",
                            h,m.localOffsetPx,100*off,Alpha98Findings.direction(m))));
            // Alpha101: this lume plot's size against the other round plots on the same dial (lighting and blur cancel)
            double med=cleanRoundSizeMedian(r,R,checks,model);
            if(Double.isFinite(med)&&Double.isFinite(m.radiusErrPx)&&sizeN>=Alpha98Findings.MIN_MATCHED){
                double rel=m.radiusErrPx/R-med;
                f.measures.add(new Measure("size",Math.abs(rel),sizeLim,sigmaPos(ref,"round_size_rel",R),ref.kSigma,sizeN,"R",
                        String.format(Locale.US,"plot %s than the others by %.2f%% of the dial",rel>=0?"larger":"smaller",100*Math.abs(rel)),
                        String.format(Locale.US,"Its lume plot is %s than the other round plots on this dial by %.2f%% of the dial radius; compared with genuine photos of similar or lower resolution",
                                rel>=0?"larger":"smaller",100*Math.abs(rel))));
            }
            settle(f);
        }
        return out;
    }

    /** Median size (fitted radius error / R) of the round markers that are measured and clear of hands; NaN below 5. */
    static double cleanRoundSizeMedian(Alpha94MarkerMeasurement.Report r,double R,Map<Integer,Alpha99MarkerInterference.Check> checks,ModelSpec model){
        if(r==null||!(R>0))return Double.NaN;
        List<Double> v=new ArrayList<>();
        for(ModelSpec.Marker mk:model.withShape(ModelSpec.Shape.ROUND)){
            Alpha94MarkerMeasurement.Marker m=r.atHour(mk.hour);Alpha99MarkerInterference.Check c=check(checks,mk.hour);
            if(m!=null&&m.usable&&Double.isFinite(m.radiusErrPx)&&c!=null&&c.clean)v.add(m.radiusErrPx/R);
        }
        if(v.size()<5)return Double.NaN;
        java.util.Collections.sort(v);int n=v.size();
        return n%2==1?v.get(n/2):0.5*(v.get(n/2-1)+v.get(n/2));
    }

    /** Alpha101: overall size of the round lume plots (all clean rounds together) against genuine dials. */
    static Finding roundsSize(Alpha94MarkerMeasurement.Report r,double R,Map<Integer,Alpha99MarkerInterference.Check> checks,ModelSpec model,ModelReference ref){
        Finding f=new Finding("rounds_size","Round lume plots","round plot size");f.shape=Shape.ROUND;f.half=0.17;f.dialWide=true;
        ModelSpec.Marker first=model.withShape(ModelSpec.Shape.ROUND).get(0);
        double a=Math.toRadians(first.hour*30.0);f.hour=first.hour;f.cx=Math.sin(a)*first.centreR;f.cy=-Math.cos(a)*first.centreR;
        if((!ref.has("rounds_size")||!Double.isFinite(ref.nominal("rounds_size")))&&noReference(f)){f.group="round markers";return f;}
        double med=cleanRoundSizeMedian(r,R,checks,model);
        if(!Double.isFinite(med)){f.status=Status.NOT_ASSESSED;f.reason="too few round markers could be measured cleanly";f.shortReason="too few clean markers";f.group="round markers";return f;}
        int n=Alpha98Findings.matchedCount(ref.radius("rounds_size"),R);
        if(n<Alpha98Findings.MIN_MATCHED){f.status=Status.NOT_ASSESSED;f.reason="the photo's resolution is too low to compare round markers with genuine photos";f.shortReason="resolution too low";f.group="round markers";return f;}
        double d=med-ref.nominal("rounds_size");
        f.measures.add(new Measure("dial size",Math.abs(d),Alpha98Findings.matchedMax(ref.far("rounds_size"),ref.radius("rounds_size"),R),
                sigmaPos(ref,"rounds_size",R),ref.kSigma,n,"R",
                String.format(Locale.US,"all round plots %s by %.2f%% of the dial",d>=0?"larger":"smaller",100*Math.abs(d)),
                String.format(Locale.US,"Taken together, the round lume plots are %s than on genuine dials by %.2f%% of the dial radius; compared with genuine photos of similar or lower resolution",
                        d>=0?"larger":"smaller",100*Math.abs(d))));
        settle(f);return f;
    }

    static Finding ring(Alpha94MarkerMeasurement.Report r,double R,ModelReference ref){
        Finding f=new Finding("ring","Dial marker ring","marker ring");f.shape=Shape.RING;f.cx=0;f.cy=0;f.half=1.05;
        if((!ref.has("ring_rot")||!Double.isFinite(ref.nominal("ring_rot")))&&noReference(f))return f;
        Alpha94MarkerMeasurement.Ring g=r==null?null:r.ring;
        if(g==null||!g.usable){f.status=Status.NOT_ASSESSED;f.reason="too few markers could be measured cleanly";f.shortReason="too few clean markers";return f;}
        double rot=g.rotationDeg-ref.nominal("ring_rot");
        f.measures.add(new Measure("rotation",Math.abs(rot),Alpha98Findings.max(ref.far("ring_rot")),sigmaAng(ref,"ring_rot",R),ref.kSigma,
                ref.far("ring_rot").length,"deg",String.format(Locale.US,"markers as a set turned %.2f° %s",Math.abs(rot),Alpha98Findings.cw(rot)),
                String.format(Locale.US,"Taken together, the hour markers are turned %.2f° %s relative to the printed minute track (a whole-dial measurement: it does not mean each marker is rotated)",
                        Math.abs(rot),Alpha98Findings.cw(rot))));
        int n=Alpha98Findings.matchedCount(ref.radius("ring_shift"),R);
        if(n>=Alpha98Findings.MIN_MATCHED){double lim=Alpha98Findings.matchedMax(ref.far("ring_shift"),ref.radius("ring_shift"),R);double sh=g.shiftPx/R;
            f.measures.add(new Measure("shift",sh,lim,sigmaPos(ref,"ring_shift",R),ref.kSigma,n,"R",
                    String.format(Locale.US,"markers as a set off-centre by %.2f%% of the dial",100*sh),
                    String.format(Locale.US,"Taken together, the hour markers are off-centre by %.1f px (%.2f%% of the dial radius); compared with genuine photos of similar or lower resolution",g.shiftPx,100*sh)));}
        settle(f);return f;
    }

    /**
     * @param ringConfirmed the marker ring was measured: the dial's orientation on the pose is cross-checked by the
     *                      markers. The date tilt is read relative to the pose, so without that check a mis-registered
     *                      pose would read as a tilted window; fail closed.
     */
    static Finding date(Alpha98DateWindow.Result d,double R,boolean ringConfirmed,ModelReference ref,ModelSpec.DateWindow dw){
        Finding f=new Finding("date","Date window","date window");f.shape=Shape.DATE;f.half=0.30;
        if(!ref.has("date_tilt")||!Double.isFinite(ref.nominal("date_tilt"))){noReference(f);
            f.cx=d!=null&&Double.isFinite(d.cx)?d.cx:dw.expX;f.cy=d!=null&&Double.isFinite(d.cy)?d.cy:dw.expY;return f;}
        if(d!=null&&d.usable&&!ringConfirmed){f.status=Status.NOT_ASSESSED;f.reason="the dial's orientation could not be confirmed from the hour markers";
            f.shortReason="orientation not confirmed";f.cx=d.cx;f.cy=d.cy;return f;}
        if(d==null||!d.usable){f.status=Status.NOT_ASSESSED;f.reason=Alpha98Findings.dateReason(d==null?"":d.reason);f.shortReason=shortDate(f.reason);
            f.cx=d!=null&&Double.isFinite(d.cx)?d.cx:dw.expX;f.cy=d!=null&&Double.isFinite(d.cy)?d.cy:dw.expY;
            f.visual=f.reason.startsWith("glare")||f.reason.startsWith("a hand");return f;}
        f.cx=d.cx;f.cy=d.cy;
        double t=d.windowTiltDeg-ref.nominal("date_tilt");
        f.measures.add(new Measure("tilt",Math.abs(t),Alpha98Findings.max(ref.far("date_tilt")),sigmaAng(ref,"date_tilt",R),ref.kSigma,
                ref.far("date_tilt").length,"deg",String.format(Locale.US,"tilted %.1f° %s",Math.abs(t),Alpha98Findings.cw(t)),
                String.format(Locale.US,"It is tilted %.1f° %s relative to the dial",Math.abs(t),Alpha98Findings.cw(t))));
        settle(f);return f;
    }

    private static String shortDate(String reason){
        if(reason.startsWith("glare"))return "glare on magnifier";
        if(reason.startsWith("a hand"))return "hand or date change";
        if(reason.startsWith("the dial"))return "dial not located";
        return "window not found";
    }

    /** Finding status = strongest measure: any CLEAR -> CLEAR, else any outside -> WORTH, else WITHIN. */
    static void settle(Finding f){
        Status s=Status.WITHIN;
        for(Measure m:f.measures){Status x=m.status();if(x==Status.CLEAR)s=Status.CLEAR;else if(x==Status.WORTH&&s!=Status.CLEAR)s=Status.WORTH;}
        f.status=s;
    }

    /** QC-style direction of a marker's local offset (radial + = outward, tangential + = clockwise): towards its minute
     *  mark / the centre, or towards the neighbouring hour, e.g. "towards the 3"; both when neither dominates. */
    static String towards(Alpha94MarkerMeasurement.Marker m){
        double rd=m.localRadialPx,tg=m.localTangentialPx;
        if(!Double.isFinite(rd)||!Double.isFinite(tg))return "out of place";
        String radial=rd>=0?"towards its minute mark":"towards the centre";
        int next=tg>=0?m.hour%12+1:(m.hour+10)%12+1;
        String tangential="towards the "+next;
        double a=Math.abs(rd),b=Math.abs(tg);
        if(Math.min(a,b)>=0.5*Math.max(a,b))return radial+" and the "+next;
        return a>=b?radial:tangential;
    }

    static String fmt(double v,String unit){
        if("deg".equals(unit))return String.format(Locale.US,Math.abs(v)<0.095?"%.2f°":"%.1f°",v);
        if("R".equals(unit))return String.format(Locale.US,"%.2f%%",100*v);
        return String.format(Locale.US,"%.3g",v);
    }
}
