package com.watchalign.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Dedicated non-scrolling image inspector so pinch/pan gestures have no competing ScrollView parent. */
public class FullscreenInspectActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8,17,31));
        getWindow().setNavigationBarColor(Color.rgb(8,17,31));

        Bitmap bitmap=InspectionImageStore.bitmap;
        if(bitmap==null){ finish(); return; }

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(8,17,31));
        ZoomableImageView image=new ZoomableImageView(this);image.setBackgroundColor(Color.rgb(8,17,31));image.setImageBitmap(bitmap);
        root.addView(image,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(10),dp(8));top.setBackgroundColor(0xAA08111F);
        Button back=new Button(this);back.setText("Back");back.setAllCaps(false);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(86),dp(46)));
        TextView title=new TextView(this);title.setText(InspectionImageStore.title==null?"Inspection":InspectionImageStore.title);title.setTextColor(Color.WHITE);title.setTextSize(17);title.setPadding(dp(12),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=new Button(this);reset.setText("Reset");reset.setAllCaps(false);reset.setOnClickListener(v->image.resetZoom());top.addView(reset,new LinearLayout.LayoutParams(dp(86),dp(46)));
        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(62),Gravity.TOP);root.addView(top,topLp);

        TextView hint=new TextView(this);hint.setText("Pinch to zoom · drag to pan · double-tap zoom");hint.setTextColor(Color.WHITE);hint.setTextSize(13);hint.setGravity(Gravity.CENTER);hint.setBackgroundColor(0x9908111F);hint.setPadding(dp(8),dp(6),dp(8),dp(6));
        FrameLayout.LayoutParams hintLp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(42),Gravity.BOTTOM);root.addView(hint,hintLp);

        setContentView(root);
    }

    @Override protected void onDestroy(){ super.onDestroy(); }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
