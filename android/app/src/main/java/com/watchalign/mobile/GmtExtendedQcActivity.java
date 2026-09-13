package com.watchalign.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.watchalign.mobile.qc.PerspectiveConfidenceService;

/** Full automatic/advisory GMT checks alongside the manual rectified triangle workflow. */
public final class GmtExtendedQcActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8,17,31));
        getWindow().setNavigationBarColor(Color.rgb(8,17,31));

        Bitmap base = InspectionImageStore.baseBitmap;
        PerspectiveMasterRenderer.Pose pose = InspectionImageStore.alignedPose;
        String model = InspectionImageStore.alignedModelRef;
        if (base == null || pose == null || model == null) { finish(); return; }

        PerspectiveConfidenceService.Assessment perspective = PerspectiveConfidenceService.assess(
                pose.anchor12X, pose.anchor12Y,
                pose.anchor3X, pose.anchor3Y,
                pose.anchor6X, pose.anchor6Y,
                pose.anchor9X, pose.anchor9Y);
        QcExtendedAnalyzer.Result ext = QcExtendedAnalyzer.analyse(base, model);
        GmtIndexAutoAnalyzer.Result indices = GmtIndexAutoAnalyzer.analyse(base, pose);
        String guardedReport = GmtExtendedQcReportGuard.sanitize(ext.report, perspective);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(8,17,31));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(-1,-2));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("Back"); back.setOnClickListener(v -> finish());
        TextView title = text("GMT QC checks", 21); title.setPadding(dp(12),0,0,0);
        top.addView(back, new LinearLayout.LayoutParams(dp(78),dp(48)));
        top.addView(title, new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(top);

        root.addView(text("Per-index positions and fine-QC confidence use the corrected 12/3/6/9 perspective pose. Legacy photo-sensitive component fits are withheld when their geometry is implausible.", 12));

        // Show only the plausibility-gated index overlay here. The legacy extended overlay can draw
        // boxes around contaminated detections even when its text verdict is correctly advisory.
        Bitmap shown = indices.annotated != null ? indices.annotated : base;
        if (shown != null) {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setImageBitmap(shown);
            root.addView(image, new LinearLayout.LayoutParams(-1,-2));
        }

        TextView idx = text(indices.summary, 12); idx.setPadding(0,dp(12),0,dp(10)); root.addView(idx);
        TextView report = text(guardedReport, 12); root.addView(report);

        TextView caveat = text("Measurements are reference/QC evidence, not Rolex factory tolerances and not an authenticity verdict.", 11);
        caveat.setPadding(0,dp(16),0,dp(24)); root.addView(caveat);
        setContentView(scroll);
    }

    private Button button(String s) { Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private TextView text(String s,int sp) { TextView t=new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(sp); return t; }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
}
