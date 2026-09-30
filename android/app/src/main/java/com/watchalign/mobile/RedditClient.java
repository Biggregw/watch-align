package com.watchalign.mobile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searches a subreddit through Reddit's official API (alpha67) and lists the photos in each post.
 * Uses app-only OAuth for an "installed app": the user creates the app once at
 * reddit.com/prefs/apps and enters its client id; no Reddit password is involved.
 *
 * Photos are taken from Reddit-hosted images and galleries, direct Imgur links, and (only when an
 * Imgur Client-ID is set) Imgur albums. Everything network-free is static so it is unit-tested.
 */
final class RedditClient {
    static final String AUTH="https://www.reddit.com/api/v1/access_token",API="https://oauth.reddit.com";
    static final int MAX_IMAGES_PER_POST=12;

    static final class Post {
        String id="",title="",permalink="",author="";double created;
        final List<String> images=new ArrayList<>();
        /** Imgur album or gallery ids still to be expanded. */
        final List<String> imgurAlbums=new ArrayList<>();
    }
    static final class Page {final List<Post> posts=new ArrayList<>();String after;}

    final String clientId,userAgent,deviceId,imgurClientId;
    private String token;private long tokenUntil;

    RedditClient(String clientId,String redditUser,String deviceId,String imgurClientId){
        this.clientId=clientId.trim();this.deviceId=deviceId;this.imgurClientId=imgurClientId==null?"":imgurClientId.trim();
        String u=redditUser==null?"":redditUser.trim().replaceFirst("^/?u/","");
        userAgent="android:com.watchalign.mobile:v"+WatchAlignCoreV13.CORE_VERSION+(u.isEmpty()?"":" (by /u/"+u+")");
        if(this.clientId.isEmpty())throw new IllegalArgumentException("Reddit client id is not set");
    }

    // ---- network ----

