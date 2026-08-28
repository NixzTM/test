package com.nixz.autopilot2d;

import com.nixz.autopilot2d.core.PixelFrame;
import android.graphics.Color;
import java.util.*;

public class BubbleBot {
    static class Bubble { float x,y,r; int value; boolean[] glyph; Bubble(float x,float y,float r,int value,boolean[] glyph){this.x=x;this.y=y;this.r=r;this.value=value;this.glyph=glyph;} }
    static class Shot { float x,y; Bubble target; boolean bank; double score; Shot(float x,float y,Bubble t,boolean b,double s){this.x=x;this.y=y;target=t;bank=b;score=s;} }

    public long play(PixelFrame frame){
        List<Bubble> all=detectCoins(frame);
        if(all.size()<2){ BotRuntime.setStatus("Bubble: finding coins ("+all.size()+")"); return 160; }
        final int H=frame.height();
        Bubble shooter=null;
        for(Bubble q:all){ if(q.y < H*.68f) continue; if(shooter==null || q.r>shooter.r*1.12f || (Math.abs(q.r-shooter.r)<4f && q.y<shooter.y)) shooter=q; }
        if(shooter==null){ for(Bubble q:all) if(shooter==null || q.y>shooter.y) shooter=q; }
        if(shooter==null){ BotRuntime.setStatus("Bubble: shooter not found"); return 170; }
        ArrayList<Bubble> field=new ArrayList<>();
        for(Bubble q:all){ if(q!=shooter && q.y < shooter.y - Math.max(q.r,shooter.r)*.75f) field.add(q); }
        if(field.isEmpty()){ BotRuntime.setStatus("Bubble: waiting for targets"); return 180; }
        Shot best=findUpgrade(frame,shooter,field);
        if(best==null){ BotRuntime.setStatus("Bubble: no confirmed upgrade yet"); return 180; }
        String sv=shooter.value>0?String.valueOf(shooter.value):"?";
        String tv=best.target.value>0?String.valueOf(best.target.value):sv;
        BotRuntime.setStatus("Bubble: "+sv+" → "+tv+(best.bank?" bank":" upgrade"));
        boolean sent=BotAccessibilityService.tapPixels(best.x,best.y,55);
        if(!sent){ BotRuntime.setStatus("Bubble: Android rejected shot gesture"); return 220; }
        return 780;
    }

    private Shot findUpgrade(PixelFrame f,Bubble s,List<Bubble> field){
        int W=f.width(); float left=W*.055f,right=W*.945f; Shot best=null;
        for(Bubble t:field){
            double same=glyphDistance(s.glyph,t.glyph); boolean numeric=s.value>0 && t.value>0 && s.value==t.value;
            if(!numeric && same>.235) continue;
            Shot d=directShot(s,t,field,left,right);
            if(d!=null){ d.score=10000-same*1200+t.y*.02+(numeric?1200:0); if(best==null||d.score>best.score)best=d; }
            Shot l=bankShot(s,t,field,left,right,true);
            if(l!=null){l.score=9000-same*1200+t.y*.02+(numeric?1200:0);if(best==null||l.score>best.score)best=l;}
            Shot r=bankShot(s,t,field,left,right,false);
            if(r!=null){r.score=9000-same*1200+t.y*.02+(numeric?1200:0);if(best==null||r.score>best.score)best=r;}
        }
        return best;
    }

    private Shot directShot(Bubble s,Bubble target,List<Bubble> field,float left,float right){ Bubble first=firstCollision(s,target.x,target.y,field,left,right); return first==target?new Shot(target.x,target.y,target,false,0):null; }

    private Shot bankShot(Bubble s,Bubble target,List<Bubble> field,float left,float right,boolean useLeft){
        float mirror=useLeft?2*left-target.x:2*right-target.x; Bubble first=firstCollision(s,mirror,target.y,field,left,right); if(first!=target)return null;
        float dx=mirror-s.x,dy=target.y-s.y;if(dy>=-1)return null; float wall=useLeft?left:right; float tt=(wall-s.x)/dx; if(tt<=0||tt>=1)return null;
        float wy=s.y+dy*tt; return new Shot(Math.max(left+3,Math.min(right-3,wall)),Math.max(80,Math.min(s.y-80,wy)),target,true,0);
    }

    private Bubble firstCollision(Bubble s,float aimX,float aimY,List<Bubble> field,float left,float right){
        float dx=aimX-s.x,dy=aimY-s.y,len=(float)Math.hypot(dx,dy); if(len<2||dy>=0)return null; dx/=len;dy/=len;
        float x=s.x,y=s.y,step=Math.max(3f,s.r*.14f);
        for(int i=0;i<2600;i++){
            x+=dx*step;y+=dy*step;
            if(x<left+s.r){x=2*(left+s.r)-x;dx=-dx;} if(x>right-s.r){x=2*(right-s.r)-x;dx=-dx;}
            Bubble hit=null;float hd=Float.MAX_VALUE;
            for(Bubble q:field){float d=dist(x,y,q.x,q.y),rr=s.r+q.r*.78f;if(d<rr&&d<hd){hd=d;hit=q;}}
            if(hit!=null)return hit; if(y<50)return null;
        }
        return null;
    }

