from pathlib import Path

p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

def replace_method(src, signature, replacement):
    i=src.find(signature)
    if i<0:
        raise SystemExit(f'missing signature: {signature}')
    b=src.find('{', i)
    depth=0
    j=b
    while j<len(src):
        if src[j]=='{': depth+=1
        elif src[j]=='}':
            depth-=1
            if depth==0:
                j+=1
                break
        j+=1
    return src[:i]+replacement+src[j:]

s=s.replace('private static final int BG=Color.rgb(6,11,17), PANEL=Color.rgb(12,20,30), BORDER=Color.rgb(29,53,66), CYAN=Color.rgb(90,231,245), MUTED=Color.rgb(135,157,177);',
'''private static final int BG=Color.rgb(4,10,17), PANEL=Color.rgb(8,20,31), PANEL2=Color.rgb(10,27,42), BORDER=Color.rgb(25,57,78), BLUE=Color.rgb(17,103,231), BLUE2=Color.rgb(8,72,170), CYAN=Color.rgb(76,183,255), MUTED=Color.rgb(150,170,188), GREEN=Color.rgb(76,222,105);''')

build='''  private void buildShell(){
    getWindow().setStatusBarColor(Color.rgb(3,9,15)); getWindow().setNavigationBarColor(Color.rgb(3,8,13));
    root=new FrameLayout(this); root.setBackgroundColor(BG);
    LinearLayout main=new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); root.addView(main,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(14),dp(8),dp(14),dp(8)); head.setBackgroundColor(Color.rgb(5,14,23));
    ImageView logo=new ImageView(this); logo.setScaleType(ImageView.ScaleType.CENTER_CROP); try{byte[] b=Base64.decode(readRaw(R.raw.arena_logo_b64).trim(),Base64.DEFAULT);logo.setImageBitmap(BitmapFactory.decodeByteArray(b,0,b.length));}catch(Exception ignored){}
    head.addView(logo,new LinearLayout.LayoutParams(dp(58),dp(58)));
    LinearLayout names=new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setGravity(Gravity.CENTER_VERTICAL); title=txt("ARENA CONTROL",21,Color.WHITE,true); names.addView(title); names.addView(txt("TrackMania Nations Forever",11,MUTED,false)); head.addView(names,new LinearLayout.LayoutParams(0,dp(62),1));
    topStatus=txt("OFFLINE",11,MUTED,true); topStatus.setGravity(Gravity.CENTER); topStatus.setBackground(round(Color.rgb(7,24,34),1,BORDER,11)); head.addView(topStatus,new LinearLayout.LayoutParams(dp(100),dp(45))); main.addView(head,new LinearLayout.LayoutParams(-1,dp(76)));
    busy=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); busy.setIndeterminate(true); busy.setVisibility(View.GONE); main.addView(busy,new LinearLayout.LayoutParams(-1,dp(2)));
    ScrollView sv=new ScrollView(this); sv.setFillViewport(true); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(12),dp(10),dp(12),dp(14)); sv.addView(body); main.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    nav=new LinearLayout(this); nav.setPadding(dp(4),dp(2),dp(4),dp(2)); nav.setBackgroundColor(Color.rgb(5,14,23)); addNav("SERVERS",this::showDashboard);addNav("REFRESH",()->refreshCurrent(true));addNav("ANNOUNCE",this::showAnnounce);addNav("LOGOUT",this::logout);main.addView(nav,new LinearLayout.LayoutParams(-1,dp(50)));
    setContentView(root);
  }'''
s=replace_method(s,'  private void buildShell()',build)

