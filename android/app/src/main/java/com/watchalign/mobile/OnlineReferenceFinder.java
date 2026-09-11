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
        public final Bitmap bitmap; public final String source; public final boolean fromCache;
        Result(Bitmap bitmap,String source,boolean fromCache){this.bitmap=bitmap;this.source=source;this.fromCache=fromCache;}
    }
    public static final class PoolResult {
        public final List<Bitmap> bitmaps; public final List<String> sources; public final boolean fromCacheOnly;
        PoolResult(List<Bitmap>b,List<String>s,boolean c){bitmaps=b;sources=s;fromCacheOnly=c;}
    }

    private static final Pattern ROLEX_REF=Pattern.compile("m?(\\d{6}[a-z]{0,6})(?:-\\d{4})?",Pattern.CASE_INSENSITIVE);

    public static Result find(Context context,Bitmap watch,String modelRef)throws Exception{
        PoolResult pool=findPool(context,watch,modelRef,1);
        if(pool.bitmaps.isEmpty())throw new IllegalStateException("No usable exact-model official reference was found for "+modelRef+".");
        return new Result(pool.bitmaps.get(0),pool.sources.get(0),pool.fromCacheOnly);
    }

    public static PoolResult findPool(Context context,Bitmap watch,String modelRef,int maxRefs)throws Exception{
        ModelCatalog.Profile profile=ModelCatalog.require(modelRef);
        if(!profile.supportsAutoReference())throw new IllegalArgumentException("Automatic official reference discovery is not configured for "+profile.label+".");
        int limit=Math.max(1,Math.min(12,maxRefs));
        File dir=new File(context.getFilesDir(),"reference-cache/"+modelRef+"-pool-v1");dir.mkdirs();
        List<Bitmap> cached=new ArrayList<>();List<String> cachedSources=new ArrayList<>();
        File[] files=dir.listFiles();
        if(files!=null)for(File f:files){if(!f.getName().endsWith(".jpg"))continue;try(InputStream in=new FileInputStream(f)){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null&&Double.isFinite(WatchAlignCoreV7.referenceScore(watch,b))){cached.add(b);cachedSources.add("Cached exact-model official reference "+f.getName());}}catch(Exception ignored){}}

        List<Bitmap> accepted=new ArrayList<>();List<String> sources=new ArrayList<>();
        try{
            String html=getText(profile.officialPage);List<String> urls=extractImageUrls(html,modelRef);int checked=0;
            for(String u:urls){if(checked++>=40||accepted.size()>=limit)break;try{
                Bitmap b=getBitmap(u);if(b==null||Math.min(b.getWidth(),b.getHeight())<450)continue;
                double score=WatchAlignCoreV7.referenceScore(watch,b);if(!Double.isFinite(score)||score>=3.2)continue;
                if(isNearDuplicate(b,accepted))continue;
                accepted.add(b);sources.add(u);
            }catch(Exception ignored){}}
            for(int i=0;i<cached.size()&&accepted.size()<limit;i++){if(!isNearDuplicate(cached.get(i),accepted)){accepted.add(cached.get(i));sources.add(cachedSources.get(i));}}
            if(accepted.isEmpty())throw new IllegalStateException("Official page did not expose a geometrically usable exact-model image for "+modelRef+".");
            for(int i=0;i<accepted.size();i++){File outFile=new File(dir,String.format(java.util.Locale.US,"ref_%02d.jpg",i));try(FileOutputStream out=new FileOutputStream(outFile)){accepted.get(i).compress(Bitmap.CompressFormat.JPEG,94,out);}}
            return new PoolResult(accepted,sources,false);
        }catch(Exception onlineFailure){
            if(!cached.isEmpty()){while(cached.size()>limit)cached.remove(cached.size()-1);while(cachedSources.size()>limit)cachedSources.remove(cachedSources.size()-1);return new PoolResult(cached,cachedSources,true);}throw onlineFailure;
        }
    }

    private static boolean isNearDuplicate(Bitmap candidate,List<Bitmap> existing){
        for(Bitmap b:existing){if(Math.abs(candidate.getWidth()-b.getWidth())<4&&Math.abs(candidate.getHeight()-b.getHeight())<4){double s=WatchAlignCoreV7.referenceScore(candidate,b);if(Double.isFinite(s)&&s<0.12)return true;}}
        return false;
    }

    private static String getText(String url)throws Exception{HttpURLConnection c=open(url);try(InputStream in=new BufferedInputStream(c.getInputStream())){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[]buf=new byte[16384];int n;while((n=in.read(buf))>=0)out.write(buf,0,n);return out.toString(StandardCharsets.UTF_8.name());}finally{c.disconnect();}}
    private static Bitmap getBitmap(String url)throws Exception{HttpURLConnection c=open(url);try(InputStream in=new BufferedInputStream(c.getInputStream())){return BitmapFactory.decodeStream(in);}finally{c.disconnect();}}
    private static HttpURLConnection open(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");c.setRequestProperty("Accept","text/html,image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");c.setRequestProperty("Accept-Language","en-GB,en;q=0.9");return c;}

    static List<String> extractImageUrls(String html,String modelRef){
        String unescaped=html.replace("\\u002F","/").replace("\\/","/").replace("&amp;","&");String token=modelRef.toLowerCase();Set<String>found=new LinkedHashSet<>();
        Pattern p=Pattern.compile("https://[^\\\"'<>\\s]+?(?:\\.jpg|\\.jpeg|\\.png|\\.webp)(?:\\?[^\\\"'<>\\s]*)?",Pattern.CASE_INSENSITIVE);Matcher m=p.matcher(unescaped);
        while(m.find()){String u=m.group(),l=u.toLowerCase();int lo=Math.max(0,m.start()-320),hi=Math.min(unescaped.length(),m.end()+320);String context=unescaped.substring(lo,hi).toLowerCase();if(l.contains(token)||l.contains("m"+token)||contextIdentifiesOnlyTarget(context,token))found.add(u);}
        Pattern og=Pattern.compile("property=[\\\"']og:image[\\\"'][^>]*content=[\\\"']([^\\\"']+)",Pattern.CASE_INSENSITIVE);Matcher om=og.matcher(unescaped);
        while(om.find()){String u=om.group(1),l=u.toLowerCase();int lo=Math.max(0,om.start()-320),hi=Math.min(unescaped.length(),om.end()+320);String context=unescaped.substring(lo,hi).toLowerCase();if(l.contains(token)||l.contains("m"+token)||contextIdentifiesOnlyTarget(context,token))found.add(u);}
        return new ArrayList<>(found);
    }
    static boolean contextIdentifiesOnlyTarget(String context,String token){Matcher refs=ROLEX_REF.matcher(context.toLowerCase());boolean targetSeen=false;while(refs.find()){String ref=refs.group(1).toLowerCase();if(ref.equals(token))targetSeen=true;else return false;}return targetSeen;}
    private OnlineReferenceFinder(){}
}
