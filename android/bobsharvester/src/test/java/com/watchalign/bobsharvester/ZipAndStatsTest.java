package com.watchalign.bobsharvester;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import org.junit.Test;

public class ZipAndStatsTest {
    @Test public void zipsAreBelow30MbSplitAndReadable()throws Exception{
        File dir=Files.createTempDirectory("ref").toFile(),ref=new File(dir,"124060"),out=new File(dir,"out");ref.mkdirs();out.mkdirs();
        Random rnd=new Random(1);
        for(int i=0;i<9;i++){byte[] b=new byte[7*1024*1024];rnd.nextBytes(b);Files.write(new File(ref,"124060_"+(100000+i)+".jpg").toPath(),b);}   // incompressible, 63 MB
        Files.write(new File(ref,"manifest.csv").toPath(),"reference,sku\n\"124060\",\"100000\"\n".getBytes("UTF-8"));
        List<File> zips=ZipExporter.export(ref,out,"20261008-0000");
        assertTrue("split into numbered parts: "+zips,zips.size()>=3);
        int images=0;
        for(File z:zips){
            assertTrue(z.getName(),z.getName().matches("Rolex_124060_Bobs_QC_part0\\d-of-0\\d_20261008-0000\\.zip"));
            assertTrue(z.length()+" bytes",z.length()<ZipExporter.MAX_ZIP);
            try(ZipFile zf=new ZipFile(z)){assertNotNull(zf.getEntry("README.txt"));assertNotNull(zf.getEntry("manifest.csv"));
                for(Enumeration<? extends ZipEntry> e=zf.entries();e.hasMoreElements();){ZipEntry en=e.nextElement();if(en.getName().startsWith("images/")){images++;try(InputStream in=zf.getInputStream(en)){byte[] b=new byte[8192];long n=0;int k;while((k=in.read(b))>=0)n+=k;assertEquals(7*1024*1024,n);}}}
                String readme=new String(readAll(zf.getInputStream(zf.getEntry("README.txt"))),"UTF-8");assertFalse("real newlines, not \\n",readme.contains("\\n"));}
        }
        assertEquals(9,images);
    }
    static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int k;while((k=in.read(b))>=0)o.write(b,0,k);return o.toByteArray();}

    @Test public void zeroImageRunsAreExplained(){
        HarvestStats s=new HarvestStats();assertTrue(s.explainZero().contains("No Rolex listings"));
        s.listingsDiscovered=61;assertTrue(s.explainZero().contains("No listings match"));
        s.listingsMatched=2;assertTrue(s.explainZero().contains("none of their product pages loaded"));
        s.pagesLoaded=2;assertTrue(s.explainZero().contains("no product photographs"));
        s.imageUrlsFound=2;s.downloadsAttempted=2;s.downloadFailed("HTTP 403");s.downloadFailed("HTTP 403");assertTrue(s.explainZero().contains("every download failed"));
        s.downloadsOk=2;s.reject("not face-on: dial ellipse ratio 0.880 < 0.950 (tilt ~28 deg)");s.reject("dial too small (90 px)");
        String w=s.explainZero();assertTrue(w,w.contains("none passed the face-on suitability check")&&w.contains("not face-on")&&w.contains("dial too small"));
        s.accepted=1;assertNull(s.explainZero());
    }
}
