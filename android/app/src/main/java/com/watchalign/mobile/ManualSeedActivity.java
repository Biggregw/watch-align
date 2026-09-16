package com.watchalign.mobile;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dedicated interactive 3-point touch seed activity (Center, 12, 6). */
public class ManualSeedActivity extends Activity {
    private Bitmap watchBitmap;
    private final List<PointF> tappedImagePoints = new ArrayList<>();
    private TextView promptText, statsText;
    private Button applyButton, resetButton;
    private TouchOverlayView touchOverlayView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8, 17, 31));
        getWindow().setNavigationBarColor(Color.rgb(8, 17, 31));

        watchBitmap = InspectionImageStore.baseBitmap;
        if (watchBitmap == null) {
            Toast.makeText(this, "No watch image available for 3-point seeding", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 17, 31));

        touchOverlayView = new TouchOverlayView(this);
        root.addView(touchOverlayView, new FrameLayout.LayoutParams(-1, -1));

        // Top Navigation Bar
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(8), dp(12), dp(8));
        top.setBackgroundColor(0xCC08111F);

        Button back = new Button(this);
        back.setText("Back");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(76), dp(46)));

        TextView title = new TextView(this);
        title.setText("3-Point Touch Alignment Seed");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding(dp(10), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1));

        resetButton = new Button(this);
        resetButton.setText("Reset Taps");
        resetButton.setAllCaps(false);
        resetButton.setOnClickListener(v -> resetTaps());
        top.addView(resetButton, new LinearLayout.LayoutParams(dp(100), dp(46)));

        root.addView(top, new FrameLayout.LayoutParams(-1, dp(62), Gravity.TOP));

        // Bottom Controls & Status Bar
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(12));
        bottom.setBackgroundColor(0xD008111F);

        promptText = new TextView(this);
        promptText.setText("Step 1/3: Tap the DIAL CENTER");
        promptText.setTextColor(Color.rgb(50, 213, 242));
        promptText.setTextSize(15);
        promptText.setGravity(Gravity.CENTER);
        bottom.addView(promptText, new LinearLayout.LayoutParams(-1, dp(32)));

        statsText = new TextView(this);
        statsText.setText("Tap on the watch photo above to set alignment points.");
        statsText.setTextColor(Color.rgb(158, 176, 201));
        statsText.setTextSize(12);
        statsText.setGravity(Gravity.CENTER);
        bottom.addView(statsText, new LinearLayout.LayoutParams(-1, dp(28)));

        applyButton = new Button(this);
        applyButton.setText("Apply Seed & Solve Pose");
        applyButton.setAllCaps(false);
        applyButton.setBackgroundColor(Color.rgb(50, 213, 242));
        applyButton.setTextColor(Color.rgb(4, 32, 42));
        applyButton.setEnabled(false);
        applyButton.setOnClickListener(v -> applySeed());
        bottom.addView(applyButton, new LinearLayout.LayoutParams(-1, dp(50)));

        root.addView(bottom, new FrameLayout.LayoutParams(-1, dp(134), Gravity.BOTTOM));
        setContentView(root);
    }

    private void resetTaps() {
        tappedImagePoints.clear();
        updateStepUi();
        touchOverlayView.invalidate();
    }

    private void updateStepUi() {
        int count = tappedImagePoints.size();
        if (count == 0) {
            promptText.setText("Step 1/3: Tap the DIAL CENTER");
            promptText.setTextColor(Color.rgb(50, 213, 242));
            statsText.setText("Tap the exact center of the watch dial.");
            applyButton.setEnabled(false);
        } else if (count == 1) {
            promptText.setText("Step 2/3: Tap the 12 O'CLOCK MARKER");
            promptText.setTextColor(Color.rgb(255, 75, 75));
            statsText.setText("Tap the top 12 o'clock marker or triangle tip.");
            applyButton.setEnabled(false);
        } else if (count == 2) {
            promptText.setText("Step 3/3: Tap the 6 O'CLOCK MARKER");
            promptText.setTextColor(Color.rgb(75, 150, 255));
            statsText.setText("Tap the bottom 6 o'clock marker.");
            applyButton.setEnabled(false);
        } else {
            promptText.setText("All 3 Points Set!");
            promptText.setTextColor(Color.rgb(127, 225, 170));
            double[] seed = calculateSeedParams();
            if (seed != null) {
                statsText.setText(String.format(Locale.US, "Center: (%.0f, %.0f)  Radius: %.1f px  Roll: %+.2f°",
                        seed[0], seed[1], seed[2], seed[3]));
                applyButton.setEnabled(true);
            }
        }
    }

    private double[] calculateSeedParams() {
        if (tappedImagePoints.size() < 3) return null;
        PointF p0 = tappedImagePoints.get(0);
        PointF p1 = tappedImagePoints.get(1);
        PointF p2 = tappedImagePoints.get(2);

        double cx = p0.x;
        double cy = p0.y;
        double r1 = Math.hypot(p1.x - cx, p1.y - cy);
        double r2 = Math.hypot(p2.x - cx, p2.y - cy);
        double r = (r1 + r2) / 2.0;

        double clockAngle = Math.toDegrees(Math.atan2(p1.x - cx, -(p1.y - cy)));
        if (clockAngle < 0) clockAngle += 360.0;
        double rollDeg = clockAngle <= 180.0 ? clockAngle : clockAngle - 360.0;

        return new double[]{cx, cy, r, rollDeg};
    }

    private void applySeed() {
        double[] seed = calculateSeedParams();
        if (seed == null) return;

        InspectionImageStore.manualCx = seed[0];
        InspectionImageStore.manualCy = seed[1];
        InspectionImageStore.manualR = seed[2];
        InspectionImageStore.manualRoll = seed[3];
        InspectionImageStore.hasManualSeed = true;

        setResult(RESULT_OK);
        Toast.makeText(this, "Manual 3-point seed applied!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private class TouchOverlayView extends View {
        private final Paint paintCenter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paint12 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paint6 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TouchOverlayView(Context context) {
            super(context);
            paintCenter.setColor(Color.rgb(50, 213, 242));
            paintCenter.setStrokeWidth(4f);
            paintCenter.setStyle(Paint.Style.STROKE);

            paint12.setColor(Color.rgb(255, 75, 75));
            paint12.setStrokeWidth(4f);
            paint12.setStyle(Paint.Style.STROKE);

            paint6.setColor(Color.rgb(75, 150, 255));
            paint6.setStrokeWidth(4f);
            paint6.setStyle(Paint.Style.STROKE);

            paintLine.setColor(Color.WHITE);
            paintLine.setStrokeWidth(3f);
            paintLine.setStyle(Paint.Style.STROKE);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (watchBitmap == null) return;

            int vw = getWidth(), vh = getHeight();
            int bw = watchBitmap.getWidth(), bh = watchBitmap.getHeight();
            if (vw <= 0 || vh <= 0 || bw <= 0 || bh <= 0) return;

            float scale = Math.min((float) vw / bw, (float) vh / bh);
            float dx = (vw - bw * scale) / 2f;
            float dy = (vh - bh * scale) / 2f;

            canvas.drawBitmap(watchBitmap, null, new android.graphics.RectF(dx, dy, dx + bw * scale, dy + bh * scale), null);

            for (int i = 0; i < tappedImagePoints.size(); i++) {
                PointF imgP = tappedImagePoints.get(i);
                float vx = dx + imgP.x * scale;
                float vy = dy + imgP.y * scale;

                Paint p = (i == 0) ? paintCenter : (i == 1) ? paint12 : paint6;
                canvas.drawCircle(vx, vy, 18f, p);
                canvas.drawCircle(vx, vy, 4f, p);

                if (i > 0) {
                    PointF cP = tappedImagePoints.get(0);
                    float cvx = dx + cP.x * scale;
                    float cvy = dy + cP.y * scale;
                    canvas.drawLine(cvx, cvy, vx, vy, paintLine);
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (tappedImagePoints.size() >= 3) return true;

                int vw = getWidth(), vh = getHeight();
                int bw = watchBitmap.getWidth(), bh = watchBitmap.getHeight();
                float scale = Math.min((float) vw / bw, (float) vh / bh);
                float dx = (vw - bw * scale) / 2f;
                float dy = (vh - bh * scale) / 2f;

                float touchX = event.getX();
                float touchY = event.getY();

                float imgX = (touchX - dx) / scale;
                float imgY = (touchY - dy) / scale;

                if (imgX >= 0 && imgX <= bw && imgY >= 0 && imgY <= bh) {
                    tappedImagePoints.add(new PointF(imgX, imgY));
                    updateStepUi();
                    invalidate();
                    performClick();
                }
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
