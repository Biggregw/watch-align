package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.CvType;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;

/** Offline stage comparison. Input manifest: id,path,decoder (opencv or imageio).
 * Usage: run.sh BatgirlInputAudit manifest.csv results.csv
 * Frozen-pose rows are diagnostic interventions, never production QC decisions.
 */
public class BatgirlInputAudit {
    public static void main(String[] args)throws Exception{
        nu.pattern.OpenCV.loadLocally();
        if(args.length==1&&"--self-test".equals(args[0])){selfTest();return;}
        if(args.length==2&&"--pipeline-check".equals(args[0])){
            Alpha99Pipeline.Output result=Alpha99Pipeline.run(Alpha96Calib.loadAlpha96(args[1]),HarnessModel.spec(),HarnessModel.ref());
            if(!result.ok())throw new AssertionError("Pipeline failed");
            System.out.println(result.technical);return;
        }
        ModelSpec model=HarnessModel.spec();ModelReference ref=HarnessModel.ref();
        double[] baselineH=null;int baselineW=0,baselineHeight=0;
        try(PrintWriter csv=new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))){
            csv.println("id,mode,decoder,file_sha256,width,height,rgba_sha256,gray_sha256,pose_valid,reason,dial_R_px,refit_ticks,refit_sectors,refit_tick_rms_px,refit_branch_turn,H,ring_model,ring_n,ring_rotation_deg,raw_rotation_deg,qc_signed_rotation_deg,lower_edge_deg,upper_edge_deg,width_dev_px,width_limit_px,fit_coverage,fit_integrity,genuine_max_deg,sigma_deg,allowance_deg,clear_boundary_deg,margin_to_clear_deg,status,position_withheld,area_withheld");
            java.util.List<String> lines=Files.readAllLines(Path.of(args[0]));
            for(int row=1;row<lines.size();row++){
                String[] fields=lines.get(row).split(",",-1);String id=fields[0],decoder=fields[2];
                if(!decoder.equals("opencv")&&!decoder.equals("imageio"))throw new IllegalArgumentException("Unknown decoder "+decoder);
                Path path=Path.of(fields[1]);Bitmap image="imageio".equals(decoder)?Load.imageio(path.toString()):Alpha96Calib.loadAlpha96(path.toString());
                if(image==null)throw new IllegalArgumentException("Cannot decode "+id);
                Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(image,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                String fileHash=hash(Files.readAllBytes(path)),rgbHash=Alpha99Pipeline.pixelSha256(rgba),grayHash=Alpha99Pipeline.pixelSha256(gray);
                AutomaticDialOverlay.Result pose=AutomaticDialOverlay.build(image,model);
                if(!pose.valid){
                    Object[] failed=new Object[35];Arrays.fill(failed,"");
                    Object[] input={id,"refit",decoder,fileHash,image.getWidth(),image.getHeight(),rgbHash,grayHash,false,pose.reason};
                    System.arraycopy(input,0,failed,0,input.length);emit(csv,failed);
                    csv.flush();rgba.release();gray.release();continue;
                }
                write(csv,id,"refit",decoder,fileHash,rgbHash,grayHash,image,gray,pose,pose.homography,model,ref);
                if(baselineH==null){baselineH=pose.homography.clone();baselineW=image.getWidth();baselineHeight=image.getHeight();}
                else{
                    // Pixel-centre resize mapping: destination = scale*(source+0.5)-0.5.
                    double sx=image.getWidth()/(double)baselineW,sy=image.getHeight()/(double)baselineHeight;
                    double[] frozen=scaled(baselineH,sx,sy);
                    write(csv,id,"frozen_source_pose",decoder,fileHash,rgbHash,grayHash,image,gray,pose,frozen,model,ref);
                }
                csv.flush();rgba.release();gray.release();System.err.println("finished "+id);
            }
        }
    }
    static double[] scaled(double[] H,double sx,double sy){
        double tx=(sx-1)/2,ty=(sy-1)/2;double[] result=H.clone();
        for(int i=0;i<3;i++){result[i]=sx*H[i]+tx*H[6+i];result[3+i]=sy*H[3+i]+ty*H[6+i];}
        return result;
    }
    private static void write(PrintWriter out,String id,String mode,String decoder,String fileHash,String rgbHash,String grayHash,Bitmap image,Mat gray,
                              AutomaticDialOverlay.Result pose,double[] H,ModelSpec model,ModelReference ref){
        Alpha94MarkerMeasurement.Report report=Alpha94MarkerMeasurement.analyse(image,H,model);
        Alpha99MarkerInterference.Check check=Alpha99MarkerInterference.analyse(gray,H,model).get(9);
        ModelSpec.Marker spec=model.atHour(9);Alpha94MarkerMeasurement.Marker marker=report.atHour(9);
        Alpha99Findings.Finding finding=Alpha99Findings.baton(report,spec,report.dialRadiusPx,check,ref,model.resolutionMatched.contains(spec.key+"_off"));
        MarkerMismatchResearch.Result area=MarkerMismatchResearch.score(report,spec,ref,finding,check);
        BatonFitDiagnostic.Angles angles=BatonFitDiagnostic.angles(ExpectedMarkerOutline.polygon(spec,ref,report.ring.model),marker.fitVertices);
        Alpha99Findings.Measure rotation=null;for(Alpha99Findings.Measure m:finding.measures)if(m.name.equals("rotation"))rotation=m;
        double width=marker.sideOffR==null?Double.NaN:(marker.sideOffR[0]+marker.sideOffR[2]-ref.sigma("nine_width_agreement","nominal"))*report.dialRadiusPx;
        Object[] values={id,mode,decoder,fileHash,image.getWidth(),image.getHeight(),rgbHash,grayHash,true,"",
                report.dialRadiusPx,pose.detectedTicks,pose.completePairs,pose.fitAfter,pose.markerBranchTurn,array(H),array(report.ring.model),report.ring.n,report.ring.rotationDeg,
                marker.rotationDeg,marker.rotationDeg-ref.nominal("nine_rot"),angles==null?Double.NaN:angles.side0Deg,angles==null?Double.NaN:angles.side2Deg,
                width,ref.limit("nine_width_agreement")*report.dialRadiusPx,array(marker.sideCov),1-marker.fitScorePx,
                rotation==null?Double.NaN:rotation.genuineMax,rotation==null?Double.NaN:rotation.sigma,rotation==null?Double.NaN:rotation.allowance(),
                rotation==null?Double.NaN:rotation.genuineMax+rotation.allowance(),rotation==null?Double.NaN:rotation.value-rotation.genuineMax-rotation.allowance(),
                finding.status,finding.partNotAssessed==null?"":finding.partNotAssessed,area.trusted()?"":area.reason};
        emit(out,values);
    }
    private static void emit(PrintWriter out,Object[] values){
        for(int i=0;i<values.length;i++){if(i>0)out.print(',');Object v=values[i];out.print(v instanceof Double?String.format(Locale.US,"%.12f",v):safe(String.valueOf(v)));}out.println();
    }
    static String hash(byte[] bytes)throws Exception{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void selfTest()throws Exception{
        Mat pixels=new Mat(2,3,CvType.CV_8UC1);pixels.put(0,0,new byte[]{0,1,2,3,4,5});
        if(!hash(new byte[]{0,1,2,3,4,5}).equals(Alpha99Pipeline.pixelSha256(pixels)))throw new AssertionError("Contiguous fingerprint");
        Mat roi=pixels.submat(new Rect(1,0,2,2));
        if(!hash(new byte[]{1,2,4,5}).equals(Alpha99Pipeline.pixelSha256(roi)))throw new AssertionError("Strided fingerprint");
        Mat rgba=new Mat(1,2,CvType.CV_8UC4);rgba.put(0,0,new byte[]{1,2,3,(byte)255,4,5,6,(byte)255});
        String before=Alpha99Pipeline.pixelSha256(rgba);
        byte[] bytes=new byte[8];rgba.get(0,0,bytes);
        if(!hash(bytes).equals(before))throw new AssertionError("RGBA fingerprint");
        rgba.put(0,0,new byte[]{1,2,3,(byte)254,4,5,6,(byte)255});
        if(before.equals(Alpha99Pipeline.pixelSha256(rgba)))throw new AssertionError("Alpha included");
        double[] H={300,4,600,-3,290,700,.001,-.002,1},original=H.clone();
        if(!Arrays.equals(H,scaled(H,1,1)))throw new AssertionError("Identity pose");
        double[] resized=scaled(H,.8,.9);
        double x=.3,y=-.5,z=H[6]*x+H[7]*y+H[8];
        for(int axis=0;axis<2;axis++){
            double expected=(axis==0?.8:.9)*((H[3*axis]*x+H[3*axis+1]*y+H[3*axis+2])/z+.5)-.5;
            double actual=(resized[3*axis]*x+resized[3*axis+1]*y+resized[3*axis+2])/z;
            if(Math.abs(expected-actual)>1e-12)throw new AssertionError("Pixel-centre homography");
        }
        if(!Arrays.equals(H,original))throw new AssertionError("Original pose mutated");
        roi.release();pixels.release();rgba.release();System.out.println("7 diagnostic checks passed");
    }
    private static String array(double[] values){return values==null?"":Arrays.toString(values).replace(", ",";");}
    private static String safe(String s){return s.replace(',',';').replace('\n',' ');}
}
