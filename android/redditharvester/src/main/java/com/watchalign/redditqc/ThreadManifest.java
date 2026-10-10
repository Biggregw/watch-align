package com.watchalign.redditqc;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure Java input validation. No network requests are made when a list is imported. */
final class ThreadManifest {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 1000;

    static final class CaseRow {
        String id, model, family, factory, url, album, feature, review, label;
        String focusMarker, focusIssue;
    }

    static final class Selection {
        final ArrayList<CaseRow> rows;
        final int threads;
        final String modelSummary;
        final String markerSummary;

        Selection(ArrayList<CaseRow> rows, int threads, String models, String markers) {
            this.rows=rows;
            this.threads=threads;
            this.modelSummary=models;
            this.markerSummary=markers;
        }

        String summary() {
            return threads+" unique Reddit threads, "+rows.size()+" case rows; models: "+
                    modelSummary+"; markers: "+markerSummary;
        }
    }

    static Selection parse(byte[] bytes) throws IOException {
        if(bytes==null || bytes.length==0) throw new IOException("CSV is empty");
        if(bytes.length>MAX_BYTES) throw new IOException("CSV larger than 2 MB");
        String csv=new String(bytes,StandardCharsets.UTF_8);
        if(csv.startsWith("\uFEFF"))csv=csv.substring(1);
        if(csv.indexOf('\u0000')>=0)throw new IOException("Not a UTF-8 CSV text file");
        ArrayList<String> records=records(csv);
        if(records.size()<2)throw new IOException("CSV must have a header and at least one case");
        List<String> headers=fields(records.get(0));
        if(!headers.contains("reddit_url")||!headers.contains("case_id")||
                !headers.contains("reference")||!headers.contains("primary_feature")) {
            throw new IOException("CSV requires case_id, reference, reddit_url and primary_feature headers");
        }
        if(records.size()-1>MAX_ROWS)throw new IOException("Too many case rows (maximum 1000)");
        ArrayList<CaseRow> rows=new ArrayList<>();
        LinkedHashSet<String> threadIds=new LinkedHashSet<>();
        LinkedHashSet<String> models=new LinkedHashSet<>();
        LinkedHashSet<String> markers=new LinkedHashSet<>();
        HashSet<String> caseIds=new HashSet<>();
        for(int rowIndex=1;rowIndex<records.size();rowIndex++) {
            List<String> vals=fields(records.get(rowIndex));
            if(vals.size()!=headers.size())throw new IOException("CSV row "+(rowIndex+1)+": column count mismatch");
            Map<String,String> m=new HashMap<>();
            for(int c=0;c<headers.size();c++)m.put(headers.get(c).trim(),vals.get(c).trim());
            CaseRow r=new CaseRow();
            r.id=get(m,"case_id","");
            r.model=get(m,"reference","");
            r.family=get(m,"watch_family","Unknown");
            r.factory=get(m,"factory","");
            r.url=get(m,"reddit_url","");
            r.album=get(m,"reported_album_url","");
            r.feature=get(m,"primary_feature","");
            r.review=get(m,"paraphrased_evidence","");
            r.label=get(m,"provisional_label","");
            r.focusMarker=get(m,"focus_marker","");
            r.focusIssue=get(m,"focus_issue","");
            if(r.id.isEmpty()||r.model.isEmpty()||r.feature.isEmpty())
                throw new IOException("CSV row "+(rowIndex+1)+": case ID, model or feature is blank");
            if(!caseIds.add(r.id))
                throw new IOException("CSV row "+(rowIndex+1)+": duplicate case_id "+r.id);
            String postId=postId(r.url);
            if(postId==null)
                throw new IOException("CSV row "+(rowIndex+1)+": expected an HTTPS Reddit comments URL");
            threadIds.add(postId);
            models.add(r.model);
            if(!r.focusMarker.isEmpty())markers.add(r.focusMarker);
            rows.add(r);
        }
        if(rows.isEmpty())throw new IOException("No valid Reddit thread rows");
        return new Selection(rows,threadIds.size(),String.join("/",models),
                markers.isEmpty()?"mixed/unspecified":String.join("/",markers));
    }

    static String postId(String url) {
        try {
            URI u=new URI(url);
            String host=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme())||host==null)return null;
            host=host.toLowerCase(Locale.ROOT);
            if(!host.equals("reddit.com")&&!host.endsWith(".reddit.com"))return null;
            String path=u.getPath();
            if(path==null)return null;
            String[] parts=path.split("/");
            for(int i=0;i+1<parts.length;i++){
                if("comments".equalsIgnoreCase(parts[i]) && parts[i+1].matches("[A-Za-z0-9]{5,12}"))
                    return parts[i+1].toLowerCase(Locale.ROOT);
            }
        } catch(Exception ignored) {}
        return null;
    }

    private static String get(Map<String,String> row,String key,String fallback) {
        String x=row.get(key);
        return x==null||x.isEmpty()?fallback:x;
    }

    /** RFC4180-compatible splitting, including quoted multiline reviewer comments. */
    private static ArrayList<String> records(String csv)throws IOException {
        ArrayList<String> out=new ArrayList<>();
        StringBuilder s=new StringBuilder();
        boolean quoted=false;
        for(int i=0;i<csv.length();i++) {
            char c=csv.charAt(i);
            if(c=='"'){
                s.append(c);
                if(quoted && i+1<csv.length() && csv.charAt(i+1)=='"'){
                    s.append('"');i++;
                } else quoted=!quoted;
            } else if((c=='\r'||c=='\n')&&!quoted) {
                if(s.length()>0){out.add(s.toString());s.setLength(0);}
                if(c=='\r'&&i+1<csv.length()&&csv.charAt(i+1)=='\n')i++;
            } else s.append(c);
        }
        if(quoted)throw new IOException("Unterminated quoted CSV field");
        if(s.length()>0)out.add(s.toString());
        return out;
    }

    private static List<String> fields(String record) throws IOException {
        ArrayList<String> result=new ArrayList<>();
        StringBuilder value=new StringBuilder();
        boolean quoted=false;
        for(int i=0;i<record.length();i++){
            char c=record.charAt(i);
            if(c=='"') {
                if(quoted&&i+1<record.length()&&record.charAt(i+1)=='"'){value.append('"');i++;}
                else quoted=!quoted;
            } else if(c==','&&!quoted) {
                result.add(value.toString());value.setLength(0);
            } else value.append(c);
        }
        if(quoted)throw new IOException("Unterminated quoted CSV field");
        result.add(value.toString());
        return result;
    }

    private ThreadManifest(){}
}
