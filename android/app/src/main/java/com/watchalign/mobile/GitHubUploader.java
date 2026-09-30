package com.watchalign.mobile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uploads test-set files to a GitHub repository through the REST contents API (alpha66). Optional:
 * only used when the user has entered a repository and a token in Collect settings. The token
 * should be a fine-grained token limited to that one repository with Contents read/write.
 * Files go onto their own branch, so the app's main branch never carries photos.
 */
final class GitHubUploader {
    final String owner,repo,branch,folder,token;
    static final String API="https://api.github.com";

    GitHubUploader(String ownerRepo,String branch,String folder,String token){
        String[] p=ownerRepo.trim().split("/");
        if(p.length!=2||p[0].isEmpty()||p[1].isEmpty())throw new IllegalArgumentException("repository must be owner/name");
        owner=p[0];repo=p[1];this.branch=branch.trim().isEmpty()?"testset-inbox":branch.trim();
        this.folder=trimSlashes(folder.trim().isEmpty()?"testset":folder.trim());this.token=token.trim();
    }

    static final class Response {final int code;final String body;Response(int c,String b){code=c;body=b;}}

    /** Makes sure the branch exists, creating it from the default branch when it does not. */
    void ensureBranch()throws IOException{
        Response r=call("GET","/repos/"+owner+"/"+repo+"/git/ref/heads/"+branch,null);
        if(r.code==200)return;
        if(r.code!=404)throw new IOException("GitHub: "+r.code+" "+shortMsg(r.body));
        Response info=call("GET","/repos/"+owner+"/"+repo,null);
        if(info.code!=200)throw new IOException("GitHub: repository not reachable ("+info.code+" "+shortMsg(info.body)+")");
        String def=field(info.body,"default_branch");
        Response base=call("GET","/repos/"+owner+"/"+repo+"/git/ref/heads/"+def,null);
        String sha=field(base.body,"sha");
        if(base.code!=200||sha==null)throw new IOException("GitHub: could not read the default branch");
        Response made=call("POST","/repos/"+owner+"/"+repo+"/git/refs","{\"ref\":\"refs/heads/"+branch+"\",\"sha\":\""+sha+"\"}");
        if(made.code!=201&&made.code!=422)throw new IOException("GitHub: could not create branch "+branch+" ("+made.code+" "+shortMsg(made.body)+")");
    }

    /** Uploads one file. True when written, false when it was already there. */
    boolean put(String relPath,byte[] content,String message)throws IOException{
        String path=folder+"/"+trimSlashes(relPath);
        Response r=call("PUT","/repos/"+owner+"/"+repo+"/contents/"+encodePath(path),body(message,content,branch));
        if(r.code==200||r.code==201)return true;
        if(r.code==422&&r.body.contains("sha"))return false;   // exists already
        throw new IOException("GitHub upload of "+path+" failed: "+r.code+" "+shortMsg(r.body));
    }

    static String body(String message,byte[] content,String branch){
        return "{\"message\":\""+json(message)+"\",\"branch\":\""+json(branch)+"\",\"content\":\""+Base64.getEncoder().encodeToString(content)+"\"}";
    }

    Response call(String method,String path,String json)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();
        try{
            c.setRequestMethod(method);c.setConnectTimeout(20000);c.setReadTimeout(60000);
            c.setRequestProperty("Accept","application/vnd.github+json");
            c.setRequestProperty("Authorization","Bearer "+token);
            c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            c.setRequestProperty("User-Agent","WatchAlign");
            if(json!=null){
                c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
                try(OutputStream o=c.getOutputStream()){o.write(json.getBytes(StandardCharsets.UTF_8));}
            }
            int code=c.getResponseCode();
            InputStream in=code<400?c.getInputStream():c.getErrorStream();
            return new Response(code,in==null?"":read(in));
        }finally{c.disconnect();}
    }

    static String read(InputStream in)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))>0)b.write(buf,0,n);
        return b.toString("UTF-8");
    }
    static String field(String json,String name){
        Matcher m=Pattern.compile("\""+Pattern.quote(name)+"\"\\s*:\\s*\"([^\"]*)\"").matcher(json==null?"":json);
        return m.find()?m.group(1):null;
    }
    static String shortMsg(String body){String m=field(body,"message");return m!=null?m:"";}
    static String json(String s){
        StringBuilder b=new StringBuilder();
        for(char ch:s.toCharArray()){
            if(ch=='"'||ch=='\\')b.append('\\').append(ch);
            else if(ch<0x20)b.append(String.format(java.util.Locale.US,"\\u%04x",(int)ch));
            else b.append(ch);
        }
        return b.toString();
    }
    static String trimSlashes(String s){while(s.startsWith("/"))s=s.substring(1);while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;}
    static String encodePath(String p){
        StringBuilder b=new StringBuilder();
        for(String seg:p.split("/")){
            if(b.length()>0)b.append('/');
            try{b.append(java.net.URLEncoder.encode(seg,"UTF-8").replace("+","%20"));}catch(java.io.UnsupportedEncodingException e){b.append(seg);}
        }
        return b.toString();
    }
}
