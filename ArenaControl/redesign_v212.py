from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

# App-originated announcements need a chronological anchor. v2.9 simply appended them after every
# fetched server message, which made old announcements remain permanently below newer game chat.
needle='  private String lastChatSignature="";'
if needle not in s: raise SystemExit('v2.11 chat signature field missing')
s=s.replace(needle, needle+'\n  private final ConcurrentHashMap<ChatLine,String> localSentAnchors=new ConcurrentHashMap<>();\n  private volatile String lastServerChatKey="";',1)

# When sending an announcement, remember which real game-chat line was newest at that instant.
old='localSentChat.add(mine);while(localSentChat.size()>30)localSentChat.remove(0);chatStickBottom=true;'
new='localSentChat.add(mine);localSentAnchors.put(mine,lastServerChatKey);while(localSentChat.size()>30){ChatLine oldLocal=localSentChat.remove(0);localSentAnchors.remove(oldLocal);}chatStickBottom=true;'
if old not in s: raise SystemExit('local send marker missing')
s=s.replace(old,new,1)

# Replace v2.10 loader. Real server rows are first put oldest->newest. Each local announcement is then
# inserted immediately after the real chat line that was newest when it was sent. Therefore later real
# game messages naturally appear below the announcement instead of the announcement staying at bottom forever.
start=s.find('private List<ChatLine> loadMobileChat()throws Exception')
if start<0: raise SystemExit('loadMobileChat missing')
b=s.find('{',start);d=0;j=b
while j<len(s):
    if s[j]=='{': d+=1
    elif s[j]=='}':
        d-=1
        if d==0:
            j+=1;break
    j+=1
replacement='''private String chatKey(ChatLine c){if(c==null)return "";return (c.login==null?"":c.login)+"\\u0001"+(c.nickname==null?"":c.nickname)+"\\u0001"+(c.message==null?"":c.message);}\n  private List<ChatLine> loadMobileChat()throws Exception{HttpResult r=mobileRequest("GET","/v1/chat?limit=120",null);if(r.code!=200)throw new Exception("chat HTTP "+r.code);JSONArray a=new JSONArray(r.body);ArrayList<ChatLine> serverLines=new ArrayList<>();for(int i=a.length()-1;i>=0;i--){JSONObject x=a.optJSONObject(i);if(x==null)continue;ChatLine c=new ChatLine();c.sourceKey="AC3";c.login=x.optString("login","");c.nickname=x.optString("nickname",c.login);c.message=x.optString("message","");serverLines.add(c);}String newestServer=serverLines.isEmpty()?lastServerChatKey:chatKey(serverLines.get(serverLines.size()-1));ArrayList<ChatLine> out=new ArrayList<>(serverLines);HashMap<String,Integer> insertedAfter=new HashMap<>();for(ChatLine local:localSentChat){String anchor=localSentAnchors.getOrDefault(local,"");int pos=-1;if(!anchor.isEmpty()){for(int i=out.size()-1;i>=0;i--){if(chatKey(out.get(i)).equals(anchor)){pos=i;break;}}}if(pos<0){out.add(local);continue;}int extra=insertedAfter.getOrDefault(anchor,0);int at=Math.min(pos+1+extra,out.size());out.add(at,local);insertedAfter.put(anchor,extra+1);}lastServerChatKey=newestServer;return out;}'''
s=s[:start]+replacement+s[j:]

p.write_text(s)
print('v2.12 app announcements now interleave chronologically with real game chat')
