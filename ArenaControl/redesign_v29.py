from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

# 1) Put PLAYERS in the actual bottom nav produced by v2.4/v2.5.
old='addNav("SERVERS",this::showDashboard);addNav("REFRESH",()->refreshCurrent(true));addNav("ANNOUNCE",this::showAnnounce);addNav("LOGOUT",this::logout);'
new='addNav("SERVERS",this::showDashboard);addNav("PLAYERS",this::showPlayerDirectory);addNav("ANNOUNCE",this::showAnnounce);addNav("REFRESH",()->refreshCurrent(true));addNav("LOGOUT",this::logout);'
if old not in s:
    raise SystemExit('bottom nav marker missing')
s=s.replace(old,new,1)

# 2) Put a highly visible PLAYER DIRECTORY button directly in the live server player card.
old='LinearLayout players=card();players.addView(txt("ONLINE PLAYERS ("+pc.players.size()+")",12,Color.WHITE,true));for(Player p:pc.players)players.addView(fullPlayerRow(p),lpTop(4));body.addView(players,lpTop(8));'
new='LinearLayout players=card();LinearLayout phead=new LinearLayout(this);phead.setGravity(Gravity.CENTER_VERTICAL);phead.addView(txt("ONLINE PLAYERS ("+pc.players.size()+")",12,Color.WHITE,true),new LinearLayout.LayoutParams(0,dp(44),1));Button directory=blue("PLAYER DIRECTORY");directory.setOnClickListener(v->showPlayerDirectory());phead.addView(directory,new LinearLayout.LayoutParams(dp(168),dp(44)));players.addView(phead);for(Player p:pc.players)players.addView(fullPlayerRow(p),lpTop(4));body.addView(players,lpTop(8));'
if old not in s:
    raise SystemExit('online players card marker missing')
s=s.replace(old,new,1)

# 3) Locally retain messages sent from the app. Server announcements do not arrive back through PlayerChat/ac_chatlog,
# so without this the sender cannot see their own app-originated message in the chat pane.
field='  private boolean chatRenderedOnce=false;'
if field not in s:
    raise SystemExit('chat state marker missing')
s=s.replace(field, field+'\n  private final CopyOnWriteArrayList<ChatLine> localSentChat=new CopyOnWriteArrayList<>();',1)

old_send='Button b=blue("SEND");b.setOnClickListener(v->{String m=e.getText().toString().trim();if(!m.isEmpty()){e.clearFocus();chatHoldUntil=SystemClock.uptimeMillis()+2500L;mobileAction("chat.announce","",m,0);e.setText("");}});'
new_send='Button b=blue("SEND");b.setOnClickListener(v->{String m=e.getText().toString().trim();if(!m.isEmpty()){ChatLine mine=new ChatLine();mine.sourceKey="AC3";mine.login=login;mine.nickname=login;mine.message=m;localSentChat.add(mine);while(localSentChat.size()>30)localSentChat.remove(0);chatStickBottom=true;e.clearFocus();chatHoldUntil=SystemClock.uptimeMillis()+2500L;mobileAction("chat.announce","",m,0);e.setText("");}});'
if old_send not in s:
    raise SystemExit('chat send marker missing')
s=s.replace(old_send,new_send,1)

# Merge local app-originated messages into the fetched LJA chat feed.
old_load='private List<ChatLine> loadMobileChat()throws Exception{HttpResult r=mobileRequest("GET","/v1/chat?limit=120",null);if(r.code!=200)throw new Exception("chat HTTP "+r.code);JSONArray a=new JSONArray(r.body);ArrayList<ChatLine> out=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;ChatLine c=new ChatLine();c.sourceKey="AC3";c.login=x.optString("login","");c.nickname=x.optString("nickname",c.login);c.message=x.optString("message","");out.add(c);}return out;}'
new_load='private List<ChatLine> loadMobileChat()throws Exception{HttpResult r=mobileRequest("GET","/v1/chat?limit=120",null);if(r.code!=200)throw new Exception("chat HTTP "+r.code);JSONArray a=new JSONArray(r.body);ArrayList<ChatLine> out=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;ChatLine c=new ChatLine();c.sourceKey="AC3";c.login=x.optString("login","");c.nickname=x.optString("nickname",c.login);c.message=x.optString("message","");out.add(c);}out.addAll(localSentChat);return out;}'
if old_load not in s:
    raise SystemExit('loadMobileChat marker missing')
s=s.replace(old_load,new_load,1)

# 4) Never force chat back to bottom during refresh. Initial open goes to newest once; after that preserve exact Y.
old_scroll='boolean first=!chatRenderedOnce;chatRenderedOnce=true;final boolean goBottom=first||chatStickBottom;final int restoreY=chatSavedY;sc.post(()->{if(goBottom)sc.fullScroll(View.FOCUS_DOWN);else sc.scrollTo(0,restoreY);});'
new_scroll='boolean first=!chatRenderedOnce;chatRenderedOnce=true;final int restoreY=chatSavedY;sc.post(()->{if(first)sc.fullScroll(View.FOCUS_DOWN);else sc.scrollTo(0,restoreY);});'
if old_scroll not in s:
    raise SystemExit('chat scroll marker missing')
s=s.replace(old_scroll,new_scroll,1)

p.write_text(s)
print('v2.9 visible player management + own chat + stable chat scroll applied')
