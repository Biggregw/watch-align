package com.watchalign.mobile;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs the frozen Alpha90 batch first, then performs a completely second-stage marker review.
 * Reddit RL/GL labels and defect notes are not read here. The blind page contains only the full
 * Alpha90 overlay and marker close-ups selected from geometric excursion against that fixed master.
 */
@RunWith(AndroidJUnit4.class)
public class Alpha90SelectiveReportingTest {
    @BeforeClass public static void initOpenCv(){
        assertTrue("OpenCV failed to initialise",OpenCVLoader.initLocal());
    }

    @Test public void runFrozenBatchThenBuildBlindSelectiveReview() throws Exception {
        // Keep the existing frozen batch harness as the single source of Alpha90 output.
        new Alpha90BatchValidationTest().runFrozenAlpha90AcrossFixtureSet();
        buildBlindSelectiveReview();
    }

    private static void buildBlindSelectiveReview() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File out=new File(app.getFilesDir(),"alpha90-batch");
        File results=new File(out,"results.csv");
        assertTrue("Alpha90 results.csv missing",results.isFile());

        List<List<String>> rows=readCsv(results);
        assertTrue("Alpha90 results.csv empty",rows.size()>1);
        Map<String,Integer> col=headerMap(rows.get(0));
        for(String need:new String[]{"case_id","asset","status","dial_cx","dial_cy","dial_radius"})
            assertTrue("Alpha90 results missing column "+need,col.containsKey(need));

        StringBuilder csv=new StringBuilder();
        csv.append("blind_id,case_id,asset,hour,level,excursion_px,excursion_over_dial_r,reason\n");
        StringBuilder key=new StringBuilder("blind_id,case_id,asset\n");
        StringBuilder html=new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'><title>Alpha90 blind selective review</title>")
            .append("<style>body{font-family:sans-serif;margin:24px;background:#111;color:#eee}.case{border:1px solid #555;padding:16px;margin:18px 0;border-radius:10px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:12px}.grid img{width:100%;height:auto;border:1px solid #555}.crop{position:relative}.controls button{margin-right:8px;padding:8px 12px}.small{opacity:.8;font-size:.9em}</style>")
            .append("<script>function blink(id){const a=document.getElementById(id+'a'),b=document.getElementById(id+'b');let n=0;window.clearInterval(window[id]);window[id]=setInterval(()=>{const on=(n++%2)==0;a.style.display=on?'none':'block';b.style.display=on?'block':'none';if(n>=8){clearInterval(window[id]);a.style.display='none';b.style.display='block';}},700)}function stopBlink(id){clearInterval(window[id]);document.getElementById(id+'a').style.display='none';document.getElementById(id+'b').style.display='block'}</script></head><body>")
            .append("<h1>Alpha90 blind selective marker review</h1>")
            .append("<p>Frozen Alpha90 pose and projected genuine master. Candidate markers are inspected only after pose is complete. Reddit labels and defect descriptions are hidden from this page.</p>")
            .append("<p>All applied hour markers in the frozen master are checked. The date position at 3 is not an applied marker. Minute ticks are deliberately not scored here because Alpha90 already uses them as pose evidence.</p>")
            .append("<p class='small'>Provisional review triggers: borderline at ")
            .append(String.format(Locale.US,"%.3fR",Alpha90SelectiveMarkerReporter.BORDERLINE_EXCURSION_R))
            .append(", clear difference at ")
            .append(String.format(Locale.US,"%.3fR",Alpha90SelectiveMarkerReporter.CLEAR_EXCURSION_R))
            .append(". These are review triggers, not QC tolerances.</p>");

