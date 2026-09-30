package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small JSON reader (alpha67) for the Reddit and Imgur API replies: objects become Map, arrays
 * List, numbers Double, plus String, Boolean and null. Kept dependency-free so the parsing is
 * unit-tested on the JVM (Android's org.json is a stub there).
 */
final class MiniJson {
    private final String s;private int i;
    private MiniJson(String s){this.s=s;}

    static Object parse(String text){
        MiniJson p=new MiniJson(text);p.ws();Object v=p.value();p.ws();
        if(p.i!=p.s.length())throw new IllegalArgumentException("trailing data at "+p.i);
        return v;
    }

    /** Follows a path of keys (String) and indexes (Integer); null when any step is missing. */
    @SuppressWarnings("unchecked")
    static Object at(Object root,Object... path){
        Object cur=root;
        for(Object k:path){
            if(k instanceof String&&cur instanceof Map)cur=((Map<String,Object>)cur).get(k);
            else if(k instanceof Integer&&cur instanceof List){List<Object> l=(List<Object>)cur;int n=(Integer)k;cur=n>=0&&n<l.size()?l.get(n):null;}
            else return null;
            if(cur==null)return null;
        }
        return cur;
    }
    static String str(Object root,Object... path){Object v=at(root,path);return v instanceof String?(String)v:null;}
    @SuppressWarnings("unchecked") static List<Object> list(Object root,Object... path){Object v=at(root,path);return v instanceof List?(List<Object>)v:new ArrayList<>();}
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object root,Object... path){Object v=at(root,path);return v instanceof Map?(Map<String,Object>)v:new LinkedHashMap<>();}

    private void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
    private Object value(){
        if(i>=s.length())throw new IllegalArgumentException("unexpected end");
        char c=s.charAt(i);
        if(c=='{')return object();
        if(c=='[')return array();
        if(c=='"')return string();
        if(s.startsWith("true",i)){i+=4;return Boolean.TRUE;}
        if(s.startsWith("false",i)){i+=5;return Boolean.FALSE;}
        if(s.startsWith("null",i)){i+=4;return null;}
        return number();
    }
    private Map<String,Object> object(){
        Map<String,Object> m=new LinkedHashMap<>();i++;ws();
        if(s.charAt(i)=='}'){i++;return m;}
        while(true){
            ws();String k=string();ws();expect(':');ws();m.put(k,value());ws();
            char c=s.charAt(i++);
            if(c=='}')return m;
            if(c!=',')throw new IllegalArgumentException("expected , or } at "+(i-1));
        }
    }
    private List<Object> array(){
        List<Object> l=new ArrayList<>();i++;ws();
        if(s.charAt(i)==']'){i++;return l;}
        while(true){
            ws();l.add(value());ws();
            char c=s.charAt(i++);
            if(c==']')return l;
            if(c!=',')throw new IllegalArgumentException("expected , or ] at "+(i-1));
        }
    }
    private String string(){
        expect('"');StringBuilder b=new StringBuilder();
        while(true){
            char c=s.charAt(i++);
            if(c=='"')return b.toString();
            if(c!='\\'){b.append(c);continue;}
            char e=s.charAt(i++);
            switch(e){
                case 'n':b.append('\n');break;case 't':b.append('\t');break;case 'r':b.append('\r');break;
                case 'b':b.append('\b');break;case 'f':b.append('\f');break;
                case 'u':b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;break;
                default:b.append(e);
            }
        }
    }
    private Double number(){
        int st=i;
        while(i<s.length()&&"+-0123456789.eE".indexOf(s.charAt(i))>=0)i++;
        if(st==i)throw new IllegalArgumentException("unexpected character at "+i);
        return Double.parseDouble(s.substring(st,i));
    }
    private void expect(char c){if(s.charAt(i)!=c)throw new IllegalArgumentException("expected "+c+" at "+i);i++;}
}
