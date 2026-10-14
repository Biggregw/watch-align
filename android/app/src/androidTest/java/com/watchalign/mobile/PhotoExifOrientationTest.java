package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Uploaded photos are measured as the gallery shows them: EXIF rotation and mirroring applied (on a device). */
@RunWith(AndroidJUnit4.class)
public class PhotoExifOrientationTest {
    /** 2 x 3 bitmap with a red top-left pixel, to follow where that corner goes. */
    static Bitmap marked(){Bitmap b=Bitmap.createBitmap(2,3,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLACK);b.setPixel(0,0,Color.RED);return b;}

    @Test public void rotationsSwapTheSidesAndMoveTheCorner(){
        Bitmap r90=PerspectiveOverlayPocActivity.upright(marked(),6);    // rotate 90 clockwise
        assertEquals(3,r90.getWidth());assertEquals(2,r90.getHeight());assertEquals(Color.RED,r90.getPixel(2,0));
        Bitmap r270=PerspectiveOverlayPocActivity.upright(marked(),8);   // rotate 270 clockwise
        assertEquals(3,r270.getWidth());assertEquals(Color.RED,r270.getPixel(0,1));
        Bitmap r180=PerspectiveOverlayPocActivity.upright(marked(),3);
        assertEquals(2,r180.getWidth());assertEquals(Color.RED,r180.getPixel(1,2));
    }

    @Test public void mirroringKeepsTheSizeAndFlipsTheCorner(){
        Bitmap h=PerspectiveOverlayPocActivity.upright(marked(),2);
        assertEquals(2,h.getWidth());assertEquals(3,h.getHeight());assertEquals(Color.RED,h.getPixel(1,0));
        Bitmap t=PerspectiveOverlayPocActivity.upright(marked(),5);      // transpose
        assertEquals(3,t.getWidth());assertEquals(Color.RED,t.getPixel(0,0));
    }

    @Test public void normalOrientationIsUnchanged(){
        Bitmap b=marked();assertSame(b,PerspectiveOverlayPocActivity.upright(b,1));assertSame(b,PerspectiveOverlayPocActivity.upright(b,0));
    }
}
