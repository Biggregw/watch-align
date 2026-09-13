package com.watchalign.mobile;

import android.Manifest;import android.app.Activity;import android.content.pm.PackageManager;import android.graphics.Bitmap;import android.graphics.Color;import android.graphics.Matrix;import android.graphics.Rect;import android.graphics.SurfaceTexture;import android.hardware.camera2.*;import android.media.Image;import android.media.ImageReader;import android.os.*;import android.util.Range;import android.util.Size;import android.view.*;import android.widget.*;import java.nio.ByteBuffer;import java.util.*;

/** Camera2 preview guidance plus a full-resolution ImageReader JPEG still capture. */
public final class CaptureActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final int CAMERA_PERMISSION=44;private TextureView preview;private CaptureGuideView guide;private TextView status;private Button capture;private CameraDevice camera;private CameraCaptureSession session;private ImageReader stillReader;private HandlerThread thread;private Handler handler;private volatile boolean analysing,capturing;private Size previewSize,stillSize;private CameraCharacteristics characteristics;
    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(ui());if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION);}
    private View ui(){FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);preview=new TextureView(this);preview.setSurfaceTextureListener(this);root.addView(preview,new FrameLayout.LayoutParams(-1,-1));guide=new CaptureGuideView(this);root.addView(guide,new FrameLayout.LayoutParams(-1,-1));LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setGravity(Gravity.CENTER);controls.setPadding(18,12,18,20);controls.setBackgroundColor(0xCC08111F);status=new TextView(this);status.setText("Keep the full dial in the guide and hold the phone far enough away to focus");status.setTextColor(Color.WHITE);status.setTextSize(16);status.setGravity(Gravity.CENTER);controls.addView(status,new LinearLayout.LayoutParams(-1,70));capture=new Button(this);capture.setText("Capture anyway");capture.setAllCaps(false);capture.setOnClickListener(v->captureStill());controls.addView(capture,new LinearLayout.LayoutParams(-1,58));root.addView(controls,new FrameLayout.LayoutParams(-1,158,Gravity.BOTTOM));return root;}
    @Override protected void onResume(){super.onResume();thread=new HandlerThread("camera-capture");thread.start();handler=new Handler(thread.getLooper());if(preview.isAvailable())openCamera();}
    @Override protected void onPause(){closeCamera();if(thread!=null)thread.quitSafely();thread=null;handler=null;super.onPause();}

    /** Prefer the logical/main rear camera, falling back to the largest rear sensor instead of arbitrary camera-id order. */
    private String chooseRearCamera(CameraManager manager)throws CameraAccessException{
        String best=null;long bestScore=Long.MIN_VALUE;
        for(String id:manager.getCameraIdList()){
            CameraCharacteristics c=manager.getCameraCharacteristics(id);Integer facing=c.get(CameraCharacteristics.LENS_FACING);if(facing==null||facing!=CameraCharacteristics.LENS_FACING_BACK)continue;
            long score=0;int[] caps=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);if(caps!=null)for(int cap:caps)if(cap==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA){score+=1_000_000_000L;break;}
            Rect active=c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);if(active!=null)score+=(long)active.width()*active.height();
            Float minFocus=c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);if(minFocus!=null&&minFocus>0)score+=10_000_000L;
            if(best==null||score>bestScore){best=id;bestScore=score;characteristics=c;}
        }
        return best;
    }

    private void openCamera(){if(camera!=null||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;try{CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);String chosen=chooseRearCamera(manager);if(chosen==null)throw new IllegalStateException("No rear camera");android.hardware.camera2.params.StreamConfigurationMap map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map==null)throw new IllegalStateException("No camera stream configuration");stillSize=CameraCaptureConfig.chooseStill(map.getOutputSizes(android.graphics.ImageFormat.JPEG));previewSize=CameraCaptureConfig.choosePreview(map.getOutputSizes(SurfaceTexture.class),stillSize,preview.getWidth(),preview.getHeight());stillReader=ImageReader.newInstance(stillSize.getWidth(),stillSize.getHeight(),android.graphics.ImageFormat.JPEG,2);stillReader.setOnImageAvailableListener(this::onStillAvailable,handler);manager.openCamera(chosen,new CameraDevice.StateCallback(){public void onOpened(CameraDevice c){camera=c;startPreview();}public void onDisconnected(CameraDevice c){c.close();camera=null;}public void onError(CameraDevice c,int e){c.close();camera=null;show("Camera unavailable");}},handler);}catch(Exception e){show("Camera unavailable: "+e.getMessage());}}

    private void applyPhotoControls(CaptureRequest.Builder request){
        request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);request.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO);
        if(Build.VERSION.SDK_INT>=30){Range<Float> zr=characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);if(zr!=null){float z=Math.max(zr.getLower(),Math.min(1.5f,zr.getUpper()));request.set(CaptureRequest.CONTROL_ZOOM_RATIO,z);}}
    }

    private void startPreview(){try{SurfaceTexture texture=preview.getSurfaceTexture();texture.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());configurePreviewTransform(preview.getWidth(),preview.getHeight());Surface surface=new Surface(texture);CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(surface);applyPhotoControls(request);camera.createCaptureSession(Arrays.asList(surface,stillReader.getSurface()),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){session=s;try{s.setRepeatingRequest(request.build(),null,handler);handler.post(analyseLoop);}catch(Exception e){show("Camera preview unavailable");}}public void onConfigureFailed(CameraCaptureSession s){show("Camera preview unavailable");}},handler);}catch(Exception e){show("Camera preview unavailable");}}

    /** Aspect-preserving centre crop. Never use non-uniform FILL scaling because it makes a round watch appear oval. */
    private void configurePreviewTransform(int width,int height){
        if(previewSize==null||width==0||height==0)return;
        Integer sensor=characteristics==null?null:characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);int sensorDeg=sensor==null?90:sensor;
        int displayRotation=getWindowManager().getDefaultDisplay().getRotation();int displayDeg=displayRotation==Surface.ROTATION_90?90:displayRotation==Surface.ROTATION_180?180:displayRotation==Surface.ROTATION_270?270:0;
        int relative=(sensorDeg-displayDeg+360)%360;boolean swap=relative==90||relative==270;
        float bufferW=swap?previewSize.getHeight():previewSize.getWidth();float bufferH=swap?previewSize.getWidth():previewSize.getHeight();
        float scale=Math.max(width/bufferW,height/bufferH);float scaledW=bufferW*scale,scaledH=bufferH*scale;
        Matrix matrix=new Matrix();
        matrix.setScale(scaledW/width,scaledH/height,width/2f,height/2f);
        if(relative!=0)matrix.postRotate(relative,width/2f,height/2f);
        preview.setTransform(matrix);
    }

    private final Runnable analyseLoop=new Runnable(){public void run(){if(handler==null||camera==null)return;if(!analysing&&!capturing&&preview.isAvailable()){analysing=true;Bitmap frame=preview.getBitmap(480,480);if(frame!=null)try{CaptureQualityAnalyzer.Result r=CaptureQualityAnalyzer.analyse(frame);runOnUiThread(()->{status.setText(r.guidance);guide.setReady(r.ready);capture.setText(r.ready?"Capture":"Capture anyway");});}catch(Throwable ignored){}finally{frame.recycle();analysing=false;}}handler.postDelayed(this,650);}};
    private void captureStill(){if(capturing||camera==null||session==null||stillReader==null)return;capturing=true;capture.setEnabled(false);status.setText("Capturing full-resolution photo…");try{CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);request.addTarget(stillReader.getSurface());applyPhotoControls(request);Integer sensor=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);Integer facing=characteristics.get(CameraCharacteristics.LENS_FACING);request.set(CaptureRequest.JPEG_ORIENTATION,CameraCaptureConfig.jpegOrientation(sensor==null?0:sensor,getWindowManager().getDefaultDisplay().getRotation(),facing!=null&&facing==CameraCharacteristics.LENS_FACING_FRONT));session.capture(request.build(),new CameraCaptureSession.CaptureCallback(){@Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f){capturing=false;runOnUiThread(()->{capture.setEnabled(true);status.setText("Still capture failed. Try again.");});}},handler);}catch(Exception e){capturing=false;capture.setEnabled(true);status.setText("Still capture failed: "+e.getMessage());}}
    private void onStillAvailable(ImageReader reader){try(Image image=reader.acquireNextImage()){if(image==null)return;ByteBuffer buffer=image.getPlanes()[0].getBuffer();byte[] jpeg=new byte[buffer.remaining()];buffer.get(jpeg);Bitmap upright=StillImageDecoder.decodeUpright(jpeg);InspectionImageStore.setCaptured(upright);runOnUiThread(()->{setResult(RESULT_OK);finish();});}catch(Exception e){capturing=false;runOnUiThread(()->{capture.setEnabled(true);status.setText("Could not decode still photo: "+e.getMessage());});}}
    private void closeCamera(){if(handler!=null)handler.removeCallbacks(analyseLoop);if(session!=null)session.close();session=null;if(camera!=null)camera.close();camera=null;if(stillReader!=null)stillReader.close();stillReader=null;}
    private void show(String value){runOnUiThread(()->status.setText(value));}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==CAMERA_PERMISSION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED&&preview.isAvailable())openCamera();else if(r==CAMERA_PERMISSION)finish();}
    public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){openCamera();}public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){configurePreviewTransform(w,h);}public boolean onSurfaceTextureDestroyed(SurfaceTexture s){return true;}public void onSurfaceTextureUpdated(SurfaceTexture s){}
}
