package com.watchalign.bobsharvester;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure (Android-free) harvester logic, unit-tested on the desktop: reference parsing and grouping, keyword matching,
 * product URL rules, selection of a listing's own product photographs, CSV quoting and ZIP part planning.
 */
public final class HarvestLogic {
    private HarvestLogic(){}

    static final Pattern REF_EX=Pattern.compile("\\bREF(?:ERENCE)?\\.?\\s*([0-9]{4,6}[A-Z]{0,5})\\b",Pattern.CASE_INSENSITIVE);
    static final Pattern REF_ANY=Pattern.compile("(?<![0-9])([0-9]{4,6}[A-Z]{0,5})(?![0-9])",Pattern.CASE_INSENSITIVE);
    static final Pattern SKU=Pattern.compile("\\bSKU\\s*[:#-]?\\s*([0-9]{4,8})\\b",Pattern.CASE_INSENSITIVE);
    static final Set<String> NONREF=new HashSet<>(Arrays.asList("1905","1926","1931","1945","1953","1955","1956","1963","2024","2025","2026"));
    /** references made in one bezel colour only: Bob's writes "124060" and "124060LN" for the same watch */
    static final Set<String> LN_ONLY=new HashSet<>(Arrays.asList("124060","114060","14060","14060M"));
    /** Bob's image CDN prefix that serves resized / re-encoded copies of an /images/ original */
    static final Pattern CDN_RESIZE=Pattern.compile("/cdn-cgi/image/[^/]+/");

    // ------------------------------------------------------------------ references
    public static String ref(String s){
        String u=clean(s).toUpperCase(Locale.US);
        Matcher x=REF_EX.matcher(u);
        if(x.find()&&!NONREF.contains(x.group(1)))return canonicalRef(variant(x.group(1),u));
        Matcher m=REF_ANY.matcher(u);List<String> a=new ArrayList<>();
        while(m.find())if(!NONREF.contains(m.group(1)))a.add(m.group(1));
        for(String r:a){int n=r.replaceAll("[A-Z]","").length();if(n>=5&&n<=6)return canonicalRef(variant(r,u));}
        return a.isEmpty()?"":canonicalRef(variant(a.get(0),u));
    }
    /** one folder per Rolex reference: strip a colour suffix that the reference only ever has (124060LN -> 124060) */
    public static String canonicalRef(String r){
        if(r==null)return "";r=r.toUpperCase(Locale.US);
        if(r.endsWith("LN")&&LN_ONLY.contains(r.substring(0,r.length()-2)))return r.substring(0,r.length()-2);
        return r;
    }
    static String variant(String r,String t){
        r=r.toUpperCase(Locale.US);
        if(r.equals("126710")){if(t.matches(".*\\b(PEPSI|BLRO)\\b.*"))return"126710BLRO";if(t.matches(".*\\b(BATMAN|BATGIRL|BLNR)\\b.*"))return"126710BLNR";if(t.matches(".*(BRUCE WAYNE|\\bGRNR\\b|GREY.*BLACK|BLACK.*GREY).*"))return"126710GRNR";}
        if(r.equals("126720")&&t.matches(".*\\b(SPRITE|VTNR)\\b.*"))return"126720VTNR";
        if(r.equals("126610")){if(t.matches(".*(STARBUCKS|CERMIT|GREEN BEZEL|\\bLV\\b).*"))return"126610LV";if(t.matches(".*(BLACK BEZEL|\\bLN\\b).*"))return"126610LN";}
        if(r.equals("116610")){if(t.matches(".*(HULK|GREEN DIAL|GREEN BEZEL|\\bLV\\b).*"))return"116610LV";if(t.matches(".*(BLACK BEZEL|\\bLN\\b).*"))return"116610LN";}
        if(r.equals("16610")&&t.matches(".*(KERMIT|GREEN BEZEL|\\bLV\\b).*"))return"16610LV";
        return r;
    }
    public static String skuFrom(String text){Matcher m=SKU.matcher(text==null?"":text);return m.find()?m.group(1):"";}

