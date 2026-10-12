package com.watchalign.mobile;

import java.util.*;

/** Image-supported closed separator fallback. No reference/inset/target dimensions participate.
 * Each ray supplies observed exit gradients. A bounded cyclic path chooses spatially coherent
 * edges instead of jumping between the insert and outer bevel. Missing evidence is not filled.
 */
final class LuminousSeparator {
    static final int RAYS=96;
    static final class Result {
        final List<double[][]> candidates=new ArrayList<>();
        String code="SEPARATOR_NOT_CLOSED",reason="no closed luminous-to-metal separator";
        int supportedRays,metalRecoveryRays;double[][][] observed;double[][] visible;
    }
    static final class Edge {
        double radius,drop;boolean metal;double floor;double[] levels=new double[3];
    }
    static Result trace(byte[] pixels,int n,int seed){
        Result result=new Result();double increment=.25;List<List<Edge>> options=new ArrayList<>();
        for(int k=0;k<RAYS;k++){
            double a=k*2*Math.PI/RAYS,dx=Math.cos(a),dy=Math.sin(a),max=(n/2.0-2)/Math.max(Math.abs(dx),Math.abs(dy));int count=(int)(max/increment);
            double[] v=new double[count];for(int i=0;i<count;i++)v[i]=smooth(pixels,n,seed+i*increment*dx,seed+i*increment*dy);
            List<Edge> edges=new ArrayList<>();
            for(int j=12;j<count-8&&edges.size()<8;j++){
                double drop=v[j-4]-v[j+4],previous=v[j-5]-v[j+3],next=v[j-3]-v[j+5];
                if(drop<12||drop<previous||drop<next)continue;
                int supported=0;for(int i=0;i<j-4;i++)if(v[i]>=v[j+4]+.6*drop)supported++;
                if(supported<.90*(j-4))continue;
                Edge edge=new Edge();edge.drop=drop;int left=j-4,right=j+4;boolean crossingFound=true;
                for(int level=0;level<3;level++){
                    double target=v[right]+new double[]{.42,.50,.58}[level]*drop;int crossing=-1;
                    for(int i=left;i<right;i++)if(v[i]>=target&&v[i+1]<target){crossing=i;break;}
                    if(crossing<0){crossingFound=false;break;}
                    edge.levels[level]=(crossing+(target-v[crossing])/(v[crossing+1]-v[crossing]))*increment;
                }
                if(!crossingFound)continue;edge.radius=edge.levels[1];edge.floor=v[right]+.42*drop;
                int trough=j;for(int i=j;i<Math.min(count,j+16);i++)if(v[i]<v[trough])trough=i;
                double recovery=v[trough];for(int i=trough+1;i<Math.min(count,trough+24);i++)recovery=Math.max(recovery,v[i]);edge.metal=recovery-v[trough]>=12;
                if(edges.isEmpty()||Math.abs(edge.radius-edges.get(edges.size()-1).radius)>.5)edges.add(edge);
            }
            options.add(edges);if(!edges.isEmpty())result.supportedRays++;
        }
        if(result.supportedRays<RAYS){
            List<double[]> visible=new ArrayList<>();for(int k=0;k<RAYS;k++)if(!options.get(k).isEmpty()&&options.get(k).get(0).metal){double a=k*2*Math.PI/RAYS,r=options.get(k).get(0).radius;visible.add(new double[]{seed+r*Math.cos(a),seed+r*Math.sin(a)});}
            result.visible=visible.toArray(new double[0][]);result.reason="separator not closed: observed exit gradients on "+result.supportedRays+" of "+RAYS+" rays";return result;
        }
        int[] chosen=closedPath(options);double[][][] contours=new double[3][RAYS][2];double floor=Double.POSITIVE_INFINITY;
        for(int k=0;k<RAYS;k++){
            Edge edge=options.get(k).get(chosen[k]);if(edge.metal)result.metalRecoveryRays++;floor=Math.min(floor,edge.floor);
            double a=k*2*Math.PI/RAYS;for(int level=0;level<3;level++)contours[level][k]=new double[]{seed+edge.levels[level]*Math.cos(a),seed+edge.levels[level]*Math.sin(a)};
        }
        result.observed=contours;
        List<double[]> supported=new ArrayList<>();for(int k=0;k<RAYS;k++)if(options.get(k).get(chosen[k]).metal)supported.add(contours[1][k]);
        result.visible=supported.toArray(new double[0][]);
        if(result.metalRecoveryRays<.80*RAYS){result.reason="insufficient luminous-to-metal evidence: bright surround recovery on "+result.metalRecoveryRays+" of "+RAYS+" rays";return result;}
        for(double[][] contour:contours){
            double[][] hull=LuminousGeometry.hull(contour);if(hull==null){result.code="SEPARATOR_INVALID";result.reason="separator polygon invalid";return result;}
            for(double[] p:contour)if(distanceToBoundary(p,hull)>1.25){result.code="SEPARATOR_CONVEXITY";result.reason=String.format(Locale.US,"separator has unresolved notch or discontinuity (%.2f crop px)",distanceToBoundary(p,hull));return result;}
            result.candidates.add(hull);
        }
        double[][] middle=result.candidates.get(1);
        for(int y=1;y<n-1;y++)for(int x=1;x<n-1;x++)
            if((pixels[y*n+x]&255)<floor&&inside(middle,x,y)&&distanceToBoundary(new double[]{x,y},middle)>1.25){result.code="SEPARATOR_INTERIOR_DARK";result.reason="unresolved dark feature inside luminous boundary; hole, printing or shadow cannot be distinguished";return result;}
        result.code="NONE";result.reason="measured";return result;
    }
    /** Bounded cyclic image-edge association. Earlier exits are preferred; no target shape/radius is supplied.
     * Continuity is a search cost, not permission to create an edge: every chosen point is observed.
     * Convexity, interior dark features and optical support are checked independently afterwards.
     */
    static int[] closedPath(List<List<Edge>> options){
        int rays=options.size();double best=Double.POSITIVE_INFINITY;int[] result=null;
        for(int start=0;start<options.get(0).size();start++){
            double[] previous=new double[options.get(0).size()];Arrays.fill(previous,Double.POSITIVE_INFINITY);previous[start]=edgeCost(options.get(0).get(start),start);int[][] back=new int[rays][];
            for(int k=1;k<rays;k++){
                List<Edge> now=options.get(k),prior=options.get(k-1);double[] cost=new double[now.size()];Arrays.fill(cost,Double.POSITIVE_INFINITY);back[k]=new int[now.size()];
                for(int i=0;i<now.size();i++)for(int j=0;j<prior.size();j++){
                    double c=previous[j]+Math.abs(now.get(i).radius-prior.get(j).radius)+edgeCost(now.get(i),i);
                    if(c<cost[i]){cost[i]=c;back[k][i]=j;}
                }previous=cost;
            }
            for(int end=0;end<previous.length;end++){
                double cost=previous[end]+Math.abs(options.get(rays-1).get(end).radius-options.get(0).get(start).radius);
                if(cost<best){best=cost;int[] path=new int[rays];path[rays-1]=end;for(int k=rays-1;k>0;k--)path[k-1]=back[k][path[k]];result=path;}
            }
        }return result;
    }
    static double edgeCost(Edge edge,int index){return index+12/edge.drop;}
    static boolean inside(double[][] p,double x,double y){boolean in=false;for(int i=0,j=p.length-1;i<p.length;j=i++)if((p[i][1]>y)!=(p[j][1]>y)&&x<(p[j][0]-p[i][0])*(y-p[i][1])/(p[j][1]-p[i][1])+p[i][0])in=!in;return in;}
    static double distanceToBoundary(double[] point,double[][] polygon){double nearest=Double.POSITIVE_INFINITY;
        for(int k=0;k<polygon.length;k++){double[] a=polygon[k],b=polygon[(k+1)%polygon.length];double vx=b[0]-a[0],vy=b[1]-a[1],den=vx*vx+vy*vy;if(den==0)continue;
            double t=Math.max(0,Math.min(1,((point[0]-a[0])*vx+(point[1]-a[1])*vy)/den));nearest=Math.min(nearest,Math.hypot(point[0]-a[0]-t*vx,point[1]-a[1]-t*vy));}return nearest;
    }
    static double smooth(byte[] p,int n,double x,double y){double sum=0;int[] w={1,2,1};for(int iy=-1;iy<=1;iy++)for(int ix=-1;ix<=1;ix++)sum+=w[ix+1]*w[iy+1]*at(p,n,x+ix,y+iy);return sum/16;}
    static double at(byte[] p,int n,double x,double y){int ix=(int)Math.floor(x),iy=(int)Math.floor(y);if(ix<0||iy<0||ix>=n-1||iy>=n-1)return 0;double fx=x-ix,fy=y-iy;
        return (1-fy)*((1-fx)*(p[iy*n+ix]&255)+fx*(p[iy*n+ix+1]&255))+fy*((1-fx)*(p[(iy+1)*n+ix]&255)+fx*(p[(iy+1)*n+ix+1]&255));}
}
