package com.watchalign.mobile;

import android.graphics.Bitmap;

/**
 * Single production measurement and review route for every supported Rolex family.
 *
 * ModelSpec supplies only dial geometry and marker positions. The model's genuine
 * references supply nominal measurements and uncertainty. The detectors, outer-metal
 * fitting, uncertainty handling and review decisions are ALWAYS those of
 * Alpha99Pipeline and ClearlyOff (Watch Align 1.6.3).
 *
 * A new family must not fall back to a legacy or family-specific analyser.
 */
final class UnifiedMarkerReview {
    static final String[] FAMILY_IDS={
            "gmt_126710", "submariner_124060", "submariner_126610"
    };

    private UnifiedMarkerReview(){}

    static boolean supports(String id){
        if(id==null)return false;
        for(String family:FAMILY_IDS)if(family.equals(id))return true;
        return false;
    }

    /** Do not offer a family without a genuine reference for all of its actual markers. */
    static boolean ready(ModelSpec model,ModelReference genuine,AlignmentReference align){
        if(model==null||!supports(model.id)||genuine==null||genuine.triangle==null
                ||align==null||!model.id.equals(align.model))return false;
        for(ModelSpec.Marker marker:model.markers){
            AlignmentReference.ShapeRef shape=align.shape(marker.shape);
            if(shape==null||!shape.supported||align.hours.get(marker.hour)==null
                    ||align.setup(marker.shape)==null)return false;
        }
        return true;
    }

    /** No family switch: every watch goes through the exact same measurement implementation. */
    static Alpha99Pipeline.Output run(Bitmap photo,ModelSpec model,ModelReference genuine,AlignmentReference align){
        if(!ready(model,genuine,align))
            throw new IllegalArgumentException("This model does not have a complete alignment reference");
        return Alpha99Pipeline.run(photo,model,genuine,align,null);
    }
}
