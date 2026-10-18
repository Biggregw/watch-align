package com.watchalign.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Watch Align 1.6.2: the owner's calibration on the phone (app-private storage, never uploaded):
 *   calibration/limits.json        saved limits per model
 *   calibration/photos/ID.json     one checked photo: its measured numbers and the owner's verdicts
 *   calibration/photos/ID.jpg      a small picture of that dial, so the owner can recognise it
 * Every failure is swallowed: calibration never stops a check from running.
 */
final class CalibrationStore {
    static final int MAX_PHOTOS=300;
    private CalibrationStore(){}

    static File dir(Context c){File d=new File(c.getFilesDir(),"calibration/photos");d.mkdirs();return d;}
    static File limitsFile(Context c){File d=new File(c.getFilesDir(),"calibration");d.mkdirs();return new File(d,"limits.json");}

    /** the saved limits into ClearlyOff.USER (call once at start-up) */
    static void loadLimits(Context c){
        try{File f=limitsFile(c);if(!f.exists())return;
            Map<String,ClearlyOff.Limits> m=Calibration.limitsFromJson(read(f));ClearlyOff.USER.clear();ClearlyOff.USER.putAll(m);}
        catch(Throwable ignored){}
    }
    /** save (or, with null, remove) the limits of one model */
    static void saveLimits(Context c,String model,ClearlyOff.Limits L){
        try{if(L==null||L.isDefault())ClearlyOff.USER.remove(model);else ClearlyOff.USER.put(model,L.copy());
            write(limitsFile(c),Calibration.limitsToJson(new LinkedHashMap<>(ClearlyOff.USER)));}
        catch(Throwable ignored){}
    }

    static void save(Context c,Calibration.Record r,Bitmap thumb){
        if(r==null)return;
        try{write(new File(dir(c),r.id+".json"),Calibration.toJson(r));
            if(thumb!=null){Bitmap t=Bitmap.createScaledBitmap(thumb,240,Math.max(1,240*thumb.getHeight()/Math.max(1,thumb.getWidth())),true);
                try(OutputStream o=new FileOutputStream(new File(dir(c),r.id+".jpg"))){t.compress(Bitmap.CompressFormat.JPEG,85,o);}}
            List<Calibration.Record> all=all(c);
            for(int i=MAX_PHOTOS;i<all.size();i++)delete(c,all.get(i).id);   // oldest beyond the cap
        }catch(Throwable ignored){}
    }
    static void delete(Context c,String id){new File(dir(c),id+".json").delete();new File(dir(c),id+".jpg").delete();}

    /** every saved photo, newest first */
    static List<Calibration.Record> all(Context c){
        List<Calibration.Record> out=new ArrayList<>();
        File[] fs=dir(c).listFiles((d,n)->n.endsWith(".json"));
        if(fs!=null)for(File f:fs){try{out.add(Calibration.recordFromJson(read(f)));}catch(Throwable ignored){}}
        out.sort((a,b)->Long.compare(b.time,a.time));
        return out;
    }
    static Calibration.Record get(Context c,String id){
        try{File f=new File(dir(c),id+".json");return f.exists()?Calibration.recordFromJson(read(f)):null;}catch(Throwable t){return null;}
    }
    static Bitmap thumb(Context c,String id){try{File f=new File(dir(c),id+".jpg");return f.exists()?BitmapFactory.decodeFile(f.getPath()):null;}catch(Throwable t){return null;}}

    /** the verified genuine markers' numbers for a model (asset), or null */
    static Calibration.Genuine genuine(Context c,String model){
        try(InputStream in=c.getAssets().open("models/"+model+"/alignment/genuine_track.csv")){
            return Calibration.Genuine.parse(new String(readAll(in),StandardCharsets.UTF_8));
        }catch(Throwable t){return null;}
    }

    static String read(File f)throws java.io.IOException{return new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}
    static void write(File f,String s)throws java.io.IOException{
        File tmp=new File(f.getPath()+".tmp");java.nio.file.Files.write(tmp.toPath(),s.getBytes(StandardCharsets.UTF_8));
        if(!tmp.renameTo(f)){f.delete();tmp.renameTo(f);}
    }
    static byte[] readAll(InputStream in)throws java.io.IOException{
        java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))>0)b.write(buf,0,n);return b.toByteArray();
    }
    static Map<String,ClearlyOff.Limits> limitsForExport(){return new LinkedHashMap<>(ClearlyOff.USER);}
}
