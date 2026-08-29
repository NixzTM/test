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

# Server switches must immediately discard the previous server UI/chat instead of leaving it visible
# while the new server request is in flight.
mark='  private void loadControl(){ loadControl(true); }'
if mark not in s: raise SystemExit('loadControl marker missing')
helper='''  private void switchServer(String srv){
    if(srv==null||!KEYS.containsKey(srv))return;
    stopLive();
    server=srv;pane="chat";
    chatList=null;chatScroller=null;chatComposer=null;
    lastChatSignature="";chatSavedY=0;chatStickBottom=true;chatRenderedOnce=false;chatHoldUntil=0L;
    screen=Screen.SERVER;title.setText("ΛRENA · "+srv);body.removeAllViews();
    body.addView(txt("Loading "+serverLabel(srv)+"…",16,MUTED,true));
    loadControl();
  }\n\n'''
s=s.replace(mark,helper+mark,1)

# Use the helper for both server selectors. It clears stale chat synchronously before loading.
old='b.setOnClickListener(v->{server=ss;pane="chat";loadControl();});'
count=s.count(old)
if count < 2: raise SystemExit('expected two server switch handlers, got '+str(count))
s=s.replace(old,'b.setOnClickListener(v->switchServer(ss));',2)

# Bind every load to the server selected when the load started. A late response from AC4 must never
# render after the user has already switched to AC7 (and vice versa).
load='''  private void loadControl(boolean startLoop){
    if(!ready())return;
    final String loadServer=server;
    screen=Screen.SERVER;if(startLoop)stopLive();setBusy(true,"LOADING");
    io.submit(()->{try{
      ParsedControl pc;
      if(isDirectServer(loadServer)){pc=loadMobileState(loadServer);pc.chat=loadMobileChat(loadServer);pc.console=loadMobileConsole(loadServer);}
      else{String key=KEYS.get(loadServer);HttpResult r=request("GET",BASE+"/control.php?server="+enc(key),null);ensureAdmin(r);pc=parseControl(r.body);}
      ParsedControl out=pc;
      runOnUiThread(()->{if(screen!=Screen.SERVER||!loadServer.equals(server))return;setBusy(false,"CONNECTED");renderControl(out);if(startLoop)startLive();});
    }catch(Exception e){runOnUiThread(()->{if(!loadServer.equals(server))return;setBusy(false,authenticated?"CONNECTED":"OFFLINE");toast("Load failed: "+msg(e));});}});
  }'''
s=repl(s,'  private void loadControl(boolean startLoop)',load)

# Live polling is also server-bound. Discard any poll result that belongs to a server no longer selected.
live='''  private void startLive(){
    stopLive();liveTask=timer.scheduleWithFixedDelay(()->{
      if(screen!=Screen.SERVER||!authenticated||destroyed)return;
      final String pollServer=server;
      try{
        ParsedControl pc;
        if(isDirectServer(pollServer)){pc=loadMobileState(pollServer);pc.chat=loadMobileChat(pollServer);pc.console=loadMobileConsole(pollServer);}
        else{HttpResult r=request("GET",BASE+"/control.php?server="+enc(KEYS.get(pollServer)),null);ensureAdmin(r);pc=parseControl(r.body);}
        ParsedControl out=pc;
        runOnUiThread(()->{if(screen!=Screen.SERVER||!pollServer.equals(server))return;if("chat".equals(pane)&&chatList!=null){updateChatListInPlace(out.chat);return;}if(chatComposer!=null&&chatComposer.hasFocus())return;if(SystemClock.uptimeMillis()<chatHoldUntil)return;renderControl(out);});
      }catch(Exception e){if(e.getMessage()!=null&&e.getMessage().contains("session"))sessionExpired();}
    },2,2,TimeUnit.SECONDS);
  }'''
s=repl(s,'  private void startLive()',live)

