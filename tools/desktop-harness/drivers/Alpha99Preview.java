package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Alpha99 preview on the desktop: runs Alpha99Pipeline (the app's own analysis path) on each photo and draws the
 * results screen as laid out by Alpha99ResultsActivity (title, headline, disclaimer, overview hero with badges,
 * interference note, two-column tile grid, within line, not-assessed line, secondary buttons) into a PNG. Also writes
 * findings.csv with the Alpha98 status (before) and the Alpha99 status (after) of every feature, and prints the same.
 *
 * Usage: run.sh Alpha99Preview <manifest.csv> <root> <out_dir> [--no-images]
 */
public class Alpha99Preview {
    static final int W=1080,PAD=48;
    static final Color BG=new Color(8,17,31),CARD=new Color(17,30,50),MUTED=new Color(158,176,201),
            RED=new Color(255,99,88),AMBER=new Color(255,176,50),GREEN=new Color(72,209,108),GREY=new Color(160,160,168);

    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),true,"UTF-8"));
        nu.pattern.OpenCV.loadLocally();
        boolean images=a.length<4||!a[3].equals("--no-images");
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){if(hdr[i].equals("local_path")||hdr[i].equals("path"))iPath=i;if(hdr[i].equals("photo_id"))iId=i;}
        Path out=Path.of(a[2]);Files.createDirectories(out);
        try(PrintWriter csv=new PrintWriter(new FileWriter(out.resolve("findings.csv").toFile()))){
            csv.println("photo_id,feature,alpha98_status,alpha99_status,short_line,alpha101_only");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);String id=iId>=0?f[iId]:f[iPath];
                Path p=Path.of(a[1]).resolve(f[iPath]);if(!Files.exists(p)){System.out.println("== "+id+": missing");continue;}
                Bitmap b=Alpha96Calib.loadAlpha96(p.toString());
                Alpha99Pipeline.Output o=b==null?null:Alpha99Pipeline.run(b,HarnessModel.spec(),HarnessModel.ref());
                if(o==null||!o.ok()){System.out.println("== "+id+": dial not found");continue;}
                Alpha98Findings.Summary old=Alpha98Findings.build(o.measurement,o.date,HarnessModel.spec(),HarnessModel.ref());
                System.out.println("== "+id+String.format(java.util.Locale.US," (R=%.0f)",o.measurement.dialRadiusPx));
                for(String h:o.summary.headlineLines())System.out.println("  "+h);
                for(Alpha99Findings.Finding x:o.summary.all){
                    String before=alpha98Status(old,x);
                    csv.println(String.join(",",id,x.key,before,x.status.name(),"\""+(x.status==Alpha99Findings.Status.WITHIN?"":x.shortLine()).replace("\"","'")+"\"",
                            ""+Alpha99Findings.outsideOnlyByNewMeasures(x)));
                    if(x.status!=Alpha99Findings.Status.WITHIN||"OUTSIDE".equals(before))
                        System.out.println(String.format("    %-16s %-13s -> %-13s %s",x.title,before,x.statusLabel(),x.status==Alpha99Findings.Status.WITHIN?"":x.shortLine()));
                }
                System.out.println("  "+o.summary.withinLine());
                if(images)javax.imageio.ImageIO.write(screen(o),"png",out.resolve(id+"_results.png").toFile());
            }
        }
    }

    /** Alpha98 status of the feature this Alpha99 finding belongs to. Rounds: Alpha98 judged the round markers as one
     *  feature (reporting the worst), so every Alpha99 round carries that feature-level status. */
    static String alpha98Status(Alpha98Findings.Summary s,Alpha99Findings.Finding x){
        String key=x.key.startsWith("round")?"rounds":x.key;
        for(Alpha98Findings.Finding f:s.all)if(f.key.equals(key))return f.status.name();
        return "";
    }

    // ------------------------------------------------------------------ screen drawing (mirrors Alpha99ResultsActivity)
    static BufferedImage screen(Alpha99Pipeline.Output o){
        BufferedImage img=new BufferedImage(W,6000,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(BG);g.fillRect(0,0,W,6000);
        Alpha99Findings.Summary s=o.summary;
        int y=PAD;
        y=text(g,"Results",PAD,y,W-2*PAD,font(true,76),Color.WHITE)+16;
        List<String> head=s.headlineLines();
        for(int i=0;i<head.size();i++)y=text(g,head.get(i),PAD,y,W-2*PAD,font(i==0,i==0?52:44),Color.WHITE)+6;
        y=text(g,Alpha99Findings.DISCLAIMER,PAD,y+8,W-2*PAD,font(false,36),MUTED)+28;
        if(o.overview!=null){int sz=W-2*PAD;g.drawImage(o.overview.img,PAD,y,sz,sz,null);y+=sz+10;
            y=text(g,"✓ within   ! worth a look   !! clear finding   – not assessed   ·   tap for the full overlay",PAD,y,W-2*PAD,font(false,32),MUTED)+28;}
        List<Alpha99Findings.Finding> tiles=s.tiles();
        if(!tiles.isEmpty()){
            y=text(g,Alpha99Findings.INTERFERENCE_NOTE,PAD,y,W-2*PAD,font(false,36),MUTED)+24;
            int tw=(W-2*PAD-28)/2;
            for(int i=0;i<tiles.size();i+=2){
                int h=0;
                for(int k=0;k<2&&i+k<tiles.size();k++)h=Math.max(h,tileHeight(g,tiles.get(i+k),tw));
                for(int k=0;k<2&&i+k<tiles.size();k++)tile(g,o,tiles.get(i+k),PAD+k*(tw+28),y,tw,h);
                y+=h+28;
            }
        }
        String within=s.withinLine();if(!within.isEmpty())y=text(g,within,PAD,y+16,W-2*PAD,font(false,38),MUTED)+10;
        String na=s.notAssessedLine();if(!na.isEmpty())y=text(g,na,PAD,y+6,W-2*PAD,font(false,38),MUTED)+10;
        y=button(g,"Open full overlay",y+30);
        y=button(g,"Technical details  ▸",y+20);
        g.dispose();
        return img.getSubimage(0,0,W,Math.min(6000,y+PAD));
    }

    static int tileHeight(Graphics2D g,Alpha99Findings.Finding f,int tw){
        int y=16+(tw-32)+12;
        y=measure(g,f.title,tw-32,font(true,40),y)+4;
        y=measure(g,f.statusLabel(),tw-32,font(true,32),y)+4;
        y=measure(g,f.shortLine(),tw-32,font(false,34),y);
        return y+20;
    }

    static void tile(Graphics2D g,Alpha99Pipeline.Output o,Alpha99Findings.Finding f,int x,int y,int tw,int h){
        g.setColor(CARD);g.fillRoundRect(x,y,tw,h,18,18);
        Bitmap c=o.closeups.get(f.key);int sz=tw-32;
        if(c!=null)g.drawImage(c.img,x+16,y+16,sz,sz,null);
        int yy=y+16+sz+12;
        yy=text(g,f.title,x+16,yy,tw-32,font(true,40),Color.WHITE)+4;
        Color col=f.status==Alpha99Findings.Status.CLEAR?RED:f.status==Alpha99Findings.Status.WORTH?AMBER:f.status==Alpha99Findings.Status.WITHIN?GREEN:GREY;
        yy=text(g,f.statusLabel(),x+16,yy,tw-32,font(true,32),col)+4;
        text(g,f.shortLine(),x+16,yy,tw-32,font(false,34),Color.WHITE);
    }

    static int button(Graphics2D g,String label,int y){
        g.setColor(new Color(214,214,220));g.fillRoundRect(PAD,y,W-2*PAD,120,14,14);
        g.setColor(Color.BLACK);g.setFont(font(false,40));FontMetrics fm=g.getFontMetrics();
        g.drawString(label,(W-fm.stringWidth(label))/2,y+60+fm.getAscent()/2-6);
        return y+120;
    }

    static Font font(boolean bold,int px){return new Font(Font.SANS_SERIF,bold?Font.BOLD:Font.PLAIN,px);}

    /** Word-wrapped text; returns the y below it. */
    static int text(Graphics2D g,String s,int x,int y,int width,Font f,Color c){
        g.setFont(f);g.setColor(c);FontMetrics fm=g.getFontMetrics();
        for(String line:wrap(s,fm,width)){y+=fm.getAscent();g.drawString(line,x,y);y+=fm.getDescent()+4;}
        return y;
    }
    static int measure(Graphics2D g,String s,int width,Font f,int y){
        g.setFont(f);FontMetrics fm=g.getFontMetrics();return y+wrap(s,fm,width).size()*(fm.getAscent()+fm.getDescent()+4);
    }
    static List<String> wrap(String s,FontMetrics fm,int width){
        List<String> out=new ArrayList<>();
        for(String para:s.split("\n")){
            StringBuilder line=new StringBuilder();
            for(String w:para.split(" ")){
                String t=line.length()==0?w:line+" "+w;
                if(fm.stringWidth(t)>width&&line.length()>0){out.add(line.toString());line=new StringBuilder(w);}else{line=new StringBuilder(t);}
            }
            out.add(line.toString());
        }
        return out;
    }
}
