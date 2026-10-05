package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.PointF;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

/**
 * Alpha91 research-only assisted pose: four user-labelled points on the OUTER BLACK DIAL edge.
 * Order is 12, 3, 6, 9. The four points define one unrestricted planar homography.
 * Applied markers, hands, date, text, bezel and rehaut never participate in the fit.
 */
final class AssistedCardinalOverlay {
    private AssistedCardinalOverlay() {}

    static Bitmap build(Bitmap input, List<PointF> cardinalPoints) {
        if (input == null || cardinalPoints == null || cardinalPoints.size() != 4) return null;
        PointF p12 = cardinalPoints.get(0), p3 = cardinalPoints.get(1),
                p6 = cardinalPoints.get(2), p9 = cardinalPoints.get(3);
        if (!validPoint(p12) || !validPoint(p3) || !validPoint(p6) || !validPoint(p9)) return null;

        // Reject obviously degenerate / crossed selections before asking OpenCV for H.
        double area = polygonArea(p12, p3, p6, p9);
        if (!Double.isFinite(area) || Math.abs(area) < 100.0) return null;

        MatOfPoint2f src = new MatOfPoint2f(
                new Point(0.0, -1.0), // 12
                new Point(1.0,  0.0), // 3
                new Point(0.0,  1.0), // 6
                new Point(-1.0, 0.0)  // 9
        );
        MatOfPoint2f dst = new MatOfPoint2f(
                new Point(p12.x, p12.y), new Point(p3.x, p3.y),
                new Point(p6.x, p6.y), new Point(p9.x, p9.y)
        );
        Mat h = null;
        try {
            h = Imgproc.getPerspectiveTransform(src, dst);
            if (h == null || h.empty()) return null;
            return warpOutline(input.getWidth(), input.getHeight(), h);
        } finally {
            if (h != null) h.release();
            src.release();
            dst.release();
        }
    }

    private static boolean validPoint(PointF p) {
        return p != null && Float.isFinite(p.x) && Float.isFinite(p.y);
    }

    private static double polygonArea(PointF a, PointF b, PointF c, PointF d) {
        PointF[] p = {a,b,c,d};
        double s = 0;
        for (int i=0;i<4;i++) {
            PointF q=p[(i+1)%4];
            s += p[i].x*q.y - q.x*p[i].y;
        }
        return 0.5*s;
    }

    private static Bitmap warpOutline(int w, int h, Mat canonicalToImage) {
        Bitmap ref = BakedDialOutline.bitmap();
        Mat src = new Mat(), dst = new Mat(), n = Mat.eye(3,3, CvType.CV_64F), m = null;
        try {
            Utils.bitmapToMat(ref, src);
            double invR = 1.0 / BakedDialOutline.R;
            n.put(0,0,
                    invR,0,-BakedDialOutline.CX*invR,
                    0,invR,-BakedDialOutline.CY*invR,
                    0,0,1);
            m = multiply(canonicalToImage, n);
            Imgproc.warpPerspective(src, dst, m, new Size(w,h), Imgproc.INTER_LINEAR,
                    org.opencv.core.Core.BORDER_CONSTANT, new Scalar(0,0,0,0));
            Bitmap out = Bitmap.createBitmap(w,h, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(dst, out);
            return out;
        } finally {
            if (m != null) m.release();
            n.release(); dst.release(); src.release();
        }
    }

    private static Mat multiply(Mat a, Mat b) {
        double[] av=new double[9], bv=new double[9], cv=new double[9];
        a.get(0,0,av); b.get(0,0,bv);
        for(int r=0;r<3;r++) for(int c=0;c<3;c++) for(int k=0;k<3;k++)
            cv[r*3+c] += av[r*3+k]*bv[k*3+c];
        Mat out=new Mat(3,3,CvType.CV_64F); out.put(0,0,cv); return out;
    }
}
