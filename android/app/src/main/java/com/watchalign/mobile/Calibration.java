package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Watch Align 1.6.2: the owner's calibration (pure Java, no Android, so it is unit-tested on the JVM).
 *
 *   Record      one checked photo, kept ON THE PHONE ONLY: its measured numbers per marker and the owner's verdicts
 *               (Pass / Fail / Unsure). No photo pixels (the store keeps a small thumbnail beside it, never uploaded).
 *   rescore     the answers a record gets under other limits (nothing is measured again).
 *   Genuine     the measured numbers of the verified genuine markers (an asset, numbers only), to count how many of
 *               them a setting would flag.
 *   suggest     the limits for one marker type that agree with most of the owner's verdicts while flagging no genuine
 *               marker.
 *   export      the limits, the records and the verdicts as plain text (no photos), for the owner to share.
 */
final class Calibration {
    private Calibration(){}

    enum Verdict{PASS,FAIL,UNSURE}

    static final class Mark {
        int hour;ModelSpec.Shape shape;String gate="",reason="";
        /** 1.6.3 outer-metal fit: why the reading is uncertain (empty when reliable) and whether the triangle's gap came
         *  from the fitted outline; a reading over a limit then stays "Possible misalignment - inspect" when re-judged */
        String uncertain="";boolean gapFromFit;
        double side=Double.NaN,inout=Double.NaN,tilt=Double.NaN,gap=Double.NaN;
        Verdict verdict;
    }

    static final class Record {
        String id,model,label;long time;double rpx;String photoReason;
        final List<Mark> marks=new ArrayList<>();
        Mark at(int h){for(Mark m:marks)if(m.hour==h)return m;return null;}
    }

    /** a record of one analysed photo (null when the check did not run) */
    static Record of(String id,long time,String model,String label,ClearlyOff.Result r,String photoReason){
        if(r==null)return null;
        Record rec=new Record();rec.id=id;rec.time=time;rec.model=model;rec.label=label;rec.rpx=r.rpx;rec.photoReason=photoReason;
        for(ClearlyOff.Marker m:r.markers){
            Mark k=new Mark();k.hour=m.hour;k.shape=m.shape;k.gate=String.join("; ",m.gate);k.reason=m.gate.isEmpty()?"":m.reason;
            k.side=m.sidePct;k.inout=m.inoutPct;k.tilt=m.tiltDeg;k.gap=m.gapPct;k.uncertain=m.uncertain;k.gapFromFit=m.gapFromFit;rec.marks.add(k);
        }
        return rec;
    }

    /** the clearly-off marker this record's numbers give under limits L */
    static ClearlyOff.Marker judge(Record rec,Mark k,ClearlyOff.Limits L){
        ClearlyOff.Marker m=new ClearlyOff.Marker(k.hour,k.shape);
        m.sidePct=k.side;m.inoutPct=k.inout;m.tiltDeg=k.tilt;m.gapPct=k.gap;m.uncertain=k.uncertain;m.gapFromFit=k.gapFromFit;
        if(!k.gate.isEmpty())for(String g:k.gate.split("; "))m.gate.add(g);
        ClearlyOff.decide(m,rec.rpx,null,L);
        if(!k.gate.isEmpty()&&!k.reason.isEmpty())m.reason=k.reason;
        if(rec.photoReason!=null){m.state=ClearlyOff.State.CANT_CHECK;m.reason=rec.photoReason;}
        return m;
    }
    static ClearlyOff.State state(Record rec,Mark k,ClearlyOff.Limits L){return judge(rec,k,L).state;}

    // ------------------------------------------------------------------------------------------------ genuine numbers

    /** one measured genuine marker: shape, dial radius px, side %, in/out %, tilt deg, gap % (NaN when not measured) */
    static final class Genuine {
        final List<Mark> marks=new ArrayList<>();final List<Double> rpx=new ArrayList<>();
        int total(ModelSpec.Shape s){int n=0;for(Mark m:marks)if(m.shape==s)n++;return n;}
        int flagged(ModelSpec.Shape s,ClearlyOff.Limits L){
            int n=0;Record r=new Record();
            for(int i=0;i<marks.size();i++){Mark m=marks.get(i);if(m.shape!=s)continue;r.rpx=rpx.get(i);
                if(Calibration.flagged(state(r,m,L)))n++;}
            return n;
        }
        static Genuine parse(String csv){
            Genuine g=new Genuine();
            for(String line:csv.split("\n")){
                line=line.trim();if(line.isEmpty()||line.startsWith("#")||line.startsWith("shape"))continue;
                String[] f=line.split(",",-1);if(f.length<6)continue;
                Mark m=new Mark();m.shape=AlignmentReference.shape(f[0]);if(m.shape==null)continue;
                m.side=num(f[2]);m.inout=num(f[3]);m.tilt=num(f[4]);m.gap=num(f[5]);
                g.marks.add(m);g.rpx.add(num(f[1]));
            }
            return g;
        }
    }

