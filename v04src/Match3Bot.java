package com.nixz.autopilot2d;

import android.graphics.Color;
import com.nixz.autopilot2d.core.PixelFrame;
import java.util.*;

public class Match3Bot {
    private static final int COLS=6, ROWS=8, EMPTY=-9;
    private Geometry geo;
    private long lastBoardHash=Long.MIN_VALUE;
    private String pendingMove=null;
    private long pendingBoardHash=Long.MIN_VALUE;
    private final HashSet<String> rejectedMoves=new HashSet<>();

    static class Cell { int type; boolean plus,special; Cell(int t,boolean p,boolean s){type=t;plus=p;special=s;} Cell copy(){return new Cell(type,plus,special);} }
    static class Geometry { float x0,y0,s,r; Geometry(float a,float b,float c){x0=a;y0=b;s=c;r=c*.42f;} float x(int c){return x0+c*s;} float y(int r){return y0+r*s;} }
    static class Move { int r1,c1,r2,c2; double score; Move(int a,int b,int c,int d,double s){r1=a;c1=b;r2=c;c2=d;score=s;} }
    static class MatchInfo { HashSet<Integer> cells=new HashSet<>(); int fours=0,fives=0,lines=0; }

    public long play(PixelFrame b){
        Geometry g=resolveGeometry(b); if(g==null) return 220;
        Cell[][] board=readBoard(b,g); if(board==null) { geo=null; return 220; }
        long bh=boardHash(board);
        if(lastBoardHash!=bh){ rejectedMoves.clear(); lastBoardHash=bh; }
        else if(pendingMove!=null && pendingBoardHash==bh){ rejectedMoves.add(pendingMove); BotRuntime.setStatus("Match-3: rejected illegal swap, replanning"); }
        pendingMove=null;
        Move best=findBest(board,true);
        if(best==null || best.score<1) { BotRuntime.setStatus("Match-3: scanning board"); return 260; }
        float x1=g.x(best.c1),y1=g.y(best.r1),x2=g.x(best.c2),y2=g.y(best.r2);
        boolean targetsPlus = clearsPlus(board,best);
        int plusCount=0; for(int rr=0;rr<ROWS;rr++)for(int cc=0;cc<COLS;cc++)if(board[rr][cc].plus)plusCount++;
        BotRuntime.setStatus(targetsPlus ? ("Match-3: CLEARING +1 | seen "+plusCount) : ("Match-3: setup | +1 seen "+plusCount));
        String mk=moveKey(best);
        boolean sent=BotAccessibilityService.swipePixels(x1,y1,x2,y2,150);
        if(sent){ pendingMove=mk; pendingBoardHash=bh; }
        else BotRuntime.setStatus("Match-3: gesture rejected by Android");
        return 720;
    }

    private Geometry resolveGeometry(PixelFrame b){
        if(geo!=null && geometryScore(b,geo)>=44) return geo;
        int w=b.width(),h=b.height(); float baseS=w*.1535f, baseX=w*.118f;
        Geometry best=null; int bs=-1;
        for(float sf=.94f;sf<=1.06f;sf+=.02f){
            float sp=baseS*sf;
            for(float xo=-.025f;xo<=.025f;xo+=.008f){
                for(float yf=.125f;yf<=.205f;yf+=.006f){
                    Geometry g=new Geometry(baseX+xo*w,yf*h,sp);
                    if(g.x(5)>=w*.985f || g.y(7)>=h*.75f) continue;
                    int sc=geometryScore(b,g); if(sc>bs){bs=sc;best=g;}
                }
            }
        }
        if(bs>=42){geo=best;return best;} geo=null; return null;
    }

    private int geometryScore(PixelFrame b,Geometry g){
        int ok=0; for(int r=0;r<ROWS;r++) for(int c=0;c<COLS;c++) if(Vision.dominantPalette(b,g.x(c),g.y(r),g.r)>=0) ok++; return ok;
    }

    private Cell[][] readBoard(PixelFrame b,Geometry g){
        Cell[][] out=new Cell[ROWS][COLS]; int good=0;
        for(int r=0;r<ROWS;r++) for(int c=0;c<COLS;c++){
            int t=Vision.dominantPalette(b,g.x(c),g.y(r),g.r); if(t>=0) good++;
            boolean plus=t>=0 && plusBadgeV2(b,g.x(c),g.y(r),g.r);
            boolean sp=t>=0 && Vision.specialOverlay(b,g.x(c),g.y(r),g.r,t,plus);
            out[r][c]=new Cell(t,plus,sp);
        }
        return good>=40?out:null;
    }