showdash='''  private void showDashboard(){
    if(!ready())return; stopLive(); screen=Screen.DASHBOARD; title.setText("ARENA CONTROL"); body.removeAllViews();
    LinearLayout intro=card(); intro.addView(txt("SERVER SELECT",11,CYAN,true)); intro.addView(txt("AC3 · AC4 · AC7",23,Color.WHITE,true),lpTop(3)); intro.addView(txt("Logged in as "+login+" · "+role,12,MUTED,false),lpTop(3)); body.addView(intro);
    LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
    for(String ss:KEYS.keySet()){Button b=serverButton(ss,false); b.setOnClickListener(v->{server=ss;pane="chat";loadControl();}); tabs.addView(b,new LinearLayout.LayoutParams(0,dp(74),1));}
    body.addView(tabs,lpTop(10));
    LinearLayout note=card(); note.addView(txt("LIVE ADMIN",13,CYAN,true)); note.addView(txt("Open a server for live map, players, chat, console and moderation.",13,MUTED,false),lpTop(4)); body.addView(note,lpTop(10));
  }'''
s=replace_method(s,'  private void showDashboard()',showdash)

startlive='''  private void startLive(){
    stopLive(); liveTask=timer.scheduleWithFixedDelay(()->{if(screen!=Screen.SERVER||!authenticated||destroyed)return;try{HttpResult r=request("GET",BASE+"/control.php?server="+enc(KEYS.get(server)),null);ensureAdmin(r);ParsedControl pc=parseControl(r.body);try{pc.chat=loadGameChat();}catch(Exception e){pc.chatError=msg(e);}runOnUiThread(()->{if(screen==Screen.SERVER)renderControl(pc);});}catch(Exception e){if(e.getMessage()!=null&&e.getMessage().contains("session"))sessionExpired();}},3,3,TimeUnit.SECONDS);
  }'''
s=replace_method(s,'  private void startLive()',startlive)

render='''  private void renderControl(ParsedControl pc){
    if(screen!=Screen.SERVER)return; title.setText("ARENA CONTROL"); body.removeAllViews();
    LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); for(String ss:KEYS.keySet()){Button b=serverButton(ss,ss.equals(server)); b.setOnClickListener(v->{server=ss;pane="chat";loadControl();}); tabs.addView(b,new LinearLayout.LayoutParams(0,dp(66),1));} body.addView(tabs);

    LinearLayout map=card(); map.addView(txt("CURRENT MAP",11,MUTED,true)); TextView mn=txt("",24,Color.WHITE,true); mn.setText(TmnfText.render(pc.mapName.isEmpty()?"Unknown map":pc.mapName,Color.WHITE)); map.addView(mn,lpTop(3)); if(!pc.mapAuthor.isEmpty()){TextView au=txt("",13,MUTED,false);au.setText(new SpannableStringBuilder("by ").append(TmnfText.render(pc.mapAuthor,CYAN)));map.addView(au,lpTop(2));} map.addView(txt("● CONNECTED   ·   "+pc.playerCount+" online   ·   live 3s",12,GREEN,true),lpTop(7)); body.addView(map,lpTop(8));

    LinearLayout mods=new LinearLayout(this); mods.setOrientation(LinearLayout.HORIZONTAL); Button mp=module("♟","Manage\nPlayers"), sp=module("⌕","Search\nPlayers"), an=module("◉","Announce"), rf=module("↻","Refresh"); mp.setOnClickListener(v->showAllPlayers(pc)); sp.setOnClickListener(v->searchPlayers(pc)); an.setOnClickListener(v->showAnnounce()); rf.setOnClickListener(v->loadControl(false)); mods.addView(mp,new LinearLayout.LayoutParams(0,dp(82),1));mods.addView(sp,new LinearLayout.LayoutParams(0,dp(82),1));mods.addView(an,new LinearLayout.LayoutParams(0,dp(82),1));mods.addView(rf,new LinearLayout.LayoutParams(0,dp(82),1)); body.addView(mods,lpTop(8));

    LinearLayout actions=card();actions.addView(txt("SERVER ACTIONS",12,Color.WHITE,true));LinearLayout row=new LinearLayout(this);Button restart=blue("↻  RESTART MAP"),skip=blue("≫  SKIP MAP"),announce=blue("◀  ANNOUNCE");restart.setOnClickListener(v->confirm("Restart current map?",()->submit("map.restart","","Android app","0")));skip.setOnClickListener(v->confirm("Skip to next map?",()->submit("map.skip","","Android app","0")));announce.setOnClickListener(v->showAnnounce());row.addView(restart,new LinearLayout.LayoutParams(0,dp(54),1));row.addView(skip,new LinearLayout.LayoutParams(0,dp(54),1));row.addView(announce,new LinearLayout.LayoutParams(0,dp(54),1));actions.addView(row,lpTop(7));body.addView(actions,lpTop(8));

    LinearLayout split=new LinearLayout(this); split.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout live=card(); LinearLayout tg=new LinearLayout(this);Button chat=segment("CHAT",pane.equals("chat")), con=segment("CONSOLE",pane.equals("console"));chat.setOnClickListener(v->{pane="chat";loadControl(false);});con.setOnClickListener(v->{pane="console";loadControl(false);});tg.addView(chat,new LinearLayout.LayoutParams(0,dp(42),1));tg.addView(con,new LinearLayout.LayoutParams(0,dp(42),1));live.addView(tg);if(pane.equals("chat"))renderChatPane(live,pc);else renderConsolePane(live,pc);split.addView(live,new LinearLayout.LayoutParams(0,dp(390),1.15f));
    LinearLayout players=card();players.addView(txt("ONLINE PLAYERS ("+pc.players.size()+")",12,Color.WHITE,true));int lim=Math.min(9,pc.players.size());for(int i=0;i<lim;i++){Player p=pc.players.get(i);players.addView(compactPlayerRow(p),lpTop(4));}Button all=ghost("VIEW ALL PLAYERS  ›");all.setOnClickListener(v->showAllPlayers(pc));players.addView(all,lpTop(7));split.addView(players,new LinearLayout.LayoutParams(0,dp(390),.85f));body.addView(split,lpTop(8));

    LinearLayout foot=card();foot.setGravity(Gravity.CENTER_VERTICAL);foot.addView(txt("◈  Logged in as "+login+"  ·  "+role.toUpperCase(Locale.ROOT),11,MUTED,false));body.addView(foot,lpTop(8));
  }'''
