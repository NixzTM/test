from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

def repl_method(src, sig, rep):
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

render='''  private void renderControl(ParsedControl pc){
    if(screen!=Screen.SERVER)return; title.setText("ARENA CONTROL"); body.removeAllViews();
    LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); for(String ss:KEYS.keySet()){Button b=serverButton(ss,ss.equals(server)); b.setOnClickListener(v->{server=ss;pane="chat";loadControl();}); tabs.addView(b,new LinearLayout.LayoutParams(0,dp(62),1));} body.addView(tabs);

    LinearLayout map=card(); map.addView(txt("CURRENT MAP",11,MUTED,true)); TextView mn=txt("",22,Color.WHITE,true); mn.setText(TmnfText.render(pc.mapName.isEmpty()?"Unknown map":pc.mapName,Color.WHITE)); map.addView(mn,lpTop(3)); if(!pc.mapAuthor.isEmpty()){TextView au=txt("",13,MUTED,false);au.setText(new SpannableStringBuilder("by ").append(TmnfText.render(pc.mapAuthor,CYAN)));map.addView(au,lpTop(2));} map.addView(txt("● CONNECTED   ·   "+pc.playerCount+" online   ·   live 3s",12,GREEN,true),lpTop(7)); body.addView(map,lpTop(8));

    LinearLayout mods=new LinearLayout(this);mods.setOrientation(LinearLayout.HORIZONTAL);Button mp=module("♟","Manage\\nPlayers"),sp=module("⌕","Search\\nPlayers"),an=module("◉","Announce"),rf=module("↻","Refresh");mp.setOnClickListener(v->showAllPlayers(pc));sp.setOnClickListener(v->searchPlayers(pc));an.setOnClickListener(v->showAnnounce());rf.setOnClickListener(v->loadControl(false));mods.addView(mp,new LinearLayout.LayoutParams(0,dp(74),1));mods.addView(sp,new LinearLayout.LayoutParams(0,dp(74),1));mods.addView(an,new LinearLayout.LayoutParams(0,dp(74),1));mods.addView(rf,new LinearLayout.LayoutParams(0,dp(74),1));body.addView(mods,lpTop(8));

    LinearLayout actions=card(); actions.addView(txt("SERVER ACTIONS",12,Color.WHITE,true)); LinearLayout row=new LinearLayout(this); Button restart=blue("↻  RESTART"),skip=blue("≫  SKIP"),announce=blue("◀  ANNOUNCE"); restart.setOnClickListener(v->confirm("Restart current map?",()->submit("map.restart","","Android app","0")));skip.setOnClickListener(v->confirm("Skip to next map?",()->submit("map.skip","","Android app","0")));announce.setOnClickListener(v->showAnnounce());row.addView(restart,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(skip,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(announce,new LinearLayout.LayoutParams(0,dp(50),1));actions.addView(row,lpTop(7));body.addView(actions,lpTop(8));

    LinearLayout live=card();LinearLayout tg=new LinearLayout(this);Button chat=segment("CHAT",pane.equals("chat")),con=segment("CONSOLE",pane.equals("console"));chat.setOnClickListener(v->{pane="chat";loadControl(false);});con.setOnClickListener(v->{pane="console";loadControl(false);});tg.addView(chat,new LinearLayout.LayoutParams(0,dp(42),1));tg.addView(con,new LinearLayout.LayoutParams(0,dp(42),1));live.addView(tg);if(pane.equals("chat"))renderChatPane(live,pc);else renderConsolePane(live,pc);body.addView(live,new LinearLayout.LayoutParams(-1,dp(330)));((LinearLayout.LayoutParams)live.getLayoutParams()).topMargin=dp(8);

    LinearLayout players=card();players.addView(txt("ONLINE PLAYERS ("+pc.players.size()+")",12,Color.WHITE,true));for(Player p:pc.players)players.addView(fullPlayerRow(p),lpTop(4));body.addView(players,lpTop(8));
    LinearLayout foot=card();foot.addView(txt("◈  Logged in as "+login+"  ·  "+role.toUpperCase(Locale.ROOT),11,MUTED,false));body.addView(foot,lpTop(8));
  }'''
s=repl_method(s,'  private void renderControl(ParsedControl pc)',render)

chat='''  private void renderChatPane(LinearLayout live,ParsedControl pc){
    live.addView(txt("GAME CHAT",11,MUTED,true),lpTop(8));
    LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(10),dp(10),dp(10),dp(10));info.setBackground(round(Color.rgb(8,23,34),1,BORDER,10));TextView t=txt("Game chat is recorded by the same OnChat hook that feeds Discord.",12,Color.WHITE,true);info.addView(t);info.addView(txt("The old direct :47831 mobile relay has been removed because it is not reachable from the phone. No fake website-community chat is substituted.",11,MUTED,false),lpTop(5));live.addView(info,lpTop(8));
    Button ann=blue("ANNOUNCE TO SERVER");ann.setOnClickListener(v->showAnnounce());live.addView(ann,new LinearLayout.LayoutParams(-1,dp(48)));((LinearLayout.LayoutParams)ann.getLayoutParams()).topMargin=dp(10);
  }'''
s=repl_method(s,'  private void renderChatPane(LinearLayout live,ParsedControl pc)',chat)

start='''  private void startLive(){
    stopLive(); liveTask=timer.scheduleWithFixedDelay(()->{if(screen!=Screen.SERVER||!authenticated||destroyed)return;try{HttpResult r=request("GET",BASE+"/control.php?server="+enc(KEYS.get(server)),null);ensureAdmin(r);ParsedControl pc=parseControl(r.body);runOnUiThread(()->{if(screen==Screen.SERVER)renderControl(pc);});}catch(Exception e){if(e.getMessage()!=null&&e.getMessage().contains("session"))sessionExpired();}},3,3,TimeUnit.SECONDS);
  }'''
s=repl_method(s,'  private void startLive()',start)

mark='  private View compactPlayerRow(Player p)'
helper='''  private View fullPlayerRow(Player p){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(8),dp(7),dp(8),dp(7));r.setBackground(round(Color.rgb(7,21,32),1,BORDER,9));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView n=txt("",14,Color.WHITE,true);n.setSingleLine(true);n.setEllipsize(TextUtils.TruncateAt.END);n.setText(p.nickHtml.isEmpty()?TmnfText.render(p.plainNick,Color.WHITE):renderNickHtml(p.nickHtml));text.addView(n);TextView lg=txt(p.login,10,MUTED,false);lg.setSingleLine(true);text.addView(lg);r.addView(text,new LinearLayout.LayoutParams(0,-2,1));Button pm=ghost("PM");pm.setOnClickListener(v->promptText("PM · "+p.login,"Message",m->submit("player.message",p.login,m,"0")));r.addView(pm,new LinearLayout.LayoutParams(dp(58),dp(38)));Button more=ghost("•••");more.setOnClickListener(v->showPlayerActions(p));r.addView(more,new LinearLayout.LayoutParams(dp(58),dp(38)));return r;}\n'''
if mark not in s: raise SystemExit('missing helper marker')
s=s.replace(mark,helper+mark,1)

p.write_text(s)
print('v2.4 applied')
