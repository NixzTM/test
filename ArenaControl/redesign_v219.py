from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

def repl(src,sig,rep):
    i=src.find(sig)
    if i<0: raise SystemExit('missing '+sig)
    b=src.find('{',i); d=0; j=b
    while j<len(src):
        if src[j]=='{': d+=1
        elif src[j]=='}':
            d-=1
            if d==0:
                j+=1; break
        j+=1
    return src[:i]+rep+src[j:]

# Keep the raw TMNF nickname (including $ color/style codes) from the mobile addon.
s=s.replace('p.players.add(new Player(lg,TmnfText.plain(nn),"",x.optBoolean("spectator",false)));',
            'p.players.add(new Player(lg,TmnfText.plain(nn),nn,x.optBoolean("spectator",false)));')

# Add a chosen display nickname for app-originated server chat. Default is the website/TMNF login.
needle='  private String server="AC3", login="", role="", pane="chat";'
if needle not in s: raise SystemExit('state marker missing')
s=s.replace(needle,needle+'\n  private String chatNickname="";',1)

# Render direct-server raw TMNF nicknames with the native TMNF formatter; retain HTML fallback for old sources.
mark='  private View fullPlayerRow(Player p)'
if mark not in s: raise SystemExit('player row marker missing')
helper='''  private CharSequence renderedPlayerNick(Player p){
    if(p==null)return "";
    String raw=p.nickHtml==null?"":p.nickHtml;
    if(raw.indexOf('$')>=0)return TmnfText.render(raw,Color.WHITE);
    if(!raw.isEmpty())return renderNickHtml(raw);
    return TmnfText.render(p.plainNick==null?"":p.plainNick,Color.WHITE);
  }\n\n'''
s=s.replace(mark,helper+mark,1)
# Replace both common nickname-render expressions if present.
s=s.replace('p.nickHtml.isEmpty()?TmnfText.render(p.plainNick,Color.WHITE):renderNickHtml(p.nickHtml)','renderedPlayerNick(p)')
s=s.replace('p.nickHtml.isEmpty()?p.plainNick:renderNickHtml(p.nickHtml)','renderedPlayerNick(p)')

# Helper used by formatting buttons to insert TMNF codes at the cursor.
mark='  private void renderChatPane(LinearLayout live,ParsedControl pc)'
if mark not in s: raise SystemExit('chat pane marker missing')
fmt_helpers='''  private void insertChatCode(EditText e,String code){int a=Math.max(0,e.getSelectionStart()),b=Math.max(0,e.getSelectionEnd());if(a>b){int t=a;a=b;b=t;}e.getText().replace(a,b,code);e.setSelection(Math.min(e.length(),a+code.length()));}\n  private Button fmtButton(String label,String code,EditText e){Button b=ghost(label);b.setTextSize(10);b.setOnClickListener(v->insertChatCode(e,code));return b;}\n\n'''
s=s.replace(mark,fmt_helpers+mark,1)

