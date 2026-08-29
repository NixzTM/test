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
                j+=1;break
        j+=1
    return src[:i]+rep+src[j:]

# The controller DB history is shared/ambiguous, but newly observed messages are correct per endpoint.
# Therefore baseline each server's current feed once, then keep only messages observed after that baseline
# in a per-server in-memory history. This prevents old shared DB rows from bleeding across server tabs.
needle='  private final java.util.concurrent.ConcurrentHashMap<String,String> chatSignatureByServer=new java.util.concurrent.ConcurrentHashMap<>();'
if needle not in s: raise SystemExit('v2.23 signature state missing')
extra='''\n  private final java.util.concurrent.ConcurrentHashMap<String,String> chatBaselineKeyByServer=new java.util.concurrent.ConcurrentHashMap<>();\n  private final java.util.concurrent.ConcurrentHashMap<String,CopyOnWriteArrayList<ChatLine>> observedChatByServer=new java.util.concurrent.ConcurrentHashMap<>();\n  private CopyOnWriteArrayList<ChatLine> observedChat(String srv){return observedChatByServer.computeIfAbsent(srv,k->new CopyOnWriteArrayList<>());}\n'''
s=s.replace(needle,needle+extra,1)

chat='''  private List<ChatLine> loadMobileChat()throws Exception{return loadMobileChat(server);}\n  private List<ChatLine> loadMobileChat(String srv)throws Exception{\n    HttpResult r=mobileRequestFor(srv,"GET","/v1/chat?limit=120",null);\n    if(r.code!=200)throw new Exception("chat HTTP "+r.code);\n    JSONArray a=new JSONArray(r.body);\n    ArrayList<ChatLine> raw=new ArrayList<>();\n    for(int i=a.length()-1;i>=0;i--){\n      JSONObject x=a.optJSONObject(i);if(x==null)continue;\n      ChatLine c=new ChatLine();c.sourceKey=srv;c.login=x.optString("login","");c.nickname=x.optString("nickname",c.login);c.message=x.optString("message","");raw.add(c);\n    }\n    String newest=raw.isEmpty()?"":chatKey(raw.get(raw.size()-1));\n    String baseline=chatBaselineKeyByServer.get(srv);\n    if(baseline==null){chatBaselineKeyByServer.put(srv,newest);setLastChatKey(srv,newest);return new ArrayList<>(observedChat(srv));}\n    CopyOnWriteArrayList<ChatLine> keep=observedChat(srv);\n    int start=0;\n    if(!baseline.isEmpty()){for(int i=raw.size()-1;i>=0;i--){if(chatKey(raw.get(i)).equals(baseline)){start=i+1;break;}}}\n    for(int i=start;i<raw.size();i++){ChatLine c=raw.get(i);String k=chatKey(c);boolean seen=false;for(ChatLine old:keep){if(chatKey(old).equals(k)){seen=true;break;}}if(!seen)keep.add(c);}\n    while(keep.size()>120)keep.remove(0);\n    chatBaselineKeyByServer.put(srv,newest);setLastChatKey(srv,newest);\n    return new ArrayList<>(keep);\n  }'''
s=repl(s,'  private List<ChatLine> loadMobileChat()',chat)

# Switching servers clears only the rendered widget, not that server's already observed clean history.
# The selected server will immediately repopulate from its own observedChat cache, then add new lines.
old='''    lastChatSignature="";chatSavedY=0;chatStickBottom=true;chatRenderedOnce=false;chatHoldUntil=0L;'''
new='''    lastChatSignature="";chatSavedY=0;chatStickBottom=true;chatRenderedOnce=false;chatHoldUntil=0L;'''
if old not in s: raise SystemExit('switch reset marker missing')
# no semantic change; marker validates transform order

p.write_text(s)
print('v2.24 shared historical chat baseline isolation applied')
