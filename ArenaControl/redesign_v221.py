from pathlib import Path
import base64

root=Path('.')
p=root/'app/src/main/java/com/arenacommunity/control/MainActivity.java'
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
                j+=1;break
        j+=1
    return src[:i]+rep+src[j:]

# v2.21: chat is sourced only from the selected controller's /v1/chat endpoint.
# That endpoint is backed by that controller's Chatlog.RecentPublic, so switching server
# necessarily loads that server's own persisted chatlog and never merges another server's cache.
chat='''  private List<ChatLine> loadMobileChat()throws Exception{return loadMobileChat(server);}\n  private List<ChatLine> loadMobileChat(String srv)throws Exception{\n    HttpResult r=mobileRequestFor(srv,"GET","/v1/chat?limit=120",null);\n    if(r.code!=200)throw new Exception("chat HTTP "+r.code);\n    JSONArray a=new JSONArray(r.body);\n    ArrayList<ChatLine> out=new ArrayList<>();\n    for(int i=a.length()-1;i>=0;i--){\n      JSONObject x=a.optJSONObject(i);if(x==null)continue;\n      ChatLine c=new ChatLine();\n      c.sourceKey=srv;\n      c.login=x.optString("login","");\n      c.nickname=x.optString("nickname",c.login);\n      c.message=x.optString("message","");\n      out.add(c);\n    }\n    setLastChatKey(srv,out.isEmpty()?"":chatKey(out.get(out.size()-1)));\n    return out;\n  }'''
s=repl(s,'  private List<ChatLine> loadMobileChat()',chat)

# Make the switch visibly empty until the selected server's persisted chatlog arrives.
old='''    screen=Screen.SERVER;title.setText("ΛRENA · "+srv);body.removeAllViews();\n    body.addView(txt("Loading "+serverLabel(srv)+"…",16,MUTED,true));'''
new='''    screen=Screen.SERVER;title.setText("ΛRENA · "+srv);body.removeAllViews();\n    LinearLayout loading=card();loading.addView(txt("GAME CHAT",11,CYAN,true));loading.addView(txt("Loading "+serverLabel(srv)+" chatlog…",14,MUTED,false),lpTop(8));body.addView(loading);'''
if old not in s: raise SystemExit('switch loading marker missing')
s=s.replace(old,new,1)

# Remove app-local echo injection from SEND. The message appears only after the target server
# writes it to / exposes it through its own chatlog. Action routing remains immutable per server.
needle='''String chosen=nick.getText().toString().trim();if(chosen.isEmpty())chosen=login;chatNickname=chosen;ChatLine mine=new ChatLine();mine.sourceKey=sendServer;mine.login=login;mine.nickname=chosen;mine.message=m;CopyOnWriteArrayList<ChatLine> local=localChat(sendServer);local.add(mine);localSentAnchors.put(mine,lastChatKey(sendServer));while(local.size()>30){ChatLine oldLocal=local.remove(0);localSentAnchors.remove(oldLocal);}chatStickBottom=true;e.clearFocus();chatHoldUntil=SystemClock.uptimeMillis()+2500L;String wire=chosen+"$z$fff: "+m;mobileActionFor(sendServer,"chat.announce","",wire,0);e.setText("");'''
replacement='''String chosen=nick.getText().toString().trim();if(chosen.isEmpty())chosen=login;chatNickname=chosen;chatStickBottom=true;e.clearFocus();chatHoldUntil=SystemClock.uptimeMillis()+2500L;String wire=chosen+"$z$fff: "+m;mobileActionFor(sendServer,"chat.announce","",wire,0);e.setText("");'''
if needle not in s: raise SystemExit('formatted SEND marker missing')
s=s.replace(needle,replacement,1)

p.write_text(s)

# Use the existing ARENA logo resource as the Android launcher icon.
b64=(root/'app/src/main/res/raw/arena_logo_b64.txt').read_text().strip()
out=root/'app/src/main/res/drawable-nodpi'
out.mkdir(parents=True,exist_ok=True)
(out/'arena_app_icon.png').write_bytes(base64.b64decode(b64))

manifest=root/'app/src/main/AndroidManifest.xml'
m=manifest.read_text()
if 'android:icon="@drawable/arena_app_icon"' not in m:
    m=m.replace('android:label="ARENA Control"','android:label="ARENA Control"\n        android:icon="@drawable/arena_app_icon"\n        android:roundIcon="@drawable/arena_app_icon"',1)
manifest.write_text(m)

print('v2.21 per-server persisted chatlog feed + ARENA launcher icon applied')