    // ------------------------------------------------------------------------------------------------ verdicts

    /** how the owner's verdicts compare with the answers under limits L (markers of one type, or all when shape null) */
    static final class Agreement {
        int agree,total;final List<String> disagree=new ArrayList<>();
    }
    static Agreement agreement(List<Record> recs,String model,ModelSpec.Shape shape,ClearlyOff.Limits L){
        Agreement a=new Agreement();
        for(Record r:recs){if(!model.equals(r.model))continue;
            for(Mark k:r.marks){if(k.verdict==null||k.verdict==Verdict.UNSURE||(shape!=null&&k.shape!=shape))continue;
                ClearlyOff.State s=state(r,k,L);if(s==ClearlyOff.State.CANT_CHECK)continue;
                a.total++;
                boolean ok=(k.verdict==Verdict.FAIL)==flagged(s);
                if(ok)a.agree++;else a.disagree.add(r.label+", "+k.hour+" o'clock: you said "+(k.verdict==Verdict.FAIL?"Fail":"Pass")
                        +", the app says "+word(s));
            }}
        return a;
    }

    /** markers whose answer changes between limits A and B (for "this change flips ...") */
    static List<String> changes(List<Record> recs,String model,ClearlyOff.Limits A,ClearlyOff.Limits B){
        List<String> out=new ArrayList<>();
        for(Record r:recs){if(!model.equals(r.model))continue;
            for(Mark k:r.marks){ClearlyOff.State a=state(r,k,A),b=state(r,k,B);
                if(a!=b)out.add(r.label+", "+k.hour+" o'clock: "+word(a)+" → "+word(b));}}
        return out;
    }
    /** a finding the user is shown: clearly off, or (1.6.3) a possible misalignment to inspect */
    static boolean flagged(ClearlyOff.State s){return s==ClearlyOff.State.CLEARLY_OFF||s==ClearlyOff.State.POSSIBLE;}
    static String word(ClearlyOff.State s){return s==ClearlyOff.State.CLEARLY_OFF?"Clearly off":s==ClearlyOff.State.POSSIBLE?"Possible misalignment — inspect":s==ClearlyOff.State.NO_VISIBLE_ISSUE?"No visible issue":"Can't check";}

    // ------------------------------------------------------------------------------------------------ settings

    /** one adjustable limit of a marker type */
    static final class Param {
        final String key,name,unit;final double min,max,step;final boolean canDisable;
        Param(String key,String name,String unit,double min,double max,double step,boolean canDisable){
            this.key=key;this.name=name;this.unit=unit;this.min=min;this.max=max;this.step=step;this.canDisable=canDisable;}
        double get(ClearlyOff.Limits L){
            switch(key){case "roundPos":return L.roundPosR*100;case "batonPos":return L.batonPosR*100;case "batonTilt":return L.batonTiltDeg;
                case "triSide":return L.triSideR*100;case "triGap":return L.triGapR*100;default:return L.triTiltDeg;}
        }
        void set(ClearlyOff.Limits L,double v){
            switch(key){case "roundPos":L.roundPosR=v/100;break;case "batonPos":L.batonPosR=v/100;break;case "batonTilt":L.batonTiltDeg=v;break;
                case "triSide":L.triSideR=v/100;break;case "triGap":L.triGapR=v/100;break;default:L.triTiltDeg=v;}
        }
    }
    static List<Param> params(ModelSpec.Shape s){
        List<Param> l=new ArrayList<>();
        if(s==ModelSpec.Shape.ROUND)l.add(new Param("roundPos","Distance from where it should be","%",0.3,3.0,0.05,false));
        else if(s==ModelSpec.Shape.BATON){l.add(new Param("batonPos","Distance from where it should be","%",0.3,3.0,0.05,false));
            l.add(new Param("batonTilt","Rotation","°",0.5,6.0,0.25,false));}
        else if(s==ModelSpec.Shape.TRIANGLE){l.add(new Param("triSide","Sideways","%",0.3,3.0,0.05,false));
            l.add(new Param("triGap","Gap under the minute track","%",0.5,4.0,0.05,false));
            l.add(new Param("triTilt","Rotation","°",0.5,6.0,0.25,true));}
        return l;
    }

