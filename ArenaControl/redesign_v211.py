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

# Persistent chat list: polling updates only the list when message content actually changes.
needle='  private final CopyOnWriteArrayList<ChatLine> localSentChat=new CopyOnWriteArrayList<>();'
if needle not in s: raise SystemExit('localSentChat marker missing')
s=s.replace(needle, needle+'\n  private LinearLayout chatList;\n  private String lastChatSignature="";',1)

# Do NOT reconstruct the entire server screen every 2 seconds while CHAT is open.
# Update the existing chat list in place, and only when the feed content changed.
live='''  private void startLive(){stopLive();liveTask=timer.scheduleWithFixedDelay(()->{if(screen!=Screen.SERVER||!authenticated||destroyed)return;try{ParsedControl pc;if(server.equals("AC3")){pc=loadMobileState();pc.chat=loadMobileChat();pc.console=loadMobileConsole();}else{HttpResult r=request("GET",BASE+"/control.php?server="+enc(KEYS.get(server)),null);ensureAdmin(r);pc=parseControl(r.body);}ParsedControl out=pc;runOnUiThread(()->{if(screen!=Screen.SERVER)return;if("chat".equals(pane)&&chatList!=null){updateChatListInPlace(out.chat);return;}if(chatComposer!=null&&chatComposer.hasFocus())return;if(SystemClock.uptimeMillis()<chatHoldUntil)return;renderControl(out);});}catch(Exception e){if(e.getMessage()!=null&&e.getMessage().contains("session"))sessionExpired();}},2,2,TimeUnit.SECONDS);}'''
s=repl(s,'  private void startLive()',live)

# Replace renderChatPane with a stable container. It is created once per screen render; subsequent
# polling updates chatList directly instead of rebuilding ScrollView/EditText/body.
chat='''  private void renderChatPane(LinearLayout live,ParsedControl pc){
    LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView lab=txt("GAME CHAT",11,MUTED,true);bar.addView(lab,new LinearLayout.LayoutParams(0,dp(34),1));Button latest=ghost("↓ LATEST");bar.addView(latest,new LinearLayout.LayoutParams(dp(94),dp(34)));live.addView(bar,lpTop(5));
    ScrollView sc=new ScrollView(this);chatScroller=sc;sc.setFillViewport(true);sc.setVerticalScrollBarEnabled(true);sc.setScrollbarFadingEnabled(false);sc.setSmoothScrollingEnabled(true);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(2),dp(2),dp(6),dp(8));chatList=list;sc.addView(list);live.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    sc.setOnScrollChangeListener((v,sx,sy,osx,osy)->{chatSavedY=sy;View child=sc.getChildAt(0);if(child!=null){int remain=child.getHeight()-(sc.getHeight()+sy);chatStickBottom=remain<dp(36);}});
    sc.setOnTouchListener((v,e)->{int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE){chatHoldUntil=SystemClock.uptimeMillis()+12000L;v.getParent().requestDisallowInterceptTouchEvent(true);}else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){chatHoldUntil=SystemClock.uptimeMillis()+6000L;v.getParent().requestDisallowInterceptTouchEvent(false);}return false;});
    latest.setOnClickListener(v->{chatStickBottom=true;chatSavedY=Integer.MAX_VALUE;chatHoldUntil=SystemClock.uptimeMillis()+1500L;sc.post(()->sc.fullScroll(View.FOCUS_DOWN));});
    lastChatSignature="";updateChatListInPlace(pc.chat);
    LinearLayout send=new LinearLayout(this);EditText e=input("Send server message",false);chatComposer=e;e.setSingleLine(false);e.setMaxLines(3);e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);e.setOnFocusChangeListener((v,has)->{if(has)chatHoldUntil=Long.MAX_VALUE;else chatHoldUntil=SystemClock.uptimeMillis()+2500L;});Button b=blue("SEND");b.setOnClickListener(v->{String m=e.getText().toString().trim();if(!m.isEmpty()){ChatLine mine=new ChatLine();mine.sourceKey="AC3";mine.login=login;mine.nickname=login;mine.message=m;localSentChat.add(mine);while(localSentChat.size()>30)localSentChat.remove(0);chatStickBottom=true;e.clearFocus();chatHoldUntil=SystemClock.uptimeMillis()+2500L;mobileAction("chat.announce","",m,0);e.setText("");}});send.addView(e,new LinearLayout.LayoutParams(0,dp(58),1));send.addView(b,new LinearLayout.LayoutParams(dp(90),dp(58)));live.addView(send,lpTop(7));
    boolean first=!chatRenderedOnce;chatRenderedOnce=true;if(first)sc.post(()->sc.fullScroll(View.FOCUS_DOWN));
  }'''
s=repl(s,'  private void renderChatPane(LinearLayout live,ParsedControl pc)',chat)

mark='  private void renderConsolePane(LinearLayout live,ParsedControl pc)'
helpers='''  private String chatSignature(List<ChatLine> lines){StringBuilder x=new StringBuilder();if(lines!=null)for(ChatLine m:lines)x.append(m.login).append('\\u0001').append(m.nickname).append('\\u0001').append(m.message).append('\\u0002');return x.toString();}\n  private void updateChatListInPlace(List<ChatLine> lines){if(chatList==null)return;String sig=chatSignature(lines);if(sig.equals(lastChatSignature))return;boolean bottom=chatStickBottom;int y=chatSavedY;lastChatSignature=sig;chatList.removeAllViews();if(lines==null||lines.isEmpty()){chatList.addView(txt(server.equals("AC3")?"Waiting for game chat…":"Direct chat addon is currently LJA only.",12,MUTED,false));}else for(ChatLine m:lines){TextView line=txt("",13,Color.WHITE,false);line.setTextIsSelectable(true);SpannableStringBuilder b=new SpannableStringBuilder();String who=(m.nickname==null||m.nickname.isEmpty())?m.login:m.nickname;if(who==null||who.isEmpty())who="Unknown";b.append(TmnfText.render(who,CYAN));b.append("  ");b.append(TmnfText.render(m.message,Color.WHITE));line.setText(b);chatList.addView(line,lpTop(8));}if(chatScroller!=null)chatScroller.post(()->{if(bottom)chatScroller.fullScroll(View.FOCUS_DOWN);else chatScroller.scrollTo(0,y);});}\n\n'''
if mark not in s: raise SystemExit('console marker missing')
s=s.replace(mark,helpers+mark,1)

p.write_text(s)
print('v2.11 stable in-place chat polling applied')
