package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader for model spec files: objects -> LinkedHashMap, arrays -> ArrayList, numbers -> Double (parsed
 * with Double.parseDouble, so a value written as the old Java literal reads back as the identical double), strings,
 * booleans, null. Pure Java so the same spec loads on Android, in JVM unit tests (where android's org.json is a stub)
 * and in the desktop harness.
 */
final class MiniJson {
    private final String s;private int i;
    private MiniJson(String s){this.s=s;}

    static Object parse(String text){
        MiniJson p=new MiniJson(text);Object v=p.value();p.ws();
        if(p.i!=p.s.length())throw p.err("trailing content");
        return v;
    }

    private Object value(){
        ws();if(i>=s.length())throw err("unexpected end");
        char c=s.charAt(i);
        switch(c){
            case '{':return object();
            case '[':return array();
            case '"':return string();
            case 't':lit("true");return Boolean.TRUE;
            case 'f':lit("false");return Boolean.FALSE;
            case 'n':lit("null");return null;
            default:return number();
        }
    }
    private Map<String,Object> object(){
        Map<String,Object> m=new LinkedHashMap<>();i++;ws();
        if(peek('}')){i++;return m;}
        while(true){ws();String k=string();ws();expect(':');m.put(k,value());ws();
            if(peek(',')){i++;continue;}expect('}');return m;}
    }
    private List<Object> array(){
        List<Object> a=new ArrayList<>();i++;ws();
        if(peek(']')){i++;return a;}
        while(true){a.add(value());ws();if(peek(',')){i++;continue;}expect(']');return a;}
    }
    private String string(){
        expect('"');StringBuilder b=new StringBuilder();
        while(true){
            if(i>=s.length())throw err("unterminated string");
            char c=s.charAt(i++);
            if(c=='"')return b.toString();
            if(c=='\\'){char e=s.charAt(i++);
                switch(e){case 'n':b.append('\n');break;case 't':b.append('\t');break;case 'u':b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;break;default:b.append(e);}}
            else b.append(c);
        }
    }
    private Double number(){
        int st=i;while(i<s.length()&&"+-0123456789.eE".indexOf(s.charAt(i))>=0)i++;
        if(st==i)throw err("unexpected character");
        return Double.parseDouble(s.substring(st,i));
    }
    private void lit(String w){if(!s.startsWith(w,i))throw err("expected "+w);i+=w.length();}
    private void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
    private boolean peek(char c){return i<s.length()&&s.charAt(i)==c;}
    private void expect(char c){if(!peek(c))throw err("expected '"+c+"'");i++;}
    private IllegalArgumentException err(String m){return new IllegalArgumentException("model spec JSON: "+m+" at "+i);}
}
