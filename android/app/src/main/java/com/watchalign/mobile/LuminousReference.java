package com.watchalign.mobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Own experimental inner polygons, measured from multiple genuine listing groups. Never an outer inset. */
final class LuminousReference {
    final Map<String,double[][]> shapes=new LinkedHashMap<>();
    final Map<String,Integer> groups=new LinkedHashMap<>();
    static String key(ModelSpec.Marker m){return m.shape==ModelSpec.Shape.ROUND?"rounds":m.key;}
    @SuppressWarnings("unchecked") static LuminousReference load(ModelSpec.Assets assets,String id)throws IOException {
        String text;try(InputStream in=assets.open("experimental/luminous/"+id+".json")){text=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        Map<String,Object> root=(Map<String,Object>)MiniJson.parse(text);LuminousReference ref=new LuminousReference();
        if(!id.equals(root.get("model"))||!Boolean.TRUE.equals(root.get("experimental")))throw new IOException("Wrong luminous reference model/status");
        Map<String,Object> entries=(Map<String,Object>)root.get("shapes");
        for(Map.Entry<String,Object> e:entries.entrySet()){
            Map<String,Object> item=(Map<String,Object>)e.getValue();List<Object> vertices=(List<Object>)item.get("polygon");double[][] p=new double[vertices.size()][];
            for(int i=0;i<p.length;i++){List<Object> v=(List<Object>)vertices.get(i);p[i]=new double[]{((Number)v.get(0)).doubleValue(),((Number)v.get(1)).doubleValue()};}
            int n=((Number)item.get("recorded_groups")).intValue();if(n>=3&&MarkerPolygonMismatch.compare(p,p)!=null){ref.shapes.put(e.getKey(),p);ref.groups.put(e.getKey(),n);}
        }return ref;
    }
    double[][] expected(ModelSpec.Marker m,double[] ring){double[][] p=shapes.get(key(m));return p==null?null:LuminousGeometry.placed(p,m,ring);}
}
