package com.watchalign.mobile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A watch model's dial layout, loaded from assets/models/&lt;id&gt;/model.json. Everything the pipeline needs to know about
 * a particular dial lives here (minute-track radii for the pose, each applied marker's hour, shape and size, the date
 * window region, where the seconds hand carries its lume dot) so that adding a model is a data change: a new spec
 * file plus its genuine reference (ModelReference), no code. Canonical units: dial radius 1, 12 at the top (y = -1).
 */
final class ModelSpec {
    enum Shape{TRIANGLE,BATON,ROUND}

    /** Opens a file under the assets root (Android: AssetManager; JVM tests / desktop harness: a directory). */
    interface Assets{InputStream open(String path)throws IOException;}

    static Assets directory(File root){return p->new FileInputStream(new File(root,p));}

    static final class Marker {
        final int hour;final Shape shape;
        /** Name used for this marker's genuine reference and uncertainty entries (e.g. "six"); rounds share "rounds". */
        final String key;
        // round
        final double centreR,outerR;
        // baton
        final double radialHalf,tangentialHalf;
        // triangle (apex towards the centre)
        final double apexR,baseR,halfBase,areaCentroidR,corridorStartR;
        Marker(int hour,Shape shape,String key,double centreR,double outerR,double radialHalf,double tangentialHalf,
               double apexR,double baseR,double halfBase,double areaCentroidR,double corridorStartR){
            this.hour=hour;this.shape=shape;this.key=key;this.centreR=centreR;this.outerR=outerR;this.radialHalf=radialHalf;
            this.tangentialHalf=tangentialHalf;this.apexR=apexR;this.baseR=baseR;this.halfBase=halfBase;
            this.areaCentroidR=areaCentroidR;this.corridorStartR=corridorStartR;
        }
        String kind(){return shape==Shape.TRIANGLE?"triangle":shape==Shape.BATON?"baton":"round";}
        /** Master centre (the measured point) in canonical units. */
        double[] masterPoint(){
            double r=shape==Shape.TRIANGLE?areaCentroidR:centreR;
            if(hour%12==0)return new double[]{0,-r};                 // exact at 12 (no sin(2 pi) round-off)
            double a=Math.toRadians(hour*30.0);
            return new double[]{Math.sin(a)*r,-Math.cos(a)*r};
        }
        /** Triangle outline (apex towards the centre, then base right, base left as seen with this hour at the top). */
        double[][] trianglePolygon(){
            double[][] v={{0,-apexR},{halfBase,-baseR},{-halfBase,-baseR}};
            if(hour%12==0)return v;
            double a=Math.toRadians(hour*30.0),c=Math.cos(a),s=Math.sin(a);double[][] o=new double[3][];
            for(int i=0;i<3;i++)o[i]=new double[]{v[i][0]*c-v[i][1]*s,v[i][0]*s+v[i][1]*c};
            return o;
        }
        /** Baton outline: (radial, tangential) corners (-1,-1),(1,-1),(1,1),(-1,1), as the research marker_polygon. */
        double[][] batonPolygon(){
            double a=Math.toRadians(hour*30.0);double[] er={Math.sin(a),-Math.cos(a)},et={Math.cos(a),Math.sin(a)};
            int[][] su={{-1,-1},{1,-1},{1,1},{-1,1}};double[][] v=new double[4][];
            for(int i=0;i<4;i++)v[i]=new double[]{centreR*er[0]+su[i][0]*radialHalf*er[0]+su[i][1]*tangentialHalf*et[0],
                    centreR*er[1]+su[i][0]*radialHalf*er[1]+su[i][1]*tangentialHalf*et[1]};
            return v;
        }
        /** Radial extent and tangential half-width of the marker's outline. */
        double innerR(){return shape==Shape.TRIANGLE?apexR:shape==Shape.BATON?centreR-radialHalf:centreR-outerR;}
        double outerEdgeR(){return shape==Shape.TRIANGLE?baseR:shape==Shape.BATON?centreR+radialHalf:centreR+outerR;}
        double halfWidth(){return shape==Shape.TRIANGLE?halfBase:shape==Shape.BATON?tangentialHalf:outerR;}
    }

