from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

def repl(src,sig,rep):
    i=src.find(sig)
    if i<0: raise SystemExit('missing '+sig)
    b=src.find('{',i);d=0;j=b
    while j<len(src):
        if src[j]=='{':d+=1
        elif src[j]=='}':
            d-=1
            if d==0:j+=1;break
        j+=1
    return src[:i]+rep+src[j:]

# persistent chat scroll state
needle='  private volatile long chatHoldUntil=0L;'
rep='''  private volatile long chatHoldUntil=0L;\n  private int chatSavedY=0;\n  private boolean chatStickBottom=true;\n  private boolean chatRenderedOnce=false;'''
if needle in s and 'chatSavedY' not in s:
    s=s.replace(needle,rep,1)

# Make Players nav unambiguous even if prior rewrite missed exact old callback.
s=s.replace('addNav("Players",this::loadControl);','addNav("Players",this::showPlayerDirectory);')
s=s.replace('addNav("Players", this::loadControl);','addNav("Players",this::showPlayerDirectory);')

# Add obvious directory button to server screen near online player heading.
old='body.addView(txt(pc.players.size()+" online players",22,Color.WHITE,true),lpTop(14));'
new='''LinearLayout ph=new LinearLayout(this);ph.setGravity(Gravity.CENTER_VERTICAL);ph.addView(txt(pc.players.size()+" online players",22,Color.WHITE,true),new LinearLayout.LayoutParams(0,dp(48),1));Button directory=secondary("PLAYER DIRECTORY");directory.setOnClickListener(v->showPlayerDirectory());ph.addView(directory,new LinearLayout.LayoutParams(dp(170),dp(48)));body.addView(ph,lpTop(14));'''
if old in s:
    s=s.replace(old,new,1)

chat='''  private void renderChatPane(LinearLayout live,ParsedControl pc){
    LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView lab=txt("GAME CHAT",11,MUTED,true);bar.addView(lab,new LinearLayout.LayoutParams(0,dp(34),1));Button latest=ghost("↓ LATEST");bar.addView(latest,new LinearLayout.LayoutParams(dp(94),dp(34)));live.addView(bar,lpTop(5));
    ScrollView sc=new ScrollView(this);chatScroller=sc;sc.setFillViewport(true);sc.setVerticalScrollBarEnabled(true);sc.setScrollbarFadingEnabled(false);sc.setSmoothScrollingEnabled(true);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(2),dp(2),dp(6),dp(8));sc.addView(list);live.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    sc.setOnScrollChangeListener((v,sx,sy,osx,osy)->{chatSavedY=sy;View child=sc.getChildAt(0);if(child!=null){int remain=child.getHeight()-(sc.getHeight()+sy);chatStickBottom=remain<dp(36);}});
    sc.setOnTouchListener((v,e)->{int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE){chatHoldUntil=SystemClock.uptimeMillis()+12000L;v.getParent().requestDisallowInterceptTouchEvent(true);}else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){chatHoldUntil=SystemClock.uptimeMillis()+6000L;v.getParent().requestDisallowInterceptTouchEvent(false);}return false;});
    latest.setOnClickListener(v->{chatStickBottom=true;chatSavedY=Integer.MAX_VALUE;chatHoldUntil=SystemClock.uptimeMillis()+1500L;sc.post(()->sc.fullScroll(View.FOCUS_DOWN));});
    if(pc.chat==null||pc.chat.isEmpty()){list.addView(txt(server.equals("AC3")?"Waiting for game chat…":"Direct chat addon is currently LJA only.",12,MUTED,false));}else for(ChatLine m:pc.chat){TextView line=txt("",13,Color.WHITE,false);line.setTextIsSelectable(true);SpannableStringBuilder b=new SpannableStringBuilder();String who=(m.nickname==null||m.nickname.isEmpty())?m.login:m.nickname;if(who==null||who.isEmpty())who="Unknown";b.append(TmnfText.render(who,CYAN));b.append("  ");b.append(TmnfText.render(m.message,Color.WHITE));line.setText(b);list.addView(line,lpTop(8));}
    LinearLayout send=new LinearLayout(this);EditText e=input("Send server message",false);chatComposer=e;e.setSingleLine(false);e.setMaxLines(3);e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);e.setOnFocusChangeListener((v,has)->{if(has)chatHoldUntil=Long.MAX_VALUE;else chatHoldUntil=SystemClock.uptimeMillis()+2500L;});Button b=blue("SEND");b.setOnClickListener(v->{String m=e.getText().toString().trim();if(!m.isEmpty()){e.clearFocus();chatHoldUntil=SystemClock.uptimeMillis()+2500L;mobileAction("chat.announce","",m,0);e.setText("");}});send.addView(e,new LinearLayout.LayoutParams(0,dp(58),1));send.addView(b,new LinearLayout.LayoutParams(dp(90),dp(58)));live.addView(send,lpTop(7));
    boolean first=!chatRenderedOnce;chatRenderedOnce=true;final boolean goBottom=first||chatStickBottom;final int restoreY=chatSavedY;sc.post(()->{if(goBottom)sc.fullScroll(View.FOCUS_DOWN);else sc.scrollTo(0,restoreY);});
  }'''
s=repl(s,'  private void renderChatPane(LinearLayout live,ParsedControl pc)',chat)

# reset scroll state when switching server/pane to chat intentionally
s=s.replace('server=s;pane="chat";loadControl();','server=s;pane="chat";chatRenderedOnce=false;chatStickBottom=true;chatSavedY=0;loadControl();')
s=s.replace('server=ss;showPlayerDirectory();','server=ss;showPlayerDirectory();')

p.write_text(s)
print('v2.8 chat scroll retention + visible player directory applied')
