package com.watchalign.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Finds GMT photos on r/RepTimeQC through Reddit's official API (alpha67). Photos are downloaded
 * into a separate candidates list, given the usual photo check, and only join the test set when
 * the user adds them. Model and factory are guessed from the post title and can be changed.
 * Titles that suggest a genuine watch is also shown are tagged "not sure" and never bulk-added.
 */
public class RepTimeQcActivity extends Activity {
    private static final int BG=Color.rgb(8,17,31),ACCENT=Color.rgb(50,213,242),MUTED=Color.rgb(158,176,201);
    private static final int NEW_POSTS_PER_SEARCH=10,MAX_PAGES=5;

    private final ExecutorService worker=Executors.newSingleThreadExecutor(),reviewer=Executors.newSingleThreadExecutor();
    private final java.util.Map<String,Bitmap> thumbs=new java.util.concurrent.ConcurrentHashMap<>();
    private TestSetStore cands,store;
    private TextView status,counts;
    private EditText query;
    private LinearLayout list;
    private volatile boolean busy;

    @Override public void onCreate(Bundle s){
        super.onCreate(s);
        OpenCVLoader.initLocal();
        File root=getExternalFilesDir(null);if(root==null)root=getFilesDir();
        store=new TestSetStore(root);cands=new TestSetStore(new File(root,"reptimeqc"));
        try{store.load();cands.load();}catch(Exception e){toast("Could not read the saved lists: "+e.getMessage());}
        setContentView(buildUi());
        refresh();
        if(prefs().getString("client_id","").isEmpty())settings();
    }

