package com.watchalign.mobile;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/** The owner's three options (20 Oct 2026): GMT-Master II and Submariner Date measure exactly alike; only Submariner
 *  (no date) has a 3 o'clock baton. The GMT folder is written from the Submariner Date folder by
 *  tools/research/shared_engine/make_gmt_from_date.py and keeps only its id, label and seconds-hand print spots. */
public class ThreeOptionsTest {
    static final String[] SAME={"alignment/metal_alignment.properties","reference/genuine_reference.csv","reference/nominal.properties",
            "reference/triangle_nominal.properties","reference/triangle_reference.csv","reference/uncertainty.properties"};
    static final String HEADER="# gmt_126710: copied from submariner_126610";

    static String text(String model,String f)throws Exception{
        return new String(Files.readAllBytes(new File(TestModels.assets(),"models/"+model+"/"+f).toPath()),"UTF-8");
    }

    @Test public void theOptionsAreGmtNoDateAndDateInThatOrder(){
        String[] ids=new File(TestModels.assets(),"models").list();
        List<ModelRegistry.Family> fs=ModelRegistry.supported(ModelRegistry.discover(TestModels.files(),ids,"gmt_126710"));
        assertEquals(3,fs.size());
        assertEquals("GMT-Master II",fs.get(0).model.label);
        assertEquals("Submariner (no date)",fs.get(1).model.label);
        assertEquals("Submariner Date",fs.get(2).model.label);
        assertNotNull(fs.get(0).model.date);assertNull(fs.get(1).model.date);assertNotNull(fs.get(2).model.date);
        assertNotNull(fs.get(1).model.atHour(3));assertNull(fs.get(0).model.atHour(3));assertNull(fs.get(2).model.atHour(3));
    }

    @Test public void gmtAndDateDialsAreIdenticalApartFromIdLabelAndPrintSpots()throws Exception{
        java.util.Map<?,?> g=(java.util.Map<?,?>)MiniJson.parse(text("gmt_126710","model.json")),d=(java.util.Map<?,?>)MiniJson.parse(text("submariner_126610","model.json"));
        assertEquals(d.keySet(),g.keySet());
        for(Object k:d.keySet()){
            if(k.equals("id")||k.equals("label")||k.equals("note")||k.equals("seconds_hand"))continue;
            assertEquals(String.valueOf(k),d.get(k),g.get(k));
        }
        assertNotEquals("the GMT keeps its own dial-print spots",d.get("seconds_hand"),g.get("seconds_hand"));
        ModelSpec gs=ModelRegistryTest.spec("gmt_126710"),ds=ModelRegistryTest.spec("submariner_126610");
        assertEquals(ds.markers.size(),gs.markers.size());
        assertEquals(ds.minuteTrackInnerR,gs.minuteTrackInnerR,0);
    }

    @Test public void gmtAndDateReferencesAreTheSameData()throws Exception{
        for(String f:SAME){
            String g=text("gmt_126710",f),d=text("submariner_126610",f);
            if(f.endsWith(".properties")){assertTrue(f,g.startsWith(HEADER));g=g.substring(g.indexOf('\n')+1);}
            assertEquals(f,d,g);
        }
        AlignmentReference g=AlignmentReference.load(TestModels.files(),ModelRegistryTest.spec("gmt_126710")),
                d=AlignmentReference.load(TestModels.files(),ModelRegistryTest.spec("submariner_126610"));
        assertEquals("gmt_126710",g.model);
        assertEquals(d.hours.keySet(),g.hours.keySet());
        for(ModelSpec.Shape s:ModelSpec.Shape.values()){
            AlignmentReference.ShapeRef a=d.shape(s),b=g.shape(s);
            assertEquals(s.toString(),a==null,b==null);
            if(a!=null){assertEquals(a.insetR,b.insetR,0);assertEquals(a.radialLimitR,b.radialLimitR,0);
                assertEquals(a.tangentialLimitR,b.tangentialLimitR,0);assertEquals(a.rotationLimitDeg,b.rotationLimitDeg,0);}
        }
    }
}