    // ------------------------------------------------------------------ keyword filter
    /** Several searches separated by commas or semicolons ("116610, 116613, gmt master"): a listing matches when ANY search
     *  matches; within one search every word must appear in the listing title or URL (case and punctuation ignored). */
    public static boolean matchesFilter(String filter,String title,String url){
        if(filter==null||filter.trim().isEmpty())return true;
        String hay=" "+searchText((title==null?"":title)+" "+(url==null?"":url))+" ";
        boolean any=false;
        for(String search:filter.split("[,;]")){
            String words=searchText(search);if(words.isEmpty())continue;any=true;
            boolean all=true;for(String part:words.split(" "))if(!hay.contains(part)){all=false;break;}
            if(all)return true;
        }
        return !any;
    }
    /** The searches of a filter, cleaned, for the log ("116610 | 116613"). */
    public static List<String> searches(String filter){
        List<String> o=new ArrayList<>();if(filter==null)return o;
        for(String s:filter.split("[,;]")){String c=clean(s);if(!c.isEmpty())o.add(c);}
        return o;
    }
    static String searchText(String s){return clean(s==null?"":s).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+"," ").trim();}

    // ------------------------------------------------------------------ URLs
    public static String canonicalProductUrl(String s){try{URI u=URI.create(s);String scheme=u.getScheme()==null?"https":u.getScheme(),host=u.getHost();if(host==null)return s;return new URI(scheme,host.toLowerCase(Locale.US),u.getPath(),null,null).toString();}catch(Exception e){return s;}}
    public static boolean productUrl(String s){try{URI u=URI.create(s);String h=u.getHost();if(h==null||!h.toLowerCase(Locale.US).endsWith("bobswatches.com"))return false;String p=u.getPath().toLowerCase(Locale.US);if(p.contains("/rolex-blog/")||p.contains("/sell-")||p.contains("/rolex-app")||p.contains("/about")||p.contains("/faq"))return false;return p.endsWith(".html");}catch(Exception e){return false;}}
    public static Integer resultTotal(String s){Matcher m=Pattern.compile("\\bof\\s+([0-9,]+)\\s+results\\b",Pattern.CASE_INSENSITIVE).matcher(s==null?"":s);if(!m.find())return null;try{return Integer.parseInt(m.group(1).replace(",",""));}catch(Exception e){return null;}}

    public static String normalizeImageUrl(String text,String base){
        if(text==null)return "";text=text.trim();
        if(text.isEmpty()||text.startsWith("data:")||text.startsWith("blob:"))return "";
        try{URI u=URI.create(text.replace(" ","%20"));if(!u.isAbsolute())u=URI.create(base).resolve(u);String scheme=u.getScheme(),host=u.getHost();
            if(scheme==null||host==null||(!scheme.equalsIgnoreCase("http")&&!scheme.equalsIgnoreCase("https")))return "";return u.toString();}catch(Exception e){return "";}
    }
    /** the full-size original behind a Bob's CDN resize URL (/cdn-cgi/image/width=480,.../images/x.jpg -> /images/x.jpg) */
    public static String originalImageUrl(String u){return u==null?"":CDN_RESIZE.matcher(u).replaceFirst("/");}

    /** A candidate image URL found on a product page, with where it came from. */
    public static final class Candidate{public final String url,source;public Candidate(String url,String source){this.url=url;this.source=source;}}
    /** Outcome of choosing a listing's own photographs. */
    public static final class Selection{public final List<String> keep=new ArrayList<>();public final Map<String,String> dropped=new LinkedHashMap<>();}

    /**
     * The listing's own product photographs: Bob's image files under /images/ whose name carries this listing's SKU
     * (zUsed-...-SKU194818PL.jpg, zy-191511-06-20-2024.jpg), or the page's JSON-LD Product images when no SKU is known.
     * Resized CDN copies are mapped to their original; thumbnails of OTHER listings (related-product tiles carrying
     * another SKU) and site artwork are dropped, each with its reason.
     */
    public static Selection selectProductImages(String sku,String pageUrl,List<Candidate> cands){
        Selection s=new Selection();Set<String> seen=new HashSet<>();
        for(Candidate c:cands){
            String u=normalizeImageUrl(c.url,pageUrl);
            if(u.isEmpty()){s.dropped.put(c.url,"not an http(s) image URL");continue;}
            u=originalImageUrl(u);
            String path;try{URI x=URI.create(u);String h=x.getHost();if(h==null||!h.toLowerCase(Locale.US).endsWith("bobswatches.com")){s.dropped.put(u,"not a bobswatches.com image");continue;}path=x.getPath()==null?"":x.getPath();}catch(Exception e){s.dropped.put(u,"bad URL");continue;}
            String name=path.substring(path.lastIndexOf('/')+1);
            if(!path.toLowerCase(Locale.US).startsWith("/images/")||!name.toLowerCase(Locale.US).matches(".*\\.(jpe?g|png|webp)$")){s.dropped.putIfAbsent(u,"site artwork / not a product photo file");continue;}
            if(path.toLowerCase(Locale.US).startsWith("/images/brands/")){s.dropped.putIfAbsent(u,"site artwork");continue;}
            boolean ownSku=!sku.isEmpty()&&Pattern.compile("(?<![0-9])"+Pattern.quote(sku)+"(?![0-9])").matcher(name).find();
            String otherSku=otherSku(name,sku);
            if(otherSku!=null){s.dropped.putIfAbsent(u,"belongs to another listing (SKU "+otherSku+")");continue;}
            if(!ownSku&&!(sku.isEmpty()&&c.source.startsWith("jsonld"))){s.dropped.putIfAbsent(u,sku.isEmpty()?"not in the page's product data":"file name does not carry this listing's SKU "+sku);continue;}
            if(!name.toLowerCase(Locale.US).startsWith("z")&&!c.source.startsWith("jsonld")){s.dropped.putIfAbsent(u,"thumbnail-size listing image (s/m prefix)");continue;}
            if(seen.add(u))s.keep.add(u);
        }
        for(String k:s.keep)s.dropped.remove(k);
        return s;
    }
    static String otherSku(String name,String sku){
        Matcher m=Pattern.compile("(?i)SKU([0-9]{5,8})").matcher(name);
        while(m.find())if(!m.group(1).equals(sku))return m.group(1);
        Matcher z=Pattern.compile("^zy-([0-9]{5,8})-").matcher(name);
        if(z.find()&&!z.group(1).equals(sku))return z.group(1);
        return null;
    }

    /** Bob's names its on-the-wrist photographs with a 'w' after the SKU (zUsed-Rolex-Submariner-124060-SKU189182w.jpg).
     *  They are only mildly tilted (dial ellipse 0.98-0.99, bezel/dial parallax 0.020-0.023 vs <= 0.020 face-on) but
     *  Watch Align cannot fit their pose (owner's 1.4 phone run: 3 of 3 rejected by the Watch Align runner). */
    public static boolean wristShot(String url){return url!=null&&url.matches("(?i).*SKU[0-9]{5,8}w\\.(jpe?g|png|webp)(\\?.*)?$");}

    // ------------------------------------------------------------------ export
    /** Splits files (sizes in bytes) into consecutive parts whose payload stays below the limit; a single file over the
     *  limit gets its own part (and is reported by the exporter as unexportable). */
    public static List<List<Integer>> planParts(long[] sizes,long limit){
        List<List<Integer>> parts=new ArrayList<>();List<Integer> cur=new ArrayList<>();long sz=0;
        for(int i=0;i<sizes.length;i++){if(!cur.isEmpty()&&sz+sizes[i]>limit){parts.add(cur);cur=new ArrayList<>();sz=0;}cur.add(i);sz+=sizes[i];}
        if(!cur.isEmpty())parts.add(cur);return parts;
    }
    /** RFC 4180 CSV field */
    public static String csv(String s){s=s==null?"":s;return "\""+s.replace("\"","\"\"")+"\"";}
    public static String safe(String s){s=(s==null?"UNKNOWN":s.toUpperCase(Locale.US)).replaceAll("[^A-Z0-9._-]","_");return s.isEmpty()?"UNKNOWN":s;}
    public static String clean(String s){return s==null?"":s.replaceAll("\\s+"," ").trim();}
}
