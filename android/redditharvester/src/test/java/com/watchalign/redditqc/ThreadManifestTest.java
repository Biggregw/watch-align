package com.watchalign.redditqc;

import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class ThreadManifestTest {
    private static final String HEADER =
            "case_id,watch_family,reference,factory,reddit_url,reported_album_url,primary_feature,paraphrased_evidence,provisional_label,focus_marker\n";
    private static final String GMT = "https://www.reddit.com/r/RepTimeQC/comments/1jkb0x8/";

    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static String row(String id,String url,String label){
        return id+",GMT,126710BLNR,ARF,"+url+",,9 rotation,\"Marker is crooked, but minor\","+label+",9\n";
    }

    @Test public void importsGmtNineAndDeduplicatesThreadNotCases()throws Exception {
        String csv="\uFEFF"+HEADER+row("QC-001",GMT,"mild")+
                "QC-002,GMT,126710BLNR,ARF,"+GMT+",,6 rotation,\"6 is also rotated\",clear,9\n";
        ThreadManifest.Selection s=ThreadManifest.parse(bytes(csv));
        assertEquals(2,s.rows.size());
        assertEquals(1,s.threads);
        assertEquals("126710BLNR",s.modelSummary);
        assertEquals("9",s.markerSummary);
        assertEquals("QC-001",s.rows.get(0).id);
        assertEquals("Marker is crooked, but minor",s.rows.get(0).review);
    }

    @Test public void acceptsQuotedMultilineReviewAndCommas()throws Exception {
        String csv=HEADER+"QC-003,GMT,126710BLNR,Clean,"+GMT+
                ",,9 rotation,\"One commenter says ""GL"",\nother says fine\",mild,9\n";
        ThreadManifest.Selection s=ThreadManifest.parse(bytes(csv));
        assertEquals(1,s.threads);
        assertTrue(s.rows.get(0).review.contains("\nother says fine"));
    }

    @Test public void rejectsNonRedditAndDeceptiveHosts() {
        for(String url:new String[]{"https://www.reddit.com.evil.test/r/RepTimeQC/comments/1jkb0x8/",
                "http://www.reddit.com/r/RepTimeQC/comments/1jkb0x8/",
                "https://www.reddit.com/r/RepTimeQC/",
                "https://reddit.example.com/r/RepTimeQC/comments/1jkb0x8/"}) {
            assertNull(ThreadManifest.postId(url));
            try{ThreadManifest.parse(bytes(HEADER+row("QC-004",url,"clean")));fail(url);}
            catch(IOException expected){assertTrue(expected.getMessage().contains("Reddit comments URL"));}
        }
    }

    @Test public void rejectsMissingHeadersAndDuplicateIds()throws Exception {
        try{ThreadManifest.parse(bytes("name,url\nx,https://www.reddit.com\n"));fail();}
        catch(IOException expected){assertTrue(expected.getMessage().contains("headers"));}
        try{ThreadManifest.parse(bytes(HEADER+row("QC-001",GMT,"clean")+row("QC-001",GMT,"clear")));fail();}
        catch(IOException expected){assertTrue(expected.getMessage().contains("duplicate case_id"));}
    }

    @Test public void validatesOriginalBundledFortyNineThreadSeed()throws Exception {
        byte[] original=Files.readAllBytes(Paths.get("src/main/assets/cases.csv"));
        ThreadManifest.Selection s=ThreadManifest.parse(original);
        assertEquals(51,s.rows.size());
        assertEquals(49,s.threads);
    }
}
