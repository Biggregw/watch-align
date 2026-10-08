package com.watchalign.bobsharvester;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** Regression cases from the live Bob's pages probed on 2026-10-08 (CI run 37850454728). */
public class HarvestLogicTest {
    static List<HarvestLogic.Candidate> c(String... su){List<HarvestLogic.Candidate> l=new ArrayList<>();for(int i=0;i<su.length;i+=2)l.add(new HarvestLogic.Candidate(su[i],su[i+1]));return l;}

    @Test public void listing194818KeepsItsOwnOriginalOnlyNotTheResizedCopyOrAnotherListingsThumbnail(){
        // what v1.3 extracted on the 124060LN listing (SKU 194818): its JSON-LD original, a 480 px CDN copy of it, and a
        // related-listing thumbnail of SKU 194255 (it carries "124060" in its name)
        HarvestLogic.Selection s=HarvestLogic.selectProductImages("194818","https://www.bobswatches.com/pre-owned-rolex-submariner-ceramic-bezel-ref-124060ln-black-dial.html",c(
                "https://www.bobswatches.com/images/zUsed-Rolex-Submariner-124060-SKU194818PL.jpg","jsonld",
                "https://www.bobswatches.com/cdn-cgi/image/width=480,quality=85,sharpen=0.5,format=avif/images/zUsed-Rolex-Submariner-124060-SKU194818PL.jpg","img",
                "https://www.bobswatches.com/images/sUsed-Rolex-Submariner-124060-SKU194255.jpg","img",
                "https://www.bobswatches.com/cdn-cgi/image/width=480,fit=scale-down,quality=75,format=auto/pdp/images/bobs-qa.jpg","img",
                "https://www.bobswatches.com/cdn-cgi/image/width=480,fit=scale-down,quality=75,format=auto/images/brands/1-rolex-sect.jpg","img"));
        assertEquals(Collections.singletonList("https://www.bobswatches.com/images/zUsed-Rolex-Submariner-124060-SKU194818PL.jpg"),s.keep);
        assertTrue(s.dropped.values().stream().anyMatch(r->r.contains("another listing (SKU 194255)")));
    }

    @Test public void daytona191511KeepsAllFourOriginals(){
        HarvestLogic.Selection s=HarvestLogic.selectProductImages("191511","https://www.bobswatches.com/used-rolex-cosmograph-daytona-ref-126503-tachymeter-bezel.html",c(
                "https://www.bobswatches.com/images/zUsed-Rolex-Daytona-126503-SKU191511.jpg","jsonld",
                "https://www.bobswatches.com/images/zUsed-Rolex-Daytona-126503-Champagne-Dial-SKU191511.jpg","jsonld",
                "https://www.bobswatches.com/images/zUsed-Rolex-Daytona-126503-Two-Tone-SKU191511.jpg","jsonld",
                "https://www.bobswatches.com/images/zy-191511-06-20-2024.jpg","jsonld",
                "https://www.bobswatches.com/cdn-cgi/image/width=480,quality=85,sharpen=0.5,format=avif/images/zy-191511-06-20-2024.jpg","img",
                "https://www.bobswatches.com/images/sUsed-Rolex-Daytona-126503-SKU192328.jpg","img"));
        assertEquals(4,s.keep.size());
        assertTrue(s.keep.contains("https://www.bobswatches.com/images/zy-191511-06-20-2024.jpg"));
        assertFalse(s.keep.stream().anyMatch(u->u.contains("192328")));
    }

    @Test public void noSkuFallsBackToThePagesProductData(){
        HarvestLogic.Selection s=HarvestLogic.selectProductImages("","https://www.bobswatches.com/x.html",c(
                "/images/zUsed-Rolex-GMT-126710-Pepsi.jpg","jsonld","/images/sUsed-Rolex-Other-SKU111111.jpg","img"));
        assertEquals(Collections.singletonList("https://www.bobswatches.com/images/zUsed-Rolex-GMT-126710-Pepsi.jpg"),s.keep);
    }