    private List<Bubble> detectCoins(PixelFrame b){
        int W=b.width(),H=b.height(),scale=Math.max(2,W/300),sw=(W+scale-1)/scale,sh=(H+scale-1)/scale;
        boolean[] mask=new boolean[sw*sh];
        for(int yy=0;yy<sh;yy++){int y=Math.min(H-1,yy*scale+scale/2);for(int xx=0;xx<sw;xx++){int x=Math.min(W-1,xx*scale+scale/2),c=b.argbAt(x,y);float[] hsv=new float[3];Color.colorToHSV(c,hsv);mask[yy*sw+xx]=y>H*.08f&&y<H*.88f&&hsv[0]>=31f&&hsv[0]<=72f&&hsv[1]>.38f&&hsv[2]>.52f;}}
        boolean[] seen=new boolean[mask.length];int[] qx=new int[mask.length],qy=new int[mask.length];ArrayList<Bubble> out=new ArrayList<>();
        for(int yy=1;yy<sh-1;yy++)for(int xx=1;xx<sw-1;xx++){
            int id=yy*sw+xx;if(!mask[id]||seen[id])continue;
            int head=0,tail=0;qx[tail]=xx;qy[tail++]=yy;seen[id]=true;int minX=xx,maxX=xx,minY=yy,maxY=yy,n=0;long sx=0,sy=0;
            while(head<tail){int x=qx[head],y=qy[head++];n++;sx+=x;sy+=y;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++){if(ox==0&&oy==0)continue;int nx=x+ox,ny=y+oy;if(nx<0||ny<0||nx>=sw||ny>=sh)continue;int ni=ny*sw+nx;if(mask[ni]&&!seen[ni]){seen[ni]=true;qx[tail]=nx;qy[tail++]=ny;}}}
            float pw=(maxX-minX+1)*scale,ph=(maxY-minY+1)*scale,ratio=pw/ph;
            if(n<16||pw<W*.045f||ph<W*.045f||pw>W*.18f||ph>W*.18f||ratio<.67f||ratio>1.48f)continue;
            float circular=n/(float)Math.max(1,(maxX-minX+1)*(maxY-minY+1));if(circular<.34f)continue;
            float cx=(sx/(float)n)*scale+scale*.5f,cy=(sy/(float)n)*scale+scale*.5f,r=Math.max(pw,ph)*.51f;
            boolean[] glyph=glyphSignature(b,cx,cy,r);if(glyph==null)continue; int value=readSimpleValue(b,cx,cy,r); out.add(new Bubble(cx,cy,r,value,glyph));
        }
        out.sort((a,z)->Float.compare(z.r,a.r));ArrayList<Bubble> dedup=new ArrayList<>();for(Bubble q:out){boolean dup=false;for(Bubble e:dedup)if(dist(q.x,q.y,e.x,e.y)<Math.max(q.r,e.r)*.62f){dup=true;break;}if(!dup)dedup.add(q);}return dedup;
    }

    private boolean[] glyphSignature(PixelFrame b,float cx,float cy,float r){
        final int GW=9,GH=11;boolean[] g=new boolean[GW*GH];int darkCount=0;
        for(int gy=0;gy<GH;gy++)for(int gx=0;gx<GW;gx++){float fx=(gx+.5f)/GW-.5f,fy=(gy+.5f)/GH-.5f;int x=Math.round(cx+fx*r*.82f),y=Math.round(cy+fy*r*.92f);if(x<0||y<0||x>=b.width()||y>=b.height())continue;int c=b.argbAt(x,y),br=(Color.red(c)+Color.green(c)+Color.blue(c))/3;boolean d=br<150;g[gy*GW+gx]=d;if(d)darkCount++;}
        return darkCount>=3&&darkCount<45?g:null;
    }

    private double glyphDistance(boolean[] a,boolean[] z){ if(a==null||z==null||a.length!=z.length)return 1;int diff=0,used=0;for(int i=0;i<a.length;i++){if(a[i]||z[i])used++;if(a[i]!=z[i])diff++;}return diff/(double)Math.max(1,used); }

    private int readSimpleValue(PixelFrame b,float cx,float cy,float r){
        boolean[] g=glyphSignature(b,cx,cy,r);if(g==null)return -1;int top=0,mid=0,bot=0,left=0,right=0;
        for(int y=0;y<11;y++)for(int x=0;x<9;x++)if(g[y*9+x]){if(y<3)top++;else if(y<8)mid++;else bot++;if(x<3)left++;if(x>5)right++;}
        if(top>=2&&mid>=4&&bot>=2&&Math.abs(left-right)<=4)return 8; if(mid>=5&&right>=left&&bot<=top+3)return 4; if(top>=2&&bot>=2)return 2; return -1;
    }

    private static float dist(float x1,float y1,float x2,float y2){return (float)Math.hypot(x1-x2,y1-y2);}
}