s=replace_method(s,'  private void renderControl(ParsedControl pc)',render)

chat='''  private void renderChatPane(LinearLayout live,ParsedControl pc){
    ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);live.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    if(pc.chatError!=null&&!pc.chatError.isEmpty()){list.addView(txt("Chat unavailable\n"+pc.chatError,11,Color.rgb(230,150,110),false));return;}int shown=0;if(pc.chat!=null)for(int i=Math.max(0,pc.chat.size()-30);i<pc.chat.size();i++){ChatLine m=pc.chat.get(i);if(!chatMatchesServer(m.sourceKey))continue;TextView line=txt("",12,Color.WHITE,false);line.setText(new SpannableStringBuilder().append(TmnfText.render(m.nickname.isEmpty()?m.login:m.nickname,CYAN)).append("\n").append(TmnfText.render(m.message,Color.WHITE)));list.addView(line,lpTop(6));shown++;}if(shown==0)list.addView(txt("Waiting for server chat…",12,MUTED,false));Button ann=ghost("ANNOUNCE TO SERVER");ann.setOnClickListener(v->showAnnounce());live.addView(ann,lpTop(5));
  }'''
s=replace_method(s,'  private void renderChatPane(LinearLayout live,ParsedControl pc)',chat)

console='''  private void renderConsolePane(LinearLayout live,ParsedControl pc){
    ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);live.addView(sc,new LinearLayout.LayoutParams(-1,0,1));if(pc.console.isEmpty()){list.addView(txt("No recent ARCO command activity.",11,MUTED,false));return;}for(ConsoleLine x:pc.console){TextView l=txt(x.time+"  ["+x.status+"]\n"+x.actor+" · "+x.action+(x.target.isEmpty()?"":" · "+x.target)+(x.result.isEmpty()?"":"\n↳ "+x.result),10,Color.rgb(203,220,230),false);l.setTypeface(Typeface.MONOSPACE);list.addView(l,lpTop(6));}
  }'''
s=replace_method(s,'  private void renderConsolePane(LinearLayout live,ParsedControl pc)',console)