    private ScrollView buildUi(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);
        scroll.addView(root,new ViewGroup.LayoutParams(-1,-1));
        root.addView(text("Find photos on RepTimeQC",26,Color.WHITE));
        root.addView(text("Searches r/RepTimeQC through Reddit's official API and downloads the photos from new GMT posts for you to review. "
                +"Model and factory are guessed from the post title: tap a photo's title to correct them. Photos without a dial are dropped. "
                +"Posts whose title mentions a genuine watch are tagged \"not sure\" and are not added in bulk.",13,MUTED));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(Color.WHITE);query.setHintTextColor(MUTED);
        query.setHint("Search");query.setText(prefs().getString("query","GMT"));
        row.addView(query,new LinearLayout.LayoutParams(0,-2,1));
        Button go=button("Search");go.setBackgroundColor(ACCENT);go.setTextColor(Color.rgb(4,32,42));go.setOnClickListener(v->search());
        row.addView(go,new LinearLayout.LayoutParams(dp(110),dp(52)));
        root.addView(row,lp(-1,-2,10));
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        Button all=button("Add all good ones");all.setOnClickListener(v->addAllGood());
        Button rest=button("Skip the rest");rest.setOnClickListener(v->skipAll());
        Button set=button("Reddit settings");set.setOnClickListener(v->settings());
        row2.addView(all,new LinearLayout.LayoutParams(0,dp(52),1));row2.addView(rest,new LinearLayout.LayoutParams(0,dp(52),1));row2.addView(set,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(row2,lp(-1,dp(52),6));
        status=text("",13,MUTED);root.addView(status,lp(-1,-2,8));
        counts=text("",15,Color.WHITE);root.addView(counts,lp(-1,-2,10));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list,lp(-1,-2,6));
        return scroll;
    }

    private void refresh(){
        List<TestSetStore.Entry> snap;synchronized(cands){snap=new ArrayList<>(cands.entries);}
        int good=0;for(TestSetStore.Entry e:snap)if(bulkOk(e))good++;
        counts.setText(String.format(Locale.US,"%d photos to review · %d good for the test set\nTest set: %d replica · %d genuine",
                snap.size(),good,store.count("rep"),store.count("gen")));
        list.removeAllViews();
        for(final TestSetStore.Entry e:snap){
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.HORIZONTAL);item.setPadding(0,dp(8),0,dp(8));
            ImageView th=new ImageView(this);th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b=thumbs.get(e.file);
            if(b==null){b=thumb(new File(cands.images,e.file));if(b!=null)thumbs.put(e.file,b);}
            if(b!=null)th.setImageBitmap(b);
            item.addView(th,new LinearLayout.LayoutParams(dp(96),dp(96)));
            LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(dp(10),0,0,0);
            TextView title=text(e.notes,13,Color.WHITE);title.setMaxLines(2);title.setOnClickListener(v->editTags(e));col.addView(title);
            col.addView(text(e.label().replace(" · watch "+e.watchId,"")+"  (tap title to change)",12,ACCENT));
            col.addView(text(e.check(),12,e.check().startsWith("good")?Color.rgb(40,210,120):MUTED));
            LinearLayout btns=new LinearLayout(this);btns.setOrientation(LinearLayout.HORIZONTAL);
            Button add=button("Add");add.setOnClickListener(v->{add.setEnabled(false);reviewer.submit(()->{adopt(e);post(null);});});
            Button skip=button("Skip");skip.setOnClickListener(v->{skip.setEnabled(false);reviewer.submit(()->{drop(e);post(null);});});
            btns.addView(add,new LinearLayout.LayoutParams(0,dp(44),1));btns.addView(skip,new LinearLayout.LayoutParams(0,dp(44),1));
            col.addView(btns);
            item.addView(col,new LinearLayout.LayoutParams(0,-2,1));
            list.addView(item);
        }
    }

    /** "Add all good ones" takes replica photos that passed the photo check. */
    private static boolean bulkOk(TestSetStore.Entry e){return "rep".equals(e.cls)&&"yes".equals(e.suitable);}

    // ---- searching ----

    private void search(){
        if(busy){toast("Still working");return;}
        SharedPreferences p=prefs();
        if(p.getString("client_id","").isEmpty()){settings();return;}
        String q=query.getText().toString().trim();if(q.isEmpty())q="GMT";
        p.edit().putString("query",q).apply();
        final String fq=q;busy=true;status.setText("Searching…");
        worker.submit(()->{
            int posts=0,photos=0,noDial=0,skipped=0;String msg;
            try{
                RedditClient rc=new RedditClient(p.getString("client_id",""),p.getString("user",""),deviceId(),p.getString("imgur",""));
                Set<String> seen=new HashSet<>(p.getStringSet("seen",new HashSet<>()));
                String sub=p.getString("sub","RepTimeQC"),after=null;
                for(int page=0;page<MAX_PAGES&&posts<NEW_POSTS_PER_SEARCH;page++){
                    RedditClient.Page res=rc.search(sub,fq,after);
                    for(RedditClient.Post post:res.posts){
                        if(posts>=NEW_POSTS_PER_SEARCH)break;
                        if(seen.contains(post.id))continue;
                        TitleTags.Guess g=TitleTags.guess(post.title);
                        if(!g.gmt){skipped++;markSeen(seen,post.id);continue;}
                        List<String> urls=new ArrayList<>(post.images);
                        for(String album:post.imgurAlbums){
                            if(urls.size()>=RedditClient.MAX_IMAGES_PER_POST)break;
                            try{urls.addAll(rc.imgurAlbum(album));}catch(Exception ignored){/* album gone or private */}
                        }
                        if(urls.isEmpty()){skipped++;markSeen(seen,post.id);continue;}
                        posts++;
                        final int fp=posts;post("Post "+fp+": "+post.title);
                        int k=0;
                        for(String u:urls){
                            if(k>=RedditClient.MAX_IMAGES_PER_POST)break;
                            TestSetStore.Entry e=candidate(rc,post,g,u,k++);
                            if(e==null)continue;
                            if("no".equals(e.dialFound)){noDial++;new File(cands.images,e.file).delete();continue;}
                            synchronized(cands){cands.entries.add(e);cands.save();}
                            photos++;
                            post(null);
                        }
                        markSeen(seen,post.id);
                    }
                    after=res.after;if(after==null)break;
                    Thread.sleep(1000);   // stay well inside Reddit's rate limit
                }
                msg=posts==0?"No new GMT posts with photos"+(skipped>0?" ("+skipped+" posts without GMT photos skipped).":".")
                        :String.format(Locale.US,"%d new photos from %d posts%s. Review them below.",photos,posts,noDial>0?" ("+noDial+" without a dial dropped)":"");
            }catch(Throwable t){msg="Search stopped: "+t.getMessage();}
            busy=false;post(msg);
        });
    }

    /** Downloads, stores and checks one photo; null when it could not be fetched or read. */
    private TestSetStore.Entry candidate(RedditClient rc,RedditClient.Post post,TitleTags.Guess g,String url,int k){
        try{
            byte[] bytes=rc.download(url);
            TestSetStore.Entry e=new TestSetStore.Entry();
            e.file="rtqc_"+post.id+"_"+k+".jpg";
            File f=new File(cands.images,e.file);
            boolean jpeg=bytes.length>3&&(bytes[0]&0xFF)==0xFF&&(bytes[1]&0xFF)==0xD8;
            if(jpeg){try(OutputStream o=new FileOutputStream(f)){o.write(bytes);}TestSetOps.stripGps(f);}
            else{
                Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b==null)return null;
                try(OutputStream o=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.JPEG,95,o);}
            }
            e.cls=g.mixed?"unsure":"rep";e.model=g.model;e.factory=g.factory;e.source=post.permalink;
            e.notes=post.title+(g.mixed?" [title mentions a genuine: check which watch this is]":"");
            e.watchId="post_"+post.id;e.capturedAt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(new Date());
            e.appVersion=WatchAlignCoreV13.CORE_VERSION;
            TestSetOps.checkPhoto(f,e);
            return e;
        }catch(Throwable t){return null;}
    }

    private void markSeen(Set<String> seen,String id){seen.add(id);prefs().edit().putStringSet("seen",new HashSet<>(seen)).apply();}

    // ---- reviewing ----

    /** Moves a candidate into the test set. Photos of one post share one watch id. */
    private void adopt(TestSetStore.Entry c){
        synchronized(store){
            try{
                store.load();
                String wid=null;
                for(TestSetStore.Entry e:store.entries)if(!c.source.isEmpty()&&c.source.equals(e.source))wid=e.watchId;
                TestSetStore.Entry e=new TestSetStore.Entry();
                e.file=c.file;e.cls=c.cls;e.model=c.model;e.factory=c.factory;e.source=c.source;e.notes="RepTimeQC: "+c.notes;
                e.watchId=wid!=null?wid:store.nextWatchId();e.capturedAt=c.capturedAt;e.appVersion=c.appVersion;
                e.dialFound=c.dialFound;e.twelveFound=c.twelveFound;e.pose=c.pose;e.markerTilt=c.markerTilt;e.suitable=c.suitable;
                File from=new File(cands.images,c.file),to=new File(store.images,c.file);
                if(to.exists()){drop(c);return;}   // already in the test set
                if(!from.renameTo(to))throw new java.io.IOException("could not move "+c.file);
                store.entries.add(e);store.save();
                synchronized(cands){cands.entries.remove(c);cands.save();}
                thumbs.remove(c.file);
            }catch(Exception ex){post("Could not add: "+ex.getMessage());}
        }
    }
    private void drop(TestSetStore.Entry c){synchronized(cands){thumbs.remove(c.file);try{cands.remove(c);}catch(Exception ex){post("Could not skip: "+ex.getMessage());}}}

    private void addAllGood(){
        List<TestSetStore.Entry> good=new ArrayList<>();synchronized(cands){for(TestSetStore.Entry e:cands.entries)if(bulkOk(e))good.add(e);}
        if(good.isEmpty()){toast("No replica photos have passed the photo check");return;}
        new AlertDialog.Builder(this).setTitle("Add "+good.size()+" photos?")
                .setMessage("These are tagged replica with the model and factory shown. Check the tags first; photos stay on this phone until you upload them from Collect.")
                .setPositiveButton("Add",(d,w)->reviewer.submit(()->{for(TestSetStore.Entry e:good)adopt(e);post("Added "+good.size()+" photos to the test set.");}))
                .setNegativeButton("Cancel",null).show();
    }

    private void skipAll(){
        if(cands.entries.isEmpty())return;
        new AlertDialog.Builder(this).setTitle("Skip all "+cands.entries.size()+" photos left?")
                .setPositiveButton("Skip",(d,w)->reviewer.submit(()->{List<TestSetStore.Entry> all;synchronized(cands){all=new ArrayList<>(cands.entries);}for(TestSetStore.Entry e:all)drop(e);post("Cleared.");}))
                .setNegativeButton("Cancel",null).show();
    }

    /** Changes the tags of every photo from the same post. */
    private void editTags(TestSetStore.Entry c){
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(20),dp(8),dp(20),0);
        f.addView(text(c.notes,13,Color.DKGRAY));
        RadioGroup cls=new RadioGroup(this);cls.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton r=radio("Replica"),u=radio("Not sure");cls.addView(r);cls.addView(u);f.addView(cls);
        ("rep".equals(c.cls)?r:u).setChecked(true);
        Spinner model=new Spinner(this);model.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,TestSetStore.MODELS));f.addView(model);
        model.setSelection(TestSetStore.MODELS.length-1);
        for(int k=0;k<TestSetStore.MODELS.length;k++)if(TestSetStore.MODELS[k].equals(c.model))model.setSelection(k);
        EditText factory=new EditText(this);factory.setHint("Factory");factory.setSingleLine(true);factory.setText(c.factory);f.addView(factory);
        new AlertDialog.Builder(this).setTitle("Tags for this post").setView(f)
                .setPositiveButton("Save",(d,w)->{
                    String m=TestSetStore.MODELS[model.getSelectedItemPosition()];
                    synchronized(cands){
                        for(TestSetStore.Entry e:cands.entries)if(e.watchId.equals(c.watchId)){
                            e.cls=cls.getCheckedRadioButtonId()==r.getId()?"rep":"unsure";e.model=m.startsWith("Other")?"":m;e.factory=factory.getText().toString().trim();
                        }
                        try{cands.save();}catch(Exception ex){toast(ex.getMessage());}
                    }
                    refresh();
                })
                .setNegativeButton("Cancel",null).show();
    }

    // ---- settings ----

    private SharedPreferences prefs(){return getSharedPreferences("reptimeqc",MODE_PRIVATE);}

    private String deviceId(){
        SharedPreferences p=prefs();String id=p.getString("device_id","");
        if(id.length()<20){
            StringBuilder b=new StringBuilder();SecureRandom r=new SecureRandom();
            for(int i=0;i<30;i++)b.append("0123456789abcdef".charAt(r.nextInt(16)));
            id=b.toString();p.edit().putString("device_id",id).apply();
        }
        return id;
    }

    private void settings(){
        SharedPreferences p=prefs();
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(20),dp(8),dp(20),0);
        f.addView(text("One-time setup: on reddit.com/prefs/apps (signed in), choose \"create another app\", type \"installed app\", "
                +"any name, redirect uri http://localhost. The client id is the short code under the app's name. No password is stored.",12,Color.DKGRAY));
        EditText id=new EditText(this);id.setHint("Reddit client id");id.setSingleLine(true);id.setText(p.getString("client_id",""));f.addView(id);
        EditText user=new EditText(this);user.setHint("Your Reddit username (optional)");user.setSingleLine(true);user.setText(p.getString("user",""));f.addView(user);
        EditText sub=new EditText(this);sub.setHint("Subreddit");sub.setSingleLine(true);sub.setText(p.getString("sub","RepTimeQC"));f.addView(sub);
        f.addView(text("Optional, for Imgur albums: an Imgur Client-ID from api.imgur.com/oauth2/addclient. Without it, only Reddit photos and single Imgur images are fetched.",12,Color.DKGRAY));
        EditText imgur=new EditText(this);imgur.setHint("Imgur Client-ID (optional)");imgur.setSingleLine(true);imgur.setText(p.getString("imgur",""));f.addView(imgur);
        new AlertDialog.Builder(this).setTitle("Reddit settings").setView(f)
                .setPositiveButton("Save",(d,w)->p.edit().putString("client_id",id.getText().toString().trim()).putString("user",user.getText().toString().trim())
                        .putString("sub",sub.getText().toString().trim().replaceFirst("^/?r/","").isEmpty()?"RepTimeQC":sub.getText().toString().trim().replaceFirst("^/?r/",""))
                        .putString("imgur",imgur.getText().toString().trim()).apply())
                .setNeutralButton("Forget seen posts",(d,w)->p.edit().remove("seen").apply())
                .setNegativeButton("Cancel",null).show();
    }

    // ---- helpers ----
    private void post(String msg){runOnUiThread(()->{if(msg!=null)status.setText(msg);refresh();});}
    private static Bitmap thumb(File f){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        int s=1;while(Math.max(o.outWidth,o.outHeight)/(s*2)>=240)s*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=s;return BitmapFactory.decodeFile(f.getPath(),o);
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private RadioButton radio(String s){RadioButton b=new RadioButton(this);b.setText(s);b.setId(android.view.View.generateViewId());return b;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(w,h);q.topMargin=dp(top);return q;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();reviewer.shutdownNow();super.onDestroy();}
}