# Server-specific direct loaders. Keep no-arg wrappers for any existing callers, but all async control
# paths above now pass an immutable server id.
state='''  private ParsedControl loadMobileState()throws Exception{return loadMobileState(server);}\n  private ParsedControl loadMobileState(String srv)throws Exception{HttpResult r=mobileRequestFor(srv,"GET","/v1/state",null);if(r.code!=200)throw new Exception("Mobile addon HTTP "+r.code);JSONObject o=new JSONObject(r.body);ParsedControl p=new ParsedControl();JSONObject m=o.optJSONObject("map");if(m!=null){p.mapName=m.optString("name","");p.mapAuthor=m.optString("author","");}p.secondsLeft=o.optInt("seconds_left",-1);p.paused=o.optBoolean("paused",false);JSONArray a=o.optJSONArray("players");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String lg=x.optString("login","");String nn=x.optString("nickname",lg);p.players.add(new Player(lg,TmnfText.plain(nn),"",x.optBoolean("spectator",false)));}p.playerCount=p.players.size();return p;}'''
s=repl(s,'  private ParsedControl loadMobileState()',state)

chat='''  private List<ChatLine> loadMobileChat()throws Exception{return loadMobileChat(server);}\n  private List<ChatLine> loadMobileChat(String srv)throws Exception{HttpResult r=mobileRequestFor(srv,"GET","/v1/chat?limit=120",null);if(r.code!=200)throw new Exception("chat HTTP "+r.code);JSONArray a=new JSONArray(r.body);ArrayList<ChatLine> serverLines=new ArrayList<>();for(int i=a.length()-1;i>=0;i--){JSONObject x=a.optJSONObject(i);if(x==null)continue;ChatLine c=new ChatLine();c.sourceKey=srv;c.login=x.optString("login","");c.nickname=x.optString("nickname",c.login);c.message=x.optString("message","");serverLines.add(c);}String prior=lastChatKey(srv);String newestServer=serverLines.isEmpty()?prior:chatKey(serverLines.get(serverLines.size()-1));ArrayList<ChatLine> out=new ArrayList<>(serverLines);HashMap<String,Integer> insertedAfter=new HashMap<>();for(ChatLine local:localChat(srv)){String anchor=localSentAnchors.getOrDefault(local,"");int pos=-1;if(!anchor.isEmpty()){for(int i=out.size()-1;i>=0;i--){if(chatKey(out.get(i)).equals(anchor)){pos=i;break;}}}if(pos<0){out.add(local);continue;}int extra=insertedAfter.getOrDefault(anchor,0);int at=Math.min(pos+1+extra,out.size());out.add(at,local);insertedAfter.put(anchor,extra+1);}setLastChatKey(srv,newestServer);return out;}'''
s=repl(s,'  private List<ChatLine> loadMobileChat()',chat)

console='''  private ArrayList<ConsoleLine> loadMobileConsole()throws Exception{return loadMobileConsole(server);}\n  private ArrayList<ConsoleLine> loadMobileConsole(String srv)throws Exception{HttpResult r=mobileRequestFor(srv,"GET","/v1/console?lines=100",null);ArrayList<ConsoleLine> out=new ArrayList<>();if(r.code!=200)return out;JSONArray a=new JSONArray(r.body);for(int i=0;i<a.length();i++){ConsoleLine c=new ConsoleLine();c.result=a.optString(i,"");out.add(c);}return out;}'''
s=repl(s,'  private ArrayList<ConsoleLine> loadMobileConsole()',console)

# Helper overload for immutable-server checks.
needle='  private boolean isDirectServer(){return server.equals("AC3")||server.equals("AC4")||server.equals("AC7");}'
if needle not in s: raise SystemExit('isDirectServer marker missing')
s=s.replace(needle,'  private boolean isDirectServer(String srv){return "AC3".equals(srv)||"AC4".equals(srv)||"AC7".equals(srv);}\n  private boolean isDirectServer(){return isDirectServer(server);}',1)

p.write_text(s)
print('v2.18 stale cross-server chat UI/race fix applied')
