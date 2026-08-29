from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

def repl(src,sig,rep):
    i=src.find(sig)
    if i<0: raise SystemExit('missing '+sig)
    b=src.find('{',i);d=0;j=b
    while j<len(src):
        if src[j]=='{':d+=1
        elif src[j]=='}':
            d-=1
            if d==0:j+=1;break
        j+=1
    return src[:i]+rep+src[j:]

s=s.replace('addNav("Players",this::loadControl);','addNav("Players",this::showPlayerDirectory);')

mark='  private void refreshCurrent(boolean user)'
helpers=r'''  private void showPlayerDirectory(){
    if(!ready())return;stopLive();screen=Screen.SERVER;title.setText("ΛRENA · PLAYERS");body.removeAllViews();
    LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);for(String ss:KEYS.keySet()){Button b=serverButton(serverLabel(ss),ss.equals(server));b.setOnClickListener(v->{server=ss;showPlayerDirectory();});tabs.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}body.addView(tabs);
    LinearLayout c=card();c.addView(txt("PLAYER DIRECTORY",12,Color.WHITE,true));c.addView(txt("Search current or offline players by login or nickname.",12,MUTED,false),lpTop(3));
    EditText q=input("Login or nickname",false);Button find=blue("SEARCH");LinearLayout r=new LinearLayout(this);r.addView(q,new LinearLayout.LayoutParams(0,dp(50),1));r.addView(find,new LinearLayout.LayoutParams(dp(100),dp(50)));c.addView(r,lpTop(8));
    LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);c.addView(results,lpTop(8));body.addView(c,lpTop(8));
    LinearLayout bans=card();LinearLayout bh=new LinearLayout(this);bh.setGravity(Gravity.CENTER_VERTICAL);bh.addView(txt("BANNED PLAYERS",12,Color.WHITE,true),new LinearLayout.LayoutParams(0,dp(40),1));Button reload=ghost("REFRESH");bh.addView(reload,new LinearLayout.LayoutParams(dp(90),dp(40)));bans.addView(bh);LinearLayout banList=new LinearLayout(this);banList.setOrientation(LinearLayout.VERTICAL);bans.addView(banList);body.addView(bans,lpTop(8));
    find.setOnClickListener(v->searchOfflinePlayers(q.getText().toString().trim(),results));q.setOnEditorActionListener((v,a,e)->{find.performClick();return true;});reload.setOnClickListener(v->loadBans(banList));
    loadBans(banList);
  }
  private void searchOfflinePlayers(String query,LinearLayout out){if(query.length()<2){toast("Type at least 2 characters");return;}if(!server.equals("AC3")){toast("Offline search direct API is staged on LJA first.");return;}setBusy(true,"SEARCHING");io.submit(()->{try{HttpResult r=mobileRequest("GET","/v1/players/search?q="+enc(query),null);if(r.code!=200)throw new Exception("HTTP "+r.code+": "+r.body);JSONArray a=new JSONArray(r.body);runOnUiThread(()->{setBusy(false,"CONNECTED");out.removeAllViews();if(a.length()==0){out.addView(txt("No players found.",12,MUTED,false));return;}for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String lg=x.optString("login","");String nn=x.optString("nickname",lg);String updated=x.optString("updated_at","");boolean banned=x.optBoolean("banned",false);LinearLayout row=card();TextView n=txt("",15,Color.WHITE,true);n.setText(TmnfText.render(nn,Color.WHITE));row.addView(n);row.addView(txt(lg+(updated.isEmpty()?"":"  ·  last seen "+updated)+(banned?"  ·  BANNED":""),11,banned?Color.rgb(240,110,110):MUTED,false),lpTop(2));Button manage=banned?redButton("MANAGE BAN"):secondary("MANAGE PLAYER");manage.setOnClickListener(v->showOfflinePlayerActions(lg,nn,banned));row.addView(manage,lpTop(6));out.addView(row,lpTop(5));}});}catch(Exception e){runOnUiThread(()->{setBusy(false,"CONNECTED");toast("Search failed: "+msg(e));});}});}
  private void loadBans(LinearLayout out){if(!server.equals("AC3")){out.removeAllViews();out.addView(txt("Ban management direct API is staged on LJA first.",12,MUTED,false));return;}io.submit(()->{try{HttpResult r=mobileRequest("GET","/v1/bans",null);if(r.code!=200)throw new Exception("HTTP "+r.code);JSONArray a=new JSONArray(r.body);runOnUiThread(()->{out.removeAllViews();if(a.length()==0){out.addView(txt("No active bans.",12,MUTED,false),lpTop(5));return;}for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String lg=x.optString("login","");String reason=x.optString("reason","");String by=x.optString("by","");String at=x.optString("updated_at","");LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(8),dp(8),dp(8),dp(8));row.setBackground(round(Color.rgb(24,18,22),1,Color.rgb(95,45,55),9));row.addView(txt(lg,14,Color.WHITE,true));row.addView(txt((reason.isEmpty()?"No reason":reason)+(by.isEmpty()?"":"  ·  by "+by)+(at.isEmpty()?"":"  ·  "+at),11,MUTED,false),lpTop(2));Button un=redButton("UNBAN");un.setOnClickListener(v->confirm("Unban "+lg+"?",()->mobileAction("player.unban",lg,"",0)));row.addView(un,lpTop(5));out.addView(row,lpTop(6));}});}catch(Exception e){runOnUiThread(()->{out.removeAllViews();out.addView(txt("Failed to load bans: "+msg(e),12,Color.rgb(230,140,120),false));});}});}
  private void showOfflinePlayerActions(String lg,String nn,boolean banned){ArrayList<String> opts=new ArrayList<>();if(banned)opts.add("Unban");else opts.add("Ban");opts.add("Mute");opts.add("Unmute");String[] arr=opts.toArray(new String[0]);new AlertDialog.Builder(this).setTitle(TmnfText.plain(nn)+"\n"+lg).setItems(arr,(d,i)->{String x=arr[i];if(x.equals("Ban"))promptText("Ban · "+lg,"Reason",m->mobileAction("player.ban",lg,m,0));else if(x.equals("Unban"))confirm("Unban "+lg+"?",()->mobileAction("player.unban",lg,"",0));else if(x.equals("Mute"))promptModeration("Mute",lg,"player.mute");else if(x.equals("Unmute"))mobileAction("player.unmute",lg,"",0);}).show();}

'''
if mark not in s: raise SystemExit('refresh marker missing')
s=s.replace(mark,helpers+mark,1)
p.write_text(s)
print('v2.7 offline player search and ban management UI applied')
