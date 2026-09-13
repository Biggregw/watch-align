package com.watchalign.mobile;

import android.graphics.Bitmap;import android.graphics.BitmapFactory;import android.graphics.Matrix;import androidx.exifinterface.media.ExifInterface;import java.io.ByteArrayInputStream;

/** Decodes a Camera2 JPEG into upright pixels regardless of device EXIF behaviour. */
final class StillImageDecoder {
    static Bitmap decodeUpright(byte[] jpeg)throws Exception{
        Bitmap source=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(source==null)throw new IllegalArgumentException("Camera returned an unreadable JPEG");
        ExifInterface exif=new ExifInterface(new ByteArrayInputStream(jpeg));int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);Matrix m=new Matrix();
        if(orientation==ExifInterface.ORIENTATION_ROTATE_90)m.postRotate(90);else if(orientation==ExifInterface.ORIENTATION_ROTATE_180)m.postRotate(180);else if(orientation==ExifInterface.ORIENTATION_ROTATE_270)m.postRotate(270);else if(orientation==ExifInterface.ORIENTATION_FLIP_HORIZONTAL)m.postScale(-1,1);else if(orientation==ExifInterface.ORIENTATION_FLIP_VERTICAL)m.postScale(1,-1);
        if(m.isIdentity())return source.copy(Bitmap.Config.ARGB_8888,false);Bitmap out=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true).copy(Bitmap.Config.ARGB_8888,false);source.recycle();return out;
    }
    private StillImageDecoder(){}
}
