package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

/** All supported models must use the single production marker review route. */
public class UnifiedMarkerReviewTest {
    private static ModelSpec spec(String id){
        try{return ModelSpec.load(TestModels.files(),id);}
        catch(Exception e){throw new AssertionError(e);}
    }

    @Test public void allSupportedFamiliesAreReadyForTheSameReviewEngine(){
        assertEquals(3,UnifiedMarkerReview.FAMILY_IDS.length);
        for(String id:UnifiedMarkerReview.FAMILY_IDS){
            ModelSpec model=spec(id);
            ModelReference metal=ModelReference.load(TestModels.files(),model);
            AlignmentReference alignment=AlignmentReference.load(TestModels.files(),model);
            assertTrue("Missing family reference: "+id,UnifiedMarkerReview.ready(model,metal,alignment));
            assertEquals(id,alignment.model);
            assertEquals(id.equals("submariner_124060")?12:11,model.markers.size());
        }
    }

    @Test public void dateFamilyHasNoThreeOclockMarkerButUsesIdenticalMarkerCategories(){
        ModelSpec noDate=spec("submariner_124060"),date=spec("submariner_126610");
        assertNull(date.atHour(3));
        assertNotNull(date.date);
        assertEquals(3,date.date.hour);
        assertNotNull(noDate.atHour(3));
        for(ModelSpec.Marker marker:date.markers){
            ModelSpec.Marker other=noDate.atHour(marker.hour);
            assertNotNull("Missing common dial marker: "+marker.hour,other);
            assertEquals(other.shape,marker.shape);
        }
    }

    @Test public void missingOrMixedReferencesNeverChooseAnotherPipeline(){
        ModelSpec gmt=spec("gmt_126710"),date=spec("submariner_126610");
        ModelReference genuine=ModelReference.load(TestModels.files(),gmt);
        AlignmentReference align=AlignmentReference.load(TestModels.files(),gmt);
        assertFalse(UnifiedMarkerReview.ready(date,genuine,align));
        assertFalse(UnifiedMarkerReview.ready(gmt,null,align));
        assertFalse(UnifiedMarkerReview.ready(gmt,genuine,null));
        assertFalse(UnifiedMarkerReview.supports("116610LN"));
        assertFalse(UnifiedMarkerReview.supports("bad_id"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void unsupportedModelMustNotFallBackToLegacyAnalyzer(){
        UnifiedMarkerReview.run(null,spec("submariner_126610"),null,null);
    }
}