    static final class DateWindow {
        final int hour;final double x0,x1,y0,y1,step,expX,expY,exclXMin,exclHalfY;
        DateWindow(int hour,double x0,double x1,double y0,double y1,double step,double expX,double expY,double exclXMin,double exclHalfY){
            this.hour=hour;this.x0=x0;this.x1=x1;this.y0=y0;this.y1=y1;this.step=step;this.expX=expX;this.expY=expY;
            this.exclXMin=exclXMin;this.exclHalfY=exclHalfY;
        }
    }

    final String id,label;
    final double minuteTrackInnerR,minuteTrackOuterR;
    final int[] excludedMinutes;
    /** Every applied marker, in measurement order. */
    final List<Marker> markers;
    /** null when the model has no date. */
    final DateWindow date;
    final double secondsDotRMin,secondsDotRMax;
    /** Folder (relative to the model folder) holding the genuine reference; see ModelReference. */
    final String referenceDir;
    /** Features compared only with genuine watches photographed at similar or lower resolution (in addition to the
     *  always-matched round / ring features), from the optional "resolution_matched" list; empty when absent. */
    java.util.Set<String> resolutionMatched=java.util.Collections.emptySet();
    /** Printed dial details that look like the seconds-hand lume dot (seconds_hand.print_spots: {angle deg clockwise
     *  from 12, r}), measured on genuine dials. A dot candidate within printSpotTolDeg / printSpotTolR of one counts as the
     *  seconds hand only at >= printSpotMinFrac x the photo's lume contrast (the print itself never gets that bright). */
    double[][] printSpots=new double[0][];
    double printSpotTolDeg=Double.NaN,printSpotTolR=Double.NaN,printSpotMinFrac=Double.NaN;
    /** Folder of this model under the assets root, e.g. "models/gmt_126710". */
    final String dir;

    private ModelSpec(String id,String label,double rin,double rout,int[] excl,List<Marker> markers,DateWindow date,
                      double dotMin,double dotMax,String referenceDir,String dir){
        this.id=id;this.label=label;this.minuteTrackInnerR=rin;this.minuteTrackOuterR=rout;this.excludedMinutes=excl;
        this.markers=Collections.unmodifiableList(markers);this.date=date;this.secondsDotRMin=dotMin;this.secondsDotRMax=dotMax;
        this.referenceDir=referenceDir;this.dir=dir;
    }

    /** Markers measured individually and fitted as the ring (every marker but the triangle). */
    List<Marker> ringMarkers(){List<Marker> o=new ArrayList<>();for(Marker m:markers)if(m.shape!=Shape.TRIANGLE)o.add(m);return o;}
    List<Marker> withShape(Shape s){List<Marker> o=new ArrayList<>();for(Marker m:markers)if(m.shape==s)o.add(m);return o;}
    Marker triangle(){for(Marker m:markers)if(m.shape==Shape.TRIANGLE)return m;return null;}
    Marker atHour(int hour){for(Marker m:markers)if(m.hour==hour)return m;return null;}
    int[] ringHours(){List<Marker> r=ringMarkers();int[] h=new int[r.size()];for(int i=0;i<h.length;i++)h[i]=r.get(i).hour;return h;}
    int[] roundHours(){List<Marker> r=withShape(Shape.ROUND);int[] h=new int[r.size()];for(int i=0;i<h.length;i++)h[i]=r.get(i).hour;return h;}

    // ------------------------------------------------------------------ loading
    static ModelSpec load(Assets assets,String id)throws IOException{
        String dir="models/"+id;
        return parse(read(assets,dir+"/model.json"),dir);
    }

