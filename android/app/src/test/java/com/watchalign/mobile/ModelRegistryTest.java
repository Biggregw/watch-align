package com.watchalign.mobile;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Model discovery from the app's assets: only complete, validated families are offered, each from its own folder;
 *  anything missing or inconsistent fails closed with a reason. */
public class ModelRegistryTest {
    static ModelSpec spec(String id){try{return ModelSpec.load(TestModels.files(),id);}catch(Exception e){throw new AssertionError(e);}}

    /** the bundled assets with some files replaced (null = removed) */
    static final class Overlay implements ModelSpec.Assets {
        final Map<String,String> files=new HashMap<>();
        Overlay put(String path,String text){files.put(path,text);return this;}
        @Override public java.io.InputStream open(String p)throws java.io.IOException{
            if(files.containsKey(p)){String t=files.get(p);if(t==null)throw new FileNotFoundException(p);
                return new ByteArrayInputStream(t.getBytes(StandardCharsets.UTF_8));}
            return TestModels.files().open(p);
        }
    }
    static String read(String p){try{return ModelSpec.read(TestModels.files(),p);}catch(Exception e){throw new AssertionError(e);}}
    static String[] ids(){return new File(TestModels.assets(),"models").list();}

    @Test public void discoversExactlyTheValidatedFamiliesDefaultFirst(){
        List<ModelRegistry.Check> all=ModelRegistry.discover(TestModels.files(),ids(),"gmt_126710");
        assertEquals("gmt_126710",all.get(0).id);
        List<ModelRegistry.Family> ok=ModelRegistry.supported(all);
        assertEquals(2,ok.size());
        assertEquals("gmt_126710",ok.get(0).id());assertEquals("submariner_124060",ok.get(1).id());
        for(ModelRegistry.Check c:all)if(!c.ok())assertFalse(c.id+" refused without a reason",c.problem==null||c.problem.isEmpty());
    }

    @Test public void stagedDateFamilyWithoutGenuineValidationIsNotOffered(){
        // the 126610 family has its own dial and metal reference, and an alignment reference transferred from the
        // 124060, but no genuine photos measured by this version: not offered
        ModelRegistry.Check c=ModelRegistry.check(TestModels.files(),"submariner_126610");
        assertFalse(c.ok());assertTrue(c.problem,c.problem.startsWith("not validated"));
        // ... and with genuine measurements of every shape it would be (its layout and references are consistent)
        Overlay o=new Overlay().put("models/submariner_126610/alignment/genuine_track.csv",read("models/submariner_124060/alignment/genuine_track.csv"));
        assertTrue(ModelRegistry.check(o,"submariner_126610").problem,ModelRegistry.check(o,"submariner_126610").ok());
    }

    @Test public void tooFewGenuineMeasurementsOfAShapeIsNotValidated(){
        Overlay o=new Overlay().put("models/submariner_124060/alignment/genuine_track.csv","shape,rpx,side_pct,inout_pct,tilt_deg,gap_pct\nround,400,0.1,0.1,,\n");
        ModelRegistry.Check c=ModelRegistry.check(o,"submariner_124060");
        assertFalse(c.ok());assertTrue(c.problem,c.problem.contains("not validated"));
    }

    @Test public void missingReferencesFailClosed(){
        for(String missing:new String[]{"alignment/luminous_alignment.json","reference/triangle_nominal.properties",
                "reference/triangle_reference.csv","alignment/genuine_track.csv","model.json"}){
            Overlay o=new Overlay().put("models/submariner_124060/"+missing,null);
            ModelRegistry.Check c=ModelRegistry.check(o,"submariner_124060");
            assertFalse(missing+" missing but offered",c.ok());
        }
    }

    @Test public void aReferenceMissingAMarkerOrShapeFailsClosed(){
        String lum=read("models/submariner_124060/alignment/luminous_alignment.json");
        // drop the 6 o'clock hour entry
        String noSix=lum.replaceFirst("\"6\"\\s*:\\s*\\{[^}]*\\},?","");
        assertNotEquals(lum,noSix);
        ModelRegistry.Check c=ModelRegistry.check(new Overlay().put("models/submariner_124060/alignment/luminous_alignment.json",noSix),"submariner_124060");
        assertFalse(c.ok());assertTrue(c.problem,c.problem.contains("6 o'clock"));
        // a shape reference that is not supported
        int b=lum.indexOf("\"baton\""),k=lum.indexOf("\"supported\": true",b);
        String unsupported=lum.substring(0,k)+"\"supported\": false"+lum.substring(k+"\"supported\": true".length());
        assertNotEquals(lum,unsupported);
        c=ModelRegistry.check(new Overlay().put("models/submariner_124060/alignment/luminous_alignment.json",unsupported),"submariner_124060");
        assertFalse(c.ok());assertTrue(c.problem,c.problem.contains("baton"));
    }

