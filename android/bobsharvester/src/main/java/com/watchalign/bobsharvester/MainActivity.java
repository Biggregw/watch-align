package com.watchalign.bobsharvester;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Standalone one-off Bob's Watches Rolex image harvester.
 * It does not alter Watch Align calibration data.
 */
public class MainActivity extends Activity {
    static final String UA="Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0 Mobile Safari/537.36";
    static final long MAX_IMG=25L*1024*1024, ZIP_TARGET=27L*1024*1024;
    static final int MAX_PAGES=150;
    static final String[] ROOTS={
        "https://www.bobswatches.com/rolex/",
        "https://www.bobswatches.com/rolex/?waitlist=1"
    };
    static final Pattern REF_EX=Pattern.compile("\\bREF(?:ERENCE)?\\.?\\s*([0-9]{4,6}[A-Z]{0,5})\\b",Pattern.CASE_INSENSITIVE);
    static final Pattern REF_ANY=Pattern.compile("(?<![0-9])([0-9]{4,6}[A-Z]{0,5})(?![0-9])",Pattern.CASE_INSENSITIVE);
    static final Pattern SKU=Pattern.compile("\\bSKU\\s*[:#-]?\\s*([0-9]{4,8})\\b",Pattern.CASE_INSENSITIVE);
    static final Set<String> NONREF=new HashSet<>(Arrays.asList("1905","1926","1931","1945","1953","1955","1956","1963","2024","2025","2026"));

