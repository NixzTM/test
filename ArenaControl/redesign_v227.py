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

needle='  private final java.util.concurrent.ConcurrentHashMap<String,CopyOnWriteArrayList<ChatLine>> observedChatByServer=new java.util.concurrent.ConcurrentHashMap<>();'
if needle not in s: raise SystemExit('observed chat state missing')
extra='''\n  private final java.util.concurrent.ConcurrentHashMap<String,CopyOnWriteArrayList<String>> chatSnapshotKeysByServer=new java.util.concurrent.ConcurrentHashMap<>();\n'''
if 'chatSnapshotKeysByServer' not in s:
    s=s.replace(needle,needle+extra,1)

chat='''  private List<ChatLine> loadMobileChat()throws Exception{return loadMobileChat(server);}\n  private List<ChatLine> loadMobileChat(String srv)throws Exception{\n    HttpResult r=mobileRequestFor(srv,"GET","/v1/chat?limit=120",null);\n    if(r.code!=200)throw new Exception("chat HTTP "+r.code);\n    JSONArray a=new JSONArray(r.body);\n    ArrayList<ChatLine> raw=new ArrayList<>();\n    ArrayList<String> keys=new ArrayList<>();\n    for(int i=a.length()-1;i>=0;i--){\n      JSONObject x=a.optJSONObject(i);if(x==null)continue;\n      ChatLine c=new ChatLine();\n      c.sourceKey=srv;c.login=x.optString("login","");c.nickname=x.optString("nickname",c.login);c.message=x.optString("message","");\n      raw.add(c);keys.add(chatKey(c));\n    }\n\n    CopyOnWriteArrayList<String> prev=chatSnapshotKeysByServer.get(srv);\n    if(prev==null){\n      chatSnapshotKeysByServer.put(srv,new CopyOnWriteArrayList<>(keys));\n      setLastChatKey(srv,keys.isEmpty()?"":keys.get(keys.size()-1));\n      return new ArrayList<>(observedChat(srv));\n    }\n\n    int max=Math.min(prev.size(),keys.size()),overlap=0;\n    for(int n=max;n>0;n--){\n      boolean same=true;\n      for(int j=0;j<n;j++){if(!prev.get(prev.size()-n+j).equals(keys.get(j))){same=false;break;}}\n      if(same){overlap=n;break;}\n    }\n\n    CopyOnWriteArrayList<ChatLine> keep=observedChat(srv);\n    for(int i=overlap;i<raw.size();i++)keep.add(raw.get(i));\n    while(keep.size()>120)keep.remove(0);\n\n    chatSnapshotKeysByServer.put(srv,new CopyOnWriteArrayList<>(keys));\n    setLastChatKey(srv,keys.isEmpty()?"":keys.get(keys.size()-1));\n    return new ArrayList<>(keep);\n  }'''
s=repl(s,'  private List<ChatLine> loadMobileChat()',chat)

p.write_text(s)
print('v2.27 stable per-server live chat delta tracking applied')
