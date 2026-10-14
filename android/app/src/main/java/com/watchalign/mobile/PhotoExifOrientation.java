package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;

/** Applies the JPEG EXIF orientation before the uploaded photo is previewed or measured. */
final class PhotoExifOrientation {
    private PhotoExifOrientation(){}

    static Bitmap apply(Bitmap source,int orientation){
        if(source==null)throw new IllegalArgumentException("source bitmap is null");
        int w=source.getWidth(),h=source.getHeight();
        Matrix m=new Matrix();
        switch(orientation){
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.setValues(new float[]{-1,0,w,0,1,0,0,0,1});break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.setValues(new float[]{-1,0,w,0,-1,h,0,0,1});break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.setValues(new float[]{1,0,0,0,-1,h,0,0,1});break;
            case ExifInterface.ORIENTATION_TRANSPOSE: m.setValues(new float[]{0,1,0,1,0,0,0,0,1});break;
            case ExifInterface.ORIENTATION_ROTATE_90: m.setValues(new float[]{0,-1,h,1,0,0,0,0,1});break;
            case ExifInterface.ORIENTATION_TRANSVERSE: m.setValues(new float[]{0,-1,h,-1,0,w,0,0,1});break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.setValues(new float[]{0,1,0,-1,0,w,0,0,1});break;
            default:return source;
        }
        return Bitmap.createBitmap(source,0,0,w,h,m,true);
    }
}
