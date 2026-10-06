package org.opencv.android;
import org.opencv.core.Mat; import android.graphics.Bitmap;
public class Utils { public static void bitmapToMat(Bitmap b, Mat m){ b.rgba.copyTo(m);} }