# Insert premium helper methods before player action dialog.
marker='  private void showPlayerActions(Player p)'
helpers=r'''  private Button serverButton(String name,boolean active){Button b=new Button(this);b.setAllCaps(false);b.setText(name);b.setTextSize(17);b.setTextColor(active?Color.WHITE:MUTED);b.setTypeface(null,Typeface.BOLD);b.setBackground(round(active?Color.rgb(9,39,69):PANEL,1,active?Color.rgb(16,132,255):BORDER,12));return b;}
  private Button module(String icon,String label){Button b=new Button(this);b.setAllCaps(false);b.setText(icon+"\n"+label);b.setTextSize(12);b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER);b.setBackground(round(PANEL2,1,BORDER,12));return b;}
  private Button blue(String label){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(12);b.setTypeface(null,Typeface.BOLD);b.setTextColor(Color.WHITE);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{BLUE,BLUE2});g.setCornerRadius(dp(10));g.setStroke(dp(1),Color.rgb(25,139,255));b.setBackground(g);return b;}
  private Button ghost(String label){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(11);b.setTextColor(CYAN);b.setBackground(round(Color.rgb(7,22,34),1,BORDER,9));return b;}
  private Button segment(String label,boolean active){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(12);b.setTextColor(active?Color.WHITE:MUTED);b.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);b.setBackground(round(active?Color.rgb(7,58,122):Color.rgb(7,18,28),1,active?Color.rgb(18,132,255):BORDER,8));return b;}
  private View compactPlayerRow(Player p){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(5),dp(5),dp(5),dp(5));TextView n=txt("",12,Color.WHITE,true);n.setText(p.richNick);r.addView(n,new LinearLayout.LayoutParams(0,-2,1));Button pm=ghost("✉");pm.setOnClickListener(v->promptText("PM · "+p.login,"Message",m->submit("player.message",p.login,m,"0")));r.addView(pm,new LinearLayout.LayoutParams(dp(42),dp(34)));Button more=ghost("•••");more.setOnClickListener(v->showPlayerActions(p));r.addView(more,new LinearLayout.LayoutParams(dp(44),dp(34)));return r;}
  private void showAllPlayers(ParsedControl pc){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);for(Player p:pc.players){LinearLayout r=playerRow(p);r.setOnClickListener(v->showPlayerActions(p));box.addView(r,lpTop(4));}ScrollView sv=new ScrollView(this);sv.addView(box);new AlertDialog.Builder(this).setTitle("Online players · "+server).setView(sv).setNegativeButton("Close",null).show();}
  private void searchPlayers(ParsedControl pc){EditText q=input("Nickname or login",false);new AlertDialog.Builder(this).setTitle("Search online players").setView(q).setNegativeButton("Cancel",null).setPositiveButton("SEARCH",(d,w)->{String x=q.getText().toString().trim().toLowerCase(Locale.ROOT);ArrayList<Player> hits=new ArrayList<>();for(Player p:pc.players)if(p.login.toLowerCase(Locale.ROOT).contains(x)||p.plainNick.toLowerCase(Locale.ROOT).contains(x))hits.add(p);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);if(hits.isEmpty())box.addView(txt("No online matches.",13,MUTED,false));for(Player p:hits){LinearLayout r=playerRow(p);r.setOnClickListener(v->showPlayerActions(p));box.addView(r,lpTop(4));}new AlertDialog.Builder(this).setTitle("Search results").setView(box).setNegativeButton("Close",null).show();}).show();}

'''
if marker not in s: raise SystemExit('showPlayerActions marker missing')
s=s.replace(marker,helpers+marker,1)

# Restyle existing primitives without changing their wiring.
s=s.replace('private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(round(PANEL,1,BORDER,16));return c;}',
'''private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(11),dp(10),dp(11),dp(10));c.setBackground(round(PANEL,1,BORDER,13));return c;}''')
s=s.replace('private Button secondary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(Color.rgb(16,28,40),1,BORDER,13));return b;}',
'''private Button secondary(String s){Button b=blue(s);return b;}''')

p.write_text(s)
print('v2.3 redesign applied')
