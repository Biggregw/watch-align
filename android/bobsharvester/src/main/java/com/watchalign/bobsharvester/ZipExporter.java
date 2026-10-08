package com.watchalign.bobsharvester;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds the per-reference ZIP parts (Android-free; the activity copies them to Downloads). */
public final class ZipExporter {
    /** hard limit: every ZIP strictly below 30 MB */
    public static final long MAX_ZIP=30L*1024*1024;
    /** image payload per part; leaves room for the manifest, README and ZIP headers */
    public static final long PAYLOAD=27L*1024*1024;

    private ZipExporter(){}

    /** @return the ZIP files written in outDir for one reference folder (images *.jpg + manifest.csv) */
    public static List<File> export(File refDir,File outDir,String stamp)throws IOException{
        File[] aa=refDir.listFiles((x,n)->n.toLowerCase(Locale.US).endsWith(".jpg"));
        List<File> out=new ArrayList<>();if(aa==null||aa.length==0)return out;
        List<File> all=new ArrayList<>(Arrays.asList(aa));all.sort(Comparator.comparing(File::getName));
        long[] sz=new long[all.size()];for(int i=0;i<sz.length;i++)sz[i]=all.get(i).length();
        List<List<Integer>> parts=HarvestLogic.planParts(sz,PAYLOAD);
        String ref=HarvestLogic.safe(refDir.getName());
        for(int p=0;p<parts.size();p++){
            String part=parts.size()>1?String.format(Locale.US,"_part%02d-of-%02d",p+1,parts.size()):"";
            File zf=new File(outDir,"Rolex_"+ref+"_Bobs_QC"+part+"_"+stamp+".zip");
            List<File> pics=new ArrayList<>();for(int i:parts.get(p))pics.add(all.get(i));
            try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zf)))){
                z.setLevel(1);
                String readme="Bob's Watches Rolex "+refDir.getName()+" QC image pack\nPart "+(p+1)+" of "+parts.size()+", images in this part: "+pics.size()+"\n"
                        +"Original image files as served by bobswatches.com (sha256 in manifest.csv); generic face-on suitability filtering only.\n"
                        +"Do not automatically use these images to set Watch Align calibration limits.\n";
                put(z,"README.txt",readme.getBytes("UTF-8"));
                File m=new File(refDir,"manifest.csv");if(m.exists())put(z,m,"manifest.csv");
                for(File f:pics)put(z,f,"images/"+f.getName());
            }
            if(zf.length()>=MAX_ZIP){long mb=zf.length()/1048576;zf.delete();throw new IOException("ZIP part would be "+mb+" MB, not below 30 MB: "+zf.getName());}
            out.add(zf);
        }
        return out;
    }
    static void put(ZipOutputStream z,File f,String n)throws IOException{z.putNextEntry(new ZipEntry(n));try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] b=new byte[65536];int k;while((k=in.read(b))>=0)z.write(b,0,k);}z.closeEntry();}
    static void put(ZipOutputStream z,String n,byte[] b)throws IOException{z.putNextEntry(new ZipEntry(n));z.write(b);z.closeEntry();}
}