    private boolean plusBadgeV2(PixelFrame b,float cx,float cy,float radius){
        int w=b.width(),h=b.height();
        int x0=Math.max(0,(int)(cx-radius*1.10f));
        int x1=Math.min(w-1,(int)(cx-radius*.30f));
        int y0=Math.max(0,(int)(cy-radius*1.18f));
        int y1=Math.min(h-1,(int)(cy-radius*.05f));
        if(x1<=x0||y1<=y0)return false;
        int stepX=Math.max(1,(int)(radius*.025f));
        int stepY=Math.max(1,(int)(radius*.020f));
        int bestRun=0,bestDarkPct=0,darkPixels=0,totalPixels=0;
        for(int x=x0;x<=x1;x+=stepX){
            int run=0,maxRun=0,dark=0,total=0;
            for(int y=y0;y<=y1;y+=stepY){
                int c=b.argbAt(x,y);
                int R=Color.red(c),G=Color.green(c),B=Color.blue(c);
                int max=Math.max(R,Math.max(G,B)), min=Math.min(R,Math.min(G,B));
                int lum=(R*3+G*4+B)/8;
                boolean d=lum<165 && (max-min)<95;
                total++; totalPixels++;
                if(d){ dark++; darkPixels++; run++; if(run>maxRun)maxRun=run; } else run=0;
            }
            bestRun=Math.max(bestRun,maxRun);
            if(total>0) bestDarkPct=Math.max(bestDarkPct,(dark*100)/total);
        }
        int overall = totalPixels>0 ? (darkPixels*100)/totalPixels : 0;
        return bestRun>=Math.max(8,(int)(radius*.42f)) && bestDarkPct>=30 && overall>=5;
    }

    private Move findBest(Cell[][] board,boolean lookahead){
        Move best=null, directPlus=null;
        for(int r=0;r<ROWS;r++) for(int c=0;c<COLS;c++){
            if(c+1<COLS){
                Move m=eval(board,r,c,r,c+1,lookahead);
                if(m!=null){
                    if(clearsPlus(board,m) && (directPlus==null||m.score>directPlus.score)) directPlus=m;
                    if(best==null||m.score>best.score) best=m;
                }
            }
            if(r+1<ROWS){
                Move m=eval(board,r,c,r+1,c,lookahead);
                if(m!=null){
                    if(clearsPlus(board,m) && (directPlus==null||m.score>directPlus.score)) directPlus=m;
                    if(best==null||m.score>best.score) best=m;
                }
            }
        }
        return directPlus!=null ? directPlus : best;
    }

    private boolean clearsPlus(Cell[][] src,Move m){
        Cell[][] b=copy(src);
        Cell t=b[m.r1][m.c1]; b[m.r1][m.c1]=b[m.r2][m.c2]; b[m.r2][m.c2]=t;
        MatchInfo mi=findMatches(b);
        for(int idx:mi.cells) if(b[idx/COLS][idx%COLS].plus) return true;
        return false;
    }

    private Move eval(Cell[][] src,int r1,int c1,int r2,int c2,boolean lookahead){
        if(lookahead && rejectedMoves.contains(moveKey(r1,c1,r2,c2))) return null;
        if(src[r1][c1].type<0||src[r2][c2].type<0||src[r1][c1].type==src[r2][c2].type) return null;
        Cell[][] b=copy(src); Cell tmp=b[r1][c1];b[r1][c1]=b[r2][c2];b[r2][c2]=tmp;
        MatchInfo mi=findMatches(b); if(mi.cells.isEmpty()) return null;
        int movedA=r1*COLS+c1,movedB=r2*COLS+c2;
        if(!mi.cells.contains(movedA)&&!mi.cells.contains(movedB)) return null;
        double score=mi.cells.size()*18 + mi.fours*95 + mi.fives*280;
        int plus=0,special=0;
        for(int idx:mi.cells){Cell x=b[idx/COLS][idx%COLS]; if(x.plus)plus++; if(x.special)special++;}
        score += plus*100000 + special*500 + mi.lines*18;
        for(int idx:mi.cells) score += (idx/COLS)*1.5;
        Cell[][] after=resolveKnownGravity(b,mi);
        if(lookahead){ Move next=findBest(after,false); if(next!=null) score += Math.min(12000,next.score)*.58; score += potential(after)*2.2; score += plusSetupPotential(after)*220; }
        if(src[r1][c1].plus||src[r2][c2].plus) score+=5000;
        if(src[r1][c1].special||src[r2][c2].special) score+=75;
        return new Move(r1,c1,r2,c2,score);
    }

