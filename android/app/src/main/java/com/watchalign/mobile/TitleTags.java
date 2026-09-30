package com.watchalign.mobile;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guesses the GMT model and factory from a RepTimeQC post title (alpha67). Only a guess: the
 * review screen shows it and the user can change it before a photo is added.
 */
final class TitleTags {
    private TitleTags(){}

    static final class Guess {
        String model="",factory="";
        /** Title mentions a GMT (reference, nickname or "GMT"). */
        boolean gmt;
        /** Title suggests a genuine watch may be in the photos (comparisons): never added in bulk. */
        boolean mixed;
    }

    private static final Pattern REF=Pattern.compile("\\b(1267(?:10|11|13|15|18|19|20|29))\\s*-?\\s*(BLNR|BLRO|GRNR|CHNR|VTNR)?\\b");
    private static final Pattern SUFFIX=Pattern.compile("\\b(BLNR|BLRO|GRNR|CHNR|VTNR)\\b");
    private static final String[][] NICK={
            {"bruce\\s*wayne","126710GRNR"},{"batgirl|batman","126710BLNR"},{"pepsi","126710BLRO"},
            {"sprite|destro|lefty|left[- ]handed","126720VTNR"},{"root\\s*beer","126711CHNR"}};
    private static final String[][] FACTORY={
            {"vsf|vs\\s*factory","VSF"},{"clean|cf","Clean"},{"arf","ARF"},{"gmf","GMF"},{"c\\+","C+"},{"ewf|ew\\s*factory","EWF"},
            {"apsf|aps","APSF"},{"bp|bpf","BP"},{"djf","DJF"},{"zf","ZF"},{"noob","Noob"},{"rich","Rich"}};
    private static final Pattern MIXED=Pattern.compile("\\b(gens?|genuine|real|retail|authentic|legit\\s*check)\\b|\\bvs\\.?\\s*(gen|real)",Pattern.CASE_INSENSITIVE);

    static Guess guess(String title){
        Guess g=new Guess();if(title==null)return g;
        String up=title.toUpperCase(Locale.US),low=title.toLowerCase(Locale.US);
        Matcher m=REF.matcher(up);
        if(m.find()){
            String ref=m.group(1),suf=m.group(2);
            if(suf==null){Matcher s=SUFFIX.matcher(up);if(s.find())suf=s.group(1);}
            if(suf==null)suf=nickSuffix(low);
            g.model=suf!=null&&valid(ref+suf)?ref+suf:"";
            g.gmt=true;
        }else{
            Matcher s=SUFFIX.matcher(up);
            String nick=nick(low);
            if(s.find())g.model=byDefault(s.group(1));
            else if(nick!=null)g.model=nick;
            // "Everose root beer" is the 126715.
            if("126711CHNR".equals(g.model)&&low.contains("everose"))g.model="126715CHNR";
            g.gmt=!g.model.isEmpty()||low.contains("gmt");
        }
        for(String[] f:FACTORY)if(Pattern.compile("(?<![a-z0-9])("+f[0]+")(?![a-z0-9])").matcher(low).find()){g.factory=f[1];break;}
        g.mixed=MIXED.matcher(title).find();
        return g;
    }

    /**
     * Title and link from text shared by another app (alpha67), e.g. the Reddit app's share of a post:
     * a link, sometimes with the title before it or in the subject. Without a title, the words in a
     * reddit post link's slug are used. Tracking parameters are removed from the link.
     */
    static String[] fromShare(String subject,String text){
        String sub=subject==null?"":subject.trim(),txt=text==null?"":text.trim();
        String all=(sub.isEmpty()||txt.contains(sub)?txt:sub+"\n"+txt).trim();
        Matcher m=RedditClient.LINK.matcher(all);
        String raw=m.find()?m.group():"";
        String link=raw.indexOf('?')>=0?raw.substring(0,raw.indexOf('?')):raw;
        String title=(raw.isEmpty()?all:all.replace(raw,"")).replaceAll("\\s+"," ").trim();
        if(title.isEmpty()){
            Matcher slug=Pattern.compile("/comments/[A-Za-z0-9]+/([^/?#]+)").matcher(link);
            if(slug.find())title=slug.group(1).replace('_',' ').trim();
        }
        return new String[]{title,link};
    }

    private static String nick(String low){for(String[] n:NICK)if(Pattern.compile("\\b("+n[0]+")\\b").matcher(low).find())return n[1];return null;}
    private static String nickSuffix(String low){String n=nick(low);return n==null?null:n.substring(6);}
    private static String byDefault(String suf){
        switch(suf){case "BLNR":return "126710BLNR";case "BLRO":return "126710BLRO";case "GRNR":return "126710GRNR";
            case "CHNR":return "126711CHNR";default:return "126720VTNR";}
    }
    private static boolean valid(String model){for(String s:TestSetStore.MODELS)if(s.equals(model))return true;return false;}
}
