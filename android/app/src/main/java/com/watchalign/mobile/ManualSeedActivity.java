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
import android.widget.Magnifier;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Precision assisted alignment using opposite points on the dial edge at 12 and 6. */
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
            Toast.makeText(this, "No watch image available for assisted alignment", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 17, 31));

        touchOverlayView = new TouchOverlayView(this);
        root.addView(touchOverlayView, new FrameLayout.LayoutParams(-1, -1));

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
        title.setText("2-Point Precision Alignment");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding(dp(10), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1));

        resetButton = new Button(this);
        resetButton.setText("Reset");
        resetButton.setAllCaps(false);
        resetButton.setOnClickListener(v -> resetTaps());
        top.addView(resetButton, new LinearLayout.LayoutParams(dp(82), dp(46)));
        root.addView(top, new FrameLayout.LayoutParams(-1, dp(62), Gravity.TOP));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(12));
        bottom.setBackgroundColor(0xD008111F);

        promptText = new TextView(this);
        promptText.setText("Step 1/2: Set the DIAL EDGE at 12");
        promptText.setTextColor(Color.rgb(255, 75, 75));
        promptText.setTextSize(15);
        promptText.setGravity(Gravity.CENTER);
        bottom.addView(promptText, new LinearLayout.LayoutParams(-1, dp(32)));

        statsText = new TextView(this);
        statsText.setText("Press near the dial/rehaut boundary, drag under the magnifier, then release.");
        statsText.setTextColor(Color.rgb(158, 176, 201));
        statsText.setTextSize(12);
        statsText.setGravity(Gravity.CENTER);
        bottom.addView(statsText, new LinearLayout.LayoutParams(-1, dp(42)));

        applyButton = new Button(this);
        applyButton.setText("Apply Alignment & Run QC");
        applyButton.setAllCaps(false);
        applyButton.setBackgroundColor(Color.rgb(50, 213, 242));
        applyButton.setTextColor(Color.rgb(4, 32, 42));
        applyButton.setEnabled(false);
        applyButton.setOnClickListener(v -> applySeed());
        bottom.addView(applyButton, new LinearLayout.LayoutParams(-1, dp(50)));

        root.addView(bottom, new FrameLayout.LayoutParams(-1, dp(148), Gravity.BOTTOM));
        setContentView(root);
    }

    private void resetTaps() {
        tappedImagePoints.clear();
        updateStepUi();
        touchOverlayView.clearPending();
        touchOverlayView.invalidate();
    }

    private void updateStepUi() {
        int count = tappedImagePoints.size();
        if (count == 0) {
            promptText.setText("Step 1/2: Set the DIAL EDGE at 12");
            promptText.setTextColor(Color.rgb(255, 75, 75));
            statsText.setText("Use the dial/rehaut boundary, not the 12 marker. Press, drag with magnifier, release.");
            applyButton.setEnabled(false);
        } else if (count == 1) {
            promptText.setText("Step 2/2: Set the DIAL EDGE at 6");
            promptText.setTextColor(Color.rgb(75, 150, 255));
            statsText.setText("Set the opposite dial/rehaut boundary at 6 o'clock. The centre is calculated automatically.");
            applyButton.setEnabled(false);
        } else {
            promptText.setText("Alignment points set");
            promptText.setTextColor(Color.rgb(127, 225, 170));
            double[] seed = calculateSeedParams();
            if (seed != null) {
                statsText.setText(String.format(Locale.US, "Calculated centre (%.0f, %.0f) · radius %.1f px · roll %+.2f°",
                        seed[0], seed[1], seed[2], seed[3]));
                applyButton.setEnabled(true);
            } else {
                statsText.setText("Those points are too close together. Reset and try again.");
                applyButton.setEnabled(false);
            }
        }
    }

    private double[] calculateSeedParams() {
        if (tappedImagePoints.size() < 2) return null;
        PointF p12 = tappedImagePoints.get(0);
        PointF p6 = tappedImagePoints.get(1);
        return ManualSeedMath.fromOppositeDialEdges(p12.x, p12.y, p6.x, p6.y);
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
        Toast.makeText(this, "Assisted dial alignment applied", Toast.LENGTH_SHORT).show();
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
        private final Paint paintPending = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Magnifier magnifier;
        private PointF pendingImagePoint;

        TouchOverlayView(Context context) {
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

            paintPending.setColor(Color.YELLOW);
            paintPending.setStrokeWidth(3f);
            paintPending.setStyle(Paint.Style.STROKE);

            magnifier = new Magnifier(this);
        }

        void clearPending() {
            pendingImagePoint = null;
            try { magnifier.dismiss(); } catch (Throwable ignored) {}
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
                Paint p = i == 0 ? paint12 : paint6;
                canvas.drawCircle(vx, vy, 18f, p);
                canvas.drawLine(vx - 12f, vy, vx + 12f, vy, p);
                canvas.drawLine(vx, vy - 12f, vx, vy + 12f, p);
            }

            if (tappedImagePoints.size() == 2) {
                double[] seed = calculateSeedParams();
                if (seed != null) {
                    float cx = dx + (float) seed[0] * scale;
                    float cy = dy + (float) seed[1] * scale;
                    PointF p12 = tappedImagePoints.get(0);
                    PointF p6 = tappedImagePoints.get(1);
                    canvas.drawLine(dx + p12.x * scale, dy + p12.y * scale,
                            dx + p6.x * scale, dy + p6.y * scale, paintLine);
                    canvas.drawCircle(cx, cy, 14f, paintCenter);
                    canvas.drawLine(cx - 10f, cy, cx + 10f, cy, paintCenter);
                    canvas.drawLine(cx, cy - 10f, cx, cy + 10f, paintCenter);
                }
            }

            if (pendingImagePoint != null) {
                float px = dx + pendingImagePoint.x * scale;
                float py = dy + pendingImagePoint.y * scale;
                canvas.drawCircle(px, py, 16f, paintPending);
                canvas.drawLine(px - 12f, py, px + 12f, py, paintPending);
                canvas.drawLine(px, py - 12f, px, py + 12f, paintPending);
            }
        }

        private PointF imagePoint(float touchX, float touchY) {
            int vw = getWidth(), vh = getHeight();
            int bw = watchBitmap.getWidth(), bh = watchBitmap.getHeight();
            float scale = Math.min((float) vw / bw, (float) vh / bh);
            float dx = (vw - bw * scale) / 2f;
            float dy = (vh - bh * scale) / 2f;
            float imgX = (touchX - dx) / scale;
            float imgY = (touchY - dy) / scale;
            if (imgX < 0 || imgX > bw || imgY < 0 || imgY > bh) return null;
            return new PointF(imgX, imgY);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (tappedImagePoints.size() >= 2) return true;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                pendingImagePoint = imagePoint(event.getX(), event.getY());
                if (pendingImagePoint != null) {
                    try { magnifier.show(event.getX(), event.getY()); } catch (Throwable ignored) {}
                    invalidate();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                PointF p = imagePoint(event.getX(), event.getY());
                try { magnifier.dismiss(); } catch (Throwable ignored) {}
                pendingImagePoint = null;
                if (p != null) {
                    tappedImagePoints.add(p);
                    updateStepUi();
                    invalidate();
                    performClick();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                clearPending();
                invalidate();
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