    @Test public void referencesGroupOneModelTogether(){
        assertEquals("124060",HarvestLogic.ref("Pre-Owned Rolex Submariner Ceramic Bezel Ref 124060LN Black Dial"));
        assertEquals("124060",HarvestLogic.ref("Rolex Submariner 124060 Men's Watch Stainless Steel Black Dial"));
        assertEquals("126610LV",HarvestLogic.ref("Rolex Submariner Date 126610LV Starbucks"));   // a real variant stays separate
        assertEquals("126710BLRO",HarvestLogic.ref("Rolex GMT-Master II 126710 Pepsi"));
        assertEquals("126503",HarvestLogic.ref("Used Rolex Cosmograph Daytona Ref 126503 Tachymeter Bezel"));
        assertEquals("",HarvestLogic.ref("Rolex Oyster 2024 box"));
    }

    @Test public void keywordFilter(){
        String t="Rolex Submariner 124060 Men's Watch Stainless Steel Black Dial",u="https://www.bobswatches.com/pre-owned-rolex-submariner-ceramic-bezel-ref-124060ln-black-dial.html";
        assertTrue(HarvestLogic.matchesFilter("submariner 124060",t,u));
        assertTrue(HarvestLogic.matchesFilter("124060",t,u));
        assertTrue(HarvestLogic.matchesFilter("Submariner",t,u));
        assertFalse(HarvestLogic.matchesFilter("Daytona",t,u));
        assertTrue(HarvestLogic.matchesFilter("GMT-Master II","Rolex GMT-Master II 126710BLRO Pepsi","https://www.bobswatches.com/rolex-gmt-master-ii-126710.html"));
        assertTrue(HarvestLogic.matchesFilter("","anything",""));
    }

    @Test public void catalogueHelpers(){
        assertEquals(Integer.valueOf(807),HarvestLogic.resultTotal("Showing 1-60 of 807 results"));
        assertNull(HarvestLogic.resultTotal("no count"));
        assertTrue(HarvestLogic.productUrl("https://www.bobswatches.com/pre-owned-rolex-submariner-ceramic-bezel-ref-124060ln-black-dial.html"));
        assertFalse(HarvestLogic.productUrl("https://www.bobswatches.com/rolex-blog/x.html"));
        assertEquals("https://www.bobswatches.com/a.html",HarvestLogic.canonicalProductUrl("https://WWW.BobsWatches.com/a.html?utm=1#x"));
        assertEquals("194818",HarvestLogic.skuFrom("Stock SKU: 194818 Ref 124060"));
    }

    @Test public void bobsWristShotsAreRecognisedByName(){
        // owner's 1.4 phone run (2026-10-08): these three passed the dial-roundness test but Watch Align cannot fit them
        assertTrue(HarvestLogic.wristShot("https://www.bobswatches.com/images/zUsed-Rolex-Submariner-124060-SKU189182w.jpg"));
        assertTrue(HarvestLogic.wristShot("https://www.bobswatches.com/images/zUsed-Rolex-Daytona-126515-SKU192468w.jpg"));
        assertFalse(HarvestLogic.wristShot("https://www.bobswatches.com/images/zUsed-Rolex-Submariner-124060-SKU189182.jpg"));
        assertFalse(HarvestLogic.wristShot("https://www.bobswatches.com/images/zUsed-Rolex-Submariner-124060-SKU194818PL.jpg"));
    }

    @Test public void csvIsRfc4180(){
        assertEquals("\"a \"\"b\"\", c\"",HarvestLogic.csv("a \"b\", c"));   // v1.3 wrote \"a\" (backslash-quotes)
    }

    @Test public void zipPartsStayBelowTheLimit(){
        long M=1024*1024;long[] s={10*M,10*M,10*M,26*M,1*M,29*M};
        List<List<Integer>> p=HarvestLogic.planParts(s,27*M);
        for(List<Integer> part:p){long t=0;for(int i:part)t+=s[i];assertTrue(part.size()==1||t<=27*M);}
        assertEquals(Arrays.asList(0,1),p.get(0));
    }
}
