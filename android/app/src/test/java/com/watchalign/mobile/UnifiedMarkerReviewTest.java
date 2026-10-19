package com.watchalign.mobile;

import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/** All supported models use the single production marker review route; anything else is refused, never rerouted. */
public class UnifiedMarkerReviewTest {
    static List<ModelRegistry.Check> bundled(){
        String[] ids=new File(TestModels.assets(),"models").list();
        return ModelRegistry.discover(TestModels.files(),ids,"gmt_126710");
    }

    @Test public void everySupportedFamilyGoesThroughTheSameEngineWithItsOwnReferences(){
        List<ModelRegistry.Family> fs=ModelRegistry.supported(bundled());
        assertFalse(fs.isEmpty());
        for(ModelRegistry.Family f:fs){
            assertEquals(f.id(),f.alignment.model);
            assertEquals("models/"+f.id(),f.model.dir);
            for(ModelSpec.Marker mk:f.model.markers)assertTrue(ModelRegistry.ENGINE_SHAPES.contains(mk.shape));
        }
    }

    @Test public void dateFamilyHasNoThreeOclockMarkerButUsesIdenticalMarkerCategories(){
        ModelSpec noDate=ModelRegistryTest.spec("submariner_124060"),date=ModelRegistryTest.spec("submariner_126610");
        assertNull(date.atHour(3));
        assertNotNull(date.date);
        assertEquals(3,date.date.hour);
        assertNotNull(noDate.atHour(3));
        assertNull(noDate.date);
        for(ModelSpec.Marker marker:date.markers){
            ModelSpec.Marker other=noDate.atHour(marker.hour);
            assertNotNull("Missing common dial marker: "+marker.hour,other);
            assertEquals(other.shape,marker.shape);
        }
    }

    @Test(expected=UnifiedMarkerReview.NotSupported.class)
    public void aModelThatIsNotAValidatedFamilyIsRefusedNotRerouted(){
        UnifiedMarkerReview.run(null,null);
    }

    @Test(expected=UnifiedMarkerReview.NotSupported.class)
    public void aFamilyWithoutItsReferencesIsRefused(){
        ModelSpec gmt=ModelRegistryTest.spec("gmt_126710");
        UnifiedMarkerReview.run(null,new ModelRegistry.Family(gmt,null,AlignmentReference.load(TestModels.files(),gmt)));
    }
}
