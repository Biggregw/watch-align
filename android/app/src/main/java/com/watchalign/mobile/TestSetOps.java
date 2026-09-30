package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.media.ExifInterface;

import java.io.File;
import java.util.Locale;

/** Photo handling shared by the Collect and RepTimeQC screens (alpha67). */
final class TestSetOps {
    private TestSetOps(){}

    static final String[] GPS_TAGS={ExifInterface.TAG_GPS_LATITUDE,ExifInterface.TAG_GPS_LATITUDE_REF,ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,ExifInterface.TAG_GPS_ALTITUDE,ExifInterface.TAG_GPS_ALTITUDE_REF,ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,ExifInterface.TAG_GPS_PROCESSING_METHOD,ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_SPEED,ExifInterface.TAG_GPS_SPEED_REF,ExifInterface.TAG_GPS_TRACK,ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,ExifInterface.TAG_GPS_IMG_DIRECTION_REF,ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,ExifInterface.TAG_GPS_MAP_DATUM,ExifInterface.TAG_GPS_SATELLITES,ExifInterface.TAG_GPS_STATUS};

    /** Removes location tags from a JPEG in place; a file without EXIF is left as it is. */
    static void stripGps(File f){
        try{ExifInterface ex=new ExifInterface(f.getPath());for(String t:GPS_TAGS)ex.setAttribute(t,null);ex.saveAttributes();}
        catch(Exception ignored){/* no EXIF block: nothing to strip */}
    }

    /** Photo check (no QC verdicts): dial found, 12 found, angle rating, marker-layout tilt, usable. */
    static void checkPhoto(File f,TestSetStore.Entry e){
        try{
            Bitmap preview=decodePreview(f);
            FullResSource full=fullSource(f);
            GmtDialCrop.Crop crop=full!=null?GmtDialCrop.make(preview,full):null;
            Bitmap b=crop!=null?crop.bitmap:preview;
            GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(b,MainActivity.GENERIC_GMT_CODE,null);
            boolean noDial=h.summary==null||h.summary.noDial||h.drawing==null||!Double.isFinite(h.drawing.dialCx);
            boolean twelve=!noDial&&h.drawing.twelve!=null;
            e.dialFound=noDial?"no":"yes";e.twelveFound=twelve?"yes":"no";e.pose=String.valueOf(h.poseLabel);
            if(!noDial&&h.round!=null){
                GmtMarkerPose.Result mp=GmtMarkerPose.estimate(h.round,Math.sqrt(h.drawing.dialA*h.drawing.dialB));
                e.markerTilt=mp.valid?String.format(Locale.US,"%.1f",mp.tiltDeg):"";
            }
            e.suitable=!noDial&&twelve&&h.poseLabel!=GmtHumanQcMath.PoseLabel.RETAKE?"yes":"no";
        }catch(Throwable t){e.dialFound="";e.suitable="no";e.notes=(e.notes.isEmpty()?"":e.notes+"; ")+"check failed: "+t.getClass().getSimpleName();}
    }

    static Bitmap decodePreview(File f){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        int max=Math.max(o.outWidth,o.outHeight),s=1;while(max/(s*2)>=1600)s*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=s;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b=BitmapFactory.decodeFile(f.getPath(),o);
        if(b==null)throw new IllegalArgumentException("not a readable image");
        int m=Math.max(b.getWidth(),b.getHeight());
        if(m<=1600)return b.copy(Bitmap.Config.ARGB_8888,false);
        float k=1600f/m;return Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*k),Math.round(b.getHeight()*k),true).copy(Bitmap.Config.ARGB_8888,false);
    }
    static FullResSource fullSource(File f){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        final int w=o.outWidth,h=o.outHeight;if(w<=0||h<=0)return null;
        return new FullResSource(){
            @Override public int width(){return w;}
            @Override public int height(){return h;}
            @SuppressWarnings("deprecation")
            @Override public Bitmap region(int x0,int y0,int x1,int y1,int ss){
                try{
                    BitmapRegionDecoder d=BitmapRegionDecoder.newInstance(f.getPath(),false);
                    try{BitmapFactory.Options ro=new BitmapFactory.Options();ro.inSampleSize=ss;ro.inPreferredConfig=Bitmap.Config.ARGB_8888;return d.decodeRegion(new Rect(x0,y0,x1,y1),ro);}
                    finally{d.recycle();}
                }catch(Throwable t){return null;}
            }
        };
    }
}
