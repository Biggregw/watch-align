package com.watchalign.mobile;
import android.view.Surface;import org.junit.Test;import static org.junit.Assert.*;
public class CameraCaptureConfigTest {
 @Test public void computesBackCameraJpegOrientation(){assertEquals(90,CameraCaptureConfig.jpegOrientation(90,Surface.ROTATION_0,false));assertEquals(180,CameraCaptureConfig.jpegOrientation(90,Surface.ROTATION_90,false));}
}
