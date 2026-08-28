from pathlib import Path
import re

p = Path('AutoPilot2D/app/src/main/java/com/nixz/autopilot2d/Match3Bot.java')
s = p.read_text()

s = s.replace(
    'private final HashSet<String> rejectedMoves=new HashSet<>();',
    'private final HashSet<String> rejectedMoves=new HashSet<>();\n    private final Random rng=new Random();'
)

old = '''        for(Move m:legal){
            m.score=immediateScore(board.c,m);
            m.after=simulate(board.c,m);
            if(m.after!=null){
                double future=search(m.after,2,.58);
                if(hasImmediatePlusClear(m.after)) m.score += 250000;
                m.score += future;
            }
            if(m.clearsPlus) m.score += 1000000;
        }'''
new = '''        int[] dist=colorDistribution(board.c);
        for(Move m:legal){
            m.score=immediateScore(board.c,m);
            m.after=simulate(board.c,m);
            if(m.after!=null){
                if(hasImmediatePlusClear(m.after)) m.score += 300000;
                m.score += sampledExpectimax(m.after,dist,3,6,.62);
            }
            if(m.clearsPlus) m.score += 2000000;
        }'''
if old not in s:
    raise SystemExit('main planner block not found')
s = s.replace(old, new)

old2 = '''    private double search(Cell[][] b,int depth,double discount){
        if(depth<=0)return heuristic(b)*discount; List<Move> moves=legalMoves(b,false); if(moves.isEmpty())return heuristic(b)*discount;
        for(Move m:moves){m.score=immediateScore(b,m);m.after=simulate(b,m);if(m.clearsPlus)m.score+=1000000;} moves.sort((a,z)->Double.compare(z.score,a.score));
        double best=-1e18; for(int i=0;i<Math.min(8,moves.size());i++){Move m=moves.get(i);double v=m.score;if(m.after!=null)v+=search(m.after,depth-1,discount*.58);best=Math.max(best,v);} return best*discount;
    }'''
new2 = '''    private int[] colorDistribution(Cell[][] b){
        int[] d=new int[8]; int total=0;
        for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++) if(b[r][c].type>=0&&b[r][c].type<d.length){d[b[r][c].type]++;total++;}
        if(total==0)for(int i=0;i<6;i++)d[i]=1;
        return d;
    }

    private int randomColor(int[] dist){
        int total=0; for(int v:dist)total+=v; if(total<=0)return rng.nextInt(6);
        int x=rng.nextInt(total); for(int i=0;i<dist.length;i++){x-=dist[i];if(x<0)return i;} return 0;
    }

    private Cell[][] refillSample(Cell[][] src,int[] dist){
        Cell[][] b=copy(src);
        for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++) if(b[r][c].type==EMPTY) b[r][c]=new Cell(randomColor(dist),false,false,1);
        return b;
    }

    private double sampledExpectimax(Cell[][] after,int[] dist,int depth,int chanceSamples,double discount){
        if(depth<=0)return heuristic(after)*discount;
        double total=0;
        for(int sample=0;sample<chanceSamples;sample++){
            Cell[][] state=refillSample(after,dist);
            for(int cascade=0;cascade<3;cascade++){
                MatchInfo mi=findMatches(state); if(mi.cells.isEmpty())break;
                for(int idx:mi.cells)state[idx/COLS][idx%COLS]=new Cell(EMPTY,false,false,1);
                for(int c=0;c<COLS;c++){
                    int wr=ROWS-1;
                    for(int r=ROWS-1;r>=0;r--)if(state[r][c].type!=EMPTY)state[wr--][c]=state[r][c];
                    while(wr>=0)state[wr--][c]=new Cell(randomColor(dist),false,false,1);
                }
            }
            List<Move> moves=legalMoves(state,false);
            if(moves.isEmpty()){total+=heuristic(state);continue;}
            for(Move m:moves){m.score=immediateScore(state,m);m.after=simulate(state,m);if(m.clearsPlus)m.score+=2000000;}
            moves.sort((a,z)->Double.compare(z.score,a.score));
            double best=-1e18;
            for(int i=0;i<Math.min(7,moves.size());i++){
                Move m=moves.get(i); double v=m.score;
                if(m.after!=null)v+=sampledExpectimax(m.after,dist,depth-1,Math.max(2,chanceSamples/2),discount*.62);
                if(v>best)best=v;
            }
            total+=best;
        }
        return (total/Math.max(1,chanceSamples))*discount;
    }'''
if old2 not in s:
    raise SystemExit('search block not found')
s = s.replace(old2, new2)
s = s.replace(
    'BotRuntime.setStatus("Match-3 AI: +1="+board.plusCount+", +1 clears="+plusLegal+", moves="+legal.size());',
    'BotRuntime.setStatus("Match-3 AI: +1="+board.plusCount+", clears="+plusLegal+", expectimax d3 x6");'
)
p.write_text(s)

bp = Path('AutoPilot2D/app/build.gradle')
b = bp.read_text()
b = re.sub(r'versionCode\s+\d+', 'versionCode 10', b)
b = re.sub(r'versionName\s+[\"\'][^\"\']+[\"\']', 'versionName "1.0-match3-expectimax"', b)
bp.write_text(b)
