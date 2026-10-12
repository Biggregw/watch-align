package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import java.nio.file.*;
import java.io.PrintWriter;
import java.util.*;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Research-only export of the frozen app pipeline. No new detector, geometry or QC decisions.
 * Input: photo_id,physical_watch_id,model,family,path,role,original_photo_id,source_width,source_height.
 * Usage: run.sh GenuineMarkerStudy inputs.csv results.csv private-crops-directory
 * One model family per invocation, supplied with -Dwatchalign.model.
 */
public class GenuineMarkerStudy {
    static final String[] COLUMNS=("photo_id,physical_watch_id,model,family,role,original_photo_id,source_sha256,source_width,source_height,working_width,working_height,hour,shape,pose_valid,pose_reason,ellipse_axis_ratio,ellipse_edge_rms_px,projective_accepted,dial_R_px,ticks,sectors,tick_rms_px,branch_turn,H,ring_usable,ring_model,ring_shift_px,ring_scale_pct,ring_rotation_deg,detector_usable,detector_reason,fit_coverage,fit_integrity,side_coverage,side_offsets_R,interference_clean,interference_reason,seconds_hand,touch_gap_px,foreign_fraction,partial_round_used,qc_status,partial_withheld,partial_reason,measures,area_trusted,area_reason,expected_area_R2,actual_area_R2,overlap_area_R2,extra_area_R2,missing_area_R2,extra_pct,missing_pct,mismatch_pct,expected_area_canonical_px2,actual_area_canonical_px2,rotation_raw_deg,rotation_reference_signed_deg,local_offset_R,local_radial_R,local_tangential_R,expected_polygon,detected_polygon,triangle_lateral_genuine_adjusted_R,triangle_radial_genuine_adjusted_R,triangle_base_to_nominal_track_R,reference_base_to_nominal_track_R,direct_metal_to_tick_gap_R").split(",");
    public static void main(String[] args)throws Exception{
        nu.pattern.OpenCV.loadLocally();org.opencv.core.Core.setNumThreads(1);
        ModelSpec model=HarnessModel.spec();ModelReference reference=HarnessModel.ref();
        boolean makeGallery=args.length<4||!"--no-gallery".equals(args[3]);
        Path crops=Path.of(args[2]);Files.createDirectories(crops);
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))){
            emit(out,Arrays.asList(COLUMNS));
            List<String> lines=Files.readAllLines(Path.of(args[0]));
            for(int index=1;index<lines.size();index++){
                String[] input=lines.get(index).split(",",-1);
                if(!input[3].equals(model.id))throw new IllegalArgumentException("Wrong model family for "+input[0]);
                Bitmap image=Alpha96Calib.loadAlpha96(input[4]);
                Alpha99Pipeline.Output result=image==null?null:Alpha99Pipeline.run(image,model,reference);
                String sourceHash=BatgirlInputAudit.hash(Files.readAllBytes(Path.of(input[4])));
                Mat rgba=new Mat();if(image!=null)Utils.bitmapToMat(image,rgba);
                for(ModelSpec.Marker spec:model.markers){
                    Map<String,Object> row=new LinkedHashMap<>();
                    put(row,"photo_id",input[0],"physical_watch_id",input[1],"model",input[2],"family",input[3],"role",input[5],"original_photo_id",input[6],
                            "source_sha256",sourceHash,"source_width",input[7],"source_height",input[8],"working_width",image==null?null:image.getWidth(),"working_height",image==null?null:image.getHeight(),"hour",spec.hour,"shape",spec.kind());
                    boolean valid=result!=null&&result.ok();
                    put(row,"pose_valid",valid,"pose_reason",valid?"":result==null?"decode failed":result.pose==null?"pose unavailable":result.pose.reason);
                    if(valid){
                        put(row,"ellipse_axis_ratio",result.pose.ellipseRatio,"ellipse_edge_rms_px",result.pose.edgeRms,"projective_accepted",result.pose.projectiveAccepted);
                        Alpha94MarkerMeasurement.Report report=result.measurement;
                        Alpha94MarkerMeasurement.Ring ring=report.ring;
                        Alpha94MarkerMeasurement.Marker marker=spec.shape==ModelSpec.Shape.TRIANGLE?report.triangle:report.atHour(spec.hour);
                        Alpha99MarkerInterference.Check check=result.checks==null?null:result.checks.get(spec.hour);
                        Alpha99Findings.Finding finding=null;for(Alpha99Findings.Finding f:result.summary.all)if(!f.dialWide&&f.hour==spec.hour)finding=f;
                        MarkerMismatchResearch.Result comparison=result.mismatch.get(spec.hour);
                        put(row,"dial_R_px",report.dialRadiusPx,"ticks",result.pose.detectedTicks,"sectors",result.pose.completePairs,"tick_rms_px",result.pose.fitAfter,"branch_turn",result.pose.markerBranchTurn,"H",array(result.pose.homography),
                                "ring_usable",ring!=null&&ring.usable,"ring_model",ring==null?null:array(ring.model),"ring_shift_px",ring==null?null:ring.shiftPx,"ring_scale_pct",ring==null?null:ring.scalePct,"ring_rotation_deg",ring==null?null:ring.rotationDeg,
                                "detector_usable",marker!=null&&marker.usable,"detector_reason",marker==null?"unmeasured":marker.reason,"fit_coverage",marker==null?null:marker.fitSupport,"fit_integrity",marker==null?null:1-marker.fitScorePx,
                                "side_coverage",marker==null?null:array(marker.sideCov),"side_offsets_R",marker==null?null:array(marker.sideOffR),"interference_clean",check!=null&&check.clean,"interference_reason",check==null?"unavailable":check.reason,
                                "seconds_hand",check==null?null:check.secondsHand,"touch_gap_px",check==null?null:check.touchGapPx,"foreign_fraction",check==null?null:check.foreignFrac,
                                "partial_round_used",result.partial!=null&&result.partial.containsKey(spec.hour),"qc_status",finding==null?"NOT_ASSESSED":finding.status,"partial_withheld",finding==null?null:finding.partNotAssessed,"partial_reason",finding==null?null:finding.partReason,
                                "measures",measures(finding),"area_trusted",comparison!=null&&comparison.trusted(),"area_reason",comparison==null?"unavailable":comparison.reason);
                        if(marker!=null)put(row,"rotation_raw_deg",spec.shape==ModelSpec.Shape.ROUND?null:marker.rotationDeg,"rotation_reference_signed_deg",spec.shape==ModelSpec.Shape.BATON?marker.rotationDeg-reference.nominal(spec.key+"_rot"):null,"local_offset_R",marker.localOffsetPx/report.dialRadiusPx,"local_radial_R",marker.localRadialPx/report.dialRadiusPx,"local_tangential_R",marker.localTangentialPx/report.dialRadiusPx);
                        if(spec.shape==ModelSpec.Shape.TRIANGLE){
                            Alpha97TwelveReadout.Result twelve=Alpha97TwelveReadout.from(report,reference);
                            if(twelve.usable)put(row,"rotation_reference_signed_deg",twelve.centrelineDeg,"triangle_lateral_genuine_adjusted_R",twelve.lateralR,"triangle_radial_genuine_adjusted_R",twelve.radialR);
                        }
                        if(comparison!=null){
                            double[][] expected=comparison.expected;
                            put(row,"expected_polygon",polygon(expected),"expected_area_R2",expected==null?null:MarkerPolygonMismatch.area(expected));
                            MarkerPolygonMismatch.Score score=comparison.score;
                            if(score!=null){
                                put(row,"actual_area_R2",score.actualArea,"overlap_area_R2",score.overlapArea,"extra_area_R2",score.extraArea,"missing_area_R2",score.missingArea,"extra_pct",score.extraPct,"missing_pct",score.missingPct,"mismatch_pct",score.mismatchPct,
                                        "expected_area_canonical_px2",score.expectedArea*report.dialRadiusPx*report.dialRadiusPx,"actual_area_canonical_px2",score.actualArea*report.dialRadiusPx*report.dialRadiusPx,"detected_polygon",polygon(score.actualPolygon));
                                if(spec.shape==ModelSpec.Shape.TRIANGLE)put(row,"triangle_base_to_nominal_track_R",baseGap(score.actualPolygon,model.minuteTrackInnerR),"reference_base_to_nominal_track_R",baseGap(expected,model.minuteTrackInnerR));
                            }
                            if(makeGallery&&expected!=null&&input[5].equals("original"))gallery(image,rgba,result.pose.homography,spec,comparison,crops.resolve(input[0]+"_"+spec.hour+".png"),input[0]);
                        }
                    }else put(row,"area_trusted",false,"area_reason",row.get("pose_reason"),"qc_status","NOT_ASSESSED");
                    List<Object> cells=new ArrayList<>();for(String key:COLUMNS)cells.add(row.get(key));emit(out,cells);
                }
                rgba.release();out.flush();System.err.println("finished "+input[0]);
            }
        }
    }
    static void put(Map<String,Object> row,Object... kv){for(int i=0;i<kv.length;i+=2)row.put((String)kv[i],kv[i+1]);}
    static String array(double[] a){return a==null?"":Arrays.toString(a);}
    static String polygon(double[][] a){return a==null?"":Arrays.deepToString(a);}
    static void emit(PrintWriter out,List<?> values){
        for(int i=0;i<values.size();i++){
            if(i>0)out.print(',');Object value=values.get(i);String s=value==null?"":value instanceof Double&&!Double.isFinite((Double)value)?"":String.valueOf(value);
            out.print('"');out.print(s.replace("\"","\"\""));out.print('"');
        }out.println();
    }
    static String measures(Alpha99Findings.Finding f){
        if(f==null)return "";List<String> result=new ArrayList<>();
        for(Alpha99Findings.Measure m:f.measures)result.add(m.name+"|value="+m.value+"|unit="+m.unit+"|max="+m.genuineMax+"|sigma="+m.sigma+"|allowance="+m.allowance()+"|status="+m.status()+"|capped="+m.capAtWorth+"|reason="+m.capReason);
        return String.join(";",result);
    }
    static double baseGap(double[][] triangle,double track){
        int apex=0;for(int i=1;i<3;i++)if(Math.hypot(triangle[i][0],triangle[i][1])<Math.hypot(triangle[apex][0],triangle[apex][1]))apex=i;
        double x=0,y=0;for(int i=0;i<3;i++)if(i!=apex){x+=triangle[i][0]/2;y+=triangle[i][1]/2;}return track-Math.hypot(x,y);
    }
    static void gallery(Bitmap image,Mat rgba,double[] H,ModelSpec.Marker spec,MarkerMismatchResearch.Result q,Path path,String id)throws Exception{
        double[] c=spec.masterPoint();double half=spec.shape==ModelSpec.Shape.TRIANGLE?.25:.21;
        Mat rectified=Alpha98Closeups.render(rgba,H,c[0],c[1],half);
        Bitmap raw=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);Utils.matToBitmap(rectified,raw);rectified.release();
        // Offline inspection of WITHHELD polygons is explicitly labelled, never supplied to production.
        Bitmap annotated=raw.copy(Bitmap.Config.ARGB_8888,true);
        MarkerMismatchOverlay.closeup(annotated,c[0],c[1],half,q.score==null?q:new MarkerMismatchResearch.Result(q.score,"measured"));
        double[][] box={{c[0]-half,c[1]-half},{c[0]+half,c[1]-half},{c[0]+half,c[1]+half},{c[0]-half,c[1]+half}};
        double[][] projected=MarkerMismatchOverlay.project(box,H);double xmin=Double.POSITIVE_INFINITY,ymin=xmin,xmax=-xmin,ymax=-xmin;
        for(double[] p:projected){xmin=Math.min(xmin,p[0]);xmax=Math.max(xmax,p[0]);ymin=Math.min(ymin,p[1]);ymax=Math.max(ymax,p[1]);}
        int x=Math.max(0,(int)Math.floor(xmin)),y=Math.max(0,(int)Math.floor(ymin));
        int w=Math.min(image.getWidth(),(int)Math.ceil(xmax))-x,h=Math.min(image.getHeight(),(int)Math.ceil(ymax))-y;
        BufferedImage source=image.img.getSubimage(x,y,w,h),canvas=new BufferedImage(1440,640,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=canvas.createGraphics();g.setColor(new Color(12,20,32));g.fillRect(0,0,1440,640);g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,20));
        g.drawString(id+" · "+spec.hour+" o'clock · "+spec.kind(),16,26);
        String[] captions={"Source crop (display resized)","Frozen-pose rectified crop","Exact fitted polygons / regions"};
        for(int i=0;i<3;i++)g.drawString(captions[i],i*480+12,55);
        double scale=Math.min(480.0/w,480.0/h);int dw=(int)Math.round(w*scale),dh=(int)Math.round(h*scale);
        g.drawImage(source,(480-dw)/2,70+(480-dh)/2,dw,dh,null);g.drawImage(raw.img,480,70,null);g.drawImage(annotated.img,960,70,null);
        g.setFont(new Font("SansSerif",Font.PLAIN,17));
        g.drawString(q.score==null?"WITHHELD · no usable detected polygon; area unavailable":String.format(Locale.US,"EXPLORATORY%s · extra %.6f%% + missing %.6f%% = %.6f%% · filled expected denominator",q.trusted()?" / existing area checks cleared":" / WITHHELD",q.score.extraPct,q.score.missingPct,q.score.mismatchPct),12,575);
        g.drawString("Yellow expected · cyan detected · orange extra · magenta missing. Geometric area; physical edge uncertainty remains.",12,602);
        String reason=q.reason;g.drawString(reason.length()>150?reason.substring(0,150):reason,12,627);g.dispose();ImageIO.write(canvas,"png",path.toFile());
    }
}
