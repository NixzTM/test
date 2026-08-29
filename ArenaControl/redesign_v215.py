from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

def repl(src,sig,rep):
    i=src.find(sig)
    if i<0: raise SystemExit('missing '+sig)
    b=src.find('{',i);d=0;j=b
    while j<len(src):
        if src[j]=='{': d+=1
        elif src[j]=='}':
            d-=1
            if d==0:
                j+=1; break
        j+=1
    return src[:i]+rep+src[j:]

# Keep app-originated chat state isolated per server.
s=s.replace('  private final CopyOnWriteArrayList<ChatLine> localSentChat=new CopyOnWriteArrayList<>();',
'''  private final ConcurrentHashMap<String,CopyOnWriteArrayList<ChatLine>> localSentChatByServer=new ConcurrentHashMap<>();''',1)
s=s.replace('  private volatile String lastServerChatKey="";',
'''  private final ConcurrentHashMap<String,String> lastServerChatKeyByServer=new ConcurrentHashMap<>();''',1)

mark='  private boolean isDirectServer()'
if mark not in s: raise SystemExit('direct server helper marker missing')
helpers='''  private CopyOnWriteArrayList<ChatLine> localChat(String srv){return localSentChatByServer.computeIfAbsent(srv,k->new CopyOnWriteArrayList<>());}\n  private String lastChatKey(String srv){return lastServerChatKeyByServer.getOrDefault(srv,"");}\n  private void setLastChatKey(String srv,String key){lastServerChatKeyByServer.put(srv,key==null?"":key);}\n  private String mobileBase(String srv){if("AC4".equals(srv))return MOBILE_AC4;if("AC7".equals(srv))return MOBILE_AC7;return MOBILE_AC3;}\n'''
s=s.replace(mark,helpers+mark,1)

# Avoid ambiguous duplicate helper routing.
s=s.replace('  private String mobileBase(){if(server.equals("AC4"))return MOBILE_AC4;if(server.equals("AC7"))return MOBILE_AC7;return MOBILE_AC3;}\n','  private String mobileBase(){return mobileBase(server);}\n',1)

# Stable chat pane: local echo belongs only to the server visible when SEND was pressed.
old='''Button b=blue("SEND");b.setOnClickListener(v->{String m=e.getText().toString().trim();if(!m.isEmpty()){ChatLine mine=new ChatLine();mine.sourceKey="AC3";mine.login=login;mine.nickname=login;mine.message=m;localSentChat.add(mine);localSentAnchors.put(mine,lastServerChatKey);while(localSentChat.size()>30){ChatLine oldLocal=localSentChat.remove(0);localSentAnchors.remove(oldLocal);}chatStickBottom=true;e.clearFocus();chatHoldUntil=SystemClock.uptimeMillis()+2500L;mobileAction("chat.announce","",m,0);e.setText("");}});'''
new='''Button b=blue("SEND");b.setOnClickListener(v->{String m=e.getText().toString().trim();if(!m.isEmpty()){String sendServer=server;ChatLine mine=new ChatLine();mine.sourceKey=sendServer;mine.login=login;mine.nickname=login;mine.message=m;CopyOnWriteArrayList<ChatLine> local=localChat(sendServer);local.add(mine);localSentAnchors.put(mine,lastChatKey(sendServer));while(local.size()>30){ChatLine oldLocal=local.remove(0);localSentAnchors.remove(oldLocal);}chatStickBottom=true;e.clearFocus();chatHoldUntil=SystemClock.uptimeMillis()+2500L;mobileActionFor(sendServer,"chat.announce","",m,0);e.setText("");}});'''
if old not in s: raise SystemExit('chat send marker missing')
s=s.replace(old,new,1)

# Server-bound action transport prevents switching tabs while a queued action is executing from
# redirecting that action/announcement to another server.
mobile_action='''  private void mobileAction(String action,String target,String text,int value){mobileActionFor(server,action,target,text,value);}\n  private void mobileActionFor(String srv,String action,String target,String text,int value){if(!ready())return;setBusy(true,"SENDING");io.submit(()->{try{JSONObject j=new JSONObject();j.put("action",action);j.put("actor",login);j.put("target",target==null?"":target);j.put("text",text==null?"":text);j.put("value",value);HttpResult r=mobileRequestFor(srv,"POST","/v1/action",j.toString());if(r.code!=200){String rb=r.body==null?"":r.body;if("player.unban".equals(action)&&rb.contains("unban recorded locally but RPC failed")){runOnUiThread(()->{setBusy(false,"CONNECTED");toast("Unbanned");showPlayerDirectory();});return;}throw new Exception("Mobile addon HTTP "+r.code+": "+rb);}JSONObject o=new JSONObject(r.body);if(!o.optBoolean("ok",false))throw new Exception(o.optString("error","Action rejected"));runOnUiThread(()->{setBusy(false,"CONNECTED");toast(o.optString("result","Done"));if("player.unban".equals(action))showPlayerDirectory();else if(srv.equals(server))loadControl(false);});}catch(Exception e){runOnUiThread(()->{setBusy(false,"CONNECTED");toast("Action failed: "+msg(e));});}});}\n'''
s=repl(s,'  private void mobileAction(String action,String target,String text,int value)',mobile_action)

