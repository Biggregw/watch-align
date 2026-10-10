package com.watchalign.redditqc;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import android.text.method.ScrollingMovementMethod;
import android.content.ContentResolver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Standalone research collector. Does not touch the Watch Align analyser or its thresholds. */
public final class MainActivity extends Activity {
    private static final long MAX_IMAGE = 22L * 1024 * 1024;
    private static final long PART_PAYLOAD = 25L * 1024 * 1024;
    private static final long MAX_ZIP = 28L * 1024 * 1024;
    private static final String OUTPUT_FOLDER = "WatchAlign_Reddit_QC";
    private TextView status, logView;
    private Button testButton, allButton, stopButton;
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private volatile boolean busy = false;

    private static final class CaseRow {
        String id, model, family, factory, url, album, feature, review, label;
    }
    private static final class Media {
        String name;
        final ArrayList<String> urls = new ArrayList<>();
        Media(String n) { name = n; }
        void add(String url) {
            if(url == null || url.isEmpty()) return;
            url = url.replace("&amp;", "&").replace("&#38;", "&");
            if (url.startsWith("https://") && !urls.contains(url)) urls.add(url);
        }
    }
    private static final class Photo {
        File file;
        String model, caseIds, postId, sourceUrl, imageUrl, sha, features, label, factory;
        int width, height;
    }
    private static final class Download {
        byte[] bytes;
        String url;
        Download(byte[] b, String u) { bytes=b; url=u; }
    }
    private final StringBuilder allErrors = new StringBuilder("case_ids,post_id,model,status,details\n");
    private int downloaded=0, postsDone=0, imagesFailed=0, postsFailed=0, duplicates=0;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(12));
        layout.setBackgroundColor(Color.rgb(247,247,247));
        ScrollView scroll = new ScrollView(this);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setText("Watch Align  |  Reddit QC Collector");
        heading.setTextSize(22); heading.setTextColor(Color.BLACK);
        inner.addView(heading);
        TextView expl = new TextView(this);
        expl.setText("\nOne-off research download for the 49 saved Reddit QC posts. Saves model-specific ZIPs smaller than 28 MB to Downloads/"+OUTPUT_FOLDER+".\n\nTest one thread first. Photos are not yet validated as face-on or suitable for calibration.\n");
        expl.setTextSize(15); expl.setTextColor(Color.DKGRAY);
        inner.addView(expl);
        testButton = button("TEST FIRST REDDIT THREAD", inner, v -> start(true));
        allButton = button("COLLECT ALL 49 THREADS", inner, v -> start(false));
        stopButton = button("STOP AFTER CURRENT DOWNLOAD", inner, v -> {
            stop.set(true);
            status.setText("Stopping after current transfer...");
        });
        stopButton.setEnabled(false);
        status = new TextView(this);
        status.setText("Ready. Results appear in Downloads/"+OUTPUT_FOLDER);
        status.setTextSize(15); status.setTextColor(Color.BLACK);
        status.setPadding(0, dp(16), 0, dp(8));
        inner.addView(status);
        logView = new TextView(this);
        logView.setTextSize(13);
        logView.setTextColor(Color.DKGRAY);
        logView.setText("No collection started.");
        logView.setMovementMethod(new ScrollingMovementMethod());
        inner.addView(logView);
        scroll.addView(inner);
        layout.addView(scroll);
        setContentView(layout);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private Button button(String text, LinearLayout parent, View.OnClickListener action) {
        Button b=new Button(this); b.setText(text); b.setOnClickListener(action); parent.addView(b); return b;
    }
    private void ui(String msg) {
        runOnUiThread(() -> {
            status.setText(msg);
            String s=logView.getText().toString();
            if(s.length()>10000) s=s.substring(s.length()-8000);
            logView.setText(s+"\n"+msg);
        });
    }
    private void start(boolean test) {
        if(busy) return;
        busy=true; stop.set(false);
        downloaded=0; imagesFailed=0; postsFailed=0; duplicates=0; postsDone=0;
        allErrors.setLength(0); allErrors.append("case_ids,post_id,model,status,details\n");
        testButton.setEnabled(false); allButton.setEnabled(false); stopButton.setEnabled(true);
        logView.setText("");
        new Thread(() -> {
            try { collect(test); }
            catch(Exception ex){ ui("FAILED: "+ex.getClass().getSimpleName()+": "+ex.getMessage()); }
            finally {
                busy=false;
                runOnUiThread(()->{testButton.setEnabled(true);allButton.setEnabled(true);stopButton.setEnabled(false);});
            }
        }, "reddit-qc-harvest").start();
    }
    private ArrayList<CaseRow> readSeed() throws Exception {
        ArrayList<CaseRow> rows=new ArrayList<>();
        try(BufferedReader in=new BufferedReader(new InputStreamReader(getAssets().open("cases.csv"), StandardCharsets.UTF_8))){
            String header=in.readLine();
            if(header==null)throw new IOException("Research CSV empty");
            List<String> cols=csvSplit(header);
            String line;
            while((line=in.readLine())!=null){
                if(line.isEmpty()) continue;
                List<String> vals=csvSplit(line);
                HashMap<String,String> m=new HashMap<>();
                for(int i=0;i<cols.size() && i<vals.size();i++)m.put(cols.get(i),vals.get(i));
                CaseRow c=new CaseRow();
                c.id=m.getOrDefault("case_id","");
                c.family=m.getOrDefault("watch_family","Unknown");
                c.model=m.getOrDefault("reference",c.family);
                c.factory=m.getOrDefault("factory","");
                c.url=m.getOrDefault("reddit_url","");
                c.album=m.getOrDefault("reported_album_url","");
                c.feature=m.getOrDefault("primary_feature","");
                c.review=m.getOrDefault("paraphrased_evidence","");
                c.label=m.getOrDefault("provisional_label","");
                if(c.url.contains("/comments/"))rows.add(c);
            }
        }
        return rows;
    }
    static List<String> csvSplit(String line) {
        ArrayList<String> a=new ArrayList<>();StringBuilder s=new StringBuilder();boolean quote=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if(c=='"'){if(quote&&i+1<line.length()&&line.charAt(i+1)=='"'){s.append('"');i++;}else quote=!quote;}
            else if(c==','&&!quote){a.add(s.toString());s.setLength(0);}
            else s.append(c);
        } a.add(s.toString());return a;
    }
    private static String q(String s){return "\""+(s==null?"":s).replace("\"","\"\"").replace("\n"," ").replace("\r"," ")+"\"";}
    private static String safe(String s){return s.replaceAll("[^A-Za-z0-9_.-]","_").replaceAll("_+","_");}
    private static String idFromUrl(String url) {
        int p=url.indexOf("/comments/");
        if(p<0)return "";
        String z=url.substring(p+10);
        int e=z.indexOf('/');return (e<0?z:z.substring(0,e)).replaceAll("[^a-zA-Z0-9]","");
    }
    private static String caseIds(List<CaseRow> rows) {
        StringJoiner j=new StringJoiner("|");for(CaseRow r:rows)j.add(r.id);return j.toString();
    }
    private void fail(String ids,String pid,String model,String kind,String detail) {
        allErrors.append(q(ids)).append(',').append(q(pid)).append(',').append(q(model)).append(',')
                .append(q(kind)).append(',').append(q(detail)).append('\n');
    }
    private static byte[] fetch(String url, int maxBytes) throws Exception {
        URL u=new URL(url);
        if(!"https".equalsIgnoreCase(u.getProtocol()))throw new IOException("HTTPS required");
        HttpURLConnection c=(HttpURLConnection)u.openConnection();
        c.setConnectTimeout(16000);c.setReadTimeout(35000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 17; Mobile) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36");
        c.setRequestProperty("Accept","application/json,image/avif,image/webp,image/*,*/*;q=0.7");
        c.setRequestProperty("Accept-Language","en-GB,en;q=0.9");
        try {
            int code=c.getResponseCode();
            if(code!=200)throw new IOException("HTTP "+code+" on "+u.getHost());
            try(InputStream in=new BufferedInputStream(c.getInputStream());ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buf=new byte[32768];int n;
                while((n=in.read(buf))!=-1){
                    if(out.size()+n>maxBytes)throw new IOException("too large");
                    out.write(buf,0,n);
                }
                return out.toByteArray();
            }
        }finally{c.disconnect();}
    }
    private static void addCandidates(Media m, JSONObject src) {
        if(src==null)return;
        JSONObject s=src.optJSONObject("s");
        if(s!=null)m.add(s.optString("u", ""));
        JSONArray p=src.optJSONArray("p");
        if(p!=null)for(int j=p.length()-1;j>=0;j--){
            JSONObject obj=p.optJSONObject(j);if(obj!=null)m.add(obj.optString("u",""));
        }
    }
    private static ArrayList<Media> photos(JSONObject post) {
        ArrayList<Media> results=new ArrayList<>();
        JSONObject meta=post.optJSONObject("media_metadata");
        JSONArray gal=post.optJSONObject("gallery_data")==null?null:post.optJSONObject("gallery_data").optJSONArray("items");
        if(gal!=null){
            for(int i=0;i<gal.length();i++) {
                JSONObject item=gal.optJSONObject(i);
                if(item==null)continue;
                String mid=item.optString("media_id","");
                Media m=new Media("gallery_"+String.format(Locale.US,"%02d",i+1));
                JSONObject source=meta==null?null:meta.optJSONObject(mid);
                if(source!=null){
                    String mt=source.optString("m","image/jpeg");
                    String ext=mt.contains("png")?"png":mt.contains("webp")?"webp":mt.contains("gif")?"gif":"jpg";
                    if(!mid.isEmpty())m.add("https://i.redd.it/"+mid+"."+ext);
                    addCandidates(m,source);
                }
                if(!m.urls.isEmpty())results.add(m);
            }
        }
        if(results.isEmpty()){
            String direct=post.optString("url_overridden_by_dest",post.optString("url",""));
            Media m=new Media("primary");
            if(direct.startsWith("https://i.redd.it/")||direct.startsWith("https://preview.redd.it/")||
                direct.startsWith("https://i.imgur.com/"))m.add(direct);
            JSONObject preview=post.optJSONObject("preview");
            JSONArray images=preview==null?null:preview.optJSONArray("images");
            if(images!=null && images.length()>0){
                JSONObject first=images.optJSONObject(0);
                if(first!=null){
                    JSONObject src=first.optJSONObject("source");
                    if(src!=null)m.add(src.optString("url",""));
                    JSONArray resolutions=first.optJSONArray("resolutions");
                    if(resolutions!=null)for(int i=resolutions.length()-1;i>=0;i--){
                        JSONObject size=resolutions.optJSONObject(i);if(size!=null)m.add(size.optString("url",""));
                    }
                }
            }
            if(!m.urls.isEmpty())results.add(m);
        }
        return results;
    }
    private static boolean imageData(byte[] b){
        return (b.length>=4 && (b[0]&255)==255 && (b[1]&255)==216) ||
                (b.length>=8 && (b[0]&255)==137 && b[1]==80 && b[2]==78 && b[3]==71) ||
                (b.length>=12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F') ||
                (b.length>=6 && b[0]=='G' && b[1]=='I' && b[2]=='F');
    }
    private static String extension(byte[] b){
        if((b[0]&255)==255)return ".jpg";
        if((b[0]&255)==137)return ".png";
        if(b[0]=='R')return ".webp";
        return ".gif";
    }
    private static String sha(byte[] b)throws Exception{
        MessageDigest m=MessageDigest.getInstance("SHA-256");
        StringBuilder s=new StringBuilder();
        for(byte v:m.digest(b))s.append(String.format(Locale.US,"%02x",v&255));
        return s.toString();
    }
    private static String joinFeature(List<CaseRow> cases,boolean labels) {
        StringJoiner j=new StringJoiner(" | ");
        for(CaseRow c:cases)j.add(labels?c.label:c.feature);
        return j.toString();
    }
    private void collect(boolean one) throws Exception {
        ArrayList<CaseRow> seed=readSeed();
        LinkedHashMap<String,ArrayList<CaseRow>> posts=new LinkedHashMap<>();
        for(CaseRow c:seed) posts.computeIfAbsent(c.url,k->new ArrayList<>()).add(c);
        if(posts.size()!=49)ui("WARNING: Expected 49 distinct threads; seed has "+posts.size());
        File root=new File(getCacheDir(),"RedditQC_"+System.currentTimeMillis());
        if(!root.mkdirs())throw new IOException("Cannot create temporary folder");
        LinkedHashMap<String,ArrayList<Photo>> grouped=new LinkedHashMap<>();
        HashSet<String> hashes=new HashSet<>();
        int idx=0,limit=one?1:posts.size();
        try {
            for(Map.Entry<String,ArrayList<CaseRow>> e: posts.entrySet()){
                if(stop.get() || idx>=limit)break;
                idx++;
                ArrayList<CaseRow> cases=e.getValue();
                CaseRow first=cases.get(0);
                String model=safe(first.model), pid=idFromUrl(first.url), ids=caseIds(cases);
                ui("Thread "+idx+"/"+limit+": "+pid+" ("+model+")");
                if(!first.album.isEmpty())fail(ids,pid,model,"external_album_not_collected",first.album);
                String json;
                JSONObject post;
                try {
                    byte[] b=fetch("https://www.reddit.com/comments/"+pid+"/.json?raw_json=1&limit=200",10*1024*1024);
                    json=new String(b,StandardCharsets.UTF_8);
                    JSONArray payload=new JSONArray(json);
                    post=payload.getJSONObject(0).getJSONObject("data")
                            .getJSONArray("children").getJSONObject(0).getJSONObject("data");
                    // The JSON contains the post and reviewer comments.
                    File saved = new File(root,"comments_"+pid+".json");
                    try(FileOutputStream out=new FileOutputStream(saved)){out.write(b);}
                }catch(Exception ex){
                    postsFailed++;fail(ids,pid,model,"post_metadata_failed",ex.toString());
                    ui("Post failed: "+pid+" "+ex.getMessage());continue;
                }
                ArrayList<Media> media=photos(post);
                if(media.isEmpty()){
                    fail(ids,pid,model,"no_reddit_media","No direct image/gallery metadata; external album may be needed");
                }
                int mindex=0;
                for(Media m:media){
                    if(stop.get())break;
                    mindex++;
                    Download found=null;StringBuilder errors=new StringBuilder();
                    for(String url:m.urls) {
                        try {
                            byte[] bytes=fetch(url,(int)MAX_IMAGE);
                            if(!imageData(bytes))throw new IOException("Response is not a JPEG/PNG/WebP/GIF image");
                            found=new Download(bytes,url);break;
                        }catch(Exception ex){errors.append(ex.getMessage()).append(" | ");}
                    }
                    if(found==null){
                        imagesFailed++;
                        fail(ids,pid,model,"image_download_failed",m.name+" "+errors);
                        continue;
                    }
                    String fingerprint=sha(found.bytes);
                    if(!hashes.add(fingerprint)){
                        duplicates++;fail(ids,pid,model,"duplicate_image",m.name+" sha256="+fingerprint);continue;
                    }
                    BitmapFactory.Options opts=new BitmapFactory.Options();
                    opts.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(found.bytes,0,found.bytes.length,opts);
                    int width=opts.outWidth,height=opts.outHeight;
                    if(width<400||height<400){
                        fail(ids,pid,model,"too_small",m.name+" "+width+"x"+height);
                        continue;
                    }
                    File imageFolder=new File(root,model);imageFolder.mkdirs();
                    File file=new File(imageFolder,pid+"_"+safe(m.name)+extension(found.bytes));
                    try(FileOutputStream out=new FileOutputStream(file)){out.write(found.bytes);}
                    Photo p=new Photo();p.file=file;p.model=model;p.caseIds=ids;p.postId=pid;
                    p.sourceUrl=first.url;p.imageUrl=found.url;p.sha=fingerprint;p.width=width;p.height=height;
                    p.features=joinFeature(cases,false);p.label=joinFeature(cases,true);p.factory=first.factory;
                    grouped.computeIfAbsent(model,k->new ArrayList<>()).add(p);
                    downloaded++;
                }
                postsDone++;
                ui("Downloaded "+downloaded+" images; processed "+postsDone+" threads");
                if(!one && !stop.get())Thread.sleep(900);
            }
            int archives=0;
            for(Map.Entry<String,ArrayList<Photo>> e:grouped.entrySet()){
                if(e.getValue().isEmpty())continue;
                archives+=exportModel(root,e.getKey(),e.getValue(),seed);
            }
            exportReport(root, seed, one);
            ui("DONE: "+postsDone+" threads; "+downloaded+" usable-resolution images; "+
                imagesFailed+" failed downloads; "+duplicates+" duplicates; "+archives+
                " image ZIPs. Check Downloads/"+OUTPUT_FOLDER);
            if(stop.get())ui("Partial run: stopped at your request.");
        } finally {
            deleteRecursive(root);
        }
    }
    private static String manifestHeader() {
        return "case_ids,post_id,model,factory,feature,provisional_reviewer_label,reddit_post_url,image_url,sha256,width,height,calibration_status\n";
    }
    private static String manifestRow(Photo p) {
        return q(p.caseIds)+","+q(p.postId)+","+q(p.model)+","+q(p.factory)+","+
            q(p.features)+","+q(p.label)+","+q(p.sourceUrl)+","+q(p.imageUrl)+","+
            q(p.sha)+","+p.width+","+p.height+","+q("unreviewed_photo_not_calibration_eligible")+"\n";
    }
    private int exportModel(File root,String model,List<Photo> images,List<CaseRow> seed) throws Exception {
        List<List<Photo>> parts=new ArrayList<>();
        List<Photo> now=new ArrayList<>();long bytes=0;
        for(Photo p:images) {
            if(!now.isEmpty() && bytes+p.file.length()>PART_PAYLOAD){
                parts.add(now);now=new ArrayList<>();bytes=0;
            }
            now.add(p);bytes+=p.file.length();
        }
        if(!now.isEmpty())parts.add(now);
        int count=0;
        for(int i=0;i<parts.size();i++) {
            String name="Reddit_QC_"+model+"_part"+String.format(Locale.US,"%02d",i+1)+
                    "_of"+String.format(Locale.US,"%02d",parts.size())+".zip";
            File zip=new File(root,name);
            try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))){
                out.setLevel(1);
                StringBuilder manifest=new StringBuilder(manifestHeader());
                HashSet<String> postIds=new HashSet<>();
                for(Photo p:parts.get(i)){
                    put(out,"photos/"+p.file.getName(),p.file);
                    manifest.append(manifestRow(p));
                    postIds.add(p.postId);
                }
                put(out,"manifest.csv",manifest.toString());
                put(out,"research_cases.csv",assetBytes("cases.csv"));
                for(String pid:postIds){
                    File json=new File(root,"comments_"+pid+".json");
                    if(json.exists() && json.length()<2*1024*1024)
                        put(out,"reddit_json/"+pid+".json",json);
                }
                put(out,"README.txt",
                    "Original QC image files and per-image source mapping. Reviewer labels are PROVISIONAL.\n"+
                    "Image face-on suitability, obstruction and independent geometry have NOT been assessed.\n"+
                    "Never derive manufacturing tolerances directly from replica QC images.\n"+
                    "The metadata includes original Reddit JSON and reviewer comments where available.\n");
            }
            if(zip.length()>=MAX_ZIP)throw new IOException("ZIP >=28 MB: "+name);
            saveDownloads(zip,name);count++;ui("Saved "+name+" ("+(zip.length()/1048576)+" MiB)");
        }
        return count;
    }
    private void exportReport(File root,List<CaseRow> seed,boolean test) throws Exception {
        String name=(test?"Reddit_QC_TEST_Report.zip":"Reddit_QC_Collection_Report.zip");
        File f=new File(root,name);
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(f))){
            put(out,"source_research_cases.csv",assetBytes("cases.csv"));
            put(out,"collection_errors.csv",allErrors.toString());
            put(out,"README.txt","Threads completed: "+postsDone+"\nImages downloaded: "+downloaded+
                    "\nImages failed: "+imagesFailed+"\nMetadata failures: "+postsFailed+
                    "\nDuplicates: "+duplicates+"\nAll labels provisional. Do not set numeric thresholds yet.\n");
        }
        saveDownloads(f,name);
    }
    private byte[] assetBytes(String n)throws IOException {
        try(InputStream in=getAssets().open(n);ByteArrayOutputStream b=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int k;while((k=in.read(buf))!=-1)b.write(buf,0,k);return b.toByteArray();
        }
    }
    private static void put(ZipOutputStream z,String name,File f)throws IOException{
        z.putNextEntry(new ZipEntry(name));
        try(InputStream in=new BufferedInputStream(new FileInputStream(f))){
            byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)z.write(b,0,n);
        } z.closeEntry();
    }
    private static void put(ZipOutputStream z,String name,String str)throws IOException{
        put(z,name,str.getBytes(StandardCharsets.UTF_8));
    }
    private static void put(ZipOutputStream z,String name,byte[] bytes)throws IOException{
        z.putNextEntry(new ZipEntry(name));z.write(bytes);z.closeEntry();
    }
    private void saveDownloads(File source,String name)throws Exception {
        ContentResolver resolver=getContentResolver();
        ContentValues values=new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME,name);
        values.put(MediaStore.Downloads.MIME_TYPE,"application/zip");
        values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/"+OUTPUT_FOLDER);
        values.put(MediaStore.Downloads.IS_PENDING,1);
        Uri dest=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(dest==null)throw new IOException("Could not create Downloads entry");
        boolean success=false;
        try(OutputStream out=resolver.openOutputStream(dest);InputStream in=new FileInputStream(source)){
            if(out==null)throw new IOException("MediaStore output stream unavailable");
            byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            success=true;
        } finally {
            if(success){ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);resolver.update(dest,done,null,null);}
            else resolver.delete(dest,null,null);
        }
    }
    private static void deleteRecursive(File f) {
        if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File c:children)deleteRecursive(c);}
        f.delete();
    }
}
