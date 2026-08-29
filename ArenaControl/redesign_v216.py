from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

# Session-control buttons must bind to the server whose screen created them, not to mutable global
# selection at execution time. This includes timer, map, voting and betting controls.
s=s.replace('private LinearLayout pair(String l1,String a1,String l2,String a2){LinearLayout r=new LinearLayout(this);Button b1=blue(l1),b2=blue(l2);b1.setOnClickListener(v->mobileAction(a1,"","",0));b2.setOnClickListener(v->mobileAction(a2,"","",0));r.addView(b1,new LinearLayout.LayoutParams(0,dp(48),1));r.addView(b2,new LinearLayout.LayoutParams(0,dp(48),1));return r;}',
'''private LinearLayout pair(String l1,String a1,String l2,String a2){String srv=server;LinearLayout r=new LinearLayout(this);Button b1=blue(l1),b2=blue(l2);b1.setOnClickListener(v->mobileActionFor(srv,a1,"","",0));b2.setOnClickListener(v->mobileActionFor(srv,a2,"","",0));r.addView(b1,new LinearLayout.LayoutParams(0,dp(48),1));r.addView(b2,new LinearLayout.LayoutParams(0,dp(48),1));return r;}''',1)

# Capture the selected server once when rendering the server screen, and route every one-off control
# through that immutable value.
needle='''  private void renderControl(ParsedControl pc){
    if(screen!=Screen.SERVER)return;title.setText("ARENA CONTROL");body.removeAllViews();'''
if needle not in s: raise SystemExit('renderControl marker missing')
s=s.replace(needle,''''  private void renderControl(ParsedControl pc){
    if(screen!=Screen.SERVER)return;final String controlServer=server;title.setText("ARENA CONTROL");body.removeAllViews();''',1)

repls={
'mobileAction("timer.add","","",60)':'mobileActionFor(controlServer,"timer.add","","",60)',
'mobileAction("timer.add","","",300)':'mobileActionFor(controlServer,"timer.add","","",300)',
'mobileAction("timer.add","","",600)':'mobileActionFor(controlServer,"timer.add","","",600)',
'mobileAction("map.restart","","",0)':'mobileActionFor(controlServer,"map.restart","","",0)',
'mobileAction("map.erase","","",0)':'mobileActionFor(controlServer,"map.erase","","",0)',
'mobileAction("bet.cancel","","",0)':'mobileActionFor(controlServer,"bet.cancel","","",0)',
}
for old,new in repls.items():
    if old not in s: raise SystemExit('missing control marker '+old)
    s=s.replace(old,new,1)

# The standalone announcement screen must likewise bind the destination before the async request.
old='''  private void submit(String action,String target,String text,String minutes){if(isDirectServer()){int v=0;try{v=Integer.parseInt(minutes);}catch(Exception ignored){}mobileAction(action,target,text,v);return;}'''
new='''  private void submit(String action,String target,String text,String minutes){String submitServer=server;if(isDirectServer()){int v=0;try{v=Integer.parseInt(minutes);}catch(Exception ignored){}mobileActionFor(submitServer,action,target,text,v);return;}'''
if old not in s: raise SystemExit('submit marker missing')
s=s.replace(old,new,1)

# Online-player moderation menu binds to the server where that player row was opened.
needle='''  private void showPlayerActions(Player p){String[] a={"Private message","Warn","Mute","Unmute","Kick","Ban","Unban"};'''
if needle not in s: raise SystemExit('player actions marker missing')
s=s.replace(needle,'''  private void showPlayerActions(Player p){final String actionServer=server;String[] a={"Private message","Warn","Mute","Unmute","Kick","Ban","Unban"};''',1)
for old,new in {
'submit("player.message",p.login,m,"0")':'mobileActionFor(actionServer,"player.message",p.login,m,0)',
'submit("player.warn",p.login,m,"0")':'mobileActionFor(actionServer,"player.warn",p.login,m,0)',
'submit("player.unmute",p.login,m,"0")':'mobileActionFor(actionServer,"player.unmute",p.login,m,0)',
'submit("player.kick",p.login,m,"0")':'mobileActionFor(actionServer,"player.kick",p.login,m,0)',
'submit("player.unban",p.login,m,"0")':'mobileActionFor(actionServer,"player.unban",p.login,m,0)',
}.items():
    if old in s: s=s.replace(old,new,1)

# Mute/ban duration dialog needs the same bound destination. Add an overload and let old callers keep
# working where appropriate.
old='''  private void promptModeration(String label,String target,String action){LinearLayout box=new LinearLayout(this);'''
new='''  private void promptModeration(String label,String target,String action){promptModerationFor(server,label,target,action);}\n  private void promptModerationFor(String srv,String label,String target,String action){LinearLayout box=new LinearLayout(this);'''
if old not in s: raise SystemExit('promptModeration marker missing')
s=s.replace(old,new,1)
# Inside that method, replace the submit/mobile action invocation if present.
s=s.replace('submit(action,target,reason.getText().toString().trim(),mins.getText().toString().trim())','mobileActionFor(srv,action,target,reason.getText().toString().trim(),parseIntSafe(mins.getText().toString().trim()))',1)

# Helper used by moderation duration binding.
mark='  private String msg(Exception e)'
if mark not in s: raise SystemExit('msg helper marker missing')
s=s.replace(mark,'  private int parseIntSafe(String x){try{return Integer.parseInt(x);}catch(Exception e){return 0;}}\n'+mark,1)

# Route online player mute/ban through the server-bound dialog.
s=s.replace('promptModeration("Mute",p.login,"player.mute")','promptModerationFor(actionServer,"Mute",p.login,"player.mute")',1)
s=s.replace('promptModeration("Ban",p.login,"player.ban")','promptModerationFor(actionServer,"Ban",p.login,"player.ban")',1)

p.write_text(s)
print('v2.16 all controls and moderation bound to selected server')