    @Test public void anotherModelsReferenceOrDefinitionIsNeverAccepted(){
        String gmtLum=read("models/gmt_126710/alignment/luminous_alignment.json");
        ModelRegistry.Check c=ModelRegistry.check(new Overlay().put("models/submariner_124060/alignment/luminous_alignment.json",gmtLum),"submariner_124060");
        assertFalse(c.ok());assertTrue(c.problem,c.problem.contains("another model"));
        String json=read("models/submariner_124060/model.json").replace("\"id\": \"submariner_124060\"","\"id\": \"gmt_126710\"");
        c=ModelRegistry.check(new Overlay().put("models/submariner_124060/model.json",json),"submariner_124060");
        assertFalse(c.ok());assertTrue(c.problem,c.problem.contains("different model"));
    }

    @Test public void aReferenceForAnotherLayoutIsRejected(){
        // the no-date 124060 reference (with a 3 o'clock baton) given to the date dial (no 3): another layout
        String lum=read("models/submariner_124060/alignment/luminous_alignment.json").replace("\"model\": \"submariner_124060\"","\"model\": \"submariner_126610\"");
        Overlay o=new Overlay().put("models/submariner_126610/alignment/luminous_alignment.json",lum)
                .put("models/submariner_126610/alignment/genuine_track.csv",read("models/submariner_124060/alignment/genuine_track.csv"));
        ModelRegistry.Check c=ModelRegistry.check(o,"submariner_126610");
        assertFalse(c.ok());assertTrue(c.problem,c.problem.contains("3 o'clock"));
    }

    @Test public void anUnknownMarkerShapeIsNeverReadAsAKnownOne(){
        String json=read("models/submariner_124060/model.json").replaceFirst("\"shape\"\\s*:\\s*\"round\"","\"shape\": \"arabic_numeral\"");
        ModelRegistry.Check c=ModelRegistry.check(new Overlay().put("models/submariner_124060/model.json",json),"submariner_124060");
        assertFalse(c.ok());assertTrue(c.problem,c.problem.contains("marker shape"));
    }

    @Test public void familiesAreIsolated(){
        // a changed 124060 reference leaves the GMT family exactly as it was
        ModelRegistry.Family before=ModelRegistry.check(TestModels.files(),"gmt_126710").family;
        String lum=read("models/submariner_124060/alignment/luminous_alignment.json").replace("\"inset_R\": 0.01609","\"inset_R\": 0.05");
        Overlay o=new Overlay().put("models/submariner_124060/alignment/luminous_alignment.json",lum)
                .put("models/submariner_124060/alignment/genuine_track.csv",null);
        List<ModelRegistry.Family> fs=ModelRegistry.supported(ModelRegistry.discover(o,ids(),"gmt_126710"));
        assertEquals(1,fs.size());
        ModelRegistry.Family after=fs.get(0);
        assertEquals("gmt_126710",after.id());
        for(ModelSpec.Shape s:ModelSpec.Shape.values()){
            AlignmentReference.ShapeRef a=before.alignment.shape(s),b=after.alignment.shape(s);
            assertEquals(a==null,b==null);if(a!=null)assertEquals(a.insetR,b.insetR,0);
        }
        assertEquals(before.alignment.hours.keySet(),after.alignment.hours.keySet());
        assertEquals(before.genuine.triangle.nominalRadialR,after.genuine.triangle.nominalRadialR,0);
        // and each family's own values differ where the families differ
        ModelRegistry.Family sub=ModelRegistry.check(TestModels.files(),"submariner_124060").family;
        assertNotEquals(before.alignment.shape(ModelSpec.Shape.TRIANGLE).insetR,sub.alignment.shape(ModelSpec.Shape.TRIANGLE).insetR,1e-9);
    }

    @Test public void dateAndNoDateLayouts(){
        ModelRegistry.Family gmt=ModelRegistry.check(TestModels.files(),"gmt_126710").family,sub=ModelRegistry.check(TestModels.files(),"submariner_124060").family;
        assertNotNull(gmt.model.date);assertNull(gmt.model.atHour(gmt.model.date.hour));
        assertFalse(gmt.alignment.hours.containsKey(gmt.model.date.hour));
        assertNull(sub.model.date);assertNotNull(sub.model.atHour(3));assertTrue(sub.alignment.hours.containsKey(3));
        assertEquals(11,gmt.model.markers.size());assertEquals(12,sub.model.markers.size());
    }
}