state='''  private ParsedControl loadMobileState()throws Exception{String srv=server;HttpResult r=mobileRequestFor(srv,"GET","/v1/state",null);if(r.code!=200)throw new Exception("Mobile addon HTTP "+r.code);JSONObject o=new JSONObject(r.body);ParsedControl p=new ParsedControl();JSONObject m=o.optJSONObject("map");if(m!=null){p.mapName=m.optString("name","");p.mapAuthor=m.optString("author","");}p.secondsLeft=o.optInt("seconds_left",-1);p.paused=o.optBoolean("paused",false);JSONArray a=o.optJSONArray("players");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String lg=x.optString("login","");String nn=x.optString("nickname",lg);p.players.add(new Player(lg,TmnfText.plain(nn),"",x.optBoolean("spectator",false)));}p.playerCount=p.players.size();return p;}'''
s=repl(s,'  private ParsedControl loadMobileState()',state)

chat='''  private String chatKey(ChatLine c){if(c==null)return "";return (c.login==null?"":c.login)+"\\u0001"+(c.nickname==null?"":c.nickname)+"\\u0001"+(c.message==null?"":c.message);}\n  private List<ChatLine> loadMobileChat()throws Exception{String srv=server;HttpResult r=mobileRequestFor(srv,"GET","/v1/chat?limit=120",null);if(r.code!=200)throw new Exception("chat HTTP "+r.code);JSONArray a=new JSONArray(r.body);ArrayList<ChatLine> serverLines=new ArrayList<>();for(int i=a.length()-1;i>=0;i--){JSONObject x=a.optJSONObject(i);if(x==null)continue;ChatLine c=new ChatLine();c.sourceKey=srv;c.login=x.optString("login","");c.nickname=x.optString("nickname",c.login);c.message=x.optString("message","");serverLines.add(c);}String prior=lastChatKey(srv);String newestServer=serverLines.isEmpty()?prior:chatKey(serverLines.get(serverLines.size()-1));ArrayList<ChatLine> out=new ArrayList<>(serverLines);HashMap<String,Integer> insertedAfter=new HashMap<>();for(ChatLine local:localChat(srv)){String anchor=localSentAnchors.getOrDefault(local,"");int pos=-1;if(!anchor.isEmpty()){for(int i=out.size()-1;i>=0;i--){if(chatKey(out.get(i)).equals(anchor)){pos=i;break;}}}if(pos<0){out.add(local);continue;}int extra=insertedAfter.getOrDefault(anchor,0);int at=Math.min(pos+1+extra,out.size());out.add(at,local);insertedAfter.put(anchor,extra+1);}setLastChatKey(srv,newestServer);return out;}'''
s=repl(s,'private String chatKey(ChatLine c)',chat)

console='''  private ArrayList<ConsoleLine> loadMobileConsole()throws Exception{String srv=server;HttpResult r=mobileRequestFor(srv,"GET","/v1/console?lines=100",null);ArrayList<ConsoleLine> out=new ArrayList<>();if(r.code!=200)return out;JSONArray a=new JSONArray(r.body);for(int i=0;i<a.length();i++){ConsoleLine c=new ConsoleLine();c.result=a.optString(i,"");out.add(c);}return out;}'''
s=repl(s,'  private ArrayList<ConsoleLine> loadMobileConsole()',console)

request='''  private HttpResult mobileRequest(String method,String path,String body)throws Exception{return mobileRequestFor(server,method,path,body);}\n  private HttpResult mobileRequestFor(String srv,String method,String path,String body)throws Exception{HttpsURLConnection c=(HttpsURLConnection)new URL(mobileBase(srv)+path).openConnection();c.setSSLSocketFactory(pinnedFactory());c.setHostnameVerifier((h,sess)->"87.237.52.153".equals(h));c.setConnectTimeout(5000);c.setReadTimeout(7000);c.setRequestProperty("User-Agent","ArenaControl-Android/2.15");c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-Arena-Mobile","1");c.setRequestProperty("X-Arena-Login",login);c.setRequestProperty("Cookie",websiteCookie());if("POST".equals(method)){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");byte[] b=(body==null?"{}":body).getBytes(StandardCharsets.UTF_8);try(OutputStream o=c.getOutputStream()){o.write(b);}}int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();String txt=in==null?"":new String(readAll(in),StandardCharsets.UTF_8);return new HttpResult(code,c.getURL().toString(),txt);}'''
s=repl(s,'  private HttpResult mobileRequest(String method,String path,String body)',request)

p.write_text(s)
print('v2.15 server-isolated chat and announcement routing applied')
