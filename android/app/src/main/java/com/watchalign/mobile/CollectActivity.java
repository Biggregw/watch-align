package com.watchalign.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Collect test photos (alpha66): add genuine and replica GMT photos with their tags, see at once
 * whether each photo is usable (dial found, 12 found, angle), and send the set on as a zip or to
 * GitHub. QC verdicts are deliberately not shown here, so they cannot sway the labels.
 */
public class CollectActivity extends Activity {
    private static final int PICK=2001,CAPTURE=2002;
    private static final int BG=Color.rgb(8,17,31),ACCENT=Color.rgb(50,213,242),MUTED=Color.rgb(158,176,201);
    static final String[] MODELS={"126710BLNR","126710BLRO","126710GRNR","126711CHNR","126713GRNR","126715CHNR",
            "126718GRNR","126719BLRO","126720VTNR","126729VTNR","Other / not sure"};
    private static final String[] GPS_TAGS={ExifInterface.TAG_GPS_LATITUDE,ExifInterface.TAG_GPS_LATITUDE_REF,ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,ExifInterface.TAG_GPS_ALTITUDE,ExifInterface.TAG_GPS_ALTITUDE_REF,ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,ExifInterface.TAG_GPS_PROCESSING_METHOD,ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_SPEED,ExifInterface.TAG_GPS_SPEED_REF,ExifInterface.TAG_GPS_TRACK,ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,ExifInterface.TAG_GPS_IMG_DIRECTION_REF,ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,ExifInterface.TAG_GPS_MAP_DATUM,ExifInterface.TAG_GPS_SATELLITES,ExifInterface.TAG_GPS_STATUS};

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TestSetStore store;
    private TextView counts,status;
    private LinearLayout list;
    private File captureFile;
    /** Tags of the last batch, offered again ("same watch"). */
    private TestSetStore.Entry last;

    @Override public void onCreate(Bundle s){
        super.onCreate(s);
        OpenCVLoader.initLocal();
        File root=getExternalFilesDir(null);if(root==null)root=getFilesDir();
        store=new TestSetStore(root);
        try{store.load();}catch(Exception e){Toast.makeText(this,"Could not read the saved list: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        if(!store.entries.isEmpty())last=store.entries.get(store.entries.size()-1);
        setContentView(buildUi());
        refresh();
        // Photos saved before a check finished (app closed) are checked now.
        for(TestSetStore.Entry e:new ArrayList<>(store.entries))if(e.suitable.isEmpty())check(e);
    }

    private View buildUi(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        // Android 15 draws apps edge to edge: keep the content clear of the status and navigation bars (alpha66).
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);
        scroll.addView(root,new ViewGroup.LayoutParams(-1,-1));
        root.addView(text("Collect test photos",26,Color.WHITE));
        root.addView(text("Add GMT-Master II dial photos you are sure about, tag each one genuine or replica, and send the set on. "
                +"Straight-on photos with the whole dial and the hands away from the markers are the most useful. "
                +"Photos stay on this phone until you export or upload them; location data is removed when a photo is added.",13,MUTED));
        counts=text("",15,Color.WHITE);root.addView(counts,lp(-1,-2,12));

        Button add=button("Add photos from gallery");add.setOnClickListener(v->pick());root.addView(add,lp(-1,dp(52),10));
        Button cam=button("Take a photo");cam.setOnClickListener(v->capture());root.addView(cam,lp(-1,dp(52),6));

        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button exp=button("Export zip");exp.setBackgroundColor(ACCENT);exp.setTextColor(Color.rgb(4,32,42));exp.setOnClickListener(v->exportZip());
        Button up=button("Upload to GitHub");up.setOnClickListener(v->upload());
        Button set=button("GitHub settings");set.setOnClickListener(v->settings());
        row.addView(exp,new LinearLayout.LayoutParams(0,dp(52),1));row.addView(up,new LinearLayout.LayoutParams(0,dp(52),1));row.addView(set,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(row,lp(-1,dp(52),10));

        status=text("",13,MUTED);root.addView(status,lp(-1,-2,8));
        root.addView(text("Photos (hold one to remove it)",15,Color.WHITE),lp(-1,-2,14));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list,lp(-1,-2,6));
        return scroll;
    }

    private void refresh(){
        int g=store.count("gen"),r=store.count("rep"),u=store.count("unsure"),good=0,up=0;
        for(TestSetStore.Entry e:store.entries){if(e.check().startsWith("good"))good++;if(e.uploaded)up++;}
        counts.setText(String.format(Locale.US,"%d genuine · %d replica · %d not sure\n%d of %d good for the test set · %d uploaded",g,r,u,good,store.entries.size(),up));
        list.removeAllViews();
        for(int i=store.entries.size()-1;i>=0;i--){
            final TestSetStore.Entry e=store.entries.get(i);
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.HORIZONTAL);item.setPadding(0,dp(6),0,dp(6));
            ImageView th=new ImageView(this);th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b=thumb(new File(store.images,e.file));if(b!=null)th.setImageBitmap(b);
            item.addView(th,new LinearLayout.LayoutParams(dp(72),dp(72)));
            LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(dp(10),0,0,0);
            col.addView(text(e.label(),14,Color.WHITE));
            col.addView(text(e.check()+(e.uploaded?" · uploaded":""),13,e.check().startsWith("good")?Color.rgb(40,210,120):MUTED));
            if(!e.source.isEmpty())col.addView(text(e.source,12,MUTED));
            item.addView(col,new LinearLayout.LayoutParams(0,-2,1));
            item.setOnLongClickListener(v->{
                new AlertDialog.Builder(this).setTitle("Remove this photo?").setMessage(e.label())
                        .setPositiveButton("Remove",(d,w)->{try{store.remove(e);}catch(Exception ex){toast(ex.getMessage());}refresh();})
                        .setNegativeButton("Keep",null).show();
                return true;
            });
            list.addView(item);
        }
    }

