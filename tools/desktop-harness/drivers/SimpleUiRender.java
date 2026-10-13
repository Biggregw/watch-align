package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** REVIEW ONLY. Desktop render of the simplified results and marker screens from the real pipeline output (overview,
 * close-ups, InspectionView text), laid out like the Android activities (dp = 2.5 px, phone width 1080 px). It is a
 * layout preview, not an Android screenshot. Usage: run.sh SimpleUiRender manifest.csv image_root out_dir [photo_id,...] */
public class SimpleUiRender {
    static final int W=1080;static final double DP=2.5;
    static final Color BG=new Color(8,17,31),CARD=new Color(17,30,50),MUTED=new Color(158,176,201),BTN=new Color(60,72,92);
    public static void main(String[] a)throws Exception{
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] c=lines.get(0).split(",",-1);int ip=-1,iid=-1;
        for(int i=0;i<c.length;i++){if(c[i].equals("local_path"))ip=i;if(c[i].equals("photo_id"))iid=i;}
        java.util.Set<String> only=a.length>3?java.util.Set.of(a[3].split(",")):null;Path out=Path.of(a[2]);Files.createDirectories(out);
        ModelSpec model=HarnessModel.spec();ModelReference ref=HarnessModel.ref();
        for(int li=1;li<lines.size();li++){
            String[] f=lines.get(li).split(",",-1);String id=f[iid];if(only!=null&&!only.contains(id))continue;
            Bitmap b=Alpha96Calib.loadAlpha96(Path.of(a[1]).resolve(f[ip]).toString());Alpha99Pipeline.Output o=b==null?null:Alpha99Pipeline.run(b,model,ref);
            if(o==null||!o.ok()){System.out.println("UI "+id+": no pose");continue;}
            for(Alpha99Findings.Finding g:o.summary.all){MarkerMismatchResearch.Result q=o.mismatch.get(g.hour);
                System.out.println("UISTATE "+id+" "+g.key+" "+g.status+" -> "+InspectionView.label(InspectionView.state(g,q))+" | "+InspectionView.area(g,q));}
            javax.imageio.ImageIO.write(main(o,model),"png",out.resolve(id+"__main.png").toFile());
            for(Alpha99Findings.Finding g:o.summary.all){
                if(g.dialWide||!o.closeups.containsKey(g.key))continue;
                InspectionView.State st=InspectionView.state(g,o.mismatch.get(g.hour));
                boolean show=st!=InspectionView.State.WITHIN||g.shape==Alpha99Findings.Shape.TRIANGLE||g.hour==9||g.hour==4;
                if(show)javax.imageio.ImageIO.write(detail(o,g),"png",out.resolve(id+"__"+g.key+".png").toFile());
            }
            System.out.println("UI "+id+" rendered");
        }
    }
    static int d(double dp){return (int)Math.round(dp*DP);}
    static Color colour(InspectionView.State st){switch(st){case CLEAR:return new Color(255,99,88);case WORTH:return new Color(255,176,50);case WITHIN:return new Color(72,209,108);case MINOR:return new Color(120,170,220);default:return new Color(160,160,168);}}
    static final class Page{BufferedImage img=new BufferedImage(W,9000,BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();int y=d(16);
        Page(){g.setColor(BG);g.fillRect(0,0,W,9000);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);}
        void text(String s,int sp,Color col,boolean bold,int top){if(s==null||s.isEmpty())return;y+=d(top);y=wrap(g,s,d(16),y,W-2*d(16),sp,col,bold);}
        void image(BufferedImage im,int top){y+=d(top);int w=W-2*d(16),h=im.getHeight()*w/im.getWidth();g.drawImage(im,d(16),y,w,h,null);y+=h;}
        void button(String s,int top){y+=d(top);g.setColor(BTN);g.fillRoundRect(d(16),y,W-2*d(16),d(48),d(6),d(6));g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,(int)(14*DP)));g.drawString(s,d(28),y+d(30));y+=d(48);}
        BufferedImage done(){return img.getSubimage(0,0,W,Math.min(9000,y+d(24)));}}
    static int wrap(Graphics2D g,String s,int x,int y,int w,int sp,Color col,boolean bold){
        Font font=new Font(Font.SANS_SERIF,bold?Font.BOLD:Font.PLAIN,(int)(sp*DP));g.setFont(font);g.setColor(col);FontMetrics fm=g.getFontMetrics();
        StringBuilder line=new StringBuilder();int lh=(int)(fm.getHeight()*1.1);
        for(String word:s.split(" ")){String t=line.length()==0?word:line+" "+word;if(fm.stringWidth(t)>w&&line.length()>0){y+=lh;g.drawString(line.toString(),x,y-fm.getDescent());line=new StringBuilder(word);}else line=new StringBuilder(t);}
        if(line.length()>0){y+=lh;g.drawString(line.toString(),x,y-fm.getDescent());}
        return y;
    }
    static BufferedImage main(Alpha99Pipeline.Output o,ModelSpec model){
        Page p=new Page();Alpha99Findings.Summary s=o.summary;
        p.text(InspectionView.headline(s,o.mismatch),26,Color.WHITE,true,0);
        p.text(InspectionView.counts(s,o.mismatch),14,MUTED,false,4);
        p.text(model.label,13,MUTED,false,2);
        if(o.overview!=null){p.image(o.overview.img,12);p.text("!! clear   ! worth a look   – not assessed   ✓ within (grey: partly assessed)   ·   tap a marker",12,MUTED,false,4);}
        List<Alpha99Findings.Finding> flagged=new ArrayList<>(),un=new ArrayList<>();
        for(Alpha99Findings.Finding f:s.all){InspectionView.State st=InspectionView.state(f,o.mismatch.get(f.hour));
            if(st==InspectionView.State.CLEAR||st==InspectionView.State.WORTH)flagged.add(f);
            else if(st==InspectionView.State.NOT_ASSESSED&&f.group==null&&o.closeups.containsKey(f.key))un.add(f);}
        if(!flagged.isEmpty()){p.text("Flagged",18,Color.WHITE,true,18);grid(p,o,flagged);}
        if(!un.isEmpty()){p.text("Not assessed",18,Color.WHITE,true,18);grid(p,o,un);}
        String grouped=s.notAssessedLine();if(!grouped.isEmpty()&&un.isEmpty()&&flagged.isEmpty())p.text(grouped,13,MUTED,false,8);
        p.button("All markers  ▸",18);p.button("Technical details  ▸",8);
        p.text(InspectionView.SHORT_DISCLAIMER,12,MUTED,false,14);
        return p.done();
    }
    static void grid(Page p,Alpha99Pipeline.Output o,List<Alpha99Findings.Finding> tiles){
        int gap=d(10),tw=(W-2*d(16)-gap)/2;
        for(int i=0;i<tiles.size();i+=2){
            p.y+=d(10);int rowH=0;
            for(int k=0;k<2&&i+k<tiles.size();k++){
                Alpha99Findings.Finding f=tiles.get(i+k);MarkerMismatchResearch.Result q=o.mismatch.get(f.hour);InspectionView.State st=InspectionView.state(f,q);
                int x=d(16)+k*(tw+gap);BufferedImage t=new BufferedImage(tw,tw+d(110),BufferedImage.TYPE_INT_RGB);Graphics2D g=t.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setColor(CARD);g.fillRect(0,0,tw,t.getHeight());
                int iw=tw-2*d(8);g.drawImage(o.closeups.get(f.key).img,d(8),d(8),iw,iw,null);
                int yy=d(8)+iw+d(6);yy=wrap(g,f.title,d(8),yy,iw,15,Color.WHITE,true);yy=wrap(g,InspectionView.label(st),d(8),yy+d(2),iw,13,colour(st),true);
                List<String> lines=InspectionView.findings(f);String one=!lines.isEmpty()?lines.get(0):InspectionView.explain(f,q);
                if(one!=null)yy=wrap(g,one,d(8),yy+d(2),iw,13,Color.WHITE,false);
                p.g.drawImage(t.getSubimage(0,0,tw,Math.min(t.getHeight(),yy+d(10))),x,p.y,null);rowH=Math.max(rowH,Math.min(t.getHeight(),yy+d(10)));
            }
            p.y+=rowH;
        }
    }
    static BufferedImage detail(Alpha99Pipeline.Output o,Alpha99Findings.Finding f){
        Page p=new Page();MarkerMismatchResearch.Result q=o.mismatch.get(f.hour);InspectionView.State st=InspectionView.state(f,q);
        p.text(f.title,24,Color.WHITE,true,0);p.text(InspectionView.label(st),20,colour(st),true,4);p.text(InspectionView.explain(f,q),15,Color.WHITE,false,4);
        p.image(o.closeups.get(f.key).img,12);
        p.text((q!=null&&q.trusted()?InspectionView.LEGEND_MEASURED:InspectionView.LEGEND_WITHHELD)+"  Tap to zoom.",12,MUTED,false,4);
        String area=InspectionView.area(f,q);
        if(area!=null){p.text(area,18,Color.WHITE,true,14);p.text(InspectionView.areaParts(q),14,MUTED,false,2);p.text(InspectionView.areaReason(f,q),14,MUTED,false,2);}
        for(String line:InspectionView.findings(f))p.text(line,16,Color.WHITE,false,10);
        p.button("Technical details  ▸",18);
        return p.done();
    }
}
