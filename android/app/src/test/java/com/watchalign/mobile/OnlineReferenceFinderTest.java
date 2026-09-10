package com.watchalign.mobile;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class OnlineReferenceFinderTest {
    @Test public void acceptsExactModelAssetUrl() {
        String html = "<img src=\"https://assets.rolex.com/foo/m126710blnr-0002-watch.webp\">";
        List<String> urls = OnlineReferenceFinder.extractImageUrls(html, "126710BLNR");
        assertEquals(1, urls.size());
        assertTrue(urls.get(0).contains("126710blnr"));
    }

    @Test public void rejectsGenericOrWrongModelAssets() {
        String html = "<img src=\"https://assets.rolex.com/daytona/m126500ln-0001.webp\">"
                + "<img src=\"https://assets.rolex.com/generic/homepage.webp\">";
        List<String> urls = OnlineReferenceFinder.extractImageUrls(html, "126710BLNR");
        assertTrue(urls.isEmpty());
    }

    @Test public void acceptsAssetWhenImmediateMetadataNamesExactModel() {
        String html = "{\"reference\":\"126710BLNR\",\"image\":\"https://assets.rolex.com/media/gmt-product.webp\"}";
        List<String> urls = OnlineReferenceFinder.extractImageUrls(html, "126710BLNR");
        assertEquals(1, urls.size());
    }

    @Test public void nearbyDifferentReferenceDoesNotQualifyWithoutExactToken() {
        String html = "{\"reference\":\"126500LN\",\"image\":\"https://assets.rolex.com/media/daytona.webp\"}";
        List<String> urls = OnlineReferenceFinder.extractImageUrls(html, "126710BLNR");
        assertTrue(urls.isEmpty());
    }
}