    // ---- adding photos ----

    private void pick(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK);
    }

    private void capture(){
        String name="capture_"+System.currentTimeMillis()+".jpg";
        File dir=new File(store.dir,"capture");//noinspection ResultOfMethodCallIgnored
        dir.mkdirs();captureFile=new File(dir,name);
        Uri out=TestSetFileProvider.uriFor("capture",name);
        Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT,out);
        i.setClipData(ClipData.newRawUri("",out));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(i,CAPTURE);}catch(Exception e){toast("No camera app found");}
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK)return;
        List<Uri> uris=new ArrayList<>();
        if(req==PICK&&data!=null){
            if(data.getClipData()!=null)for(int k=0;k<data.getClipData().getItemCount();k++)uris.add(data.getClipData().getItemAt(k).getUri());
            else if(data.getData()!=null)uris.add(data.getData());
        }else if(req==CAPTURE&&captureFile!=null&&captureFile.length()>0)uris.add(Uri.fromFile(captureFile));
        if(!uris.isEmpty())askTags(uris);
    }

    /** One set of tags for the photos just added (usually one watch). */
    private void askTags(List<Uri> uris){
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(20),dp(8),dp(20),0);
        f.addView(text(uris.size()==1?"1 photo":uris.size()+" photos (tagged together)",13,Color.DKGRAY));
        RadioGroup cls=new RadioGroup(this);cls.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton g=radio("Genuine"),r=radio("Replica"),u=radio("Not sure");
        cls.addView(g);cls.addView(r);cls.addView(u);f.addView(cls);
        Spinner model=new Spinner(this);model.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,MODELS));f.addView(model);
        EditText factory=field("Factory (replicas, if known)");f.addView(factory);
        EditText source=field("Where from (link, seller, own watch)");f.addView(source);
        EditText notes=field("Notes (optional)");f.addView(notes);
        CheckBox same=new CheckBox(this);same.setText(last!=null?"Same watch as the last photos ("+last.watchId+")":"Same watch as the last photos");
        same.setEnabled(last!=null);f.addView(same);
        if(last!=null){
            // Default to the last batch's tags: consecutive photos are usually the same watch.
            (("gen".equals(last.cls))?g:"rep".equals(last.cls)?r:u).setChecked(true);
            for(int k=0;k<MODELS.length;k++)if(MODELS[k].equals(last.model))model.setSelection(k);
            factory.setText(last.factory);source.setText(last.source);
        }
        new AlertDialog.Builder(this).setTitle("Tag these photos").setView(f)
                .setPositiveButton("Add",(d,w)->{
                    int id=cls.getCheckedRadioButtonId();
                    if(id==-1){toast("Choose genuine, replica or not sure");askTags(uris);return;}
                    TestSetStore.Entry t=new TestSetStore.Entry();
                    t.cls=id==g.getId()?"gen":id==r.getId()?"rep":"unsure";
                    String m=MODELS[model.getSelectedItemPosition()];t.model=m.startsWith("Other")?"":m;
                    t.factory=factory.getText().toString().trim();t.source=source.getText().toString().trim();t.notes=notes.getText().toString().trim();
                    t.watchId=same.isChecked()&&last!=null?last.watchId:store.nextWatchId();
                    addAll(uris,t);
                })
                .setNegativeButton("Cancel",null).show();
    }

    private void addAll(List<Uri> uris,TestSetStore.Entry tags){
        status.setText("Adding "+uris.size()+(uris.size()==1?" photo…":" photos…"));
        worker.submit(()->{
            int n=0;
            for(Uri uri:uris){
                try{
                    TestSetStore.Entry e=copyTags(tags);
                    e.file=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+"_"+(n++)+"_"+e.watchId+".jpg";
                    e.capturedAt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(new Date());
                    e.appVersion=WatchAlignCoreV13.CORE_VERSION;
                    File dst=new File(store.images,e.file);
                    importPhoto(uri,dst);
                    synchronized(store){store.entries.add(e);store.save();}
                    last=e;
                    runOnUiThread(this::refresh);
                    checkNow(e);
                }catch(Throwable t){runOnUiThread(()->toast("Could not add a photo: "+t.getMessage()));}
            }
            if(captureFile!=null){//noinspection ResultOfMethodCallIgnored
                captureFile.delete();captureFile=null;}
            runOnUiThread(()->{status.setText("Added. Each photo is checked for dial, 12 marker and angle.");refresh();});
        });
    }

    private static TestSetStore.Entry copyTags(TestSetStore.Entry t){
        TestSetStore.Entry e=new TestSetStore.Entry();
        e.cls=t.cls;e.model=t.model;e.factory=t.factory;e.source=t.source;e.notes=t.notes;e.watchId=t.watchId;return e;
    }

    /** JPEGs are copied byte for byte with their location tags removed; anything else is saved as a high-quality JPEG. */
    private void importPhoto(Uri uri,File dst)throws Exception{
        String type="file".equals(uri.getScheme())?"image/jpeg":getContentResolver().getType(uri);
        if(type!=null&&(type.equals("image/jpeg")||type.equals("image/jpg"))){
            try(InputStream in=open(uri);OutputStream out=new FileOutputStream(dst)){byte[] b=new byte[65536];int k;while((k=in.read(b))>0)out.write(b,0,k);}
            try{ExifInterface ex=new ExifInterface(dst.getPath());for(String t:GPS_TAGS)ex.setAttribute(t,null);ex.saveAttributes();}
            catch(Exception ignored){/* no EXIF block: nothing to strip */}
        }else{
            Bitmap b;try(InputStream in=open(uri)){b=BitmapFactory.decodeStream(in);}
            if(b==null)throw new IllegalArgumentException("not a readable image");
            try(OutputStream out=new FileOutputStream(dst)){b.compress(Bitmap.CompressFormat.JPEG,95,out);}
        }
    }
    private InputStream open(Uri uri)throws Exception{
        return "file".equals(uri.getScheme())?new FileInputStream(new File(uri.getPath())):getContentResolver().openInputStream(uri);
    }

    // ---- photo check (no QC verdicts) ----

    private void check(TestSetStore.Entry e){worker.submit(()->checkNow(e));}

    private void checkNow(TestSetStore.Entry e){
        File f=new File(store.images,e.file);
        try{
            Bitmap preview=decodePreview(f);
            FullResSource full=fullSource(f);
            GmtDialCrop.Crop crop=full!=null?GmtDialCrop.make(preview,full):null;
            Bitmap b=crop!=null?crop.bitmap:preview;
            GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(b,MainActivity.GENERIC_GMT_CODE,null);
            boolean noDial=h.summary==null||h.summary.noDial||h.drawing==null||!Double.isFinite(h.drawing.dialCx);
            boolean twelve=!noDial&&h.drawing.twelve!=null;
            e.dialFound=noDial?"no":"yes";e.twelveFound=twelve?"yes":"no";e.pose=String.valueOf(h.poseLabel);
            if(!noDial&&h.round!=null){
                GmtMarkerPose.Result mp=GmtMarkerPose.estimate(h.round,Math.sqrt(h.drawing.dialA*h.drawing.dialB));
                e.markerTilt=mp.valid?String.format(Locale.US,"%.1f",mp.tiltDeg):"";
            }
            e.suitable=!noDial&&twelve&&h.poseLabel!=GmtHumanQcMath.PoseLabel.RETAKE?"yes":"no";
        }catch(Throwable t){e.dialFound="";e.suitable="no";e.notes=(e.notes.isEmpty()?"":e.notes+"; ")+"check failed: "+t.getClass().getSimpleName();}
        synchronized(store){try{store.save();}catch(Exception ignored){}}
        runOnUiThread(this::refresh);
    }

    private static Bitmap decodePreview(File f){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        int max=Math.max(o.outWidth,o.outHeight),s=1;while(max/(s*2)>=1600)s*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=s;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b=BitmapFactory.decodeFile(f.getPath(),o);
        if(b==null)throw new IllegalArgumentException("not a readable image");
        int m=Math.max(b.getWidth(),b.getHeight());
        if(m<=1600)return b.copy(Bitmap.Config.ARGB_8888,false);
        float k=1600f/m;return Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*k),Math.round(b.getHeight()*k),true).copy(Bitmap.Config.ARGB_8888,false);
    }
    private static FullResSource fullSource(File f){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        final int w=o.outWidth,h=o.outHeight;if(w<=0||h<=0)return null;
        return new FullResSource(){
            @Override public int width(){return w;}
            @Override public int height(){return h;}
            @SuppressWarnings("deprecation")
            @Override public Bitmap region(int x0,int y0,int x1,int y1,int ss){
                try{
                    BitmapRegionDecoder d=BitmapRegionDecoder.newInstance(f.getPath(),false);
                    try{BitmapFactory.Options ro=new BitmapFactory.Options();ro.inSampleSize=ss;ro.inPreferredConfig=Bitmap.Config.ARGB_8888;return d.decodeRegion(new Rect(x0,y0,x1,y1),ro);}
                    finally{d.recycle();}
                }catch(Throwable t){return null;}
            }
        };
    }
    private static Bitmap thumb(File f){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        int s=1;while(Math.max(o.outWidth,o.outHeight)/(s*2)>=200)s*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=s;return BitmapFactory.decodeFile(f.getPath(),o);
    }

    // ---- sending the set on ----

    private void exportZip(){
        if(store.entries.isEmpty()){toast("Add some photos first");return;}
        status.setText("Building the zip…");
        worker.submit(()->{
            try{
                File dir=new File(store.dir,"exports");//noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                String name="watchalign_testset_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date())+".zip";
                List<TestSetStore.Entry> es;synchronized(store){es=new ArrayList<>(store.entries);}
                try(OutputStream out=new FileOutputStream(new File(dir,name))){store.exportZip(es,out);}
                Uri uri=TestSetFileProvider.uriFor("export",name);
                Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/zip");send.putExtra(Intent.EXTRA_STREAM,uri);
                send.putExtra(Intent.EXTRA_SUBJECT,"Watch Align test photos");
                send.setClipData(ClipData.newRawUri("",uri));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(()->{status.setText("Zip ready ("+es.size()+" photos). Share it to Drive, email or the chat.");
                    startActivity(Intent.createChooser(send,"Send test photos"));});
            }catch(Throwable t){runOnUiThread(()->status.setText("Export failed: "+t.getMessage()));}
        });
    }

    private SharedPreferences prefs(){return getSharedPreferences("collect",MODE_PRIVATE);}

    private void settings(){
        SharedPreferences p=prefs();
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(20),dp(8),dp(20),0);
        f.addView(text("Optional. Use a fine-grained GitHub token limited to one repository, with Contents read and write. Photos go onto their own branch, not the app's main branch.",12,Color.DKGRAY));
        EditText repo=field("Repository (owner/name)");repo.setText(p.getString("repo","Biggregw/watch-align"));f.addView(repo);
        EditText branch=field("Branch");branch.setText(p.getString("branch","testset-inbox"));f.addView(branch);
        EditText folder=field("Folder");folder.setText(p.getString("folder","testset"));f.addView(folder);
        EditText token=field("Token");token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setText(p.getString("token",""));f.addView(token);
        new AlertDialog.Builder(this).setTitle("GitHub upload").setView(f)
                .setPositiveButton("Save",(d,w)->p.edit().putString("repo",repo.getText().toString().trim()).putString("branch",branch.getText().toString().trim())
                        .putString("folder",folder.getText().toString().trim()).putString("token",token.getText().toString().trim()).apply())
                .setNeutralButton("Forget token",(d,w)->p.edit().remove("token").apply())
                .setNegativeButton("Cancel",null).show();
    }

    private void upload(){
        SharedPreferences p=prefs();
        if(p.getString("token","").isEmpty()){toast("Set up GitHub first");settings();return;}
        List<TestSetStore.Entry> todo=new ArrayList<>();
        synchronized(store){for(TestSetStore.Entry e:store.entries)if(!e.uploaded)todo.add(e);}
        if(todo.isEmpty()){toast("Everything is already uploaded");return;}
        status.setText("Uploading "+todo.size()+" photos…");
        worker.submit(()->{
            try{
                GitHubUploader up=new GitHubUploader(p.getString("repo",""),p.getString("branch","testset-inbox"),p.getString("folder","testset"),p.getString("token",""));
                up.ensureBranch();
                int done=0;
                for(TestSetStore.Entry e:todo){
                    byte[] bytes=readAll(new File(store.images,e.file));
                    up.put("images/"+e.file,bytes,"Add test photo "+e.file+" ("+e.cls+")");
                    e.uploaded=true;done++;
                    final int d=done;runOnUiThread(()->status.setText("Uploaded "+d+" of "+todo.size()+"…"));
                    synchronized(store){store.save();}
                }
                java.io.StringWriter sw=new java.io.StringWriter();TestSetStore.writeCsv(sw,todo);
                String batch="manifests/"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".csv";
                up.put(batch,sw.toString().getBytes("UTF-8"),"Add test-set manifest "+batch);
                runOnUiThread(()->{status.setText("Uploaded "+todo.size()+" photos and "+batch+" to "+up.owner+"/"+up.repo+" ("+up.branch+").");refresh();});
            }catch(Throwable t){runOnUiThread(()->{status.setText("Upload stopped: "+t.getMessage()+" Photos already sent are marked; try again to send the rest.");refresh();});}
        });
    }

    private static byte[] readAll(File f)throws Exception{
        try(InputStream in=new FileInputStream(f)){java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)b.write(buf,0,n);return b.toByteArray();}
    }

    // ---- small UI helpers ----
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private RadioButton radio(String s){RadioButton b=new RadioButton(this);b.setText(s);b.setId(View.generateViewId());return b;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);return e;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(w,h);q.topMargin=dp(top);return q;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
