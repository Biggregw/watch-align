package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Deliberately small overlay-only app used to prove the minute-track perspective idea.
 * No QC measurements, scores, tolerances or verdicts are produced here.
 */
public class PerspectiveOverlayPocActivity extends Activity {
    private static final int PICK_PHOTO = 2101;
    private static final int ALIGN_DIAL = 2102;
    private static final int BG = Color.rgb(8,17,31);
    private static final int ACCENT = Color.rgb(50,213,242);
    private static final int MUTED = Color.rgb(158,176,201);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Bitmap watchBitmap;
    private ImageView preview;
    private TextView status;
    private Button alignButton, inspectButton;
    private Bitmap lastOverlay;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!OpenCVLoader.initLocal()) Toast.makeText(this,"OpenCV could not start",Toast.LENGTH_LONG).show();
        setContentView(buildUi());
    }

    private View buildUi() {
        int pad = dp(16);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(pad,pad,pad,pad);
        scroll.addView(root,new ViewGroup.LayoutParams(-1,-1));

        root.addView(text("Perspective Overlay POC",28,Color.WHITE));
        root.addView(text("Minute-track driven · overlay only · no QC measurements",14,ACCENT));
        root.addView(text("Choose a sharp GMT photo with the full dial visible. Then set the dial edge at 12 and 6. Those two safe points establish only the starting centre/scale/direction; the 48 minor minute ticks drive the projective overlay. Hour markers and the 12 triangle are not used to make themselves fit.",13,MUTED),lp(-1,-2,10));

        Button pick = button("Choose GMT photo"); pick.setOnClickListener(v->pickPhoto()); root.addView(pick,lp(-1,dp(52),10));
        alignButton = button("Set 12 & 6 dial edge, then build overlay"); alignButton.setBackgroundColor(ACCENT); alignButton.setTextColor(Color.rgb(4,32,42));
        alignButton.setEnabled(false); alignButton.setOnClickListener(v->startAlignment()); root.addView(alignButton,lp(-1,dp(54),8));
        inspectButton = button("Inspect last overlay"); inspectButton.setEnabled(false); inspectButton.setOnClickListener(v->openInspector()); root.addView(inspectButton,lp(-1,dp(48),6));

        status = text("Choose a photo to begin.",14,MUTED); root.addView(status,lp(-1,-2,12));
        preview = new ImageView(this); preview.setAdjustViewBounds(true); preview.setScaleType(ImageView.ScaleType.FIT_CENTER); preview.setBackgroundColor(BG);
        root.addView(preview,lp(-1,-2,8));
        root.addView(text("In the inspector: cyan is the projected canonical dial; yellow dots are the detected minor minute-tick anchors. Use the opacity slider or hold Blink. Reset restores the automatic result before any optional fine nudge.",12,MUTED),lp(-1,-2,12));
        return scroll;
    }

    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_PHOTO);
    }

    private void startAlignment() {
        if (watchBitmap == null) return;
        InspectionImageStore.clearManualSeed();
        InspectionImageStore.baseBitmap = watchBitmap;
        status.setText("Set the DIAL EDGE at 12, then the opposite DIAL EDGE at 6. The existing alignment screen may mention QC, but this POC will build only the overlay.");
        startActivityForResult(new Intent(this,ManualSeedActivity.class),ALIGN_DIAL);
    }

    private void buildOverlay() {
        if (watchBitmap == null || !InspectionImageStore.hasManualSeed) return;
        final Bitmap photo = watchBitmap;
        final double cx = InspectionImageStore.manualCx, cy = InspectionImageStore.manualCy;
        final double r = InspectionImageStore.manualR, roll = InspectionImageStore.manualRoll;
        alignButton.setEnabled(false); inspectButton.setEnabled(false);
        status.setText("Building perspective overlay from the minor minute track…");
        worker.submit(() -> {
            MinuteTrackPerspectiveOverlay.Result result = MinuteTrackPerspectiveOverlay.build(photo,cx,cy,r,roll);
            runOnUiThread(() -> {
                alignButton.setEnabled(true);
                if (!result.valid) {
                    lastOverlay = null; inspectButton.setEnabled(false);
                    status.setText("Could not build a stable minute-track overlay: "+result.reason+". Try a sharper photo or redo the 12/6 dial-edge points.");
                    return;
                }
                lastOverlay = result.overlay; inspectButton.setEnabled(true);
                status.setText("Overlay ready. Perspective came from minor minute ticks only. Inspect whether the cyan hour markers and 12 triangle land on the real dial.");
                openInspector();
            });
        });
    }

    private void openInspector() {
        if (watchBitmap == null || lastOverlay == null) return;
        InspectionImageStore.setOverlay(watchBitmap,lastOverlay,"Minute-track perspective overlay · hold to blink");
        startActivity(new Intent(this,FullscreenInspectActivity.class));
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        if (request == ALIGN_DIAL) {
            if (result == RESULT_OK && InspectionImageStore.hasManualSeed) buildOverlay();
            else if (watchBitmap != null) status.setText("Alignment cancelled. Tap the alignment button when ready.");
            return;
        }
        super.onActivityResult(request,result,data);
        if (request != PICK_PHOTO || result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            watchBitmap = readBitmap(data.getData());
            lastOverlay = null; InspectionImageStore.clearManualSeed();
            preview.setImageBitmap(watchBitmap); alignButton.setEnabled(true); inspectButton.setEnabled(false);
            status.setText("Photo ready. Set the 12 and 6 dial edges to build the overlay.");
        } catch (Exception e) {
            status.setText("Could not read image: "+e.getMessage());
        }
    }

    private Bitmap readBitmap(Uri uri) throws Exception {
        BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in,null,opts); }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw new IllegalArgumentException("Not a readable image");
        int maxDim = Math.max(opts.outWidth,opts.outHeight), sample = 1;
        while (maxDim/(sample*2) >= 1600) sample *= 2;
        opts.inJustDecodeBounds = false; opts.inSampleSize = sample; opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b; try (InputStream in = getContentResolver().openInputStream(uri)) { b = BitmapFactory.decodeStream(in,null,opts); }
        if (b == null) throw new IllegalArgumentException("Not a readable image");
        int currentMax = Math.max(b.getWidth(),b.getHeight());
        if (currentMax <= 1600) return b.copy(Bitmap.Config.ARGB_8888,false);
        float scale = 1600f/currentMax;
        return Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*scale),Math.round(b.getHeight()*scale),true).copy(Bitmap.Config.ARGB_8888,false);
    }

    private TextView text(String s,int sp,int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); return v; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private LinearLayout.LayoutParams lp(int w,int h,int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.topMargin = dp(top); return p; }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
