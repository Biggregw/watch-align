package com.watchalign.mobile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Test-set photos collected on the phone (alpha66): the images and one CSV of tags, kept in the
 * app's own storage (no Android types here, so it is unit-tested on the JVM).
 *
 * The manifest uses the corpus columns (local_path, class_label, physical_watch_id) first, so an
 * export drops straight into datasets/ with tools/testset/ingest.py.
 */
final class TestSetStore {
    static final String[] COLUMNS={"local_path","class_label","physical_watch_id","model","factory","source","notes",
            "captured_at","app_version","dial_found","twelve_found","pose","marker_tilt_deg","suitable","uploaded"};

    static final class Entry {
        String file="",cls="unsure",watchId="",model="",factory="",source="",notes="",capturedAt="",appVersion="";
        /** Photo check, filled in after saving: "" until run. */
        String dialFound="",twelveFound="",pose="",markerTilt="",suitable="";
        boolean uploaded;
        String[] row(){return new String[]{"images/"+file,cls,watchId,model,factory,source,notes,capturedAt,appVersion,
                dialFound,twelveFound,pose,markerTilt,suitable,uploaded?"yes":""};}
        static Entry of(String[] r){
            Entry e=new Entry();
            e.file=r[0].startsWith("images/")?r[0].substring(7):r[0];e.cls=r[1];e.watchId=r[2];e.model=r[3];e.factory=r[4];
            e.source=r[5];e.notes=r[6];e.capturedAt=r[7];e.appVersion=r[8];e.dialFound=r[9];e.twelveFound=r[10];
            e.pose=r[11];e.markerTilt=r[12];e.suitable=r[13];e.uploaded="yes".equals(r[14]);
            return e;
        }
        String label(){
            String c="gen".equals(cls)?"Genuine":"rep".equals(cls)?"Replica":"Not sure";
            return c+(model.isEmpty()?"":" · "+model)+(factory.isEmpty()?"":" · "+factory)+(watchId.isEmpty()?"":" · watch "+watchId);
        }
        /** Plain words for the photo check. */
        String check(){
            if(suitable.isEmpty())return "checking…";
            if("no".equals(dialFound))return "no dial found: not usable";
            if("no".equals(twelveFound))return "12 marker not found: limited use";
            if("RETAKE".equals(pose))return "too angled: limited use";
            return "good for the test set"+(markerTilt.isEmpty()?"":" (angle "+markerTilt+"°)");
        }
    }

    final File dir,images,manifest;
    final List<Entry> entries=new ArrayList<>();

    TestSetStore(File root){
        dir=new File(root,"testset");images=new File(dir,"images");manifest=new File(dir,"entries.csv");
        //noinspection ResultOfMethodCallIgnored
        images.mkdirs();
    }

    void load()throws IOException{
        entries.clear();
        if(!manifest.exists())return;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(manifest),StandardCharsets.UTF_8))){
            String line=r.readLine();   // header
            while((line=r.readLine())!=null){
                if(line.trim().isEmpty())continue;
                String[] f=parse(line);
                if(f.length>=COLUMNS.length&&new File(images,f[0].replace("images/","")).exists())entries.add(Entry.of(f));
            }
        }
    }

    void save()throws IOException{
        File tmp=new File(dir,"entries.csv.tmp");
        try(Writer w=new OutputStreamWriter(new FileOutputStream(tmp),StandardCharsets.UTF_8)){writeCsv(w,entries);}
        if(!tmp.renameTo(manifest)){
            try(Writer w=new OutputStreamWriter(new FileOutputStream(manifest),StandardCharsets.UTF_8)){writeCsv(w,entries);}
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    void remove(Entry e)throws IOException{
        entries.remove(e);
        //noinspection ResultOfMethodCallIgnored
        new File(images,e.file).delete();
        save();
    }

    int count(String cls){int n=0;for(Entry e:entries)if(cls.equals(e.cls))n++;return n;}

    /** Next free watch id for a new watch ("w001", "w002", ...). */
    String nextWatchId(){
        int max=0;
        for(Entry e:entries)if(e.watchId.matches("w\\d+"))max=Math.max(max,Integer.parseInt(e.watchId.substring(1)));
        return String.format(Locale.US,"w%03d",max+1);
    }

    static void writeCsv(Writer w,List<Entry> es)throws IOException{
        w.write(join(COLUMNS));w.write("\n");
        for(Entry e:es){w.write(join(e.row()));w.write("\n");}
        w.flush();
    }

    /** Zip of manifest.csv and images/ for the given entries. */
    void exportZip(List<Entry> es,OutputStream out)throws IOException{
        try(ZipOutputStream z=new ZipOutputStream(out)){
            z.putNextEntry(new ZipEntry("manifest.csv"));
            Writer w=new OutputStreamWriter(z,StandardCharsets.UTF_8);writeCsv(w,es);w.flush();
            z.closeEntry();
            byte[] buf=new byte[65536];
            for(Entry e:es){
                File f=new File(images,e.file);if(!f.exists())continue;
                z.putNextEntry(new ZipEntry("images/"+e.file));
                try(FileInputStream in=new FileInputStream(f)){int n;while((n=in.read(buf))>0)z.write(buf,0,n);}
                z.closeEntry();
            }
        }
    }

    static String join(String[] f){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<f.length;i++){if(i>0)b.append(',');b.append(quote(f[i]));}
        return b.toString();
    }
    static String quote(String s){
        if(s==null)return "";
        if(s.indexOf(',')<0&&s.indexOf('"')<0&&s.indexOf('\n')<0)return s;
        return '"'+s.replace("\"","\"\"").replace("\n"," ")+'"';
    }
    static String[] parse(String line){
        List<String> out=new ArrayList<>();StringBuilder cur=new StringBuilder();boolean q=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if(q){if(c=='"'){if(i+1<line.length()&&line.charAt(i+1)=='"'){cur.append('"');i++;}else q=false;}else cur.append(c);}
            else if(c=='"')q=true;
            else if(c==','){out.add(cur.toString());cur.setLength(0);}
            else cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
