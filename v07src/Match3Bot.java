package com.nixz.autopilot2d;

import android.graphics.Color;
import com.nixz.autopilot2d.core.PixelFrame;
import java.util.*;

public class Match3Bot {
    private static final int COLS=6, ROWS=8, EMPTY=-9;
    private Geometry geo;
    private long observedHash=Long.MIN_VALUE;
    private int stableFrames=0;
    private String pendingMove=null;
    private long pendingHash=Long.MIN_VALUE;
    private final HashSet<String> rejectedMoves=new HashSet<>();

    static class Geometry { float x0,y0,s,r; Geometry(float x,float y,float s){x0=x;y0=y;this.s=s;r=s*.42f;} float x(int c){return x0+c*s;} float y(int r){return y0+r*s;} }
    static class Cell { int type; boolean plus,special; float confidence; Cell(int t,boolean p,boolean s,float cf){type=t;plus=p;special=s;confidence=cf;} Cell copy(){return new Cell(type,plus,special,confidence);} }
    static class Board { Cell[][] c; float confidence; int plusCount,good; Board(Cell[][] c,float cf,int pc,int g){this.c=c;confidence=cf;plusCount=pc;good=g;} }
    static class MatchInfo { HashSet<Integer> cells=new HashSet<>(); int fours,fives,lines; }
    static class Move { int r1,c1,r2,c2; double score; boolean clearsPlus; Cell[][] after; Move(int a,int b,int c,int d){r1=a;c1=b;r2=c;c2=d;} }

    public long play(PixelFrame frame){
        Geometry g=resolveGeometry(frame);
        if(g==null){ BotRuntime.setStatus("Match-3 AI: locating board"); return 180; }
        Board board=readBoard(frame,g);
        if(board==null){ geo=null; BotRuntime.setStatus("Match-3 AI: board read failed"); return 180; }
        if(board.confidence<.48f){ BotRuntime.setStatus("Match-3 AI: low confidence "+Math.round(board.confidence*100)+"% ("+board.good+"/48)"); return 160; }

        long h=boardHash(board.c);
        if(h==observedHash) stableFrames++; else { observedHash=h; stableFrames=1; }
        if(stableFrames<2){ BotRuntime.setStatus("Match-3 AI: stabilizing "+board.good+"/48, +1="+board.plusCount); return 100; }

        if(pendingMove!=null){
            if(h==pendingHash){ rejectedMoves.add(pendingMove); BotRuntime.setStatus("Match-3 AI: rejected move, replanning"); }
            else rejectedMoves.clear();
            pendingMove=null;
        }

        List<Move> legal=legalMoves(board.c,true);
        if(legal.isEmpty()){ BotRuntime.setStatus("Match-3 AI: no legal move read; rescanning"); geo=null; return 180; }

        for(Move m:legal){
            m.score=immediateScore(board.c,m);
            m.after=simulate(board.c,m);
            if(m.after!=null){
                double future=search(m.after,2,.58);
                if(hasImmediatePlusClear(m.after)) m.score += 250000;
                m.score += future;
            }
            if(m.clearsPlus) m.score += 1000000;
        }
        legal.sort((a,b)->Double.compare(b.score,a.score));
        Move best=legal.get(0);

        int plusLegal=0; for(Move m:legal) if(m.clearsPlus) plusLegal++;
        BotRuntime.setStatus("Match-3 AI: +1="+board.plusCount+", +1 clears="+plusLegal+", moves="+legal.size());

        boolean sent=BotAccessibilityService.swipePixels(g.x(best.c1),g.y(best.r1),g.x(best.c2),g.y(best.r2),170);
        if(sent){ pendingMove=key(best); pendingHash=h; stableFrames=0; }
        else BotRuntime.setStatus("Match-3 AI: Android rejected gesture");
        return 700;
    }

    private Geometry resolveGeometry(PixelFrame b){
        if(geo!=null && geometryScore(b,geo)>=40) return geo;
        int w=b.width(),h=b.height(); Geometry best=null; int bs=-1;
        float baseS=w*.1535f,baseX=w*.118f;
        for(float sf=.92f;sf<=1.08f;sf+=.02f){
            float sp=baseS*sf;
            for(float xo=-.035f;xo<=.035f;xo+=.008f){
                for(float yf=.105f;yf<=.235f;yf+=.006f){
                    Geometry g=new Geometry(baseX+xo*w,yf*h,sp);
                    if(g.x(5)>w*.99f||g.y(7)>h*.80f)continue;
                    int sc=geometryScore(b,g); if(sc>bs){bs=sc;best=g;}
                }
            }
        }
        if(bs>=38){geo=best;return best;} return null;
    }