    enum Mode{IDLE,CATALOG,PRODUCT}
    WebView web; TextView status,log; ProgressBar bar; Button start,stop,zip;
    final ExecutorService io=Executors.newSingleThreadExecutor();
    final LinkedHashMap<String,Product> products=new LinkedHashMap<>();
    final ArrayDeque<Product> queue=new ArrayDeque<>();
    final Set<String> done=new HashSet<>(), hashes=new HashSet<>();
    volatile boolean running=false, stopping=false;
    Mode mode=Mode.IDLE; int root=0,page=1,empty=0,doneRun=0,keptRun=0; Product current;
    File work,images,doneFile,hashFile,manifest;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi(); files(); loadState(); setupWeb();
        idle();
    }

    void buildUi(){
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setPadding(dp(10),dp(10),dp(10),dp(10));
        TextView t=new TextView(this); t.setText("Bob's Rolex Harvester"); t.setTextSize(22); t.setGravity(Gravity.CENTER); r.addView(t);
        TextView h=new TextView(this); h.setText("One-off run. Leave the app open. It keeps only face-on QC-style images and creates model/reference ZIPs below 30 MB."); h.setPadding(0,dp(4),0,dp(6)); r.addView(h);
        LinearLayout row=new LinearLayout(this);
        start=new Button(this);start.setText("Start / Resume"); stop=new Button(this);stop.setText("Stop");stop.setEnabled(false); zip=new Button(this);zip.setText("Build ZIPs");
        row.addView(start,new LinearLayout.LayoutParams(0,-2,1));row.addView(stop,new LinearLayout.LayoutParams(0,-2,.55f));row.addView(zip,new LinearLayout.LayoutParams(0,-2,.7f));r.addView(row);
        bar=new ProgressBar(this);bar.setVisibility(View.GONE);r.addView(bar,new LinearLayout.LayoutParams(-1,dp(4)));
        status=new TextView(this);status.setTextSize(15);status.setPadding(0,dp(7),0,dp(4));r.addView(status);
        ScrollView sc=new ScrollView(this);log=new TextView(this);log.setTextSize(12);log.setTextIsSelectable(true);sc.addView(log);r.addView(sc,new LinearLayout.LayoutParams(-1,0,.40f));
        web=new WebView(this);r.addView(web,new LinearLayout.LayoutParams(-1,0,.60f));setContentView(r);
        start.setOnClickListener(v->begin());stop.setOnClickListener(v->halt());zip.setOnClickListener(v->buildZips());
    }

    void files(){
        File base=getExternalFilesDir(null);if(base==null)base=getFilesDir();
        work=new File(base,"Bobs_Rolex_Harvest"); images=new File(work,"accepted_images"); images.mkdirs();
        doneFile=new File(work,"done_products.txt"); hashFile=new File(work,"hashes.txt"); manifest=new File(work,"accepted.csv");
        if(!manifest.exists())line(manifest,"reference,sku,title,product_url,image_url,file,sha256,width,height,dial_radius_px,axis_ratio,edge_support,sharpness");
    }
    void loadState(){readSet(doneFile,done);readSet(hashFile,hashes);}
    void idle(){status.setText("Ready. "+done.size()+" listings already completed, "+imageCount()+" accepted images saved.\\nZIPs go to Downloads/WatchAlign_Bobs_Harvest.");}

    void setupWeb(){
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setUserAgentString(UA);s.setLoadWithOverviewMode(true);s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);if(Build.VERSION.SDK_INT>=21)CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String u){
                if(!running||stopping)return;
                web.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight);true;",null);
                web.postDelayed(()->{if(!running||stopping)return;if(mode==Mode.CATALOG)catalogJs();else if(mode==Mode.PRODUCT)productJs();},3200);
            }
        });
    }

    void begin(){
        if(running)return;
        running=true;stopping=false;products.clear();queue.clear();root=0;page=1;empty=0;doneRun=0;keptRun=0;
        start.setEnabled(false);stop.setEnabled(true);bar.setVisibility(View.VISIBLE);mode=Mode.CATALOG;msg("Starting catalogue crawl.");loadCatalog();
    }
    void halt(){
        stopping=true;running=false;mode=Mode.IDLE;web.stopLoading();start.setEnabled(true);stop.setEnabled(false);bar.setVisibility(View.GONE);
        status.setText("Stopped. Progress is saved. Tap Start / Resume to continue, or Build ZIPs for what is already collected.");
    }

    String catUrl(){String x=ROOTS[root];return page==1?x:x+(x.contains("?")?"&":"?")+"page="+page;}
    void loadCatalog(){mode=Mode.CATALOG;status.setText("Finding Rolex listings… source "+(root+1)+"/"+ROOTS.length+", page "+page+"\\n"+products.size()+" unique product pages found.");web.loadUrl(catUrl());}

    void catalogJs(){
        String js="(function(){const a=[],seen=new Set();let rolex=0;document.querySelectorAll('a[href]').forEach(x=>{const im=x.querySelector('img');const parts=[x.innerText,x.textContent,x.getAttribute('aria-label'),x.getAttribute('title'),im&&im.alt].filter(Boolean);const t=parts.join(' ').replace(/\\s+/g,' ').trim();const u=x.href||'';if(/Rolex/i.test(t))rolex++;if(!u||seen.has(u))return;const path=(()=>{try{return new URL(u).pathname.toLowerCase()}catch(e){return''}})();if(!/bobswatches\\.com$/i.test((()=>{try{return new URL(u).hostname}catch(e){return''}})()))return;if(!/\\.html$/i.test(path))return;if(/\\/(rolex-blog|sell-|rolex-app|about|faq|shipping|returns)/i.test(path))return;const hay=(t+' '+u).replace(/[-_]/g,' ');if(!/Rolex/i.test(hay))return;if(!/(?:^|[^0-9])(?:[0-9]{4,6}[A-Z]{0,5})(?:[^0-9]|$)/i.test(hay))return;seen.add(u);a.push({t:t,u:u,i:im?(im.currentSrc||im.src||im.getAttribute('data-src')||''):''});});return JSON.stringify({a:a,b:(document.body.innerText||'').slice(0,24000),hrefs:document.querySelectorAll('a[href]').length,rolex:rolex,title:document.title||''});})();";
        web.evaluateJavascript(js,v->{
            if(!running||stopping)return;
            try{
                JSONObject o=new JSONObject(jsValue(v));JSONArray a=o.getJSONArray("a");int before=products.size();
                for(int i=0;i<a.length();i++){
                    JSONObject z=a.getJSONObject(i);String title=clean(z.optString("t")),u=z.optString("u"),r=ref(title+" "+u);
                    if(r.isEmpty()||!productUrl(u))continue;
                    Product p=products.get(u);if(p==null){p=new Product();p.url=u;p.title=title;p.ref=r;products.put(u,p);}
                    String im=z.optString("i");if(!im.isEmpty())p.imgs.add(im);
                }
                int add=products.size()-before;msg("Catalogue page "+page+": +"+add+" ("+products.size()+" unique), "+o.optInt("hrefs")+" links scanned.");
                empty=add==0?empty+1:0;Integer total=resultTotal(o.optString("b"));
                boolean end=empty>=2||page>=MAX_PAGES||(root==0&&total!=null&&products.size()>=total);
                if(page==1&&products.isEmpty()){
                    msg("No products yet. Page title: "+o.optString("title")+"; Rolex-labelled links seen: "+o.optInt("rolex")+". Retrying after a longer wait.");
                    web.postDelayed(()->{if(running&&!stopping&&mode==Mode.CATALOG)catalogJs();},5000);
                    return;
                }
                if(end){root++;if(root<ROOTS.length){page=1;empty=0;loadCatalog();}else beginProducts();}
                else{page++;web.postDelayed(this::loadCatalog,450);}
            }catch(Exception e){msg("Catalogue parse error: "+e.getMessage());page++;if(page>MAX_PAGES)beginProducts();else loadCatalog();}
        });
    }

    void beginProducts(){
        for(Product p:products.values())if(!done.contains(p.url))queue.add(p);
        msg("Catalogue complete: "+products.size()+" unique listings, "+queue.size()+" still to process.");
        next();
    }
    void next(){
        if(!running||stopping)return;
        current=queue.poll();if(current==null){running=false;stop.setEnabled(false);start.setEnabled(true);bar.setVisibility(View.GONE);msg("Harvest pass complete.");buildZips();return;}
        mode=Mode.PRODUCT;status.setText("Checking product pages… "+doneRun+" done this run, "+(queue.size()+1)+" remaining.\\nAccepted this run: "+keptRun+"\\n"+current.ref+"  "+shorten(current.title,82));web.loadUrl(current.url);
    }

    void productJs(){
        Product p=current;if(p==null){next();return;}
        String q=JSONObject.quote(p.ref);
        String js="(function(){const h=(document.querySelector('h1')?.innerText||'').replace(/\\\\s+/g,' ').trim();const b=(document.body.innerText||'').replace(/\\\\s+/g,' ').slice(0,18000);let sku='';const sm=b.match(/SKU\\\\s*[:#-]?\\\\s*(\\\\d{4,8})/i);if(sm)sku=sm[1];const ref="+q+";const out=[],seen=new Set();function add(u,a,w,h){if(!u||seen.has(u))return;seen.add(u);out.push({u:u,a:a||'',w:w||0,h:h||0});}document.querySelectorAll('script[type=\\\"application/ld+json\\\"]').forEach(s=>{try{let d=JSON.parse(s.textContent),n=Array.isArray(d)?d:[d];n.forEach(x=>{if(x&&x['@type']==='Product'){if(!sku&&x.sku)sku=String(x.sku);let m=x.image||[];if(typeof m==='string')m=[m];m.forEach(u=>add(u,'jsonld '+ref,1000,1000));}});}catch(e){}});document.querySelectorAll('img').forEach(im=>{const u=im.currentSrc||im.src||im.getAttribute('data-src')||'',a=im.alt||'',hay=(u+' '+a).toUpperCase();if((ref&&hay.includes(ref.toUpperCase()))||(sku&&hay.includes(sku)))add(u,a,im.naturalWidth||0,im.naturalHeight||0);});return JSON.stringify({h:h,b:b,s:sku,m:out});})();";
        web.evaluateJavascript(js,v->{
            if(!running||stopping||p!=current)return;
            try{
                JSONObject o=new JSONObject(jsValue(v));String h=clean(o.optString("h")),body=o.optString("b"),r=ref(h+" "+body);if(!r.isEmpty())p.ref=r;if(!h.isEmpty())p.title=h;p.sku=o.optString("s");
                if(p.sku.isEmpty()){Matcher sm=SKU.matcher(body);if(sm.find())p.sku=sm.group(1);}
                JSONArray m=o.optJSONArray("m");if(m!=null)for(int i=0;i<m.length();i++){JSONObject x=m.getJSONObject(i);String u=x.optString("u");int w=x.optInt("w"),hh=x.optInt("h");if(!u.isEmpty()&&(w==0||hh==0||(w>=350&&hh>=350)))p.imgs.add(u);}
                List<String> urls=new ArrayList<>(p.imgs);io.submit(()->process(p,urls));
            }catch(Exception e){msgUi("Product parse failed: "+e.getMessage());markDone(p.url);runOnUiThread(this::next);}
        });
    }

    void process(Product p,List<String> urls){
        int kept=0,ix=0;
        for(String u:new LinkedHashSet<>(urls)){
            if(stopping)break;ix++;
            try{
                byte[] bytes=download(u,p.url);if(bytes==null||bytes.length<20000)continue;String sha=sha(bytes);
                synchronized(hashes){if(hashes.contains(sha))continue;hashes.add(sha);line(hashFile,sha);}
                Bitmap bm=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bm==null)continue;Fit f=fit(bm);if(!f.ok){bm.recycle();continue;}
                String rr=safe(p.ref), sku=p.sku.isEmpty()?"listing"+Math.abs(p.url.hashCode()):p.sku;File d=new File(images,rr);d.mkdirs();
                String name=rr+"_"+sku+"_"+String.format(Locale.US,"%02d",ix)+"_"+sha.substring(0,10)+".jpg", finalName=name;File dest=new File(d,finalName);int k=2;while(dest.exists()){finalName=name.replace(".jpg","_"+k+++".jpg");dest=new File(d,finalName);}
                try(FileOutputStream os=new FileOutputStream(dest)){bm.compress(Bitmap.CompressFormat.JPEG,96,os);}int w=bm.getWidth(),h=bm.getHeight();bm.recycle();kept++;keptRun++;
                String row=csv(rr)+","+csv(p.sku)+","+csv(p.title)+","+csv(p.url)+","+csv(u)+","+csv(dest.getName())+","+csv(sha)+","+w+","+h+","+fmt(f.radius)+","+fmt(f.axis)+","+fmt(f.support)+","+fmt(f.sharp);
                line(manifest,row);line(new File(d,"manifest.csv"),row);msgUi("KEEP "+rr+" "+dest.getName()+"  R="+Math.round(f.radius)+"px face="+String.format(Locale.US,"%.3f",f.axis));
            }catch(Exception e){msgUi("Image skipped: "+e.getClass().getSimpleName());}
        }
        if(!stopping){markDone(p.url);doneRun++;msgUi("Finished "+p.ref+(p.sku.isEmpty()?"":" SKU "+p.sku)+": "+kept+" suitable image(s).");runOnUiThread(this::next);}
    }

    byte[] download(String text,String referer)throws Exception{
        URL u=new URL(text);String host=u.getHost().toLowerCase(Locale.US);if(!host.endsWith("bobswatches.com"))return null;
        HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(true);c.setConnectTimeout(25000);c.setReadTimeout(35000);c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Referer",referer);c.setRequestProperty("Accept","image/avif,image/webp,image/apng,image/*,*/*;q=0.8");String ck=CookieManager.getInstance().getCookie(text);if(ck!=null)c.setRequestProperty("Cookie",ck);
        int code=c.getResponseCode();if(code<200||code>=300){c.disconnect();return null;}if(c.getContentLengthLong()>MAX_IMG){c.disconnect();return null;}
        ByteArrayOutputStream o=new ByteArrayOutputStream();try(InputStream in=new BufferedInputStream(c.getInputStream())){byte[] b=new byte[32768];int n;long total=0;while((n=in.read(b))>=0){total+=n;if(total>MAX_IMG)return null;o.write(b,0,n);}}finally{c.disconnect();}return o.toByteArray();
    }

    /**
     * Generic, model-independent face-on gate. It looks for a strong circular watch/dial boundary
     * near the image centre, compares opposing radii to reject obvious perspective/crops, and
     * requires useful dial sharpness. It is intentionally conservative and only decides photo
     * suitability, never whether a watch is genuine or good/bad.
     */
    Fit fit(Bitmap src){
        Fit z=new Fit();int ow=src.getWidth(),oh=src.getHeight();if(Math.min(ow,oh)<500)return z;
        float sc=Math.min(1f,850f/Math.max(ow,oh));Bitmap b=sc<1?Bitmap.createScaledBitmap(src,Math.round(ow*sc),Math.round(oh*sc),true):src;int w=b.getWidth(),h=b.getHeight(),mn=Math.min(w,h);
        int[] p=new int[w*h],g=new int[w*h];b.getPixels(p,0,w,0,0,w,h);for(int i=0;i<p.length;i++){int c=p[i];g[i]=(77*Color.red(c)+150*Color.green(c)+29*Color.blue(c))>>8;}
        int bx=w/2,by=h/2,br=0;double bs=-1;
        int sx=Math.max(10,w/24),sy=Math.max(10,h/24),r0=(int)(mn*.12),r1=(int)(mn*.43),rs=Math.max(6,mn/55);
        for(int cy=(int)(h*.30);cy<=(int)(h*.70);cy+=sy)for(int cx=(int)(w*.36);cx<=(int)(w*.64);cx+=sx)for(int r=r0;r<=r1;r+=rs){
            if(cx-r<3||cy-r<3||cx+r>=w-3||cy+r>=h-3)continue;double sum=0;int strong=0;
            for(int k=0;k<64;k++){double a=2*Math.PI*k/64;int x1=cl((int)Math.round(cx+(r-3)*Math.cos(a)),0,w-1),y1=cl((int)Math.round(cy+(r-3)*Math.sin(a)),0,h-1),x2=cl((int)Math.round(cx+(r+3)*Math.cos(a)),0,w-1),y2=cl((int)Math.round(cy+(r+3)*Math.sin(a)),0,h-1);int d=Math.abs(g[y2*w+x2]-g[y1*w+x1]);sum+=Math.min(80,d);if(d>=12)strong++;}
            double sup=strong/64.0,off=Math.hypot(cx-w/2.0,cy-h/2.0)/mn,score=(sum/64)*(0.35+sup)+r*.018-off*12;if(sup>=.42&&score>bs){bs=score;bx=cx;by=cy;br=r;}
        }
        if(br==0){if(b!=src)b.recycle();return z;}
        double[] er=new double[72];int good=0;
        for(int k=0;k<72;k++){double a=2*Math.PI*k/72;int best=br,bd=-1;for(int r=(int)(br*.78);r<=(int)(br*1.18);r+=2){int x1=cl((int)Math.round(bx+(r-2)*Math.cos(a)),0,w-1),y1=cl((int)Math.round(by+(r-2)*Math.sin(a)),0,h-1),x2=cl((int)Math.round(bx+(r+2)*Math.cos(a)),0,w-1),y2=cl((int)Math.round(by+(r+2)*Math.sin(a)),0,h-1),d=Math.abs(g[y2*w+x2]-g[y1*w+x1]);if(d>bd){bd=d;best=r;}}er[k]=best;if(bd>=10)good++;}
        double hax=er[0]+er[36],vax=er[18]+er[54],d1=er[9]+er[45],d2=er[27]+er[63],max=Math.max(Math.max(hax,vax),Math.max(d1,d2)),min=Math.min(Math.min(hax,vax),Math.min(d1,d2)),axis=min/max,support=good/72.0;
        double opp=Math.max(Math.abs(er[0]-er[36])/br,Math.max(Math.abs(er[18]-er[54])/br,Math.max(Math.abs(er[9]-er[45])/br,Math.abs(er[27]-er[63])/br)));
        double sharp=sharp(g,w,h,bx,by,(int)(br*.76)),rad=br/sc;if(b!=src)b.recycle();
        if(axis<.885||support<.62||opp>.20||sharp<5||rad<130)return z;z.ok=true;z.radius=rad;z.axis=axis;z.support=support;z.sharp=sharp;return z;
    }

    double sharp(int[] g,int w,int h,int cx,int cy,int r){long sum=0;int n=0,rr=r*r;for(int y=Math.max(2,cy-r);y<Math.min(h-2,cy+r);y+=3){int dy=y-cy;for(int x=Math.max(2,cx-r);x<Math.min(w-2,cx+r);x+=3){int dx=x-cx;if(dx*dx+dy*dy>rr)continue;int i=y*w+x,lap=Math.abs(4*g[i]-g[i-1]-g[i+1]-g[i-w]-g[i+w]);sum+=Math.min(255,lap);n++;}}return n==0?0:sum/(double)n;}

    void buildZips(){
        zip.setEnabled(false);bar.setVisibility(View.VISIBLE);io.submit(()->{
            try{
                int refs=0,pics=0,zips=0;File[] ds=images.listFiles(File::isDirectory);if(ds==null)ds=new File[0];Arrays.sort(ds,Comparator.comparing(File::getName));String stamp=new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date());
                for(File d:ds){File[] aa=d.listFiles((x,n)->n.toLowerCase(Locale.US).endsWith(".jpg"));if(aa==null||aa.length==0)continue;List<File> all=new ArrayList<>(Arrays.asList(aa));all.sort(Comparator.comparing(File::getName));refs++;pics+=all.size();List<List<File>> parts=new ArrayList<>();List<File> cur=new ArrayList<>();long sz=0;for(File f:all){if(!cur.isEmpty()&&sz+f.length()>ZIP_TARGET){parts.add(cur);cur=new ArrayList<>();sz=0;}cur.add(f);sz+=f.length();}if(!cur.isEmpty())parts.add(cur);
                    for(int i=0;i<parts.size();i++){String part=parts.size()>1?String.format(Locale.US,"_part%02d-of-%02d",i+1,parts.size()):"",name="Rolex_"+safe(d.getName())+"_Bobs_QC"+part+"_"+stamp+".zip";File tmp=new File(getCacheDir(),name);writeZip(tmp,d,parts.get(i));if(tmp.length()>=30L*1024*1024)throw new IOException("ZIP over 30 MB: "+name);double mb=tmp.length()/1048576.0;downloads(tmp,name);tmp.delete();zips++;msgUi("ZIP "+name+" ("+String.format(Locale.US,"%.1f",mb)+" MB)");}
                }
                int fr=refs,fp=pics,fz=zips;runOnUiThread(()->{bar.setVisibility(View.GONE);zip.setEnabled(true);status.setText("Finished. "+fr+" Rolex references, "+fp+" accepted images, "+fz+" ZIP files.\\nSaved in Downloads/WatchAlign_Bobs_Harvest. Each ZIP is below 30 MB.");Toast.makeText(this,"ZIPs saved in Downloads/WatchAlign_Bobs_Harvest",Toast.LENGTH_LONG).show();});
            }catch(Exception e){msgUi("ZIP build failed: "+e.getMessage());runOnUiThread(()->{bar.setVisibility(View.GONE);zip.setEnabled(true);});}
        });
    }

    void writeZip(File out,File d,List<File> pics)throws Exception{try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))){z.setLevel(1);String r="Bob's Watches Rolex "+d.getName()+" QC image pack\\nImages in this part: "+pics.size()+"\\nGeneric face-on suitability filtering only. Do not automatically use these images to set Watch Align calibration limits.\\n";put(z,"README.txt",r.getBytes("UTF-8"));File m=new File(d,"manifest.csv");if(m.exists())put(z,m,"manifest.csv");for(File f:pics)put(z,f,"images/"+f.getName());}}
    void put(ZipOutputStream z,File f,String n)throws Exception{z.putNextEntry(new ZipEntry(n));try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] b=new byte[65536];int k;while((k=in.read(b))>=0)z.write(b,0,k);}z.closeEntry();}
    void put(ZipOutputStream z,String n,byte[] b)throws Exception{z.putNextEntry(new ZipEntry(n));z.write(b);z.closeEntry();}
    void downloads(File src,String name)throws Exception{ContentResolver cr=getContentResolver();ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,"application/zip");if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/WatchAlign_Bobs_Harvest");v.put(MediaStore.Downloads.IS_PENDING,1);}Uri u=cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Cannot create download");try(InputStream in=new FileInputStream(src);OutputStream out=cr.openOutputStream(u)){if(out==null)throw new IOException("Cannot open download");byte[] b=new byte[65536];int k;while((k=in.read(b))>=0)out.write(b,0,k);}if(Build.VERSION.SDK_INT>=29){ContentValues q=new ContentValues();q.put(MediaStore.Downloads.IS_PENDING,0);cr.update(u,q,null,null);}}

    boolean productUrl(String s){try{URI u=URI.create(s);String h=u.getHost();if(h==null||!h.toLowerCase(Locale.US).endsWith("bobswatches.com"))return false;String p=u.getPath().toLowerCase(Locale.US);if(p.contains("/rolex-blog/")||p.contains("/sell-")||p.contains("/rolex-app")||p.contains("/about")||p.contains("/faq"))return false;return p.endsWith(".html");}catch(Exception e){return false;}}
    String ref(String s){String u=clean(s).toUpperCase(Locale.US);Matcher x=REF_EX.matcher(u);if(x.find()&&!NONREF.contains(x.group(1)))return variant(x.group(1),u);Matcher m=REF_ANY.matcher(u);List<String>a=new ArrayList<>();while(m.find())if(!NONREF.contains(m.group(1)))a.add(m.group(1));for(String r:a){int n=r.replaceAll("[A-Z]","").length();if(n>=5&&n<=6)return variant(r,u);}return a.isEmpty()?"":variant(a.get(0),u);}
    String variant(String r,String t){r=r.toUpperCase(Locale.US);if(r.equals("126710")){if(t.matches(".*\\b(PEPSI|BLRO)\\b.*"))return"126710BLRO";if(t.matches(".*\\b(BATMAN|BATGIRL|BLNR)\\b.*"))return"126710BLNR";if(t.matches(".*(BRUCE WAYNE|\\bGRNR\\b|GREY.*BLACK|BLACK.*GREY).*"))return"126710GRNR";}if(r.equals("126720")&&t.matches(".*\\b(SPRITE|VTNR)\\b.*"))return"126720VTNR";if(r.equals("126610")){if(t.matches(".*(STARBUCKS|CERMIT|GREEN BEZEL|\\bLV\\b).*"))return"126610LV";if(t.matches(".*(BLACK BEZEL|\\bLN\\b).*"))return"126610LN";}if(r.equals("116610")){if(t.matches(".*(HULK|GREEN DIAL|GREEN BEZEL|\\bLV\\b).*"))return"116610LV";if(t.matches(".*(BLACK BEZEL|\\bLN\\b).*"))return"116610LN";}if(r.equals("16610")&&t.matches(".*(KERMIT|GREEN BEZEL|\\bLV\\b).*"))return"16610LV";return r;}
    Integer resultTotal(String s){Matcher m=Pattern.compile("\\bof\\s+([0-9,]+)\\s+results\\b",Pattern.CASE_INSENSITIVE).matcher(s);if(!m.find())return null;try{return Integer.parseInt(m.group(1).replace(",",""));}catch(Exception e){return null;}}
    String jsValue(String v)throws Exception{Object x=new JSONTokener(v==null?"null":v).nextValue();return x instanceof String?(String)x:String.valueOf(x);}
    String sha(byte[] b)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte x:d)s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
    void markDone(String u){synchronized(done){if(done.add(u))line(doneFile,u);}}
    void line(File f,String s){try{File p=f.getParentFile();if(p!=null)p.mkdirs();try(FileWriter w=new FileWriter(f,true)){w.write(s);w.write("\\n");}}catch(Exception ignored){}}
    void readSet(File f,Set<String>s){if(!f.exists())return;try(BufferedReader r=new BufferedReader(new FileReader(f))){String x;while((x=r.readLine())!=null)if(!x.trim().isEmpty())s.add(x.trim());}catch(Exception ignored){}}
    int imageCount(){int n=0;File[]d=images.listFiles(File::isDirectory);if(d!=null)for(File x:d){File[]f=x.listFiles((a,b)->b.endsWith(".jpg"));if(f!=null)n+=f.length;}return n;}
    void msg(String s){log.append(s+"\\n");if(log.length()>22000)log.setText(log.getText().subSequence(log.length()-16000,log.length()));}
    void msgUi(String s){runOnUiThread(()->msg(s));}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    int cl(int x,int a,int b){return Math.max(a,Math.min(b,x));}
    String clean(String s){return s==null?"":s.replaceAll("\\s+"," ").trim();}
    String shorten(String s,int n){s=clean(s);return s.length()<=n?s:s.substring(0,n-1)+"…";}
    String safe(String s){s=(s==null?"UNKNOWN":s.toUpperCase(Locale.US)).replaceAll("[^A-Z0-9._-]","_");return s.isEmpty()?"UNKNOWN":s;}
    String csv(String s){s=s==null?"":s;return"\\\""+s.replace("\\\"","\\\"\\\"")+"\\\"";}
    String fmt(double x){return Double.isFinite(x)?String.format(Locale.US,"%.4f",x):"";}

    @Override protected void onDestroy(){super.onDestroy();stopping=true;running=false;io.shutdownNow();if(web!=null)web.destroy();}
    static class Product{String title="",url="",ref="",sku="";LinkedHashSet<String>imgs=new LinkedHashSet<>();}
    static class Fit{boolean ok=false;double radius=Double.NaN,axis=Double.NaN,support=Double.NaN,sharp=Double.NaN;}
}