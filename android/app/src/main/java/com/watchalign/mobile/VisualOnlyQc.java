package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Conservative fallback for common QC watches whose case/dial geometry is not
 * compatible with the circular 12-index engine (notably Cartier Santos/Tank).
 * It intentionally avoids fake measurements and instead highlights the regions
 * reviewers repeatedly inspect.
 */
final class VisualOnlyQc {
    static final class Result {
        final Bitmap annotated;
        final String report;
        Result(Bitmap annotated,String report){this.annotated=annotated;this.report=report;}
    }

    static Result analyse(Bitmap src, ModelCatalog.Profile profile) {
        Bitmap out=src.copy(Bitmap.Config.ARGB_8888,true);
        Canvas c=new Canvas(out);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2f,out.getWidth()/400f));
        p.setColor(Color.rgb(50,213,242));
        float w=out.getWidth(),h=out.getHeight();
        RectF dial=new RectF(w*0.23f,h*0.23f,w*0.77f,h*0.77f);c.drawRect(dial,p);
        p.setColor(Color.rgb(220,80,220));
        c.drawRect(new RectF(w*0.30f,h*0.30f,w*0.70f,h*0.56f),p);
        if(profile.hasDate()) {
            p.setColor(Color.rgb(255,190,60));
            if(profile.dateHour==6)c.drawRect(new RectF(w*0.43f,h*0.58f,w*0.57f,h*0.70f),p);
            else if(profile.dateHour==3)c.drawRect(new RectF(w*0.58f,h*0.43f,w*0.72f,h*0.57f),p);
        }
        StringBuilder r=new StringBuilder();
        r.append(profile.label).append(" · visual QC mode\n\n");
        r.append("This model is intentionally not passed through the circular Rolex-style marker engine.\n");
        r.append("Check visually: dial/printing alignment, hand alignment, case/bezel symmetry, crystal seating and surface defects.\n");
        if(profile.label.contains("Santos")) r.append("Santos-specific: inspect printed Roman numerals/minute track, bezel screw seating, crystal/dial squareness, bracelet/case fit and date centring where fitted.\n");
        if(profile.label.contains("Tank")) r.append("Tank-specific: inspect dial print spacing, chemin-de-fer/minute track, hand centring, case/crystal squareness and crown alignment.\n");
        r.append("No automated pass/fail is produced from unsupported geometry.");
        return new Result(out,r.toString());
    }

    private VisualOnlyQc(){}
}
