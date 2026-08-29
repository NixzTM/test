from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

old='''private List<ChatLine> loadMobileChat()throws Exception{HttpResult r=mobileRequest("GET","/v1/chat?limit=120",null);if(r.code!=200)throw new Exception("chat HTTP "+r.code);JSONArray a=new JSONArray(r.body);ArrayList<ChatLine> out=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;ChatLine c=new ChatLine();c.sourceKey="AC3";c.login=x.optString("login","");c.nickname=x.optString("nickname",c.login);c.message=x.optString("message","");out.add(c);}out.addAll(localSentChat);return out;}'''
new='''private List<ChatLine> loadMobileChat()throws Exception{HttpResult r=mobileRequest("GET","/v1/chat?limit=120",null);if(r.code!=200)throw new Exception("chat HTTP "+r.code);JSONArray a=new JSONArray(r.body);ArrayList<ChatLine> out=new ArrayList<>();for(int i=a.length()-1;i>=0;i--){JSONObject x=a.optJSONObject(i);if(x==null)continue;ChatLine c=new ChatLine();c.sourceKey="AC3";c.login=x.optString("login","");c.nickname=x.optString("nickname",c.login);c.message=x.optString("message","");out.add(c);}out.addAll(localSentChat);return out;}'''
if old not in s:
    raise SystemExit('loadMobileChat v2.9 marker missing')
s=s.replace(old,new,1)
p.write_text(s)
print('v2.10 chronological chat ordering applied')
