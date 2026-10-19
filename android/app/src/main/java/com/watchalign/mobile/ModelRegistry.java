package com.watchalign.mobile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The supported watch families, discovered from the app's model assets (models/&lt;id&gt;/) and validated before they are
 * offered. Every family is measured and judged by the same engine (UnifiedMarkerReview: Alpha99Pipeline + ClearlyOff
 * 1.6.3); what differs between families is only what their own folder holds:
 *   model.json                         the dial: minute track, markers (hour, shape, size), date window
 *   reference/                         genuine outer-metal reference (12 triangle nominal and spread, uncertainty)
 *   alignment/luminous_alignment.json  genuine marker reference per shape and per hour (this family's own)
 *   alignment/genuine_track.csv        THIS engine's measurements of the family's verified genuine photos: the evidence
 *                                      that the engine and limits have been run on genuine watches of this family
 * A folder that is incomplete, inconsistent or not validated is not offered, with the reason (fail closed). Each family
 * is loaded from its own folder only, so a new or changed reference cannot affect another family.
 */
final class ModelRegistry {
    private ModelRegistry(){}

    static final String GENUINE_TRACK="alignment/genuine_track.csv";
    /** genuine markers of each shape the family's genuine run must have measured before it is offered */
    static final int MIN_GENUINE_PER_SHAPE=20;
    /** marker shapes this engine measures; a model with any other shape needs an engine extension first (never read as
     *  one of these) */
    static final Set<ModelSpec.Shape> ENGINE_SHAPES=EnumSet.of(ModelSpec.Shape.TRIANGLE,ModelSpec.Shape.BATON,ModelSpec.Shape.ROUND);

    /** a validated family: its dial and its own genuine references */
    static final class Family {
        final ModelSpec model;final ModelReference genuine;final AlignmentReference alignment;
        Family(ModelSpec m,ModelReference g,AlignmentReference a){model=m;genuine=g;alignment=a;}
        String id(){return model.id;}
    }

    /** one model folder: the family when valid, otherwise why it is not offered */
    static final class Check {
        final String id;final Family family;final String problem;
        Check(String id,Family f,String p){this.id=id;family=f;problem=p;}
        boolean ok(){return family!=null;}
    }

    static Check check(ModelSpec.Assets assets,String id){
        ModelSpec m;
        try{m=ModelSpec.load(assets,id);}
        catch(Exception e){return no(id,"its model definition cannot be read or uses an unknown marker shape ("+e.getMessage()+")");}
        if(!id.equals(m.id))return no(id,"its model definition names a different model ("+m.id+")");
        if(m.markers.isEmpty())return no(id,"its model definition has no markers");
        for(ModelSpec.Marker mk:m.markers)
            if(!ENGINE_SHAPES.contains(mk.shape))return no(id,"its "+mk.hour+" o'clock marker shape is not measured by this version");
        ModelReference genuine=ModelReference.load(assets,m);
        if(m.triangle()!=null&&(genuine==null||genuine.triangle==null))return no(id,"no genuine 12 o'clock triangle reference");
        AlignmentReference align=AlignmentReference.load(assets,m);
        if(align==null)return no(id,"no genuine marker alignment reference");
        if(!id.equals(align.model))return no(id,"its marker alignment reference belongs to another model ("+align.model+")");
        for(ModelSpec.Marker mk:m.markers){
            AlignmentReference.ShapeRef s=align.shape(mk.shape);
            if(s==null||!s.supported||align.setup(mk.shape)==null)
                return no(id,"no genuine reference for its "+AlignmentReference.key(mk.shape)+" markers");
            if(align.hours.get(mk.hour)==null)return no(id,"no genuine reference for its "+mk.hour+" o'clock marker");
        }
        for(Integer h:align.hours.keySet())
            if(m.atHour(h)==null)return no(id,"its marker alignment reference has a "+h+" o'clock marker this dial does not have (another layout)");
        String track;
        try{track=ModelSpec.read(assets,m.dir+"/"+GENUINE_TRACK);}catch(IOException|RuntimeException e){track=null;}
        if(track==null)return no(id,"not validated: no genuine measurements from this version's marker check");
        Calibration.Genuine g=Calibration.Genuine.parse(track);
        for(ModelSpec.Shape s:shapes(m)){
            int n=g.total(s);
            if(n<MIN_GENUINE_PER_SHAPE)return no(id,"not validated: "+n+" genuine "+AlignmentReference.key(s)+" markers measured by this version (at least "+MIN_GENUINE_PER_SHAPE+" needed)");
        }
        return new Check(id,new Family(m,genuine,align),null);
    }

    /** every model folder checked, the valid ones offered: the default first, the rest by id */
    static List<Check> discover(ModelSpec.Assets assets,String[] ids,String defaultId){
        List<Check> out=new ArrayList<>();
        if(ids==null)return out;
        String[] sorted=ids.clone();Arrays.sort(sorted);
        for(String id:sorted){Check c=check(assets,id);if(id.equals(defaultId))out.add(0,c);else out.add(c);}
        return out;
    }

    static List<Family> supported(List<Check> checks){
        List<Family> f=new ArrayList<>();for(Check c:checks)if(c.ok())f.add(c.family);return f;
    }

    static Set<ModelSpec.Shape> shapes(ModelSpec m){
        Set<ModelSpec.Shape> s=EnumSet.noneOf(ModelSpec.Shape.class);for(ModelSpec.Marker mk:m.markers)s.add(mk.shape);return s;
    }

    private static Check no(String id,String why){return new Check(id,null,why);}
}
