import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** Desktop copy of MainActivity.fit() (v1.3, same constants) on BufferedImage, plus the WebView canvas path (max 1900 px). */
public class FitExp {
    static BufferedImage scale(BufferedImage s,int w,int h){BufferedImage d=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=d.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(s,0,0,w,h,null);g.dispose();return d;}
    static int cl(int x,int a,int b){return Math.max(a,Math.min(b,x));}
    static String fit(BufferedImage src){
        int ow=src.getWidth(),oh=src.getHeight();if(Math.min(ow,oh)<500)return "REJECT small "+ow+"x"+oh;
        float sc=Math.min(1f,850f/Math.max(ow,oh));BufferedImage b=sc<1?scale(src,Math.round(ow*sc),Math.round(oh*sc)):src;int w=b.getWidth(),h=b.getHeight(),mn=Math.min(w,h);
        int[] g=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=b.getRGB(x,y);g[y*w+x]=(77*((c>>16)&255)+150*((c>>8)&255)+29*(c&255))>>8;}
        int bx=w/2,by=h/2,br=0;double bs=-1;
        int sx=Math.max(10,w/24),sy=Math.max(10,h/24),r0=(int)(mn*.12),r1=(int)(mn*.43),rs=Math.max(6,mn/55);
        for(int cy=(int)(h*.30);cy<=(int)(h*.70);cy+=sy)for(int cx=(int)(w*.36);cx<=(int)(w*.64);cx+=sx)for(int r=r0;r<=r1;r+=rs){
            if(cx-r<3||cy-r<3||cx+r>=w-3||cy+r>=h-3)continue;double sum=0;int strong=0;
            for(int k=0;k<64;k++){double a=2*Math.PI*k/64;int x1=cl((int)Math.round(cx+(r-3)*Math.cos(a)),0,w-1),y1=cl((int)Math.round(cy+(r-3)*Math.sin(a)),0,h-1),x2=cl((int)Math.round(cx+(r+3)*Math.cos(a)),0,w-1),y2=cl((int)Math.round(cy+(r+3)*Math.sin(a)),0,h-1);int d=Math.abs(g[y2*w+x2]-g[y1*w+x1]);sum+=Math.min(80,d);if(d>=12)strong++;}
            double sup=strong/64.0,off=Math.hypot(cx-w/2.0,cy-h/2.0)/mn,score=(sum/64)*(0.35+sup)+r*.018-off*12;if(sup>=.42&&score>bs){bs=score;bx=cx;by=cy;br=r;}
        }
        if(br==0)return "REJECT no circle";
        double[] er=new double[72];int good=0;
        for(int k=0;k<72;k++){double a=2*Math.PI*k/72;int best=br,bd=-1;for(int r=(int)(br*.78);r<=(int)(br*1.18);r+=2){int x1=cl((int)Math.round(bx+(r-2)*Math.cos(a)),0,w-1),y1=cl((int)Math.round(by+(r-2)*Math.sin(a)),0,h-1),x2=cl((int)Math.round(bx+(r+2)*Math.cos(a)),0,w-1),y2=cl((int)Math.round(by+(r+2)*Math.sin(a)),0,h-1),d=Math.abs(g[y2*w+x2]-g[y1*w+x1]);if(d>bd){bd=d;best=r;}}er[k]=best;if(bd>=10)good++;}
        double hax=er[0]+er[36],vax=er[18]+er[54],d1=er[9]+er[45],d2=er[27]+er[63],max=Math.max(Math.max(hax,vax),Math.max(d1,d2)),min=Math.min(Math.min(hax,vax),Math.min(d1,d2)),axis=min/max,support=good/72.0;
        double opp=Math.max(Math.abs(er[0]-er[36])/br,Math.max(Math.abs(er[18]-er[54])/br,Math.max(Math.abs(er[9]-er[45])/br,Math.abs(er[27]-er[63])/br)));
        long sum=0;int n=0,r=(int)(br*.76),rr=r*r;for(int y=Math.max(2,by-r);y<Math.min(h-2,by+r);y+=3){int dy=y-by;for(int x=Math.max(2,bx-r);x<Math.min(w-2,bx+r);x+=3){int dx=x-bx;if(dx*dx+dy*dy>rr)continue;int i=y*w+x,lap=Math.abs(4*g[i]-g[i-1]-g[i+1]-g[i-w]-g[i+w]);sum+=Math.min(255,lap);n++;}}
        double sharp=n==0?0:sum/(double)n,rad=br/sc;
        java.util.List<String> why=new ArrayList<>();if(axis<.885)why.add("axis");if(support<.62)why.add("support");if(opp>.20)why.add("opp");if(sharp<5)why.add("sharp");if(rad<130)why.add("radius");
        return String.format(Locale.US,"%s axis=%.3f support=%.2f opp=%.3f sharp=%.1f R=%.0f c=(%.2f,%.2f)",why.isEmpty()?"KEEP":"REJECT "+String.join("+",why),axis,support,opp,sharp,rad,bx/(double)w,by/(double)h);
    }
    public static void main(String[] a)throws Exception{
        Map<String,Integer> tally=new TreeMap<>();
        for(String p:a){BufferedImage im=ImageIO.read(new File(p));
            // WebView canvas path: downscale to max 1900 first
            double s=Math.min(1,1900.0/Math.max(im.getWidth(),im.getHeight()));BufferedImage c=s<1?scale(im,(int)Math.round(im.getWidth()*s),(int)Math.round(im.getHeight()*s)):im;
            String r=fit(c);tally.merge(r.split(" ")[0]+" "+(r.startsWith("REJECT")?r.split(" ")[1]:""),1,Integer::sum);
            System.out.println(new File(p).getName()+" "+im.getWidth()+"x"+im.getHeight()+" "+r);}
        System.out.println(tally);
    }
}
