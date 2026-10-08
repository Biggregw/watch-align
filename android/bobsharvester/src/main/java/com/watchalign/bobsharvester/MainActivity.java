package com.watchalign.bobsharvester;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
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
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.watchalign.bobsharvester.HarvestLogic.clean;
import static com.watchalign.bobsharvester.HarvestLogic.csv;
import static com.watchalign.bobsharvester.HarvestLogic.safe;

/**
 * Standalone one-off Bob's Watches Rolex image harvester. It does not alter Watch Align calibration data.
 *
 * Pipeline: catalogue pages (WebView) -> product page (WebView) -> the listing's own product photographs
 * (HarvestLogic.selectProductImages) -> download of the original files (HTTP with the page's cookies; WebView canvas
 * capture as a fallback) -> face-on suitability (FaceOnGate) -> accepted originals saved per reference -> ZIPs below
 * 30 MB in Downloads/WatchAlign_Bobs_Harvest. Every step is counted (HarvestStats) and every dropped or rejected image
 * is logged with its reason.
 *
 * Optional launch extras (used by the CI end-to-end check): filter (String), max_products (int), auto_zip (boolean).
 */
public class MainActivity extends Activity {
    static final String UA="Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0 Mobile Safari/537.36";
    static final long MAX_IMG=25L*1024*1024;
    static final int MAX_PAGES=450, PAGE_TIMEOUT_MS=60000;
    static final String[] ROOTS={"https://www.bobswatches.com/rolex/","https://www.bobswatches.com/rolex/?waitlist=1"};

