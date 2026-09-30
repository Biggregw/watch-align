package com.watchalign.mobile;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** alpha66: the collected test set is saved, reloaded and exported in the corpus manifest format. */
public class TestSetStoreTest {
    @Rule public TemporaryFolder tmp=new TemporaryFolder();

    private TestSetStore.Entry entry(TestSetStore s,String file,String cls,String notes)throws Exception{
        try(FileOutputStream o=new FileOutputStream(new File(s.images,file))){o.write(new byte[]{(byte)0xFF,(byte)0xD8,1,2,3});}
        TestSetStore.Entry e=new TestSetStore.Entry();e.file=file;e.cls=cls;e.watchId=s.nextWatchId();e.model="126710BLRO";
        e.source="https://example.com/a,b";e.notes=notes;e.dialFound="yes";e.twelveFound="yes";e.pose="GOOD";e.suitable="yes";
        s.entries.add(e);return e;
    }

    @Test public void savesAndReloadsWithCommasAndQuotes()throws Exception{
        TestSetStore s=new TestSetStore(tmp.getRoot());
        entry(s,"a.jpg","gen","said \"mint\", box and papers");
        entry(s,"b.jpg","rep","");
        s.save();
        TestSetStore t=new TestSetStore(tmp.getRoot());t.load();
        assertEquals(2,t.entries.size());
        assertEquals("said \"mint\", box and papers",t.entries.get(0).notes);
        assertEquals("https://example.com/a,b",t.entries.get(0).source);
        assertEquals("w001",t.entries.get(0).watchId);assertEquals("w002",t.entries.get(1).watchId);
        assertEquals("w003",t.nextWatchId());
        assertEquals(1,t.count("gen"));assertEquals(1,t.count("rep"));
        assertTrue(t.entries.get(0).check().startsWith("good"));
    }

    @Test public void missingImageIsDroppedOnLoad()throws Exception{
        TestSetStore s=new TestSetStore(tmp.getRoot());
        entry(s,"a.jpg","gen","");entry(s,"b.jpg","rep","");s.save();
        assertTrue(new File(s.images,"b.jpg").delete());
        TestSetStore t=new TestSetStore(tmp.getRoot());t.load();
        assertEquals(1,t.entries.size());
    }

    @Test public void exportZipHasCorpusManifestAndImages()throws Exception{
        TestSetStore s=new TestSetStore(tmp.getRoot());
        entry(s,"a.jpg","gen","");entry(s,"b.jpg","rep","");
        ByteArrayOutputStream out=new ByteArrayOutputStream();s.exportZip(s.entries,out);
        Map<String,byte[]> z=new HashMap<>();
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))){
            ZipEntry e;while((e=in.getNextEntry())!=null){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[1024];int n;while((n=in.read(buf))>0)b.write(buf,0,n);z.put(e.getName(),b.toByteArray());}
        }
        assertTrue(z.containsKey("images/a.jpg"));assertTrue(z.containsKey("images/b.jpg"));
        String m=new String(z.get("manifest.csv"),StandardCharsets.UTF_8);
        assertTrue(m,m.startsWith("local_path,class_label,physical_watch_id,"));
        assertTrue(m,m.contains("\nimages/a.jpg,gen,w001,126710BLRO,"));
        assertTrue(m,m.contains("\nimages/b.jpg,rep,w002,"));
    }

    @Test public void githubRequestBody(){
        String b=GitHubUploader.body("Add \"x\"",new byte[]{1,2,3},"testset-inbox");
        assertEquals("{\"message\":\"Add \\\"x\\\"\",\"branch\":\"testset-inbox\",\"content\":\"AQID\"}",b);
        GitHubUploader u=new GitHubUploader("Biggregw/watch-align","","/testset/","t");
        assertEquals("testset-inbox",u.branch);assertEquals("testset",u.folder);
        assertEquals("testset/images/a%20b.jpg",GitHubUploader.encodePath("testset/images/a b.jpg"));
        assertEquals("main",GitHubUploader.field("{\"name\":\"x\",\"default_branch\":\"main\"}","default_branch"));
        try{new GitHubUploader("no-slash","","","t");fail();}catch(IllegalArgumentException expected){}
    }
}
