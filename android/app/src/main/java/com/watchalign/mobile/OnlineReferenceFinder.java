package com.watchalign.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OnlineReferenceFinder {
    public static final class Result {
        public final Bitmap bitmap;
        public final String source;
        public final boolean fromCache;
        Result(Bitmap bitmap, String source, boolean fromCache){this.bitmap=bitmap;this.source=source;this.fromCache=fromCache;}
    }

    public static Result find(Context context, Bitmap watch, String modelRef) throws Exception {
        File cache = new File(context.getFilesDir(), "reference-cache/" + modelRef + ".jpg");
        if (cache.isFile()) {
            try (InputStream in = new FileInputStream(cache)) {
                Bitmap b = BitmapFactory.decodeStream(in);
                if (b != null && WatchAlignCore.referenceScore(watch, b) < 9.0) return new Result(b, "Cached official reference", true);
            } catch (Exception ignored) {}
        }

        String page = pageFor(modelRef);
        String html = getText(page);
        List<String> urls = extractImageUrls(html, modelRef);
        Bitmap best = null;
        String bestUrl = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int checked = 0;
        for (String u : urls) {
            if (checked >= 18) break;
            checked++;
            try {
                Bitmap b = getBitmap(u);
                if (b == null || Math.min(b.getWidth(), b.getHeight()) < 450) continue;
                double score = WatchAlignCore.referenceScore(watch, b);
                if (Double.isFinite(score) && score < bestScore) { bestScore = score; best = b; bestUrl = u; }
            } catch (Exception ignored) {}
        }
        if (best == null) throw new IllegalStateException("No suitable official reference image could be downloaded for " + modelRef + ".");

        cache.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(cache)) { best.compress(Bitmap.CompressFormat.JPEG, 94, out); }
        return new Result(best, bestUrl != null ? bestUrl : page, false);
    }

    private static String pageFor(String modelRef) {
        if ("124060".equals(modelRef)) return "https://www.rolex.com/watches/submariner/m124060-0001";
        return "https://www.rolex.com/watches/gmt-master-ii/m126710blnr-0002";
    }

    private static String getText(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally { c.disconnect(); }
    }

    private static Bitmap getBitmap(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            return BitmapFactory.decodeStream(in);
        } finally { c.disconnect(); }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(18000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
        c.setRequestProperty("Accept", "text/html,image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "en-GB,en;q=0.9");
        return c;
    }

    static List<String> extractImageUrls(String html, String modelRef) {
        String unescaped = html.replace("\\u002F", "/").replace("\\/", "/").replace("&amp;", "&");
        Set<String> found = new LinkedHashSet<>();
        Pattern p = Pattern.compile("https://[^\\\"'<>\\s]+?(?:\\.jpg|\\.jpeg|\\.png|\\.webp)(?:\\?[^\\\"'<>\\s]*)?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(unescaped);
        while (m.find()) {
            String u = m.group();
            String l = u.toLowerCase();
            if (l.contains(modelRef.toLowerCase()) || l.contains("rolex") || l.contains("assets.rolex")) found.add(u);
        }
        Pattern og = Pattern.compile("property=[\\\"']og:image[\\\"'][^>]*content=[\\\"']([^\\\"']+)", Pattern.CASE_INSENSITIVE);
        Matcher om = og.matcher(unescaped); while (om.find()) found.add(om.group(1));
        return new ArrayList<>(found);
    }

    private OnlineReferenceFinder() {}
}