        int blind=0;
        for(int ri=1;ri<rows.size();ri++) {
            List<String> row=rows.get(ri);
            if(!"ACCEPTED".equals(get(row,col,"status")))continue;
            String caseId=get(row,col,"case_id");
            String asset=get(row,col,"asset");
            String stem=stem(asset);
            double cx=parse(get(row,col,"dial_cx"));
            double cy=parse(get(row,col,"dial_cy"));
            double radius=parse(get(row,col,"dial_radius"));
            File dir=new File(out,stem);
            Bitmap candidate=BitmapFactory.decodeFile(new File(dir,"candidate.png").getAbsolutePath());
            Bitmap overlay=BitmapFactory.decodeFile(new File(dir,"overlay.png").getAbsolutePath());
            assertTrue("saved candidate missing for "+asset,candidate!=null);
            assertTrue("saved overlay missing for "+asset,overlay!=null);

            Alpha90SelectiveMarkerReporter.Report report=Alpha90SelectiveMarkerReporter.analyse(candidate,overlay,cx,cy,radius);
            String blindId=String.format(Locale.US,"B%02d",++blind);
            key.append(csv(blindId)).append(',').append(csv(caseId)).append(',').append(csv(asset)).append('\n');
            for(Alpha90SelectiveMarkerReporter.Feature f:report.features) {
                csv.append(csv(blindId)).append(',').append(csv(caseId)).append(',').append(csv(asset)).append(',')
                   .append(f.hour).append(',').append(f.level).append(',').append(num(f.excursionPx)).append(',')
                   .append(num6(f.excursionOverDialR)).append(',').append(csv(f.reason)).append('\n');
            }

            html.append("<section class='case'><h2>").append(blindId).append("</h2>")
                .append("<div class='grid'><div><h3>Full Alpha90 overlay</h3><img src='").append(stem).append("/composite.png'></div></div>");
            if(report.shown()==0) {
                html.append("<p><b>No marker close-ups triggered.</b></p>");
            } else {
                html.append("<h3>Markers selected for human inspection</h3><div class='grid'>");
                for(Alpha90SelectiveMarkerReporter.Feature f:report.features) {
                    if(!f.showCloseUp())continue;
                    String base=String.format(Locale.US,"h%02d",f.hour);
                    String id=blindId+"_"+base;
                    html.append("<div class='crop'><h3>").append(f.hour).append(" o'clock</h3>")
                        .append("<div class='controls'><button onclick=\"blink('").append(id).append("')\">Blink 4×</button><button onclick=\"stopBlink('").append(id).append("')\">Stop</button></div>")
                        .append("<img id='").append(id).append("a' style='display:none' src='").append(stem).append('/').append(base).append("_candidate.png'>")
                        .append("<img id='").append(id).append("b' src='").append(stem).append('/').append(base).append("_overlay.png'></div>");
                }
                html.append("</div>");
            }
            if(report.unassessable()>0)html.append("<p class='small'>").append(report.unassessable()).append(" marker positions had no sufficiently reliable candidate boundary and were not surfaced.</p>");
            html.append("</section>");
        }

        html.append("<p class='small'>Open selective_key.csv only after the blind visual review to map B-cases back to the independent Reddit/control metadata.</p></body></html>");
        write(new File(out,"selective_report.csv"),csv.toString());
        write(new File(out,"selective_key.csv"),key.toString());
        write(new File(out,"selective_blind.html"),html.toString());
        assertTrue("No accepted Alpha90 cases available for selective review",blind>0);
    }

    private static List<List<String>> readCsv(File f) throws Exception {
        List<List<String>> out=new ArrayList<>();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String line;while((line=br.readLine())!=null)out.add(parseCsvLine(line));
        }
        return out;
    }

    private static List<String> parseCsvLine(String s) {
        List<String> out=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='\"'){
                if(q&&i+1<s.length()&&s.charAt(i+1)=='\"'){b.append('\"');i++;}
                else q=!q;
            }else if(c==','&&!q){out.add(b.toString());b.setLength(0);}else b.append(c);
        }
        out.add(b.toString());return out;
    }

    private static Map<String,Integer> headerMap(List<String> h){Map<String,Integer> m=new HashMap<>();for(int i=0;i<h.size();i++)m.put(h.get(i),i);return m;}
    private static String get(List<String> r,Map<String,Integer> c,String k){Integer i=c.get(k);return i==null||i>=r.size()?"":r.get(i);}
    private static double parse(String s){try{return Double.parseDouble(s);}catch(Throwable t){return Double.NaN;}}
    private static String stem(String s){return s.replaceAll("\\.[^.]+$","");}
    private static String num(double d){return Double.isFinite(d)?String.format(Locale.US,"%.3f",d):"";}
    private static String num6(double d){return Double.isFinite(d)?String.format(Locale.US,"%.6f",d):"";}
    private static String csv(String s){if(s==null)return "";return "\""+s.replace("\"","\"\"")+"\"";}
    private static void write(File f,String s)throws Exception{try(FileOutputStream o=new FileOutputStream(f)){o.write(s.getBytes(StandardCharsets.UTF_8));}}
}