    private void auth()throws IOException{
        if(token!=null&&System.currentTimeMillis()<tokenUntil)return;
        HttpURLConnection c=open(AUTH);
        try{
            c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Authorization","Basic "+Base64.getEncoder().encodeToString((clientId+":").getBytes(StandardCharsets.UTF_8)));
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            String body="grant_type="+enc("https://oauth.reddit.com/grants/installed_client")+"&device_id="+enc(deviceId);
            try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}
            int code=c.getResponseCode();String text=readBody(c,code);
            if(code!=200)throw new IOException("Reddit sign-in failed ("+code+"). Check the client id and that the app type is \"installed app\".");
            Object j=MiniJson.parse(text);String t=MiniJson.str(j,"access_token");
            if(t==null)throw new IOException("Reddit sign-in failed: "+MiniJson.str(j,"error"));
            Object exp=MiniJson.at(j,"expires_in");
            token=t;tokenUntil=System.currentTimeMillis()+(exp instanceof Double?(long)(((Double)exp)*1000):3_600_000L)-60_000L;
        }finally{c.disconnect();}
    }

    /** One page of search results in a subreddit, newest first. */
    Page search(String subreddit,String query,String after)throws IOException{
        String url=API+"/r/"+enc(subreddit)+"/search?q="+enc(query)+"&restrict_sr=1&sort=new&limit=50&raw_json=1&type=link"
                +(after==null||after.isEmpty()?"":"&after="+enc(after));
        return parseListing(get(url,true));
    }

    /** Image links of an Imgur album or gallery; empty when no Imgur Client-ID is set. */
    List<String> imgurAlbum(String id)throws IOException{
        if(imgurClientId.isEmpty())return new ArrayList<>();
        HttpURLConnection c=open("https://api.imgur.com/3/album/"+enc(id)+"/images");
        try{
            c.setRequestProperty("Authorization","Client-ID "+imgurClientId);
            int code=c.getResponseCode();String text=readBody(c,code);
            if(code!=200)return new ArrayList<>();
            return parseImgurAlbum(text);
        }finally{c.disconnect();}
    }

    /** Downloads one image (at most 25 MB). */
    byte[] download(String url)throws IOException{
        HttpURLConnection c=open(url);
        try{
            int code=c.getResponseCode();
            if(code!=200)throw new IOException("HTTP "+code);
            String type=c.getContentType();
            if(type!=null&&!type.startsWith("image/"))throw new IOException("not an image ("+type+")");
            try(InputStream in=c.getInputStream()){
                ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))>0){b.write(buf,0,n);if(b.size()>25_000_000)throw new IOException("image too large");}
                return b.toByteArray();
            }
        }finally{c.disconnect();}
    }

    private String get(String url,boolean retryAuth)throws IOException{
        auth();
        HttpURLConnection c=open(url);
        try{
            c.setRequestProperty("Authorization","bearer "+token);
            int code=c.getResponseCode();String text=readBody(c,code);
            if(code==401&&retryAuth){token=null;return get(url,false);}
            if(code==429)throw new IOException("Reddit says too many requests; wait a minute and try again.");
            if(code!=200)throw new IOException("Reddit search failed ("+code+")");
            return text;
        }finally{c.disconnect();}
    }

    private HttpURLConnection open(String url)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(20000);c.setReadTimeout(60000);c.setRequestProperty("User-Agent",userAgent);
        return c;
    }
    private static String readBody(HttpURLConnection c,int code)throws IOException{
        InputStream in=code<400?c.getInputStream():c.getErrorStream();
        return in==null?"":GitHubUploader.read(in);
    }
    static String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}}

    // ---- parsing (no network) ----

    static Page parseListing(String json){
        Object root=MiniJson.parse(json);Page p=new Page();
        p.after=MiniJson.str(root,"data","after");
        for(Object child:MiniJson.list(root,"data","children")){
            if(!"t3".equals(MiniJson.str(child,"kind")))continue;
            p.posts.add(parsePost(MiniJson.map(child,"data")));
        }
        return p;
    }

    static Post parsePost(Map<String,Object> d){
        Post p=new Post();
        p.id=nz(MiniJson.str(d,"id"));p.title=nz(MiniJson.str(d,"title"));p.author=nz(MiniJson.str(d,"author"));
        String perm=MiniJson.str(d,"permalink");p.permalink=perm==null?"":"https://www.reddit.com"+perm;
        Object cr=MiniJson.at(d,"created_utc");p.created=cr instanceof Double?(Double)cr:0;
        Set<String> imgs=new LinkedHashSet<>(),albums=new LinkedHashSet<>();
        collect(d,imgs,albums);
        // A crosspost carries its photos in the original post.
        if(imgs.isEmpty()&&albums.isEmpty())for(Object o:MiniJson.list(d,"crosspost_parent_list"))collect(MiniJson.map(o),imgs,albums);
        for(String u:imgs)if(p.images.size()<MAX_IMAGES_PER_POST)p.images.add(u);
        p.imgurAlbums.addAll(albums);
        return p;
    }

    private static void collect(Map<String,Object> d,Set<String> imgs,Set<String> albums){
        if(Boolean.TRUE.equals(MiniJson.at(d,"is_gallery"))){
            Map<String,Object> meta=MiniJson.map(d,"media_metadata");
            for(Object it:MiniJson.list(d,"gallery_data","items")){
                String id=MiniJson.str(it,"media_id");if(id==null)continue;
                Object m=meta.get(id);
                if(!"valid".equals(MiniJson.str(m,"status"))||!"Image".equals(MiniJson.str(m,"e")))continue;
                String u=MiniJson.str(m,"s","u");if(u!=null)imgs.add(clean(u));
            }
        }
        String url=MiniJson.str(d,"url_overridden_by_dest");if(url==null)url=MiniJson.str(d,"url");
        if(url!=null)addLink(clean(url),imgs,albums);
        String self=MiniJson.str(d,"selftext");
        if(self!=null){Matcher m=LINK.matcher(self);while(m.find())addLink(clean(m.group()),imgs,albums);}
        // A link post to some other image host: Reddit's own preview copy of the image.
        if(imgs.isEmpty()&&albums.isEmpty()&&"image".equals(MiniJson.str(d,"post_hint"))){
            String u=MiniJson.str(d,"preview","images",0,"source","url");if(u!=null)imgs.add(clean(u));
        }
    }

    static final Pattern LINK=Pattern.compile("https?://[^\\s)\\]\"'<>]+");
    private static final Pattern IMGUR=Pattern.compile("https?://(?:i\\.|m\\.|www\\.)?imgur\\.com/(a/|gallery/|t/[^/]+/)?([^/?#.\\s]+)(\\.[A-Za-z]+)?.*");
    private static final Pattern DIRECT=Pattern.compile("https?://[^?#]+\\.(?:jpe?g|png|webp)(?:[?#].*)?",Pattern.CASE_INSENSITIVE);

    /** Sorts one link into a direct image, an Imgur album, or nothing (videos, gifs, other pages). */
    static void addLink(String url,Set<String> imgs,Set<String> albums){
        Matcher m=IMGUR.matcher(url);
        if(m.matches()){
            String kind=m.group(1),id=m.group(2),ext=m.group(3);
            if(kind!=null){
                // Newer album links end in "-<id>" after a title slug.
                int dash=id.lastIndexOf('-');albums.add(dash>=0?id.substring(dash+1):id);
                return;
            }
            if(ext!=null&&!ext.matches("(?i)\\.(jpe?g|png|webp)"))return;   // .gifv, .mp4, .gif
            if(id.length()>=5&&id.matches("[A-Za-z0-9]+"))imgs.add("https://i.imgur.com/"+id+(ext==null?".jpg":ext));
            return;
        }
        if(DIRECT.matcher(url).matches())imgs.add(url);
    }

    static List<String> parseImgurAlbum(String json){
        List<String> out=new ArrayList<>();
        for(Object o:MiniJson.list(MiniJson.parse(json),"data")){
            String link=MiniJson.str(o,"link"),type=MiniJson.str(o,"type");
            if(link!=null&&(type==null||type.startsWith("image/"))&&!"image/gif".equals(type))out.add(link);
            if(out.size()>=MAX_IMAGES_PER_POST)break;
        }
        return out;
    }

    static String clean(String u){return u.replace("&amp;","&");}
    private static String nz(String s){return s==null?"":s;}

    /** File extension for a downloaded image URL. */
    static String extension(String url){
        String p=url.toLowerCase(Locale.US);int q=p.indexOf('?');if(q>=0)p=p.substring(0,q);
        return p.endsWith(".png")?".png":p.endsWith(".webp")?".webp":".jpg";
    }
}
