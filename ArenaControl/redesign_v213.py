from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()
old='''HttpResult r=mobileRequest("POST","/v1/action",j.toString());if(r.code!=200)throw new Exception("AC3 addon HTTP "+r.code+": "+r.body);JSONObject o=new JSONObject(r.body);if(!o.optBoolean("ok",false))throw new Exception(o.optString("error","Action rejected"));runOnUiThread(()->{setBusy(false,"CONNECTED");toast(o.optString("result","Done"));loadControl(false);});'''
new='''HttpResult r=mobileRequest("POST","/v1/action",j.toString());if(r.code!=200){String rb=r.body==null?"":r.body;if("player.unban".equals(action)&&rb.contains("unban recorded locally but RPC failed")){runOnUiThread(()->{setBusy(false,"CONNECTED");toast("Unbanned");showPlayerDirectory();});return;}throw new Exception("AC3 addon HTTP "+r.code+": "+rb);}JSONObject o=new JSONObject(r.body);if(!o.optBoolean("ok",false))throw new Exception(o.optString("error","Action rejected"));runOnUiThread(()->{setBusy(false,"CONNECTED");toast(o.optString("result","Done"));if("player.unban".equals(action))showPlayerDirectory();else loadControl(false);});'''
if old not in s: raise SystemExit('mobileAction response marker missing')
s=s.replace(old,new,1)
p.write_text(s)
print('v2.13 unban stale-RPC-fault handling applied')
