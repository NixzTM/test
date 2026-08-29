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

# Persistent chat interaction state. Prevent live refresh from destroying the EditText/keyboard
# while typing, and temporarily freeze redraws while the user scrolls chat.
needle='  private ScheduledFuture<?> liveTask, keepAliveTask;'
insert='''  private ScheduledFuture<?> liveTask, keepAliveTask;\n  private EditText chatComposer;\n  private ScrollView chatScroller;\n  private volatile long chatHoldUntil=0L;'''
if needle not in s: raise SystemExit('field marker missing')
s=s.replace(needle,insert,1)

# Human-facing server labels only. Internal keys stay AC3/AC4/AC7.
mark='  private void showLogin()'
helper='''  private String serverLabel(String key){\n    if("AC3".equals(key))return "LJA";\n    if("AC4".equals(key))return "HUNT #1";\n    if("AC7".equals(key))return "HUNT #2";\n    return key;\n  }\n\n'''
if mark not in s: raise SystemExit('showLogin marker missing')
s=s.replace(mark,helper+mark,1)

# Dashboard visible labels.
s=s.replace('TextView sn=txt(s,24,Color.WHITE,true);','TextView sn=txt(serverLabel(s),24,Color.WHITE,true);')
s=s.replace('Button b=secondary("OPEN "+s);','Button b=secondary("OPEN "+serverLabel(s));')

# Server tab labels in v2.5 renderControl.
s=s.replace('Button b=serverButton(ss,ss.equals(server));','Button b=serverButton(serverLabel(ss),ss.equals(server));')

# Keep keyboard/composer alive during background polling. Also stop redraws while the
# nested chat scroller is actively being used.
start='''  private void startLive(){stopLive();liveTask=timer.scheduleWithFixedDelay(()->{if(screen!=Screen.SERVER||!authenticated||destroyed)return;try{ParsedControl pc;if(server.equals("AC3")){pc=loadMobileState();pc.chat=loadMobileChat();pc.console=loadMobileConsole();}else{HttpResult r=request("GET",BASE+"/control.php?server="+enc(KEYS.get(server)),null);ensureAdmin(r);pc=parseControl(r.body);}ParsedControl out=pc;runOnUiThread(()->{if(screen!=Screen.SERVER)return;if(chatComposer!=null&&chatComposer.hasFocus())return;if(SystemClock.uptimeMillis()<chatHoldUntil)return;renderControl(out);});}catch(Exception e){if(e.getMessage()!=null&&e.getMessage().contains("session"))sessionExpired();}},2,2,TimeUnit.SECONDS);}'''
s=repl(s,'  private void startLive()',start)

chat='''  private void renderChatPane(LinearLayout live,ParsedControl pc){
    LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView lab=txt("GAME CHAT",11,MUTED,true);bar.addView(lab,new LinearLayout.LayoutParams(0,dp(34),1));Button latest=ghost("↓ LATEST");bar.addView(latest,new LinearLayout.LayoutParams(dp(94),dp(34)));live.addView(bar,lpTop(5));
    ScrollView sc=new ScrollView(this);chatScroller=sc;sc.setFillViewport(true);sc.setVerticalScrollBarEnabled(true);sc.setScrollbarFadingEnabled(false);sc.setSmoothScrollingEnabled(true);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(2),dp(2),dp(6),dp(8));sc.addView(list);live.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    sc.setOnTouchListener((v,e)->{int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE){chatHoldUntil=SystemClock.uptimeMillis()+12000L;v.getParent().requestDisallowInterceptTouchEvent(true);}else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){chatHoldUntil=SystemClock.uptimeMillis()+6000L;v.getParent().requestDisallowInterceptTouchEvent(false);}return false;});
    latest.setOnClickListener(v->{chatHoldUntil=SystemClock.uptimeMillis()+3000L;sc.post(()->sc.fullScroll(View.FOCUS_DOWN));});
    if(pc.chat==null||pc.chat.isEmpty()){list.addView(txt(server.equals("AC3")?"Waiting for game chat…":"Direct chat addon is currently LJA only.",12,MUTED,false));}else for(ChatLine m:pc.chat){TextView line=txt("",13,Color.WHITE,false);line.setTextIsSelectable(true);SpannableStringBuilder b=new SpannableStringBuilder();b.append(TmnfText.render(m.nickname.isEmpty()?m.login:m.nickname,CYAN));b.append("  ");b.append(TmnfText.render(m.message,Color.WHITE));line.setText(b);list.addView(line,lpTop(8));}
    LinearLayout send=new LinearLayout(this);EditText e=input("Send server message",false);chatComposer=e;e.setSingleLine(false);e.setMaxLines(3);e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);e.setOnFocusChangeListener((v,has)->{if(has)chatHoldUntil=Long.MAX_VALUE;else chatHoldUntil=SystemClock.uptimeMillis()+2500L;});Button b=blue("SEND");b.setOnClickListener(v->{String m=e.getText().toString().trim();if(!m.isEmpty()){e.clearFocus();chatHoldUntil=SystemClock.uptimeMillis()+2500L;mobileAction("chat.announce","",m,0);e.setText("");}});send.addView(e,new LinearLayout.LayoutParams(0,dp(58),1));send.addView(b,new LinearLayout.LayoutParams(dp(90),dp(58)));live.addView(send,lpTop(7));
    sc.post(()->sc.fullScroll(View.FOCUS_DOWN));
  }'''
s=repl(s,'  private void renderChatPane(LinearLayout live,ParsedControl pc)',chat)

# More room for messages; still full-width portrait layout.
s=s.replace('body.addView(live,new LinearLayout.LayoutParams(-1,dp(360)));','body.addView(live,new LinearLayout.LayoutParams(-1,dp(520)));')

# Keep title human-readable if any existing screen title includes raw key.
s=s.replace('title.setText("ΛRENA · "+server);','title.setText("ΛRENA · "+serverLabel(server));')

p.write_text(s)
print('v2.6 chat UX and labels applied')
