package com.watchalign.mobile;

import java.io.File;

/** JVM-test access to the app's model specs and genuine references (src/main/assets/models), as the app loads them. */
final class TestModels {
    private static ModelSpec gmt;private static ModelReference gmtRef;
    private TestModels(){}

    static File assets(){
        File d=new File("").getAbsoluteFile();
        while(d!=null){
            for(String rel:new String[]{"src/main/assets","android/app/src/main/assets"}){File a=new File(d,rel);if(new File(a,"models").isDirectory())return a;}
            d=d.getParentFile();
        }
        throw new AssertionError("app assets not found");
    }
    static ModelSpec.Assets files(){return ModelSpec.directory(assets());}
    static synchronized ModelSpec gmt(){
        if(gmt==null)try{gmt=ModelSpec.load(files(),"gmt_126710");}catch(Exception e){throw new AssertionError(e);}
        return gmt;
    }
    static synchronized ModelReference gmtRef(){if(gmtRef==null)gmtRef=ModelReference.load(files(),gmt());return gmtRef;}
}
