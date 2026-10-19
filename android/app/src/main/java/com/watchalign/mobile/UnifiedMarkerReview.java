package com.watchalign.mobile;

import android.graphics.Bitmap;

/**
 * Single production measurement and review route for every supported watch family.
 *
 * ModelSpec supplies only dial geometry and marker positions. The model's genuine
 * references supply nominal measurements and uncertainty. The detectors, outer-metal
 * fitting, uncertainty handling and review decisions are ALWAYS those of
 * Alpha99Pipeline and ClearlyOff (Watch Align 1.6.3).
 *
 * Which families are supported is decided by ModelRegistry from the model assets (validated, fail closed); there is
 * no list of families here and no family switch. A model that is not a validated family is refused, never sent to a
 * legacy or family-specific analyser.
 */
final class UnifiedMarkerReview {
    private UnifiedMarkerReview(){}

    /** thrown when asked to review a model that is not a validated family */
    static final class NotSupported extends IllegalArgumentException {
        NotSupported(String why){super(why);}
    }

    static Alpha99Pipeline.Output run(Bitmap photo,ModelRegistry.Family family){
        if(family==null||family.model==null||family.genuine==null||family.alignment==null)
            throw new NotSupported("This watch model does not have a complete, validated genuine reference");
        return Alpha99Pipeline.run(photo,family.model,family.genuine,family.alignment,null);
    }
}
