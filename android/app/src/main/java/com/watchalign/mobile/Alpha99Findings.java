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
 *  4. (Alpha103, QC guardrails 3 and 4) practical significance and photo trust:
 *       TOO SMALL TO SEE  outside the measured genuine range, but by less than the eye can pick out on the watch
 *                         (VISIBLE_FRACTION of the marker's width for positions and sizes, VISIBLE_DEG for angles). Listed,
 *                         never headlined as a deviation and never a clear finding, however many sigma it is.
 *       small photos      a feature judged against shrunk-genuine rows (photo below the full-resolution coverage) is at
 *                         most worth a look: shrinking genuine photos to that size alone moves markers by as much as the
 *                         allowance (sub124060 shrink data: p99 0.53% of the dial at 150 px).
 *       opposite markers  opposite markers (3/9, 1/7, ...) displaced the same way in the picture: the shared shift comes
 *                         from the photo (angle, lighting), not two separately misplaced markers -> at most worth a look.
 *       glare             a round marker whose size reads off by at least half its position offset: one-sided glare or
 *                         bleed on its metal surround moves both together -> its position is at most worth a look.
 * Wording compares measurements with genuine watches; it never says genuine / fake.
 */
final class Alpha99Findings {
    enum Status{CLEAR,WORTH,MINOR,NOT_ASSESSED,WITHIN}

    /** Alpha103 visibility bar (QC guardrails 4, a presentation rule, not a genuine limit): a position or size difference
     *  below this fraction of the marker's own width, or an angle below VISIBLE_DEG, is too small to see on the watch.
     *  Set from what a careful human QC can pick out (a 'super slight' baton tilt of ~1 deg is reported by eye), never from
     *  replica results. */
    static final double VISIBLE_FRACTION=0.05,VISIBLE_DEG=0.75;
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
        /** Alpha103: below this value the difference is too small to see (MINOR); NaN = no bar. */
        double visibleBar=Double.NaN;
        /** @param k the model's K: clear when the excess exceeds k x sigma (NaN: nothing can be clear). */
        Measure(String name,double value,double genuineMax,double sigma,double k,int n,String unit,String shortValue,String sentence){
            this.name=name;this.value=value;this.genuineMax=genuineMax;this.sigma=sigma;this.k=k;this.n=n;this.unit=unit;this.shortValue=shortValue;this.sentence=sentence;
        }
        boolean outside(){return Double.isFinite(value)&&Double.isFinite(genuineMax)&&Alpha98Findings.beyond(value,genuineMax);}
        double excess(){return value-genuineMax;}
        boolean hasAllowance(){return Double.isFinite(sigma)&&sigma>0&&Double.isFinite(k)&&k>0;}
        double allowance(){return k*sigma;}
        boolean belowVisible(){return Double.isFinite(visibleBar)&&value<visibleBar;}
        /** Caps at worth a look, keeping the first reason given. */
        void cap(String reason){if(!capAtWorth){capAtWorth=true;capReason=reason;}}
        Status status(){
            if(!outside())return Status.WITHIN;
            if(belowVisible())return Status.MINOR;
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
        /** extra plain-English lines for the detail view (e.g. a measure withheld for resolution). */
        final List<String> notes=new ArrayList<>();
        /** a part of this feature that was not assessed (e.g. "position"), with its short reason; null when complete. */
        String partNotAssessed,partReason;
        /** the part that was assessed, named in the "within" line (default "rotation", the baton case). */
        String partAssessed;
        /** short name used in the "within" line, e.g. "4" or "date window". */
        final String shortName;
        Finding(String key,String title,String shortName){this.key=key;this.title=title;this.shortName=shortName;}

        Measure strongest(){
            Measure b=null;
            for(Measure m:measures)if(m.outside()&&(b==null||rank(m)>rank(b)))b=m;
            return b;
        }
        private static double rank(Measure m){Status st=m.status();
            return (st==Status.CLEAR?1e6:st==Status.WORTH?1e3:0)+m.strength()+(m.excess()/Math.max(1e-12,Math.abs(m.genuineMax)));}

        String statusLabel(){
            switch(status){case CLEAR:return "CLEAR FINDING";case WORTH:return "WORTH A LOOK";case MINOR:return "TOO SMALL TO SEE";case NOT_ASSESSED:return "NOT ASSESSED";default:return "WITHIN RANGE";}
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
                if(m.belowVisible())s+=" That is less than the eye can pick out on the watch (about "+fmt(m.visibleBar,m.unit)+" here), so it is listed as too small to see, not as a deviation.";
                else if(m.capAtWorth)s+=" "+m.capReason;
                else if(!m.hasAllowance())s+=" No measurement-uncertainty estimate exists for this feature, so it is not rated as a clear finding.";
                else if(m.status()==Status.CLEAR)s+=String.format(Locale.US," The excess (%s) is more than %.0f times the photo-to-photo spread measured on genuine watches (%s), so measurement error alone is unlikely to explain it.",
                        fmt(m.excess(),m.unit),m.k,fmt(m.sigma,m.unit));
                else s+=String.format(Locale.US," The excess (%s) is within %.0f times the photo-to-photo spread measured on genuine watches (%s), so photo or measurement error could account for it.",
                        fmt(m.excess(),m.unit),m.k,fmt(m.sigma,m.unit));
                out.add(s);
            }
            for(Measure m:measures)if(!m.outside()&&Double.isFinite(m.value))
                out.add(m.sentence+" - within the measured genuine range (furthest genuine "+fmt(m.genuineMax,m.unit)+").");
            out.addAll(notes);
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
        List<Finding> minor(){return with(Status.MINOR);}
        List<Finding> within(){return with(Status.WITHIN);}
        List<Finding> notAssessed(){return with(Status.NOT_ASSESSED);}
        /** Tiles: clear findings, then worth a look, then not-assessed markers whose close-up shows the reason. Alpha105:
         *  differences too small to see are shown as within (owner, 2026-10-09: "if it's too small to see make it a pass");
         *  their measured values stay in the technical details. */
        List<Finding> tiles(){
            List<Finding> o=new ArrayList<>(clear());o.addAll(worth());
            for(Finding f:notAssessed())if(f.visual)o.add(f);
            return o;
        }
        List<String> headlineLines(){
            List<String> h=new ArrayList<>();int c=clear().size(),w=worth().size(),mi=minor().size(),na=notAssessedCount();
            if(c>0)h.add(c+" clear alignment finding"+(c==1?"":"s")+": "+names(clear()));
            if(w>0)h.add(w+(c>0?" other":"")+(w==1?" measurement is":" measurements are")+" worth a look: "+names(worth()));
            if(c==0&&w==0)h.add(mi>0?"No visible deviation from the genuine watches measured":"No measured feature is outside the measured genuine range");
            // QC guardrails 4: borderline readings alone are not escalated; a repeat on another photo is the stronger evidence
            if(c==0&&w>0)h.add("Another clean, straight-on photo would show whether "+(w==1?"it repeats":"these repeat"));
            if(na>0)h.add(na+(na==1?" feature":" features")+" could not be assessed");
            return h;
        }
        String headline(){return String.join("\n",headlineLines());}
        private static String names(List<Finding> fs){
            StringBuilder b=new StringBuilder();
            for(Finding f:fs){if(b.length()>0)b.append(", ");b.append(f.shortName);}
            return b.toString();
        }
        /** Not-assessed features, a group withheld for one shared reason counting once. */
        int notAssessedCount(){
            java.util.Set<String> groups=new java.util.HashSet<>();int n=0;
            for(Finding f:notAssessed()){if(f.group==null)n++;else if(groups.add(f.group))n++;}
            return n;
        }
        String withinLine(){
            StringBuilder b=new StringBuilder();
            List<Finding> w=new ArrayList<>();for(Finding f:all)if(f.status==Status.WITHIN||f.status==Status.MINOR)w.add(f);
            for(Finding f:w){if(b.length()>0)b.append(", ");b.append(f.shortName);if(f.partNotAssessed!=null)b.append(" ").append(f.partAssessed!=null?f.partAssessed:"rotation");}
            return b.length()==0?"":"Within measured genuine range: "+b;
        }
        /** Not-assessed features without a tile, grouped by reason. */
        String notAssessedLine(){
            Map<String,List<String>> g=new java.util.LinkedHashMap<>();
            for(Finding f:notAssessed())if(!f.visual){List<String> l=g.computeIfAbsent(f.shortReason,k->new ArrayList<>());
                String name=f.group!=null?f.group:f.shortName;if(!l.contains(name))l.add(name);}
            for(Finding f:all)if(f.partNotAssessed!=null&&f.status!=Status.NOT_ASSESSED)
                g.computeIfAbsent(f.partReason,k->new ArrayList<>()).add(f.shortName+" "+f.partNotAssessed);
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
        return build(r,date,checks,null,model,ref);
    }
    /** @param partial Alpha102: round markers withheld because a hand is near (not touching) re-measured from the part
     *                 of their outline away from the hand (Alpha99Pipeline.partialRounds); null = none. */
    static Summary build(Alpha94MarkerMeasurement.Report r,Alpha98DateWindow.Result date,Map<Integer,Alpha99MarkerInterference.Check> checks,
                         Map<Integer,Alpha94MarkerMeasurement.Marker> partial,ModelSpec model,ModelReference ref){
        Summary s=new Summary();
        double R=r==null?Double.NaN:r.dialRadiusPx;
        for(ModelSpec.Marker mk:model.markers)if(mk.shape==ModelSpec.Shape.TRIANGLE)s.all.add(twelve(r,check(checks,mk.hour),R,mk,ref));
        for(ModelSpec.Marker mk:model.withShape(ModelSpec.Shape.BATON))s.all.add(baton(r,mk,R,check(checks,mk.hour),ref,model.resolutionMatched.contains(mk.key+"_off")));
        s.all.addAll(rounds(r,R,checks,partial,model,ref));
        if(!model.withShape(ModelSpec.Shape.ROUND).isEmpty())s.all.add(roundsSize(r,R,checks,model,ref));
        s.all.add(ring(r,R,ref,model));
        if(model.date!=null)s.all.add(date(date,R,r!=null&&r.ring!=null&&r.ring.usable,ref,model.date));
        oppositeMarkers(s,r);
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
        // Reached only after the hand / glare check cleared this marker: the measurement's own outline test failed (part of
        // the outline does not match cleanly). That can be a hand the hand check missed, glare, a reflection or the photo,
        // so no cause is named; the close-up shows it. A hand is named only when the hand check detects one (guardrails 3/10).
        if(s.contains("hand")||s.contains("occlu")){f.reason="part of the marker's outline could not be measured cleanly (see the close-up)";f.shortReason="outline not clear";f.visual=true;}
        // Alpha105: the close-up is shown here too, so a withheld marker is visibly not scored (owner's NecoClock frame 4:
        // the 10's outline gave clean edge on 6 of 8 sections and was withheld with no tile)
        else{f.shortReason="edge not clear";f.visual=true;}
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
        for(Measure x:f.measures)x.visibleBar=x.unit.equals("deg")?VISIBLE_DEG:VISIBLE_FRACTION*2*spec.halfBase;
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
            if(m.name.equals(name)&&m.outside()){Measure x=new Measure(m.name,Math.min(m.value,m.genuineMax),m.genuineMax,m.sigma,m.k,m.n,m.unit,m.shortValue,m.sentence);
                x.visibleBar=m.visibleBar;f.measures.set(i,x);}}
    }

    static Finding baton(Alpha94MarkerMeasurement.Report r,ModelSpec.Marker spec,double R,Alpha99MarkerInterference.Check c,ModelReference ref){
        return baton(r,spec,R,c,ref,false);
    }
    /** @param resMatched the position is compared only with genuine watches photographed at similar or lower resolution
     *                    (the model spec's resolution_matched list); with fewer than 8 such watches it is not assessed. */
    static Finding baton(Alpha94MarkerMeasurement.Report r,ModelSpec.Marker spec,double R,Alpha99MarkerInterference.Check c,ModelReference ref,boolean resMatched){
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
        f.measures.get(0).visibleBar=VISIBLE_DEG;
        double off=m.localOffsetPx/R;
        int nOff=resMatched?ref.matchedWatches(key+"_off",R):offRef.length;
        double offMax=resMatched?ref.matchedMax(key+"_off",R):Alpha98Findings.max(offRef);
        if(resMatched&&nOff<Alpha98Findings.MIN_MATCHED){
            f.notes.add("Its position is not assessed: the photo's resolution is too low to compare it with genuine photos.");
            f.partNotAssessed="position";f.partReason="resolution too low";
            settle(f);return f;
        }
        f.measures.add(new Measure("position",off,offMax,sOff,k,nOff,"R",
                String.format(Locale.US,"shifted %s by %.2f%% of the dial",towards(m),100*off),
                String.format(Locale.US,"It sits %.1f px out of place relative to the other markers (%.2f%% of the dial radius, mostly %s)",
                        m.localOffsetPx,100*off,Alpha98Findings.direction(m))));
        Measure pos=f.measures.get(f.measures.size()-1);pos.visibleBar=VISIBLE_FRACTION*2*spec.tangentialHalf;
        if(resMatched)smallPhoto(pos,ref,key+"_off",R);
        settle(f);return f;
    }

    static List<Finding> rounds(Alpha94MarkerMeasurement.Report r,double R,Map<Integer,Alpha99MarkerInterference.Check> checks,
                                ModelSpec model,ModelReference ref){
        return rounds(r,R,checks,null,model,ref);
    }
    static List<Finding> rounds(Alpha94MarkerMeasurement.Report r,double R,Map<Integer,Alpha99MarkerInterference.Check> checks,
                                Map<Integer,Alpha94MarkerMeasurement.Marker> partial,ModelSpec model,ModelReference ref){
        List<Finding> out=new ArrayList<>();
        int usable=0;
        if(r!=null)for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind)&&m.usable&&Double.isFinite(m.localOffsetPx))usable++;
        boolean hasRef=ref.has("rounds_off");
        int n=ref.matchedWatches("rounds_off",R);
        double lim=ref.matchedMax("rounds_off",R);
        double sOff=sigmaPos(ref,"rounds_off",R);
        int sizeN=ref.has("round_size_rel")?ref.matchedWatches("round_size_rel",R):0;
        double sizeLim=ref.matchedMax("round_size_rel",R);
        for(ModelSpec.Marker mk:model.withShape(ModelSpec.Shape.ROUND)){
            int h=mk.hour;
            Finding f=new Finding("round"+h,h+" o'clock",""+h);
            double a=Math.toRadians(h*30.0);f.cx=Math.sin(a)*mk.centreR;f.cy=-Math.cos(a)*mk.centreR;
            f.half=0.17;f.shape=Shape.ROUND;f.hour=h;out.add(f);
            if(!hasRef){noReference(f);f.group="round markers";continue;}
            if(r==null||usable<5){f.status=Status.NOT_ASSESSED;f.reason="too few round markers could be measured cleanly";f.shortReason="too few clean markers";f.group="round markers";continue;}
            if(n<Alpha98Findings.MIN_MATCHED){f.status=Status.NOT_ASSESSED;f.reason="the photo's resolution is too low to compare round markers with genuine photos";f.shortReason="resolution too low";f.group="round markers";continue;}
            Alpha94MarkerMeasurement.Marker pm=partial==null?null:partial.get(h);
            boolean part=false;
            if(!gate(f,check(checks,h))){
                // Alpha102: a hand near (not touching) this marker -> its position from the outline away from the hand
                Alpha99MarkerInterference.Check c=check(checks,h);
                if(c==null||!Alpha99MarkerInterference.HAND.equals(c.reason)||pm==null||!pm.usable||!Double.isFinite(pm.localOffsetPx))continue;
                f.status=Status.WITHIN;f.reason="";f.shortReason="";f.visual=false;part=true;
            }
            Alpha94MarkerMeasurement.Marker m=part?pm:r.atHour(h);
            boolean gapFit=false;
            if(!part&&(m==null||!m.usable)&&pm!=null&&pm.usable&&Double.isFinite(pm.localOffsetPx)){m=pm;part=true;gapFit=true;}
            if(m==null||!m.usable||!Double.isFinite(m.localOffsetPx)){withheldByMeasurement(f,m==null?"unavailable":m.reason);continue;}
            double off=m.localOffsetPx/R;
            f.measures.add(new Measure("position",off,lim,sOff,ref.kSigma,n,"R",
                    String.format(Locale.US,"shifted %s by %.2f%% of the dial",towards(m),100*off),
                    String.format(Locale.US,"The %d o'clock marker sits %.1f px out of place relative to the other markers (%.2f%% of the dial radius, mostly %s); compared with genuine photos of similar or lower resolution",
                            h,m.localOffsetPx,100*off,Alpha98Findings.direction(m))));
            Measure pos=f.measures.get(f.measures.size()-1);pos.visibleBar=VISIBLE_FRACTION*2*mk.outerR;smallPhoto(pos,ref,"rounds_off",R);
            if(gapFit){
                pos.capAtWorth=true;
                pos.capReason="Part of this marker's outline edge was not visible (its polished surround reflecting the dark), so its position was measured from the rest of the outline; that is less precise, so it is shown as worth a look at most.";
                f.notes.add("Measured from the part of the marker's outline where its edge is visible. Its size is not assessed.");
                f.partAssessed="position";f.partNotAssessed="size";f.partReason="edge not visible all round";
                settle(f);continue;
            }
            if(part){
                // genuine catalogues (CI 37803866932): measurable on 88 of 128 (124060) and 147 of 355 (GMT) hand-near
                // markers, 2 and 3 of them outside the genuine range; the partial fit adds error (p99 0.003 R), so at most worth a look
                pos.capAtWorth=true;
                pos.capReason="A hand lies close to this marker, so its position was measured from the part of its outline away from the hand; that is less precise, so it is shown as worth a look at most.";
                f.notes.add("Measured from the part of the marker's outline away from a nearby hand (the quarter facing the hand is ignored). Its size is not assessed.");
                f.partAssessed="position";f.partNotAssessed="size";f.partReason="hand nearby";
                settle(f);continue;
            }
            // Alpha101: this round marker's size against the other round markers on the same dial (lighting and blur cancel)
            double med=cleanRoundSizeMedian(r,R,checks,model);
            if(Double.isFinite(med)&&Double.isFinite(m.radiusErrPx)&&sizeN>=Alpha98Findings.MIN_MATCHED){
                double rel=m.radiusErrPx/R-med;
                f.measures.add(new Measure("size",Math.abs(rel),sizeLim,sigmaPos(ref,"round_size_rel",R),ref.kSigma,sizeN,"R",
                        String.format(Locale.US,"marker %s than the others by %.2f%% of the dial",rel>=0?"larger":"smaller",100*Math.abs(rel)),
                        String.format(Locale.US,"The marker (outer edge of its metal surround) is %s than the other round markers on this dial by %.2f%% of the dial radius; compared with genuine photos of similar or lower resolution",
                                rel>=0?"larger":"smaller",100*Math.abs(rel))));
                Measure sz=f.measures.get(f.measures.size()-1);sz.visibleBar=VISIBLE_FRACTION*mk.outerR;smallPhoto(sz,ref,"round_size_rel",R);
                // one-sided glare or bleed on the metal surround grows the fitted outline and moves its centre together
                if(pos.outside()&&sz.outside()&&Math.abs(rel)>=0.5*off)
                    pos.cap(String.format(Locale.US,"Its size also reads off (%.2f%% of the dial). Glare or a bright edge on one side of the metal surround moves a marker's outline and its centre together, so its position is shown as worth a look, not a clear finding.",100*Math.abs(rel)));
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

    /** Alpha101: overall size of the round markers (all clean rounds together) against genuine dials. The fit reads the
     *  outermost bright edge, which is the outer edge of the polished metal surround on ~97% of genuine photos (the lume's own
     *  edge only when the surround looks dark), so this is the applied marker's size, not the lume fill's. */
    static Finding roundsSize(Alpha94MarkerMeasurement.Report r,double R,Map<Integer,Alpha99MarkerInterference.Check> checks,ModelSpec model,ModelReference ref){
        Finding f=new Finding("rounds_size","Round marker size","round marker size");f.shape=Shape.ROUND;f.half=0.17;f.dialWide=true;
        ModelSpec.Marker first=model.withShape(ModelSpec.Shape.ROUND).get(0);
        double a=Math.toRadians(first.hour*30.0);f.hour=first.hour;f.cx=Math.sin(a)*first.centreR;f.cy=-Math.cos(a)*first.centreR;
        if((!ref.has("rounds_size")||!Double.isFinite(ref.nominal("rounds_size")))&&noReference(f)){f.group="round markers";return f;}
        double med=cleanRoundSizeMedian(r,R,checks,model);
        if(!Double.isFinite(med)){f.status=Status.NOT_ASSESSED;f.reason="too few round markers could be measured cleanly";f.shortReason="too few clean markers";f.group="round markers";return f;}
        int n=ref.matchedWatches("rounds_size",R);
        if(n<Alpha98Findings.MIN_MATCHED){f.status=Status.NOT_ASSESSED;f.reason="the photo's resolution is too low to compare round markers with genuine photos";f.shortReason="resolution too low";f.group="round markers";return f;}
        double d=med-ref.nominal("rounds_size");
        f.measures.add(new Measure("dial size",Math.abs(d),ref.matchedMax("rounds_size",R),
                sigmaPos(ref,"rounds_size",R),ref.kSigma,n,"R",
                String.format(Locale.US,"all round markers %s by %.2f%% of the dial",d>=0?"larger":"smaller",100*Math.abs(d)),
                String.format(Locale.US,"Taken together, the round markers (outer edge of the metal surround) are %s than on genuine dials by %.2f%% of the dial radius; compared with genuine photos of similar or lower resolution",
                        d>=0?"larger":"smaller",100*Math.abs(d))));
        Measure m0=f.measures.get(0);m0.visibleBar=VISIBLE_FRACTION*first.outerR;smallPhoto(m0,ref,"rounds_size",R);
        settle(f);return f;
    }

    static Finding ring(Alpha94MarkerMeasurement.Report r,double R,ModelReference ref){return ring(r,R,ref,null);}
    static Finding ring(Alpha94MarkerMeasurement.Report r,double R,ModelReference ref,ModelSpec model){
        List<ModelSpec.Marker> rs=model==null?new ArrayList<>():model.withShape(ModelSpec.Shape.ROUND);
        double roundBar=rs.isEmpty()?Double.NaN:VISIBLE_FRACTION*2*rs.get(0).outerR;
        Finding f=new Finding("ring","Dial marker ring","marker ring");f.shape=Shape.RING;f.cx=0;f.cy=0;f.half=1.05;
        if((!ref.has("ring_rot")||!Double.isFinite(ref.nominal("ring_rot")))&&noReference(f))return f;
        Alpha94MarkerMeasurement.Ring g=r==null?null:r.ring;
        if(g==null||!g.usable){f.status=Status.NOT_ASSESSED;f.reason="too few markers could be measured cleanly";f.shortReason="too few clean markers";return f;}
        double rot=g.rotationDeg-ref.nominal("ring_rot");
        f.measures.add(new Measure("rotation",Math.abs(rot),Alpha98Findings.max(ref.far("ring_rot")),sigmaAng(ref,"ring_rot",R),ref.kSigma,
                ref.far("ring_rot").length,"deg",String.format(Locale.US,"markers as a set turned %.2f° %s",Math.abs(rot),Alpha98Findings.cw(rot)),
                String.format(Locale.US,"Taken together, the hour markers are turned %.2f° %s relative to the printed minute track (a whole-dial measurement: it does not mean each marker is rotated)",
                        Math.abs(rot),Alpha98Findings.cw(rot))));
        f.measures.get(0).visibleBar=VISIBLE_DEG;
        int n=ref.matchedWatches("ring_shift",R);
        if(n>=Alpha98Findings.MIN_MATCHED){double lim=ref.matchedMax("ring_shift",R);double sh=g.shiftPx/R;
            f.measures.add(new Measure("shift",sh,lim,sigmaPos(ref,"ring_shift",R),ref.kSigma,n,"R",
                    String.format(Locale.US,"markers as a set off-centre by %.2f%% of the dial",100*sh),
                    String.format(Locale.US,"Taken together, the hour markers are off-centre by %.1f px (%.2f%% of the dial radius); compared with genuine photos of similar or lower resolution",g.shiftPx,100*sh)));
            Measure m1=f.measures.get(1);m1.visibleBar=roundBar;smallPhoto(m1,ref,"ring_shift",R);}
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
        f.measures.get(0).visibleBar=VISIBLE_DEG;
        settle(f);return f;
    }

    private static String shortDate(String reason){
        if(reason.startsWith("glare"))return "glare on magnifier";
        if(reason.startsWith("a hand"))return "hand or date change";
        if(reason.startsWith("the dial"))return "dial not located";
        return "window not found";
    }

    /** Finding status = strongest measure: CLEAR, then WORTH, then MINOR (too small to see), else WITHIN. */
    static void settle(Finding f){
        Status s=Status.WITHIN;
        for(Measure m:f.measures){Status x=m.status();
            if(x==Status.CLEAR)s=Status.CLEAR;else if(x==Status.WORTH&&s!=Status.CLEAR)s=Status.WORTH;else if(x==Status.MINOR&&s==Status.WITHIN)s=Status.MINOR;}
        f.status=s;
    }

    /** Alpha103: a feature judged against rows from shrunk genuine photos (this photo is below the full-resolution
     *  coverage) is at most worth a look: shrinking genuine photos to that size alone moves their readings by as much as
     *  the single-photo allowance (QC guardrails 3: low resolution must not manufacture confidence). */
    static void smallPhoto(Measure m,ModelReference ref,String feature,double R){
        if(ref.usesShrunkRows(feature,R))m.cap(String.format(Locale.US,"This photo is small (dial radius %.0f px). Shrinking genuine photos to this size moves their markers by as much as this from the shrinking alone, so it is shown as worth a look, not a clear finding.",R));
    }

    /** Opposite marker pairs (hours) checked for a shared displacement. */
    static final int[][] OPPOSITE={{3,9},{1,7},{2,8},{4,10},{5,11}};

    /**
     * Alpha103: two opposite markers displaced the same way in the picture (directions within 45 deg, sizes within 2x)
     * share a cause in the photo - camera angle or lighting - rather than being two separately misplaced markers (CLAUDE.md:
     * use opposing marker relationships to identify perspective contamination). Their positions are at most worth a look.
     */
    static void oppositeMarkers(Summary s,Alpha94MarkerMeasurement.Report r){
        if(r==null)return;
        for(int[] p:OPPOSITE){
            Finding a=byHour(s,p[0]),b=byHour(s,p[1]);
            Measure ma=position(a),mb=position(b);
            if(ma==null||mb==null||!(ma.outside()||mb.outside()))continue;
            double[] va=imageVector(r.atHour(p[0])),vb=imageVector(r.atHour(p[1]));
            if(va==null||vb==null)continue;
            double la=Math.hypot(va[0],va[1]),lb=Math.hypot(vb[0],vb[1]);
            if(la<=0||lb<=0||Math.min(la,lb)<0.5*Math.max(la,lb))continue;
            if((va[0]*vb[0]+va[1]*vb[1])/(la*lb)<Math.cos(Math.toRadians(45)))continue;
            ma.cap(oppositeReason(p[1]));mb.cap(oppositeReason(p[0]));
            settle(a);settle(b);
        }
    }
    static String oppositeReason(int other){
        return String.format(Locale.US,"The opposite %d o'clock marker is displaced the same way in the picture. Two opposite markers moving together point to the camera angle or lighting rather than a misplaced marker, so this is shown as worth a look, not a clear finding.",other);
    }
    private static Finding byHour(Summary s,int h){
        for(Finding f:s.all)if(f.hour==h&&(f.shape==Shape.BATON||f.shape==Shape.ROUND)&&!f.dialWide)return f;
        return null;
    }
    private static Measure position(Finding f){
        if(f==null||f.status==Status.NOT_ASSESSED)return null;
        for(Measure m:f.measures)if(m.name.equals("position"))return m;
        return null;
    }
    /** Local offset in picture-aligned dial axes (x right, y down; 12 at the top), from radial (+ out) / tangential (+ cw). */
    static double[] imageVector(Alpha94MarkerMeasurement.Marker m){
        if(m==null||!Double.isFinite(m.localRadialPx)||!Double.isFinite(m.localTangentialPx))return null;
        double a=Math.toRadians(m.hour*30.0),rd=m.localRadialPx,tg=m.localTangentialPx;
        return new double[]{rd*Math.sin(a)+tg*Math.cos(a),-rd*Math.cos(a)+tg*Math.sin(a)};
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