    private int geometryScore(PixelFrame b,Geometry g){
        int ok=0;
        for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++){
            int t=Vision.dominantPalette(b,g.x(c),g.y(r),g.r);
            if(t>=0)ok++;
        }
        return ok;
    }

    private Board readBoard(PixelFrame b,Geometry g){
        Cell[][] out=new Cell[ROWS][COLS]; float sum=0; int good=0,plus=0;
        for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++){
            Cell z=classifyCell(b,g.x(c),g.y(r),g.r); out[r][c]=z;
            if(z.type>=0){good++;sum+=z.confidence;} if(z.plus)plus++;
        }
        if(good<38)return null;
        return new Board(out,sum/Math.max(1,good),plus,good);
    }

    private Cell classifyCell(PixelFrame b,float cx,float cy,float rad){
        int[] votes=new int[8];
        float[][] off={{0,0},{-.10f,0},{.10f,0},{0,-.10f},{0,.10f},{-.07f,-.07f},{.07f,.07f}};
        int samples=0;
        for(float[] o:off){
            int t=Vision.dominantPalette(b,cx+o[0]*rad,cy+o[1]*rad,rad*.90f);
            if(t>=0&&t<votes.length){votes[t]++;samples++;}
        }
        int fallback=Vision.dominantPalette(b,cx,cy,rad);
        int best=-1,bv=0; for(int i=0;i<votes.length;i++)if(votes[i]>bv){bv=votes[i];best=i;}
        if(best<0)best=fallback;
        if(best>=0 && fallback>=0 && bv<3)best=fallback;
        float conf=best<0?0:Math.max(.50f,bv/(float)off.length);
        boolean plus=best>=0 && plusMarker(b,cx,cy,rad);
        boolean special=best>=0 && Vision.specialOverlay(b,cx,cy,rad,best,plus);
        return new Cell(best,plus,special,conf);
    }

    private boolean plusMarker(PixelFrame b,float cx,float cy,float r){
        int W=b.width(),H=b.height();
        int x0=Math.max(0,(int)(cx-r*1.12f)),x1=Math.min(W-1,(int)(cx-r*.28f));
        int y0=Math.max(0,(int)(cy-r*1.20f)),y1=Math.min(H-1,(int)(cy-r*.05f));
        int sx=Math.max(1,(int)(r*.035f)), sy=Math.max(1,(int)(r*.025f));
        int columnsWithStroke=0,bestRun=0;
        for(int x=x0;x<=x1;x+=sx){
            int run=0,maxRun=0,dark=0,total=0;
            for(int y=y0;y<=y1;y+=sy){
                int cc=b.argbAt(x,y),R=Color.red(cc),G=Color.green(cc),B=Color.blue(cc);
                int lum=(3*R+4*G+B)/8, spread=Math.max(R,Math.max(G,B))-Math.min(R,Math.min(G,B));
                boolean d=lum<175 && spread<105;
                total++; if(d){dark++;run++;maxRun=Math.max(maxRun,run);}else run=0;
            }
            bestRun=Math.max(bestRun,maxRun);
            if(maxRun>=6 && dark>=Math.max(4,total/6))columnsWithStroke++;
        }
        return bestRun>=7 && columnsWithStroke>=1;
    }

    private List<Move> legalMoves(Cell[][] b,boolean respectRejected){
        ArrayList<Move> out=new ArrayList<>();
        for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++){
            if(c+1<COLS)addIfLegal(b,r,c,r,c+1,respectRejected,out);
            if(r+1<ROWS)addIfLegal(b,r,c,r+1,c,respectRejected,out);
        }
        return out;
    }
    private void addIfLegal(Cell[][] src,int r1,int c1,int r2,int c2,boolean respectRejected,List<Move> out){
        if(src[r1][c1].type<0||src[r2][c2].type<0||src[r1][c1].type==src[r2][c2].type)return;
        Move m=new Move(r1,c1,r2,c2); if(respectRejected&&rejectedMoves.contains(key(m)))return;
        Cell[][] b=copy(src); swap(b,m); MatchInfo mi=findMatches(b); if(mi.cells.isEmpty())return;
        int a=r1*COLS+c1,d=r2*COLS+c2; if(!mi.cells.contains(a)&&!mi.cells.contains(d))return;
        for(int idx:mi.cells)if(b[idx/COLS][idx%COLS].plus){m.clearsPlus=true;break;} out.add(m);
    }
    private double immediateScore(Cell[][] src,Move m){
        Cell[][] b=copy(src); swap(b,m); MatchInfo mi=findMatches(b);
        double s=mi.cells.size()*80+mi.fours*500+mi.fives*1600+mi.lines*120; int plus=0,special=0;
        for(int idx:mi.cells){Cell z=b[idx/COLS][idx%COLS];if(z.plus)plus++;if(z.special)special++;}
        s+=plus*20000+special*1400; if(src[m.r1][m.c1].plus||src[m.r2][m.c2].plus)s+=3500; if(src[m.r1][m.c1].special||src[m.r2][m.c2].special)s+=700; return s;
    }
    private double search(Cell[][] b,int depth,double discount){
        if(depth<=0)return heuristic(b)*discount; List<Move> moves=legalMoves(b,false); if(moves.isEmpty())return heuristic(b)*discount;
        for(Move m:moves){m.score=immediateScore(b,m);m.after=simulate(b,m);if(m.clearsPlus)m.score+=1000000;} moves.sort((a,z)->Double.compare(z.score,a.score));
        double best=-1e18; for(int i=0;i<Math.min(8,moves.size());i++){Move m=moves.get(i);double v=m.score;if(m.after!=null)v+=search(m.after,depth-1,discount*.58);best=Math.max(best,v);} return best*discount;
    }
    private boolean hasImmediatePlusClear(Cell[][] b){for(Move m:legalMoves(b,false))if(m.clearsPlus)return true;return false;}
    private Cell[][] simulate(Cell[][] src,Move m){Cell[][] b=copy(src);swap(b,m);MatchInfo mi=findMatches(b);if(mi.cells.isEmpty())return null;for(int idx:mi.cells)b[idx/COLS][idx%COLS]=new Cell(EMPTY,false,false,1);for(int c=0;c<COLS;c++){int wr=ROWS-1;for(int r=ROWS-1;r>=0;r--)if(b[r][c].type!=EMPTY)b[wr--][c]=b[r][c];while(wr>=0)b[wr--][c]=new Cell(EMPTY,false,false,1);}return b;}
    private double heuristic(Cell[][] b){double s=0;for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++){Cell z=b[r][c];if(z.type<0)continue;if(z.plus)s+=6000;if(z.special)s+=700;if(c+1<COLS&&b[r][c+1].type==z.type)s+=40;if(r+1<ROWS&&b[r+1][c].type==z.type)s+=40;if(c+2<COLS&&b[r][c+2].type==z.type)s+=90;if(r+2<ROWS&&b[r+2][c].type==z.type)s+=90;}return s;}
    private MatchInfo findMatches(Cell[][] b){MatchInfo m=new MatchInfo();for(int r=0;r<ROWS;r++){int c=0;while(c<COLS){int t=b[r][c].type,j=c+1;if(t<0){c++;continue;}while(j<COLS&&b[r][j].type==t)j++;int n=j-c;if(n>=3){m.lines++;if(n==4)m.fours++;if(n>=5)m.fives++;for(int k=c;k<j;k++)m.cells.add(r*COLS+k);}c=j;}}for(int c=0;c<COLS;c++){int r=0;while(r<ROWS){int t=b[r][c].type,j=r+1;if(t<0){r++;continue;}while(j<ROWS&&b[j][c].type==t)j++;int n=j-r;if(n>=3){m.lines++;if(n==4)m.fours++;if(n>=5)m.fives++;for(int k=r;k<j;k++)m.cells.add(k*COLS+c);}r=j;}}return m;}
    private void swap(Cell[][] b,Move m){Cell t=b[m.r1][m.c1];b[m.r1][m.c1]=b[m.r2][m.c2];b[m.r2][m.c2]=t;}
    private Cell[][] copy(Cell[][] in){Cell[][] o=new Cell[ROWS][COLS];for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++)o[r][c]=in[r][c].copy();return o;}
    private String key(Move m){int a=m.r1*COLS+m.c1,b=m.r2*COLS+m.c2;if(a>b){int t=a;a=b;b=t;}return a+":"+b;}
    private long boardHash(Cell[][] b){long h=1469598103934665603L;for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++){Cell z=b[r][c];long v=(z.type+17)|(z.plus?64:0)|(z.special?128:0);h^=v;h*=1099511628211L;}return h;}
}
