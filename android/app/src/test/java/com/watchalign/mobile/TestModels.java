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
    /** The GMT folder as it was before the GMT option took the Submariner Date geometry (20 Oct 2026), frozen in test
     *  resources. The earlier analysis layers (Alpha97/98/99, luminous decision) were calibrated and their test
     *  measurements recorded against it, so their tests keep using it; the app's own GMT folder is files(). */
    static File legacyAssets(){
        File a=new File(assets().getParentFile().getParentFile(),"test/resources/legacy_models");
        if(!new File(a,"models/gmt_126710").isDirectory())throw new AssertionError("legacy GMT fixture not found: "+a);
        return a;
    }
    static ModelSpec.Assets legacyFiles(){return ModelSpec.directory(legacyAssets());}
    static synchronized ModelSpec gmt(){
        if(gmt==null)try{gmt=ModelSpec.load(legacyFiles(),"gmt_126710");}catch(Exception e){throw new AssertionError(e);}
        return gmt;
    }
    static synchronized ModelReference gmtRef(){if(gmtRef==null)gmtRef=ModelReference.load(legacyFiles(),gmt());return gmtRef;}
}