    @SuppressWarnings("unchecked")
    static ModelSpec parse(String json,String dir){
        Map<String,Object> o=(Map<String,Object>)MiniJson.parse(json);
        Map<String,Object> pose=obj(o,"pose");
        List<Object> ex=(List<Object>)pose.get("excluded_minutes");
        int[] excl=new int[ex==null?0:ex.size()];for(int i=0;i<excl.length;i++)excl[i]=(int)Math.round((Double)ex.get(i));
        List<Marker> ms=new ArrayList<>();
        for(Object x:(List<Object>)o.get("markers")){
            Map<String,Object> m=(Map<String,Object>)x;
            int hour=(int)Math.round(num(m,"hour"));
            Shape sh=Shape.valueOf(((String)m.get("shape")).toUpperCase(java.util.Locale.US));
            String key=m.containsKey("key")?(String)m.get("key"):sh==Shape.ROUND?"rounds":"marker"+hour;
            ms.add(new Marker(hour,sh,key,opt(m,"centre_r"),opt(m,"outer_r"),opt(m,"radial_half"),opt(m,"tangential_half"),
                    opt(m,"apex_r"),opt(m,"base_r"),opt(m,"half_base"),opt(m,"area_centroid_r"),opt(m,"corridor_start_r")));
        }
        for(int i=0;i<ms.size();i++)for(int k=i+1;k<ms.size();k++)
            if(ms.get(i).hour==ms.get(k).hour)throw new IllegalArgumentException("model spec: two markers at hour "+ms.get(i).hour);
        DateWindow dw=null;
        if(o.get("date_window")!=null){
            Map<String,Object> d=obj(o,"date_window"),c=obj(d,"crop"),e=obj(d,"interference_exclusion");
            List<Object> ec=(List<Object>)d.get("expected_centre");
            dw=new DateWindow((int)Math.round(num(d,"hour")),num(c,"x0"),num(c,"x1"),num(c,"y0"),num(c,"y1"),num(c,"step"),
                    (Double)ec.get(0),(Double)ec.get(1),num(e,"x_min"),num(e,"half_y"));
        }
        Map<String,Object> sec=obj(o,"seconds_hand");
        ModelSpec spec=new ModelSpec((String)o.get("id"),(String)o.get("label"),num(pose,"minute_track_inner_r"),num(pose,"minute_track_outer_r"),
                excl,ms,dw,num(sec,"dot_r_min"),num(sec,"dot_r_max"),(String)o.get("reference"),dir);
        if(sec.get("print_spots")!=null){
            List<Object> ps=(List<Object>)sec.get("print_spots");
            spec.printSpots=new double[ps.size()][];
            for(int i=0;i<ps.size();i++){List<Object> q=(List<Object>)ps.get(i);spec.printSpots[i]=new double[]{(Double)q.get(0),(Double)q.get(1)};}
            spec.printSpotTolDeg=num(sec,"print_spot_tol_deg");spec.printSpotTolR=num(sec,"print_spot_tol_r");spec.printSpotMinFrac=num(sec,"print_spot_min_frac");
        }
        if(o.get("resolution_matched")!=null){
            java.util.Set<String> rm=new java.util.LinkedHashSet<>();
            for(Object x:(List<Object>)o.get("resolution_matched"))rm.add((String)x);
            spec.resolutionMatched=java.util.Collections.unmodifiableSet(rm);
        }
        return spec;
    }

    /** Is a seconds-dot candidate at (angle, r) on a known printed spot? */
    boolean onPrintSpot(double angleDeg,double r){
        for(double[] p:printSpots){double da=Math.abs(((angleDeg-p[0])%360+540)%360-180);if(da<=printSpotTolDeg&&Math.abs(r-p[1])<=printSpotTolR)return true;}
        return false;
    }

    static String read(Assets assets,String path)throws IOException{
        try(InputStream in=assets.open(path)){
            ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))>0)b.write(buf,0,n);
            return new String(b.toByteArray(),StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> obj(Map<String,Object> o,String k){
        Object v=o.get(k);if(!(v instanceof Map))throw new IllegalArgumentException("model spec: missing object '"+k+"'");
        return (Map<String,Object>)v;
    }
    private static double num(Map<String,Object> o,String k){
        Object v=o.get(k);if(!(v instanceof Double))throw new IllegalArgumentException("model spec: missing number '"+k+"'");
        return (Double)v;
    }
    private static double opt(Map<String,Object> o,String k){Object v=o.get(k);return v instanceof Double?(Double)v:Double.NaN;}
}
