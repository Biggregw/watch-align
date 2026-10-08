package com.watchalign.mobile;

import java.io.File;

/**
 * Desktop harness access to the app's model specs and genuine references (android/app/src/main/assets/models/), so
 * drivers run exactly what the app runs. Model id from -Dwatchalign.model (default gmt_126710). Research specs that are not
 * in the app (e.g. a provisional new model) are read from -Dwatchalign.models_root=<dir containing models/> instead.
 */
final class HarnessModel {
    private static ModelSpec spec;private static ModelReference ref;
    private HarnessModel(){}

    static File assetsRoot(){
        String o=System.getProperty("watchalign.models_root");
        if(o!=null&&!o.isEmpty()){File a=new File(o).getAbsoluteFile();if(new File(a,"models").isDirectory())return a;
            throw new IllegalStateException("watchalign.models_root has no models/ directory: "+a);}
        File d=new File("").getAbsoluteFile();
        while(d!=null){File a=new File(d,"android/app/src/main/assets");if(new File(a,"models").isDirectory())return a;d=d.getParentFile();}
        throw new IllegalStateException("android/app/src/main/assets/models not found above "+new File("").getAbsolutePath());
    }
    static synchronized ModelSpec spec(){
        if(spec==null){
            try{spec=ModelSpec.load(ModelSpec.directory(assetsRoot()),System.getProperty("watchalign.model","gmt_126710"));}
            catch(Exception e){throw new IllegalStateException(e);}
        }
        return spec;
    }
    static synchronized ModelReference ref(){if(ref==null)ref=ModelReference.load(ModelSpec.directory(assetsRoot()),spec());return ref;}
}