    enum Mode{IDLE,CATALOG,PRODUCT}
    WebView web; TextView status,log; ProgressBar bar; Button start,stop,zip; EditText filter;
    final ExecutorService io=Executors.newSingleThreadExecutor();
    final LinkedHashMap<String,Product> products=new LinkedHashMap<>();
    final ArrayDeque<Product> queue=new ArrayDeque<>();
    final Set<String> done=new HashSet<>(), hashes=new HashSet<>(), rootLinks=new HashSet<>();
    volatile boolean running=false, stopping=false;
    Mode mode=Mode.IDLE; int emptyRetries=0,root=0,page=1,empty=0,doneRun=0,maxProducts=0; boolean returnToCatalog=false,catalogPageEnd=false,autoZip=false; String activeFilter=""; Product current;
    File work,images,doneFile,hashFile,manifest,decisions,runLog; HarvestStats stats=new HarvestStats();
    volatile long loadToken=0,captureToken=0; volatile Product captureProduct;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi(); files(); loadState();
        setupWeb();
        idle();
        Intent it=getIntent();
        if(it!=null&&it.hasExtra("filter")){
            filter.setText(it.getStringExtra("filter"));maxProducts=it.getIntExtra("max_products",0);autoZip=it.getBooleanExtra("auto_zip",false);
            web.post(this::begin);
        }
    }

    void buildUi(){
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setPadding(dp(10),dp(10),dp(10),dp(10));
        TextView t=new TextView(this); t.setText("Bob's Rolex Harvester"); t.setTextSize(22); t.setGravity(Gravity.CENTER); r.addView(t);
        TextView h=new TextView(this); h.setText("One-off run. Leave the app open. Product photographs are downloaded from each listing, checked for a face-on dial, then packed into model/reference ZIPs below 30 MB."); h.setPadding(0,dp(4),0,dp(6)); r.addView(h);
        filter=new EditText(this);filter.setSingleLine(true);filter.setHint("Keyword filter: e.g. Submariner, 124060, GMT-Master II");filter.setTextSize(16);filter.setPadding(dp(10),dp(4),dp(10),dp(6));r.addView(filter,new LinearLayout.LayoutParams(-1,-2));
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
        doneFile=new File(work,"done_products.txt"); hashFile=new File(work,"hashes.txt"); manifest=new File(work,"accepted.csv"); decisions=new File(work,"image_decisions.csv"); runLog=new File(work,"run_log.txt");
        String header="reference,sku,title,product_url,image_url,file,sha256,width,height,acquisition,dial_radius_px,axis_ratio,ring_support,sharpness";
        if(manifest.exists()&&!header.equals(firstLine(manifest)))manifest.renameTo(new File(work,"accepted_old_format_"+System.currentTimeMillis()+".csv"));
        if(!manifest.exists())line(manifest,"reference,sku,title,product_url,image_url,file,sha256,width,height,acquisition,dial_radius_px,axis_ratio,ring_support,sharpness");
        if(!decisions.exists())line(decisions,"time,reference,sku,product_url,image_url,stage,outcome,reason");
    }
    void loadState(){readSet(doneFile,done);readSet(hashFile,hashes);}
    void idle(){status.setText("Ready. "+done.size()+" listings already completed, "+imageCount()+" accepted images saved.\nZIPs go to Downloads/WatchAlign_Bobs_Harvest.");}

    void setupWeb(){
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setUserAgentString(UA);s.setLoadWithOverviewMode(true);s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);web.addJavascriptInterface(new CaptureBridge(),"WatchAlignCapture");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String u){
                if(!running||stopping)return;
                long tok=loadToken;
                web.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight);true;",null);
                web.postDelayed(()->{if(!running||stopping||tok!=loadToken)return;loadToken++;if(mode==Mode.CATALOG)catalogJs();else if(mode==Mode.PRODUCT)productJs();},3200);
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError err){
                if(req!=null&&req.isForMainFrame())msg("Page load error "+err.getErrorCode()+": "+err.getDescription()+" ("+req.getUrl()+")");
            }
        });
    }

    /** loads a page and gives up on it after PAGE_TIMEOUT_MS (a page that never finishes no longer hangs the run) */
    void load(String url,Runnable onTimeout){
        long tok=++loadToken;web.loadUrl(url);
        web.postDelayed(()->{if(running&&!stopping&&tok==loadToken){loadToken++;web.stopLoading();onTimeout.run();}},PAGE_TIMEOUT_MS);
    }

    void begin(){
        if(running)return;
        activeFilter=clean(filter.getText().toString());filter.setEnabled(false);stats=new HarvestStats();
        running=true;stopping=false;products.clear();queue.clear();rootLinks.clear();root=0;page=1;empty=0;doneRun=0;returnToCatalog=false;catalogPageEnd=false;
        start.setEnabled(false);stop.setEnabled(true);bar.setVisibility(View.VISIBLE);mode=Mode.CATALOG;
        msg("Starting catalogue crawl"+(activeFilter.isEmpty()?".":" with keyword filter: "+activeFilter)+(maxProducts>0?" (at most "+maxProducts+" listings)":""));loadCatalog();
    }
    void halt(){
        stopping=true;running=false;mode=Mode.IDLE;loadToken++;web.stopLoading();start.setEnabled(true);stop.setEnabled(false);bar.setVisibility(View.GONE);
        filter.setEnabled(true);msg("Stopped by user.\n"+stats.report());status.setText("Stopped. Progress is saved. Tap Start / Resume to continue, or Build ZIPs for what is already collected.");
    }

    String catUrl(){String x=ROOTS[root];return page==1?x:x+(x.contains("?")?"&":"?")+"page="+page;}
    void loadCatalog(){
        mode=Mode.CATALOG;status.setText("Finding Rolex listings… source "+(root+1)+"/"+ROOTS.length+", page "+page+"\n"+stats.listingsDiscovered+" listings seen, "+stats.listingsMatched+" match"+(activeFilter.isEmpty()?"":" \""+activeFilter+"\""));
        load(catUrl(),()->{stats.pageFailures++;msg("Catalogue page "+page+" did not finish loading in "+PAGE_TIMEOUT_MS/1000+" s; moving on.");advanceCatalog(false);});
    }

    void catalogJs(){
        String js="(function(){const a=[],seen=new Set();let rolex=0;document.querySelectorAll('a[href]').forEach(x=>{const im=x.querySelector('img');const parts=[x.innerText,x.textContent,x.getAttribute('aria-label'),x.getAttribute('title'),im&&im.alt].filter(Boolean);const t=parts.join(' ').replace(/\\s+/g,' ').trim();const u=x.href||'';if(/Rolex/i.test(t))rolex++;if(!u||seen.has(u))return;let q;try{q=new URL(u)}catch(e){return}const path=q.pathname.toLowerCase();if(!/bobswatches\\.com$/i.test(q.hostname))return;if(!/\\.html$/i.test(path))return;if(/\\/(rolex-blog|sell-|rolex-app|about|faq|shipping|returns)/i.test(path))return;const hay=(t+' '+u).replace(/[-_]/g,' ');if(!/Rolex/i.test(hay))return;if(!/(?:^|[^0-9])(?:[0-9]{4,6}[A-Z]{0,5})(?:[^0-9]|$)/i.test(hay))return;seen.add(u);a.push({t:t,u:u});});return JSON.stringify({a:a,b:(document.body.innerText||'').slice(0,24000),hrefs:document.querySelectorAll('a[href]').length,rolex:rolex,title:document.title||''});})();";
        web.evaluateJavascript(js,v->{
            if(!running||stopping)return;
            try{
                JSONObject o=new JSONObject(jsValue(v));JSONArray a=o.getJSONArray("a");int newLinks=0,matched=0,queued=0;
                stats.catalogPages++;stats.catalogLinks+=a.length();
                for(int i=0;i<a.length();i++){
                    JSONObject z=a.getJSONObject(i);String title=clean(z.optString("t")),u=HarvestLogic.canonicalProductUrl(z.optString("u"));
                    if(!HarvestLogic.productUrl(u))continue;
                    String r=HarvestLogic.ref(title+" "+u);if(r.isEmpty())continue;
                    if(rootLinks.add(u)){newLinks++;stats.listingsDiscovered++;}else continue;
                    if(!HarvestLogic.matchesFilter(activeFilter,title,u))continue;
                    matched++;stats.listingsMatched++;
                    Product p=products.get(u);if(p==null){p=new Product();p.url=u;p.title=title;p.ref=r;products.put(u,p);}
                    if(!done.contains(u)&&!queue.contains(p)&&p!=current){queue.add(p);queued++;}
                }
                msg("Catalogue source "+(root+1)+", page "+page+": "+a.length()+" product links, "+newLinks+" new, "+matched+" match"+(activeFilter.isEmpty()?"":" the filter")+", "+queued+" queued (already done earlier: "+(matched-queued)+").");
                // the end of the catalogue is decided on ALL listings seen, never on filter matches
                empty=newLinks==0?empty+1:0;Integer total=HarvestLogic.resultTotal(o.optString("b"));
                boolean endPage=empty>=2||page>=MAX_PAGES||(total!=null&&rootLinks.size()>=total);
                if(a.length()==0&&emptyRetries<3){
                    emptyRetries++;
                    msg("Catalogue page shows no product links yet (title: "+o.optString("title")+", Rolex links: "+o.optInt("rolex")+"). Retry "+emptyRetries+"/3 after a longer wait.");
                    web.postDelayed(()->{if(running&&!stopping&&mode==Mode.CATALOG)catalogJs();},5000);
                    return;
                }
                emptyRetries=0;
                if(!queue.isEmpty()){returnToCatalog=true;catalogPageEnd=endPage;next();}
                else advanceCatalog(endPage);
            }catch(Exception e){msg("Catalogue parse error: "+e.getMessage());advanceCatalog(page>=MAX_PAGES);}
        });
    }

    void advanceCatalog(boolean endPage){
        if(!running||stopping)return;
        if(maxProducts>0&&doneRun>=maxProducts){finishHarvest();return;}
        if(endPage){
            root++;
            if(root<ROOTS.length){page=1;empty=0;rootLinks.clear();msg("Moving to catalogue source "+(root+1)+" of "+ROOTS.length+".");loadCatalog();}
            else finishHarvest();
        }else{page++;web.postDelayed(this::loadCatalog,450);}
    }

    void finishHarvest(){
        running=false;mode=Mode.IDLE;stop.setEnabled(false);start.setEnabled(true);filter.setEnabled(true);bar.setVisibility(View.GONE);
        String why=stats.explainZero();
        msg("Harvest pass complete.\n"+stats.report()+(why==null?"":"NO IMAGES ACCEPTED: "+why));
        status.setText(why==null?"Harvest complete: "+stats.accepted+" accepted images this run ("+imageCount()+" stored). Tap BUILD ZIPS to export.":"Harvest complete, but no images were accepted.\n"+why);
        if(autoZip)buildZips();
    }

    void next(){
        if(!running||stopping)return;
        if(maxProducts>0&&doneRun>=maxProducts){queue.clear();finishHarvest();return;}
        current=queue.poll();
        if(current==null){
            if(returnToCatalog){boolean end=catalogPageEnd;returnToCatalog=false;catalogPageEnd=false;advanceCatalog(end);return;}
            finishHarvest();return;
        }
        mode=Mode.PRODUCT;
        status.setText("Harvesting product pages…\nCatalogue source "+(root+1)+"/"+ROOTS.length+", page "+page+
                "\n"+doneRun+" listings processed this run, "+queue.size()+" queued\n"+stats.accepted+" accepted, "+stats.rejected+" rejected images this run\n"+
                current.ref+"  "+shorten(current.title,82));
        Product p=current;
        load(p.url,()->{stats.pageFailures++;doneRun++;msg("Product page did not finish loading in "+PAGE_TIMEOUT_MS/1000+" s: "+p.url+" (left for a later resume)");next();});
    }

    void productJs(){
        Product p=current;if(p==null){next();return;}
        String js="(function(){"+
                "const h=(document.querySelector('h1')?.innerText||'').replace(/\\s+/g,' ').trim();"+
                "const b=(document.body.innerText||'').replace(/\\s+/g,' ').slice(0,18000);"+
                "let sku='';const sm=b.match(/SKU\\s*[:#-]?\\s*(\\d{4,8})/i);if(sm)sku=sm[1];"+
                "const out=[];const add=(u,s)=>{if(u&&typeof u==='object')u=u.url||u.contentUrl||u['@id']||'';if(u)out.push({u:String(u),s:s});};"+
                "const srcset=(v,s)=>{if(v)v.split(',').forEach(p=>add(p.trim().split(/\\s+/)[0],s));};"+
                "document.querySelectorAll('script[type=\"application/ld+json\"]').forEach(x=>{try{let d=JSON.parse(x.textContent),n=Array.isArray(d)?d:[d];"+
                "n.forEach(o=>{if(o&&o['@type']==='Product'){if(!sku&&o.sku)sku=String(o.sku);let m=o.image||[];if(!Array.isArray(m))m=[m];m.forEach(u=>add(u,'jsonld'));}});}catch(e){}});"+
                "document.querySelectorAll('img').forEach(im=>{add(im.currentSrc,'img');add(im.getAttribute('src'),'img');['data-src','data-zoom-image','data-large','data-original','data-full'].forEach(k=>add(im.getAttribute(k),'img'));srcset(im.getAttribute('srcset'),'img');srcset(im.getAttribute('data-srcset'),'img');});"+
                "document.querySelectorAll('source').forEach(x=>{srcset(x.getAttribute('srcset'),'source');srcset(x.getAttribute('data-srcset'),'source');});"+
                "document.querySelectorAll('a[href]').forEach(a=>{if(/\\/images\\/[^?#]+\\.(jpe?g|png|webp)/i.test(a.href))add(a.href,'link');});"+
                "return JSON.stringify({h:h,b:b,s:sku,c:out});})();";
        web.evaluateJavascript(js,v->{
            if(!running||stopping||p!=current)return;
            try{
                JSONObject o=new JSONObject(jsValue(v));stats.pagesLoaded++;
                String h=clean(o.optString("h"));
                String r=HarvestLogic.ref(h);if(!r.isEmpty())p.ref=r;
                if(!h.isEmpty())p.title=h;
                p.sku=o.optString("s");if(p.sku.isEmpty())p.sku=HarvestLogic.skuFrom(o.optString("b"));
                List<HarvestLogic.Candidate> cands=new ArrayList<>();JSONArray c=o.optJSONArray("c");
                if(c!=null)for(int i=0;i<c.length();i++){JSONObject x=c.optJSONObject(i);if(x!=null)cands.add(new HarvestLogic.Candidate(x.optString("u"),x.optString("s")));}
                HarvestLogic.Selection sel=HarvestLogic.selectProductImages(p.sku,p.url,cands);
                for(Map.Entry<String,String> d:sel.dropped.entrySet())stats.dropped(d.getValue());
                stats.imageUrlsFound+=sel.keep.size();if(!sel.keep.isEmpty())stats.listingsWithImageUrls++;
                msg(p.ref+(p.sku.isEmpty()?" (no SKU on page)":" SKU "+p.sku)+": "+cands.size()+" image references on the page, "+sel.keep.size()+" product photo(s) of this listing"+(sel.dropped.isEmpty()?"":", "+sel.dropped.size()+" other images ignored"));
                if(sel.keep.isEmpty()){finishProduct(p,"no product photographs found on the page");return;}
                io.submit(()->downloadAll(p,sel.keep));
            }catch(Exception e){
                stats.pageFailures++;msgUi("Product page could not be read: "+e.getClass().getSimpleName()+": "+e.getMessage());
                runOnUiThread(()->{doneRun++;next();});
            }
        });
    }

    void finishProduct(Product p,String note){
        markDone(p.url);doneRun++;msg("Finished "+p.ref+(p.sku.isEmpty()?"":" SKU "+p.sku)+": "+note);next();
    }

    /** io thread: original files over HTTP with the page's cookies; URLs that fail fall back to the WebView canvas */
    void downloadAll(Product p,List<String> urls){
        int kept=0;List<String> fallback=new ArrayList<>();
        for(String u:urls){
            if(stopping)return;
            stats.downloadsAttempted++;
            Download d=download(u,p.url);
            if(d.bytes==null){stats.downloadFailed(d.error);decision(p,u,"download","FAILED",d.error);fallback.add(u);continue;}
            stats.downloadsOk++;
            if(assessAndSave(p,u,d.bytes,"http "+d.type))kept++;
        }
        if(!fallback.isEmpty()&&!stopping){
            int k=kept;msgUi(fallback.size()+" download(s) failed for SKU "+p.sku+"; trying the page's own image loader.");
            runOnUiThread(()->captureInWebView(p,fallback,k));return;
        }
        int k=kept;runOnUiThread(()->finishProduct(p,k+" accepted of "+urls.size()+" product photo(s)."));
    }

    static final class Download{byte[] bytes;String type="",error="";}
    Download download(String text,String referer){
        Download r=new Download();HttpURLConnection c=null;
        try{
            URL u=new URL(text);if(!u.getHost().toLowerCase(Locale.US).endsWith("bobswatches.com")){r.error="not a bobswatches.com URL";return r;}
            c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(true);c.setConnectTimeout(25000);c.setReadTimeout(35000);
            c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Referer",referer);
            // JPEG first: the image CDN answers image/webp or image/avif to a browser-style Accept header
            c.setRequestProperty("Accept","image/jpeg,image/png;q=0.9,image/*;q=0.5");
            String ck=CookieManager.getInstance().getCookie(text);if(ck!=null)c.setRequestProperty("Cookie",ck);
            int code=c.getResponseCode();r.type=String.valueOf(c.getContentType());
            if(code<200||code>=300){r.error="HTTP "+code;return r;}
            if(!r.type.toLowerCase(Locale.US).startsWith("image/")){r.error="not an image ("+r.type+")";return r;}
            if(c.getContentLengthLong()>MAX_IMG){r.error="larger than 25 MB";return r;}
            ByteArrayOutputStream o=new ByteArrayOutputStream();
            try(InputStream in=new BufferedInputStream(c.getInputStream())){byte[] b=new byte[32768];int n;long total=0;while((n=in.read(b))>=0){total+=n;if(total>MAX_IMG){r.error="larger than 25 MB";return r;}o.write(b,0,n);}}
            r.bytes=o.toByteArray();return r;
        }catch(java.net.SocketTimeoutException e){r.error="timeout";return r;}
        catch(Exception e){r.error=e.getClass().getSimpleName();return r;}
        finally{if(c!=null)c.disconnect();}
    }

    /** suitability is assessed separately from acquisition; the original bytes are what gets saved */
    boolean assessAndSave(Product p,String u,byte[] bytes,String acquisition){
        try{
            String sha=sha(bytes);
            synchronized(hashes){if(hashes.contains(sha)){stats.duplicates++;decision(p,u,"assess","DUPLICATE","identical file already saved");return false;}}
            BitmapFactory.Options bo=new BitmapFactory.Options();bo.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bo);
            int ow=bo.outWidth,oh=bo.outHeight;
            if(ow<=0||oh<=0){stats.reject("could not decode the image");decision(p,u,"assess","REJECTED","could not decode the image ("+acquisition+")");return false;}
            int sample=1;while(Math.max(ow,oh)/(sample*2)>=1700)sample*=2;
            BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=sample;Bitmap bm=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
            if(bm==null){stats.reject("could not decode the image");decision(p,u,"assess","REJECTED","could not decode the image");return false;}
            double sc=Math.min(1.0,850.0/Math.max(bm.getWidth(),bm.getHeight()));
            Bitmap s=sc<1?Bitmap.createScaledBitmap(bm,(int)Math.round(bm.getWidth()*sc),(int)Math.round(bm.getHeight()*sc),true):bm;
            int w=s.getWidth(),h=s.getHeight();int[] px=new int[w*h],g=new int[w*h];s.getPixels(px,0,w,0,0,w,h);
            for(int i=0;i<px.length;i++){int c=px[i];g[i]=(77*Color.red(c)+150*Color.green(c)+29*Color.blue(c))>>8;}
            if(s!=bm)s.recycle();bm.recycle();
            FaceOnGate.Result f=FaceOnGate.assess(g,w,h,ow/(double)w,ow,oh);
            if(!f.ok){stats.reject(f.reason);decision(p,u,"assess","REJECTED",f.reason);msgUi("  reject "+shorten(fileName(u),48)+": "+f.reason);return false;}
            String rr=safe(p.ref),sku=p.sku.isEmpty()?"listing"+Math.abs(p.url.hashCode()):p.sku;File d=new File(images,rr);d.mkdirs();
            String ext=fileName(u).toLowerCase(Locale.US).matches(".*\\.png$")?".png":".jpg";
            File dest=new File(d,rr+"_"+sku+"_"+sha.substring(0,10)+ext);
            try(FileOutputStream os=new FileOutputStream(dest)){os.write(bytes);}
            synchronized(hashes){hashes.add(sha);line(hashFile,sha);}
            stats.accepted++;
            String row=csv(rr)+","+csv(p.sku)+","+csv(p.title)+","+csv(p.url)+","+csv(u)+","+csv(dest.getName())+","+csv(sha)+","+ow+","+oh+","+csv(acquisition)+","+fmt(f.radiusPx)+","+fmt(f.axis)+","+fmt(f.support)+","+fmt(f.sharpness);
            line(manifest,row);line(new File(d,"manifest.csv"),row);decision(p,u,"assess","ACCEPTED",f.summary());
            msgUi("KEEP "+rr+" "+dest.getName()+"  "+f.summary());
            return true;
        }catch(Exception e){stats.reject("error: "+e.getClass().getSimpleName());decision(p,u,"assess","REJECTED","error "+e.getClass().getSimpleName());return false;}
    }

    /** fallback for URLs the HTTP download could not fetch: the page's own image loader (same origin), drawn to a canvas */
    void captureInWebView(Product p,List<String> urls,int keptSoFar){
        long token=++captureToken;captureProduct=p;captureKept=keptSoFar;
        JSONArray arr=new JSONArray();for(String u:urls)arr.put(u);
        String js="(async function(){const token="+token+",urls="+arr.toString()+";"+
                "for(const raw of urls){try{const u=new URL(raw,location.href);"+
                "const img=await new Promise((ok,fail)=>{const x=new Image();x.onload=()=>ok(x);x.onerror=()=>fail(new Error('image did not load in the page'));x.src=u.href;});"+
                "const w=img.naturalWidth,h=img.naturalHeight;if(!w||!h)throw new Error('empty image');"+
                "const cv=document.createElement('canvas');cv.width=w;cv.height=h;cv.getContext('2d').drawImage(img,0,0);"+
                "WatchAlignCapture.image(token,u.href,cv.toDataURL('image/jpeg',0.97));"+
                "}catch(e){WatchAlignCapture.fail(token,String(raw),(e&&e.name?e.name+': ':'')+(e&&e.message||e));}}WatchAlignCapture.done(token);})();";
        web.evaluateJavascript(js,null);
    }
    volatile int captureKept=0;
    class CaptureBridge {
        @JavascriptInterface public void image(long token,String url,String dataUrl){
            if(token!=captureToken||captureProduct==null||dataUrl==null)return;
            int comma=dataUrl.indexOf(',');if(comma<0)return;
            byte[] bytes;try{bytes=Base64.decode(dataUrl.substring(comma+1),Base64.DEFAULT);}catch(Exception e){return;}
            Product p=captureProduct;
            io.submit(()->{stats.downloadsOk++;stats.downloadsFailed--;if(assessAndSave(p,url,bytes,"webview canvas (re-encoded)"))captureKept++;});
        }
        @JavascriptInterface public void fail(long token,String url,String reason){
            if(token!=captureToken)return;Product p=captureProduct;if(p!=null)decision(p,url,"webview capture","FAILED",reason);msgUi("  page loader failed for "+shorten(url,60)+": "+reason);
        }
        @JavascriptInterface public void done(long token){
            Product p=captureProduct;
            io.submit(()->{if(token!=captureToken||p==null)return;int kept=captureKept;runOnUiThread(()->{if(token==captureToken){captureProduct=null;finishProduct(p,kept+" accepted (after page-loader fallback).");}});});
        }
    }

    void buildZips(){
        if(imageCount()==0){String why=stats.explainZero();Toast.makeText(this,"No accepted images to export.",Toast.LENGTH_LONG).show();status.setText("No accepted images to export."+(why==null?"":"\n"+why));msg("ZIP export: nothing to export."+(why==null?"":" "+why));return;}
        zip.setEnabled(false);bar.setVisibility(View.VISIBLE);
        io.submit(()->{
            int refs=0,zips=0,fails=0;List<String> failures=new ArrayList<>();
            File[] ds=images.listFiles(File::isDirectory);if(ds==null)ds=new File[0];Arrays.sort(ds,Comparator.comparing(File::getName));
            String stamp=new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date());File tmp=new File(getCacheDir(),"zips");tmp.mkdirs();
            for(File d:ds){
                try{
                    List<File> parts=ZipExporter.export(d,tmp,stamp);if(!parts.isEmpty())refs++;
                    for(File z:parts){
                        try{double mb=z.length()/1048576.0;toDownloads(z,z.getName());zips++;stats.zipsCreated++;msgUi("ZIP saved: Downloads/WatchAlign_Bobs_Harvest/"+z.getName()+" ("+String.format(Locale.US,"%.1f",mb)+" MB)");}
                        catch(Exception e){fails++;stats.zipFailures++;failures.add(z.getName()+": "+e.getMessage());msgUi("ZIP export FAILED for "+z.getName()+": "+e.getMessage());}
                        finally{z.delete();}
                    }
                }catch(Exception e){fails++;stats.zipFailures++;failures.add(d.getName()+": "+e.getMessage());msgUi("ZIP build FAILED for "+d.getName()+": "+e.getMessage());}
            }
            int fr=refs,fz=zips,ff=fails;
            runOnUiThread(()->{bar.setVisibility(View.GONE);zip.setEnabled(true);
                status.setText((ff==0?"Export finished. ":"Export finished with "+ff+" FAILURE(S). ")+fr+" Rolex references, "+imageCount()+" images, "+fz+" ZIP files saved in Downloads/WatchAlign_Bobs_Harvest (each below 30 MB).");
                Toast.makeText(this,ff==0?"ZIPs saved in Downloads/WatchAlign_Bobs_Harvest":"Some ZIPs failed: see the log",Toast.LENGTH_LONG).show();
                msg("Export summary: "+fz+" ZIP(s), "+ff+" failure(s)"+(failures.isEmpty()?"":" "+failures));});
        });
    }
    void toDownloads(File src,String name)throws Exception{
        ContentResolver cr=getContentResolver();ContentValues v=new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,"application/zip");
        v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/WatchAlign_Bobs_Harvest");v.put(MediaStore.Downloads.IS_PENDING,1);
        Uri u=cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Downloads refused the new file");
        try{
            try(InputStream in=new FileInputStream(src);OutputStream out=cr.openOutputStream(u)){if(out==null)throw new IOException("cannot open the Downloads file");byte[] b=new byte[65536];int k;while((k=in.read(b))>=0)out.write(b,0,k);}
            ContentValues q=new ContentValues();q.put(MediaStore.Downloads.IS_PENDING,0);cr.update(u,q,null,null);
        }catch(Exception e){cr.delete(u,null,null);throw e;}
    }

    void decision(Product p,String url,String stage,String outcome,String reason){
        line(decisions,csv(new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date()))+","+csv(p.ref)+","+csv(p.sku)+","+csv(p.url)+","+csv(url)+","+csv(stage)+","+csv(outcome)+","+csv(reason));
    }
    static String firstLine(File f){try(BufferedReader r=new BufferedReader(new FileReader(f))){String x=r.readLine();return x==null?"":x.trim();}catch(Exception e){return "";}}
    static String fileName(String u){int q=u.indexOf('?');if(q>=0)u=u.substring(0,q);return u.substring(u.lastIndexOf('/')+1);}
    String jsValue(String v)throws Exception{Object x=new JSONTokener(v==null?"null":v).nextValue();return x instanceof String?(String)x:String.valueOf(x);}
    String sha(byte[] b)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte x:d)s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
    void markDone(String u){synchronized(done){if(done.add(u))line(doneFile,u);}}
    synchronized void line(File f,String s){try{File p=f.getParentFile();if(p!=null)p.mkdirs();try(FileWriter w=new FileWriter(f,true)){w.write(s);w.write("\n");}}catch(Exception ignored){}}
    void readSet(File f,Set<String>s){if(!f.exists())return;try(BufferedReader r=new BufferedReader(new FileReader(f))){String x;while((x=r.readLine())!=null)if(!x.trim().isEmpty())s.add(x.trim());}catch(Exception ignored){}}
    int imageCount(){int n=0;File[]d=images.listFiles(File::isDirectory);if(d!=null)for(File x:d){File[]f=x.listFiles((a,b)->b.endsWith(".jpg")||b.endsWith(".png"));if(f!=null)n+=f.length;}return n;}
    void msg(String s){log.append(s+"\n");line(runLog,new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+" "+s);if(log.length()>22000)log.setText(log.getText().subSequence(log.length()-16000,log.length()));}
    void msgUi(String s){runOnUiThread(()->msg(s));}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    static String shorten(String s,int n){s=clean(s);return s.length()<=n?s:s.substring(0,n-1)+"…";}
    static String fmt(double x){return Double.isFinite(x)?String.format(Locale.US,"%.4f",x):"";}

    @Override protected void onDestroy(){super.onDestroy();stopping=true;running=false;io.shutdownNow();if(web!=null)web.destroy();}
    static class Product{String title="",url="",ref="",sku="";}
}
