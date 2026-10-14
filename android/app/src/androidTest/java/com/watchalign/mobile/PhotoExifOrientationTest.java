package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.media.ExifInterface;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class PhotoExifOrientationTest {
    @Test public void uploadedBitmapUsesExifRotationAndMirroring(){
        Bitmap source=Bitmap.createBitmap(2,3,Bitmap.Config.ARGB_8888);
        Bitmap rotated=PhotoExifOrientation.apply(source,ExifInterface.ORIENTATION_ROTATE_90);
        assertEquals(3,rotated.getWidth());assertEquals(2,rotated.getHeight());
        Bitmap mirrored=PhotoExifOrientation.apply(source,ExifInterface.ORIENTATION_FLIP_HORIZONTAL);
        assertEquals(2,mirrored.getWidth());assertEquals(3,mirrored.getHeight());
    }
}