    private MatchInfo findMatches(Cell[][] b){
        MatchInfo m=new MatchInfo();
        for(int r=0;r<ROWS;r++){ int c=0; while(c<COLS){ int t=b[r][c].type,j=c+1; if(t<0){c++;continue;} while(j<COLS&&b[r][j].type==t)j++; int n=j-c; if(n>=3){m.lines++;if(n==4)m.fours++;if(n>=5)m.fives++;for(int k=c;k<j;k++)m.cells.add(r*COLS+k);} c=j; } }
        for(int c=0;c<COLS;c++){ int r=0; while(r<ROWS){ int t=b[r][c].type,j=r+1; if(t<0){r++;continue;} while(j<ROWS&&b[j][c].type==t)j++; int n=j-r; if(n>=3){m.lines++;if(n==4)m.fours++;if(n>=5)m.fives++;for(int k=r;k<j;k++)m.cells.add(k*COLS+c);} r=j; } }
        return m;
    }

    private Cell[][] resolveKnownGravity(Cell[][] b,MatchInfo mi){
        Cell[][] out=copy(b); for(int idx:mi.cells) out[idx/COLS][idx%COLS]=new Cell(EMPTY,false,false);
        for(int c=0;c<COLS;c++){ int wr=ROWS-1; for(int r=ROWS-1;r>=0;r--) if(out[r][c].type!=EMPTY){out[wr--][c]=out[r][c];} while(wr>=0) out[wr--][c]=new Cell(EMPTY,false,false); }
        return out;
    }

    private int potential(Cell[][] b){
        int p=0; for(int r=0;r<ROWS;r++) for(int c=0;c<COLS;c++){ int t=b[r][c].type;if(t<0)continue; if(c+1<COLS&&b[r][c+1].type==t)p++; if(r+1<ROWS&&b[r+1][c].type==t)p++; if(c+2<COLS&&b[r][c+2].type==t)p+=2; if(r+2<ROWS&&b[r+2][c].type==t)p+=2; if(b[r][c].plus)p+=10;if(b[r][c].special)p+=4; } return p;
    }

    private int plusSetupPotential(Cell[][] b){
        int p=0; for(int r=0;r<ROWS;r++) for(int c=0;c<COLS;c++){ Cell z=b[r][c]; if(!z.plus || z.type<0) continue; int t=z.type,same=0; for(int dr=-2;dr<=2;dr++) for(int dc=-2;dc<=2;dc++){ if(Math.abs(dr)+Math.abs(dc)==0 || Math.abs(dr)+Math.abs(dc)>2) continue; int rr=r+dr,cc=c+dc;if(rr<0||rr>=ROWS||cc<0||cc>=COLS)continue; if(b[rr][cc].type==t) same += (Math.abs(dr)+Math.abs(dc)==1)?3:1; } p += same; } return p;
    }

    private long boardHash(Cell[][] b){ long h=1469598103934665603L; for(int r=0;r<ROWS;r++) for(int c=0;c<COLS;c++){ Cell z=b[r][c]; long v=(z.type+17) | (z.plus?64:0) | (z.special?128:0); h^=v; h*=1099511628211L; } return h; }
    private String moveKey(Move m){ return moveKey(m.r1,m.c1,m.r2,m.c2); }
    private String moveKey(int r1,int c1,int r2,int c2){ int a=r1*COLS+c1,b=r2*COLS+c2; if(a>b){int t=a;a=b;b=t;} return a+":"+b; }
    private Cell[][] copy(Cell[][] in){ Cell[][] o=new Cell[ROWS][COLS]; for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++)o[r][c]=in[r][c].copy(); return o; }
}
