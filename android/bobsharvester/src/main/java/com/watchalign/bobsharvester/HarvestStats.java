package com.watchalign.bobsharvester;

import java.util.*;

/** Run counters and rejection reasons, and the plain-English explanation of a run (Android-free, unit-tested). */
public final class HarvestStats {
    public int catalogPages, catalogLinks, listingsDiscovered, listingsMatched, pagesLoaded, pageFailures,
            listingsWithImageUrls, imageUrlsFound, imageUrlsDropped, downloadsAttempted, downloadsOk, downloadsFailed,
            accepted, rejected, duplicates, zipsCreated, zipFailures;
    public final Map<String,Integer> rejectReasons=new TreeMap<>(), downloadFailures=new TreeMap<>(), dropReasons=new TreeMap<>();

    public synchronized void reject(String reason){rejected++;rejectReasons.merge(bucket(reason),1,Integer::sum);}
    public synchronized void downloadFailed(String reason){downloadsFailed++;downloadFailures.merge(reason,1,Integer::sum);}
    public synchronized void dropped(String reason){imageUrlsDropped++;dropReasons.merge(reason.replaceAll("SKU [0-9]+","SKU …"),1,Integer::sum);}
    static String bucket(String r){int i=r.indexOf(" (");String b=i>0?r.substring(0,i):r;i=b.indexOf(": dial ellipse");return i>0?b.substring(0,i):b;}

    public synchronized String report(){
        StringBuilder s=new StringBuilder();
        s.append("Catalogue pages read: ").append(catalogPages).append(" (").append(catalogLinks).append(" product links)\n");
        s.append("Listings discovered: ").append(listingsDiscovered).append(", matching the filter: ").append(listingsMatched).append('\n');
        s.append("Product pages loaded: ").append(pagesLoaded).append(pageFailures>0?", failed to load: "+pageFailures:"").append('\n');
        s.append("Product image URLs found: ").append(imageUrlsFound).append(" (in ").append(listingsWithImageUrls).append(" listings); other images on the pages ignored: ").append(imageUrlsDropped).append('\n');
        s.append("Downloads attempted: ").append(downloadsAttempted).append(", succeeded: ").append(downloadsOk).append(", failed: ").append(downloadsFailed).append('\n');
        if(!downloadFailures.isEmpty())s.append("  download failures: ").append(downloadFailures).append('\n');
        s.append("Accepted (face-on): ").append(accepted).append(", rejected: ").append(rejected).append(duplicates>0?", duplicates skipped: "+duplicates:"").append('\n');
        if(!rejectReasons.isEmpty())s.append("  rejection reasons: ").append(rejectReasons).append('\n');
        return s.toString();
    }

    /** Why a finished run kept no images (null when it kept some). */
    public synchronized String explainZero(){
        if(accepted>0)return null;
        if(listingsDiscovered==0)return "No Rolex listings were found in the catalogue (the catalogue page did not load or its layout changed).";
        if(listingsMatched==0)return "No listings match the keyword filter.";
        if(pagesLoaded==0)return "Matching listings were found but none of their product pages loaded.";
        if(imageUrlsFound==0)return "Product pages loaded but no product photographs were found on them.";
        if(downloadsOk==0)return "Product photographs were found but every download failed"+(downloadFailures.isEmpty()?".":": "+downloadFailures);
        if(duplicates>0&&rejected==0)return "Every downloaded photograph was already saved in an earlier run (duplicates).";
        return "Photographs were downloaded but none passed the face-on suitability check: "+rejectReasons;
    }
}