chat='''  private void renderChatPane(LinearLayout live,ParsedControl pc){
    final String paneServer=server;
    LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView lab=txt("GAME CHAT",11,MUTED,true);bar.addView(lab,new LinearLayout.LayoutParams(0,dp(34),1));Button latest=ghost("↓ LATEST");bar.addView(latest,new LinearLayout.LayoutParams(dp(94),dp(34)));live.addView(bar,lpTop(5));
    ScrollView sc=new ScrollView(this);chatScroller=sc;sc.setFillViewport(true);sc.setVerticalScrollBarEnabled(true);sc.setScrollbarFadingEnabled(false);sc.setSmoothScrollingEnabled(true);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(2),dp(2),dp(6),dp(8));chatList=list;sc.addView(list);live.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    sc.setOnScrollChangeListener((v,sx,sy,osx,osy)->{chatSavedY=sy;View child=sc.getChildAt(0);if(child!=null){int remain=child.getHeight()-(sc.getHeight()+sy);chatStickBottom=remain<dp(36);}});
    sc.setOnTouchListener((v,e)->{int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE){chatHoldUntil=SystemClock.uptimeMillis()+12000L;v.getParent().requestDisallowInterceptTouchEvent(true);}else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){chatHoldUntil=SystemClock.uptimeMillis()+6000L;v.getParent().requestDisallowInterceptTouchEvent(false);}return false;});
    latest.setOnClickListener(v->{chatStickBottom=true;chatSavedY=Integer.MAX_VALUE;chatHoldUntil=SystemClock.uptimeMillis()+1500L;sc.post(()->sc.fullScroll(View.FOCUS_DOWN));});
    lastChatSignature="";updateChatListInPlace(pc.chat);

    LinearLayout nickRow=new LinearLayout(this);nickRow.setGravity(Gravity.CENTER_VERTICAL);TextView nl=txt("NICK",10,MUTED,true);nickRow.addView(nl,new LinearLayout.LayoutParams(dp(46),dp(42)));EditText nick=input("Nickname / TMNF codes",false);nick.setSingleLine(true);nick.setText(chatNickname.isEmpty()?login:chatNickname);nickRow.addView(nick,new LinearLayout.LayoutParams(0,dp(42),1));Button useNick=ghost("USE");useNick.setOnClickListener(v->{chatNickname=nick.getText().toString().trim();if(chatNickname.isEmpty())chatNickname=login;toast("Chat nickname set");});nickRow.addView(useNick,new LinearLayout.LayoutParams(dp(64),dp(42)));live.addView(nickRow,lpTop(7));
    TextView nickPreview=txt("",13,Color.WHITE,true);nickPreview.setText(TmnfText.render(nick.getText().toString(),Color.WHITE));live.addView(nickPreview,lpTop(3));nick.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int st,int c,int a){}public void onTextChanged(CharSequence x,int st,int b,int c){nickPreview.setText(TmnfText.render(x.toString(),Color.WHITE));}public void afterTextChanged(android.text.Editable x){}});

    EditText e=input("Message · TMNF $ codes supported",false);chatComposer=e;e.setSingleLine(false);e.setMaxLines(3);e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);e.setOnFocusChangeListener((v,has)->{if(has)chatHoldUntil=Long.MAX_VALUE;else chatHoldUntil=SystemClock.uptimeMillis()+2500L;});
    LinearLayout fmt1=new LinearLayout(this);String[][] f1={{"B","$o"},{"I","$i"},{"RESET","$z"},{"WHITE","$fff"},{"CYAN","$6ff"}};for(String[] q:f1)fmt1.addView(fmtButton(q[0],q[1],e),new LinearLayout.LayoutParams(0,dp(34),1));live.addView(fmt1,lpTop(7));
    LinearLayout fmt2=new LinearLayout(this);String[][] f2={{"RED","$f33"},{"GREEN","$3f3"},{"YELLOW","$ff3"},{"BLUE","$39f"},{"PINK","$f6f"}};for(String[] q:f2)fmt2.addView(fmtButton(q[0],q[1],e),new LinearLayout.LayoutParams(0,dp(34),1));live.addView(fmt2,lpTop(3));
    TextView preview=txt("Preview",13,MUTED,false);live.addView(preview,lpTop(5));e.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int st,int c,int a){}public void onTextChanged(CharSequence x,int st,int b,int c){preview.setText(TmnfText.render(x.toString(),Color.WHITE));}public void afterTextChanged(android.text.Editable x){}});

    LinearLayout send=new LinearLayout(this);send.addView(e,new LinearLayout.LayoutParams(0,dp(58),1));Button b=blue("SEND");b.setOnClickListener(v->{String m=e.getText().toString().trim();if(m.isEmpty())return;String sendServer=paneServer;if(!sendServer.equals(server))return;String chosen=nick.getText().toString().trim();if(chosen.isEmpty())chosen=login;chatNickname=chosen;ChatLine mine=new ChatLine();mine.sourceKey=sendServer;mine.login=login;mine.nickname=chosen;mine.message=m;CopyOnWriteArrayList<ChatLine> local=localChat(sendServer);local.add(mine);localSentAnchors.put(mine,lastChatKey(sendServer));while(local.size()>30){ChatLine oldLocal=local.remove(0);localSentAnchors.remove(oldLocal);}chatStickBottom=true;e.clearFocus();chatHoldUntil=SystemClock.uptimeMillis()+2500L;String wire=chosen+"$z$fff: "+m;mobileActionFor(sendServer,"chat.announce","",wire,0);e.setText("");});send.addView(b,new LinearLayout.LayoutParams(dp(90),dp(58)));live.addView(send,lpTop(7));
    boolean first=!chatRenderedOnce;chatRenderedOnce=true;if(first)sc.post(()->sc.fullScroll(View.FOCUS_DOWN));
  }'''
s=repl(s,'  private void renderChatPane(LinearLayout live,ParsedControl pc)',chat)

p.write_text(s)
print('v2.19 TMNF nickname rendering + nickname chooser + formatted chat composer applied')