    /**
     * The settings of one marker type that agree with most of the owner's Pass / Fail verdicts while flagging no genuine
     * marker of that type (when genuine numbers exist); ties go to the setting closest to the current one. Returns null
     * when there are no verdicts for this type.
     */
    static ClearlyOff.Limits suggest(List<Record> recs,String model,ModelSpec.Shape shape,ClearlyOff.Limits start,Genuine gen){
        if(agreement(recs,model,shape,start).total==0)return null;
        ClearlyOff.Limits best=start.copy();
        List<Param> ps=params(shape);
        for(int round=0;round<3;round++)for(Param p:ps){
            double cur=p.get(best);double bestScore=Double.NEGATIVE_INFINITY;double bestV=cur;
            List<Double> cands=new ArrayList<>();
            if(p.canDisable)cands.add(Double.NaN);
            for(double v=p.min;v<=p.max+1e-9;v+=p.step)cands.add(Math.round(v*100)/100.0);
            for(double v:cands){
                ClearlyOff.Limits L=best.copy();p.set(L,v);
                if(gen!=null&&gen.total(shape)>0&&gen.flagged(shape,L)>0)continue;
                Agreement a=agreement(recs,model,shape,L);
                double dist=Double.isNaN(v)||Double.isNaN(cur)?(Double.isNaN(v)==Double.isNaN(cur)?0:1):Math.abs(v-cur)/(p.max-p.min);
                double score=a.agree-0.01*dist;
                if(score>bestScore+1e-12){bestScore=score;bestV=v;}
            }
            p.set(best,bestV);
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------------ JSON (MiniJson)

    static String toJson(Record r){
        StringBuilder b=new StringBuilder("{");
        kv(b,"id",r.id).append(',');kv(b,"model",r.model).append(',');kv(b,"label",r.label).append(',');
        b.append("\"time\":").append(r.time).append(",\"rpx\":").append(n(r.rpx)).append(',');
        if(r.photoReason!=null)kv(b,"photoReason",r.photoReason).append(',');
        b.append("\"marks\":[");
        for(int i=0;i<r.marks.size();i++){Mark k=r.marks.get(i);if(i>0)b.append(',');
            b.append("{\"hour\":").append(k.hour).append(',');kv(b,"shape",AlignmentReference.key(k.shape)).append(',');
            kv(b,"gate",k.gate).append(',');kv(b,"reason",k.reason).append(',');
            b.append("\"side\":").append(n(k.side)).append(",\"inout\":").append(n(k.inout)).append(",\"tilt\":").append(n(k.tilt))
             .append(",\"gap\":").append(n(k.gap));
            if(!k.uncertain.isEmpty()){b.append(',');kv(b,"uncertain",k.uncertain);}
            if(k.gapFromFit)b.append(",\"gapFit\":true");
            if(k.verdict!=null)b.append(',').append("\"verdict\":\"").append(k.verdict.name()).append('"');
            b.append('}');}
        return b.append("]}").toString();
    }
    @SuppressWarnings("unchecked")
    static Record recordFromJson(String s){
        Map<String,Object> o=(Map<String,Object>)MiniJson.parse(s);
        Record r=new Record();r.id=str(o.get("id"));r.model=str(o.get("model"));r.label=str(o.get("label"));
        r.time=(long)dbl(o.get("time"));r.rpx=dbl(o.get("rpx"));r.photoReason=(String)o.get("photoReason");
        for(Object x:(List<Object>)o.get("marks")){Map<String,Object> m=(Map<String,Object>)x;Mark k=new Mark();
            k.hour=(int)dbl(m.get("hour"));k.shape=AlignmentReference.shape(str(m.get("shape")));k.gate=str(m.get("gate"));k.reason=str(m.get("reason"));
            k.side=dbl(m.get("side"));k.inout=dbl(m.get("inout"));k.tilt=dbl(m.get("tilt"));k.gap=dbl(m.get("gap"));
            Object v=m.get("verdict");k.verdict=v==null?null:Verdict.valueOf((String)v);
            Object u=m.get("uncertain");k.uncertain=u==null?"":(String)u;k.gapFromFit=Boolean.TRUE.equals(m.get("gapFit"));
            if(k.shape!=null)r.marks.add(k);}
        return r;
    }

    static String limitsToJson(Map<String,ClearlyOff.Limits> all){
        StringBuilder b=new StringBuilder("{");int i=0;
        for(Map.Entry<String,ClearlyOff.Limits> e:all.entrySet()){if(i++>0)b.append(',');ClearlyOff.Limits L=e.getValue();
            b.append('"').append(esc(e.getKey())).append("\":{\"roundPosR\":").append(n(L.roundPosR)).append(",\"batonPosR\":").append(n(L.batonPosR))
             .append(",\"batonTiltDeg\":").append(n(L.batonTiltDeg)).append(",\"triSideR\":").append(n(L.triSideR))
             .append(",\"triGapR\":").append(n(L.triGapR)).append(",\"triTiltDeg\":").append(n(L.triTiltDeg)).append('}');}
        return b.append('}').toString();
    }
    @SuppressWarnings("unchecked")
    static Map<String,ClearlyOff.Limits> limitsFromJson(String s){
        Map<String,ClearlyOff.Limits> out=new LinkedHashMap<>();
        Map<String,Object> o=(Map<String,Object>)MiniJson.parse(s);
        for(Map.Entry<String,Object> e:o.entrySet()){Map<String,Object> m=(Map<String,Object>)e.getValue();ClearlyOff.Limits L=ClearlyOff.Limits.defaults();
            L.roundPosR=or(m.get("roundPosR"),L.roundPosR);L.batonPosR=or(m.get("batonPosR"),L.batonPosR);L.batonTiltDeg=or(m.get("batonTiltDeg"),L.batonTiltDeg);
            L.triSideR=or(m.get("triSideR"),L.triSideR);L.triGapR=or(m.get("triGapR"),L.triGapR);
            L.triTiltDeg=m.containsKey("triTiltDeg")?dbl(m.get("triTiltDeg")):L.triTiltDeg;
            out.put(e.getKey(),L);}
        return out;
    }

    /** plain-text export: the limits, then one line per marker of every record (numbers and verdicts, no photos) */
    static String export(Map<String,ClearlyOff.Limits> limits,List<Record> recs,String version){
        StringBuilder b=new StringBuilder("Watch Align ").append(version).append(" calibration export (numbers only, no photos)\n\n");
        b.append("limits (% of the dial radius, degrees; tilt blank = not judged)\nmodel,round_position,baton_position,baton_tilt,triangle_sideways,triangle_gap,triangle_tilt\n");
        for(Map.Entry<String,ClearlyOff.Limits> e:limits.entrySet()){ClearlyOff.Limits L=e.getValue();
            b.append(e.getKey()).append(',').append(f(100*L.roundPosR)).append(',').append(f(100*L.batonPosR)).append(',').append(f(L.batonTiltDeg))
             .append(',').append(f(100*L.triSideR)).append(',').append(f(100*L.triGapR)).append(',').append(f(L.triTiltDeg)).append('\n');}
        b.append("\nmarkers\nphoto,time,model,dial_radius_px,hour,shape,withheld,side_pct,inout_pct,tilt_deg,gap_pct,your_verdict,app_answer\n");
        for(Record r:recs){ClearlyOff.Limits L=limits.containsKey(r.model)?limits.get(r.model):ClearlyOff.Limits.defaults();
            for(Mark k:r.marks){
                b.append(r.label.replace(",",";")).append(',').append(r.time).append(',').append(r.model).append(',').append(f(r.rpx)).append(',').append(k.hour).append(',')
                 .append(AlignmentReference.key(k.shape)).append(',').append((r.photoReason!=null?r.photoReason:k.gate).replace(",",";")).append(',')
                 .append(f(k.side)).append(',').append(f(k.inout)).append(',').append(f(k.tilt)).append(',').append(f(k.gap)).append(',')
                 .append(k.verdict==null?"":k.verdict.name().toLowerCase(Locale.US)).append(',').append(word(state(r,k,L))).append('\n');}}
        return b.toString();
    }

    static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.3f",v):"";}
    static String n(double v){return Double.isFinite(v)?Double.toString(v):"null";}
    static double num(String s){try{return s==null||s.isEmpty()?Double.NaN:Double.parseDouble(s);}catch(NumberFormatException e){return Double.NaN;}}
    static double dbl(Object o){return o instanceof Number?((Number)o).doubleValue():Double.NaN;}
    static double or(Object o,double d){return o instanceof Number?((Number)o).doubleValue():d;}
    static String str(Object o){return o==null?"":o.toString();}
    static String esc(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
    static StringBuilder kv(StringBuilder b,String k,String v){return b.append('"').append(k).append("\":\"").append(esc(v)).append('"');}
}
