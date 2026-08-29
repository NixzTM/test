package com.arenacommunity.control;

import android.app.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import android.content.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
  private static final String BASE="https://arena-community.com";
  private static final String CHAT_RELAY="http://87.237.52.153:47831";
  private static final int BG=Color.rgb(6,11,17), PANEL=Color.rgb(12,20,30), BORDER=Color.rgb(29,53,66), CYAN=Color.rgb(90,231,245), MUTED=Color.rgb(135,157,177);
  private static final LinkedHashMap<String,String> KEYS=new LinkedHashMap<>();
  static { KEYS.put("AC3","LJA"); KEYS.put("AC4","AC4"); KEYS.put("AC7","AC7"); }

  enum Screen { LOGIN, DASHBOARD, SERVER, ANNOUNCE }
  private final ExecutorService io=Executors.newSingleThreadExecutor();
  private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
  private final java.net.CookieManager cookies=new java.net.CookieManager(null, CookiePolicy.ACCEPT_ALL);
  private String server="AC3", login="", role="", pane="chat";
  private volatile boolean authenticated=false, destroyed=false;
  private Screen screen=Screen.LOGIN;
  private FrameLayout root; private LinearLayout body,nav; private TextView topStatus,title; private ProgressBar busy;
  private ScheduledFuture<?> liveTask, keepAliveTask;

  @Override public void onCreate(Bundle b){ super.onCreate(b); CookieHandler.setDefault(cookies); buildShell(); showLogin(); }

  private void buildShell(){
    root=new FrameLayout(this); root.setBackgroundColor(BG);
    LinearLayout main=new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); root.addView(main,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(14),dp(8),dp(14),dp(8)); head.setBackgroundColor(Color.rgb(8,15,23));
    ImageView logo=new ImageView(this); logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
    try{byte[] logoBytes=Base64.decode(readRaw(R.raw.arena_logo_b64).trim(),Base64.DEFAULT);logo.setImageBitmap(BitmapFactory.decodeByteArray(logoBytes,0,logoBytes.length));}catch(Exception ignored){}
    head.addView(logo,new LinearLayout.LayoutParams(dp(54),dp(54)));
    title=txt("ΛRENA CONTROL",20,Color.WHITE,true); title.setPadding(dp(12),0,0,0); head.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));
    topStatus=txt("OFFLINE",11,Color.rgb(125,144,161),true); topStatus.setGravity(Gravity.END|Gravity.CENTER_VERTICAL); head.addView(topStatus,new LinearLayout.LayoutParams(dp(110),dp(54))); main.addView(head,new LinearLayout.LayoutParams(-1,dp(70)));
    busy=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); busy.setIndeterminate(true); busy.setVisibility(View.GONE); main.addView(busy,new LinearLayout.LayoutParams(-1,dp(2)));
    ScrollView sv=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16),dp(16),dp(16),dp(24)); sv.addView(body); main.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    nav=new LinearLayout(this); nav.setPadding(dp(4),dp(4),dp(4),dp(4)); nav.setBackgroundColor(Color.rgb(8,15,23)); main.addView(nav,new LinearLayout.LayoutParams(-1,dp(60)));
    addNav("Servers",this::showDashboard); addNav("Players",this::loadControl); addNav("Announce",this::showAnnounce); addNav("Refresh",()->refreshCurrent(true)); addNav("Logout",this::logout);
    setContentView(root);
  }

  private void showLogin(){
    stopLive(); authenticated=false; screen=Screen.LOGIN; nav.setVisibility(View.GONE); body.removeAllViews(); title.setText("ΛRENA CONTROL"); topStatus.setText("OFFLINE");
    body.addView(txt("Admin login",28,Color.WHITE,true));
    TextView s=txt("Use your ARENA website account. Only owner/admin accounts are accepted.",14,MUTED,false); s.setPadding(0,dp(6),0,dp(16)); body.addView(s);
    EditText user=input("TMNF login",false), pass=input("Website password",true); body.addView(user,lp()); body.addView(pass,lpTop(8));
    Button go=primary("LOGIN"); body.addView(go,lpTop(12));
    go.setOnClickListener(v->{String u=user.getText().toString().trim(),p=pass.getText().toString();if(u.isEmpty()||p.isEmpty()){toast("Enter login and password");return;}authenticate(u,p);});
  }

  private void authenticate(String user,String pass){ setBusy(true,"AUTHENTICATING"); io.submit(()->{
    try{
      cookies.getCookieStore().removeAll();
      HttpResult page=request("GET",BASE+"/login.php",null);
      String csrf=hidden(page.body,"csrf"); if(csrf.isEmpty())throw new Exception("Login CSRF token not found");
      String form="csrf="+enc(csrf)+"&tmnf_login="+enc(user)+"&password="+enc(pass);
      HttpResult auth=request("POST",BASE+"/login.php",form);
      if(auth.body.contains("Wrong TMNF login or password"))throw new Exception("Wrong TMNF login or website password");
      if(auth.body.contains("This account is locked"))throw new Exception("This website account is locked");
      HttpResult control=request("GET",BASE+"/control.php?server=LJA",null); ensureAdmin(control);
      String detected=extract(control.body,"website role\\s*<code>([^<]+)</code>"); if(detected.isEmpty())detected=extract(control.body,"Role\\s*<strong>([^<]+)</strong>");
      detected=htmlDecode(detected).trim().toLowerCase(Locale.ROOT);
      if(!(detected.equals("owner")||detected.equals("admin")))throw new Exception("Access denied: owner/admin website account required");
      login=user; role=detected; authenticated=true; startKeepAlive();
      runOnUiThread(()->{setBusy(false,"CONNECTED");nav.setVisibility(View.VISIBLE);showDashboard();});
    }catch(Exception e){runOnUiThread(()->{setBusy(false,"FAILED");toast(msg(e));});}
  }); }

  private void startKeepAlive(){
    if(keepAliveTask!=null)keepAliveTask.cancel(false);
    keepAliveTask=timer.scheduleWithFixedDelay(()->{if(!authenticated||destroyed)return;try{HttpResult r=request("GET",BASE+"/control.php?server="+enc(KEYS.get(server)),null);if(isExpired(r))sessionExpired();}catch(Exception ignored){}},25,25,TimeUnit.SECONDS);
  }

  private void showDashboard(){ if(!ready())return; stopLive(); screen=Screen.DASHBOARD; title.setText("ΛRENA · SERVERS"); body.removeAllViews(); body.addView(txt("Server control",28,Color.WHITE,true)); body.addView(txt("Logged in as "+login+" · "+role,13,MUTED,false),lpTop(4));
    for(String s:KEYS.keySet()){LinearLayout c=card();TextView sn=txt(s,24,Color.WHITE,true);c.addView(sn);TextView nm=txt("",14,MUTED,false);String raw=s.equals("AC3")?"$o$000Lucky Jump $6FFΛЯΞNΛ":s.equals("AC4")?"$o$000Lucky Jump $6FFHUNT":"$o$000Lucky Jump $6FFHUNT #2";nm.setText(TmnfText.render(raw,MUTED));c.addView(nm);Button b=secondary("OPEN "+s);b.setOnClickListener(v->{server=s;pane="chat";loadControl();});c.addView(b,lpTop(10));body.addView(c,lpTop(10));}
  }

  private void loadControl(){ loadControl(true); }
  private void loadControl(boolean startLoop){ if(!ready())return; screen=Screen.SERVER; if(startLoop)stopLive(); setBusy(true,"LOADING"); io.submit(()->{try{String key=KEYS.get(server);HttpResult r=request("GET",BASE+"/control.php?server="+enc(key),null);ensureAdmin(r);ParsedControl pc=parseControl(r.body);runOnUiThread(()->{setBusy(false,"CONNECTED");renderControl(pc);if(startLoop)startLive();});}catch(Exception e){runOnUiThread(()->{setBusy(false,authenticated?"CONNECTED":"OFFLINE");toast("Load failed: "+msg(e));});}}); }

  private void startLive(){
    stopLive(); liveTask=timer.scheduleWithFixedDelay(()->{if(screen!=Screen.SERVER||!authenticated||destroyed)return;try{HttpResult r=request("GET",BASE+"/control.php?server="+enc(KEYS.get(server)),null);ensureAdmin(r);ParsedControl pc=parseControl(r.body);if(pane.equals("chat"))try{pc.chat=loadGameChat();}catch(Exception e){pc.chatError=msg(e);}runOnUiThread(()->{if(screen==Screen.SERVER)renderControl(pc);});}catch(Exception e){if(e.getMessage()!=null&&e.getMessage().contains("session"))sessionExpired();}},4,4,TimeUnit.SECONDS);
  }
  private void stopLive(){if(liveTask!=null){liveTask.cancel(false);liveTask=null;}}

  private void renderControl(ParsedControl pc){ if(screen!=Screen.SERVER)return; title.setText("ΛRENA · "+server);body.removeAllViews();
    LinearLayout selector=new LinearLayout(this);for(String s:KEYS.keySet()){Button b=chip(s);b.setEnabled(!s.equals(server));b.setOnClickListener(v->{server=s;pane="chat";loadControl();});selector.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));}body.addView(selector);
    LinearLayout map=card();map.addView(txt("CURRENT MAP",11,CYAN,true));TextView mn=txt("",22,Color.WHITE,true);mn.setText(TmnfText.render(pc.mapName.isEmpty()?"Unknown":pc.mapName,Color.WHITE));map.addView(mn);if(!pc.mapAuthor.isEmpty()){TextView au=txt("",13,MUTED,false);au.setText(new SpannableStringBuilder("by ").append(TmnfText.render(pc.mapAuthor,MUTED)));map.addView(au);}map.addView(txt(pc.playerCount+" players · live refresh",12,MUTED,false));body.addView(map,lpTop(10));
    body.addView(txt(pc.players.size()+" online players",22,Color.WHITE,true),lpTop(14));
    for(Player p:pc.players){LinearLayout r=playerRow(p);r.setOnClickListener(v->showPlayerActions(p));body.addView(r,lpTop(6));}
    LinearLayout live=card();LinearLayout toggles=new LinearLayout(this);Button chat=chip("CHAT"), con=chip("CONSOLE");chat.setEnabled(!pane.equals("chat"));con.setEnabled(!pane.equals("console"));chat.setOnClickListener(v->{pane="chat";loadControl(false);});con.setOnClickListener(v->{pane="console";loadControl(false);});toggles.addView(chat,new LinearLayout.LayoutParams(0,dp(44),1));toggles.addView(con,new LinearLayout.LayoutParams(0,dp(44),1));live.addView(toggles);
    if(pane.equals("chat"))renderChatPane(live,pc); else renderConsolePane(live,pc); body.addView(live,lpTop(14));
    LinearLayout controls=card();controls.addView(txt("SERVER ACTIONS",11,CYAN,true));Button ann=secondary("ANNOUNCE");ann.setOnClickListener(v->promptText("Announcement","Message",m->submit("chat.announce","",m,"0")));controls.addView(ann,lpTop(8));Button rst=secondary("RESTART MAP");rst.setOnClickListener(v->confirm("Restart current map?",()->submit("map.restart","","Android app","0")));controls.addView(rst,lpTop(7));Button nxt=secondary("SKIP / NEXT MAP");nxt.setOnClickListener(v->confirm("Skip to next map?",()->submit("map.skip","","Android app","0")));controls.addView(nxt,lpTop(7));body.addView(controls,lpTop(14));
  }

  private void renderChatPane(LinearLayout live,ParsedControl pc){live.addView(txt("LIVE SERVER CHAT",13,CYAN,true),lpTop(10));if(pc.chatError!=null&&!pc.chatError.isEmpty()){live.addView(txt("Chat feed unavailable: "+pc.chatError,12,Color.rgb(220,150,110),false),lpTop(7));return;}if(pc.chat==null||pc.chat.isEmpty()){live.addView(txt("Waiting for game chat…",13,MUTED,false),lpTop(7));return;}for(ChatLine m:pc.chat){if(!chatMatchesServer(m.sourceKey))continue;LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);TextView n=txt("",13,CYAN,true);n.setText(new SpannableStringBuilder().append(TmnfText.render(m.nickname.isEmpty()?m.login:m.nickname,CYAN)).append("  ").append(m.login));row.addView(n);TextView msg=txt("",15,Color.WHITE,false);msg.setText(TmnfText.render(m.message,Color.WHITE));row.addView(msg);live.addView(row,lpTop(8));}}
  private void renderConsolePane(LinearLayout live,ParsedControl pc){live.addView(txt("REAL-TIME CONTROL CONSOLE",13,CYAN,true),lpTop(10));live.addView(txt("Existing ARCO command queue/results; refreshed every 4 seconds.",11,MUTED,false),lpTop(2));if(pc.console.isEmpty()){live.addView(txt("No recent command activity.",13,MUTED,false),lpTop(8));return;}for(ConsoleLine x:pc.console){TextView l=txt("",12,Color.rgb(205,218,228),false);l.setTypeface(android.graphics.Typeface.MONOSPACE);l.setText(x.time+"  ["+x.status+"] "+x.actor+"  "+x.action+(x.target.isEmpty()?"":"  "+x.target)+(x.result.isEmpty()?"":"\n  ↳ "+x.result));live.addView(l,lpTop(7));}}

  private List<ChatLine> loadGameChat() throws Exception {HttpResult r=requestAbsolute("GET",CHAT_RELAY+"/v1/messages?after=0",null);if(r.code!=200)throw new Exception("relay HTTP "+r.code);String j=r.body;ArrayList<ChatLine> out=new ArrayList<>();Pattern p=Pattern.compile("\\{[^{}]*\\\"sourceKey\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"[^{}]*\\\"login\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"[^{}]*\\\"nickname\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"[^{}]*\\\"message\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"[^{}]*\\}",Pattern.DOTALL);Matcher m=p.matcher(j);while(m.find()){ChatLine x=new ChatLine();x.sourceKey=unjson(m.group(1));x.login=unjson(m.group(2));x.nickname=unjson(m.group(3));x.message=unjson(m.group(4));out.add(x);}if(out.size()>80)out=new ArrayList<>(out.subList(out.size()-80,out.size()));return out;}
  private boolean chatMatchesServer(String k){if(k==null)return false;k=k.trim().toUpperCase(Locale.ROOT);if(server.equals("AC3"))return k.equals("LJA")||k.equals("AC3");return k.equals(server);}

  private void showPlayerActions(Player p){String[] a={"Private message","Warn","Mute","Unmute","Kick","Ban","Unban"};new AlertDialog.Builder(this).setTitle(p.plainNick+"\n"+p.login).setItems(a,(d,i)->{switch(i){case 0:promptText("PM · "+p.login,"Message",m->submit("player.message",p.login,m,"0"));break;case 1:promptText("Warn · "+p.login,"Warning text",m->submit("player.warn",p.login,m,"0"));break;case 2:promptModeration("Mute",p.login,"player.mute");break;case 3:promptText("Unmute · "+p.login,"Reason",m->submit("player.unmute",p.login,m,"0"));break;case 4:promptText("Kick · "+p.login,"Reason",m->submit("player.kick",p.login,m,"0"));break;case 5:promptModeration("Ban",p.login,"player.ban");break;case 6:promptText("Unban · "+p.login,"Reason",m->submit("player.unban",p.login,m,"0"));break;}}).show();}
  private void promptModeration(String label,String target,String action){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);EditText min=input("Minutes (0 = permanent)",false),reason=input("Reason",false);box.addView(min);box.addView(reason,lpTop(8));new AlertDialog.Builder(this).setTitle(label+" · "+target).setView(box).setNegativeButton("Cancel",null).setPositiveButton(label.toUpperCase(Locale.ROOT),(d,w)->submit(action,target,reason.getText().toString(),min.getText().toString().trim().isEmpty()?"0":min.getText().toString().trim())).show();}

  private void showAnnounce(){if(!ready())return;stopLive();screen=Screen.ANNOUNCE;body.removeAllViews();title.setText("ΛRENA · ANNOUNCE");body.addView(txt("Send server announcement",26,Color.WHITE,true));Spinner sp=new Spinner(this);ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(KEYS.keySet()));sp.setAdapter(ad);sp.setSelection(new ArrayList<>(KEYS.keySet()).indexOf(server));body.addView(sp,lpTop(12));EditText m=input("Message",false);m.setSingleLine(false);m.setMinLines(3);body.addView(m,lpTop(10));Button b=primary("SEND");body.addView(b,lpTop(10));b.setOnClickListener(v->{server=(String)sp.getSelectedItem();String x=m.getText().toString().trim();if(!x.isEmpty())submit("chat.announce","",x,"0");});}

  private void submit(String action,String target,String text,String minutes){if(!ready())return;setBusy(true,"SENDING");io.submit(()->{try{String key=KEYS.get(server);HttpResult page=request("GET",BASE+"/control.php?server="+enc(key),null);ensureAdmin(page);String csrf=hidden(page.body,"csrf");if(csrf.isEmpty())throw new Exception("Control CSRF token not found");String reason=text,message="";if(action.equals("chat.announce")||action.equals("player.message")){message=text;reason=action.equals("player.message")?"Android app PM":"Android app";}String form="csrf="+enc(csrf)+"&server_key="+enc(key)+"&control_action="+enc(action)+"&target_login="+enc(target)+"&reason="+enc(reason)+"&message="+enc(message)+"&minutes="+enc(minutes);HttpResult r=request("POST",BASE+"/control.php?server="+enc(key),form);ensureAdmin(r);if(r.body.contains("arena-admin-error")||r.body.contains("control-error"))throw new Exception("Control action rejected");runOnUiThread(()->{setBusy(false,"CONNECTED");toast("Queued: "+action);if(screen==Screen.SERVER)loadControl(false);});}catch(Exception e){runOnUiThread(()->{setBusy(false,authenticated?"CONNECTED":"OFFLINE");toast("Action failed: "+msg(e));});}});}

  private ParsedControl parseControl(String html){ParsedControl p=new ParsedControl();p.mapName=htmlDecode(extract(html,"Map:\\s*([^<]+)</p>"));p.mapAuthor=htmlDecode(extract(html,"Author:\\s*([^<]+)</"));String pc=extract(html,"Players\\s*<strong>([0-9]+)</strong>");if(pc.isEmpty())pc=extract(html,"Players:\\s*([0-9]+)");try{p.playerCount=Integer.parseInt(pc);}catch(Exception ignored){}
    Pattern row=Pattern.compile("<tr>\\s*<td><span class=\"control-login\">([^<]+)</span></td>\\s*<td><div class=\"control-nick\">(.*?)</div></td>\\s*<td>(.*?)</td>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);Matcher m=row.matcher(html);while(m.find()){String lg=htmlDecode(strip(m.group(1))).trim(), nh=m.group(2), pn=htmlDecode(strip(nh)).trim();boolean spec=m.group(3).toLowerCase(Locale.ROOT).contains("spect");if(!lg.isEmpty())p.players.add(new Player(lg,pn.isEmpty()?lg:pn,nh,spec));}
    String sec=extract(html,"<h2>Recent commands</h2>(.*?)(?:<h2>|</section>)");Pattern tr=Pattern.compile("<tr>(.*?)</tr>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);Matcher rm=tr.matcher(sec);while(rm.find()){ArrayList<String> cells=new ArrayList<>();Matcher td=Pattern.compile("<td[^>]*>(.*?)</td>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(rm.group(1));while(td.find())cells.add(htmlDecode(strip(td.group(1))).replaceAll("\\s+"," ").trim());if(cells.size()>=6&&!cells.get(0).equalsIgnoreCase("Time")){ConsoleLine x=new ConsoleLine();x.time=cells.get(0);x.actor=cells.get(1);x.action=cells.get(2);x.target=cells.get(3);x.status=cells.get(4);x.result=cells.get(5);p.console.add(x);}}
    try{p.chat=loadGameChat();}catch(Exception e){p.chatError=msg(e);}return p;}

  private CharSequence renderNickHtml(String html){if(html==null||html.isEmpty())return "";SpannableStringBuilder out=new SpannableStringBuilder();Pattern sp=Pattern.compile("<span([^>]*)>(.*?)</span>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);Matcher m=sp.matcher(html);int last=0;while(m.find()){if(m.start()>last)out.append(htmlDecode(strip(html.substring(last,m.start()))));int s=out.length();String text=htmlDecode(strip(m.group(2)));out.append(text);int e=out.length();String attrs=m.group(1);String style=extract(attrs,"style=[\"']([^\"']*)[\"']");String col=extract(style,"color\\s*:\\s*(#[0-9a-fA-F]{6})");if(!col.isEmpty())try{out.setSpan(new ForegroundColorSpan(Color.parseColor(col)),s,e,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}catch(Exception ignored){}String low=style.toLowerCase(Locale.ROOT);if(low.contains("font-weight:bold")||low.contains("font-weight: bold"))out.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),s,e,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);if(low.contains("font-style:italic")||low.contains("font-style: italic"))out.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC),s,e,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);last=m.end();}if(last<html.length())out.append(htmlDecode(strip(html.substring(last))));return out;}

  private void refreshCurrent(boolean user){if(!ready())return;if(screen==Screen.SERVER)loadControl(false);else if(screen==Screen.DASHBOARD)showDashboard();else if(screen==Screen.ANNOUNCE)showAnnounce();if(user)toast("Refreshing");}
  private void ensureAdmin(HttpResult r)throws Exception{if(isExpired(r)){sessionExpired();throw new Exception("Website session expired");}if(r.code==403||r.body.contains("Admin access is required")||r.body.contains("Access denied"))throw new Exception("Admin access denied");}
  private boolean isExpired(HttpResult r){return r==null||r.code==401||r.finalUrl.contains("login.php")||r.body.contains("name=\"tmnf_login\"");}
  private void sessionExpired(){if(!authenticated)return;authenticated=false;runOnUiThread(()->{stopLive();topStatus.setText("SESSION EXPIRED");toast("Website session expired. Log in again.");showLogin();});}

  private HttpResult request(String method,String url,String form)throws Exception{return requestAbsolute(method,url,form);}
  private HttpResult requestAbsolute(String method,String url,String form)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(true);c.setConnectTimeout(7000);c.setReadTimeout(9000);c.setRequestProperty("User-Agent","ArenaControl-Android/2.2");c.setRequestProperty("Accept","text/html,application/json");if("POST".equals(method)){c.setDoOutput(true);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");byte[] b=(form==null?"":form).getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(b.length);try(OutputStream o=c.getOutputStream()){o.write(b);}}int code=c.getResponseCode();InputStream in=(code>=400?c.getErrorStream():c.getInputStream());String body=in==null?"":new String(readAll(in),StandardCharsets.UTF_8);return new HttpResult(code,c.getURL().toString(),body);}

  private String hidden(String html,String name){Matcher m=Pattern.compile("<input[^>]*name=[\"']"+Pattern.quote(name)+"[\"'][^>]*value=[\"']([^\"']*)[\"'][^>]*>",Pattern.CASE_INSENSITIVE).matcher(html);if(m.find())return htmlDecode(m.group(1));m=Pattern.compile("<input[^>]*value=[\"']([^\"']*)[\"'][^>]*name=[\"']"+Pattern.quote(name)+"[\"'][^>]*>",Pattern.CASE_INSENSITIVE).matcher(html);return m.find()?htmlDecode(m.group(1)):"";}
  private String extract(String s,String re){Matcher m=Pattern.compile(re,Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(s==null?"":s);return m.find()?m.group(1):"";}
  private String strip(String s){return s==null?"":s.replaceAll("<[^>]+>","");}
  private String htmlDecode(String s){if(s==null)return "";return s.replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&#039;","'").replace("&#39;","'").replace("&nbsp;"," ");}
  private String unjson(String s){return s.replace("\\n","\n").replace("\\r","\r").replace("\\t","\t").replace("\\\"","\"").replace("\\/","/").replace("\\\\","\\");}
  private String enc(String s)throws Exception{return URLEncoder.encode(s==null?"":s,"UTF-8");}
  private byte[] readAll(InputStream in)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)o.write(b,0,n);return o.toByteArray();}
  private String readRaw(int id)throws Exception{return new String(readAll(getResources().openRawResource(id)),StandardCharsets.UTF_8);}

  private void logout(){stopLive();if(keepAliveTask!=null)keepAliveTask.cancel(false);cookies.getCookieStore().removeAll();login="";role="";authenticated=false;showLogin();}
  private boolean ready(){if(!authenticated){showLogin();return false;}return true;}
  private void setBusy(boolean on,String state){runOnUiThread(()->{busy.setVisibility(on?View.VISIBLE:View.GONE);topStatus.setText(state);});}
  private void promptText(String title,String hint,java.util.function.Consumer<String> done){EditText e=input(hint,false);new AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancel",null).setPositiveButton("OK",(d,w)->{String x=e.getText().toString().trim();if(!x.isEmpty())done.accept(x);}).show();}
  private void confirm(String text,Runnable yes){new AlertDialog.Builder(this).setMessage(text).setNegativeButton("Cancel",null).setPositiveButton("CONFIRM",(d,w)->yes.run()).show();}
  private String msg(Exception e){return e.getMessage()==null?e.toString():e.getMessage();}
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

  @Override public void onBackPressed(){if(screen==Screen.SERVER||screen==Screen.ANNOUNCE){showDashboard();return;}super.onBackPressed();}
  @Override protected void onDestroy(){destroyed=true;stopLive();if(keepAliveTask!=null)keepAliveTask.cancel(false);io.shutdownNow();timer.shutdownNow();super.onDestroy();}

  private LinearLayout playerRow(Player p){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(14),dp(10),dp(14),dp(10));r.setBackground(round(PANEL,1,BORDER,14));TextView n=txt("",17,Color.WHITE,true);n.setText(p.nickHtml.isEmpty()?TmnfText.render(p.plainNick,Color.WHITE):renderNickHtml(p.nickHtml));r.addView(n);TextView l=txt(p.login+(p.spectator?" · spectator":""),12,MUTED,false);r.addView(l);return r;}
  private TextView txt(String s,int size,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(c);if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
  private EditText input(String hint,boolean pw){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(91,110,128));e.setTextColor(Color.WHITE);e.setTextSize(16);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(round(Color.rgb(10,20,29),1,BORDER,14));if(pw)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
  private Button primary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.rgb(3,23,29));b.setTextSize(15);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setBackground(round(CYAN,0,0,14));return b;}
  private Button secondary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackground(round(Color.rgb(18,30,42),1,BORDER,12));return b;}
  private Button chip(String s){Button b=secondary(s);b.setTextSize(12);return b;}
  private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(PANEL,1,BORDER,16));return c;}
  private GradientDrawable round(int fill,int sw,int stroke,int r){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(r));if(sw>0)g.setStroke(dp(sw),stroke);return g;}
  private void addNav(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextSize(10);b.setAllCaps(false);b.setTextColor(Color.rgb(185,201,215));b.setBackgroundColor(Color.TRANSPARENT);b.setOnClickListener(v->r.run());nav.addView(b,new LinearLayout.LayoutParams(0,-1,1));}
  private LinearLayout.LayoutParams lp(){return new LinearLayout.LayoutParams(-1,dp(58));}
  private LinearLayout.LayoutParams lpTop(int n){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(n);return p;}
  private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}

  static class HttpResult{final int code;final String finalUrl,body;HttpResult(int c,String u,String b){code=c;finalUrl=u;body=b;}}
  static class Player{final String login,plainNick,nickHtml;final boolean spectator;Player(String l,String n,String h,boolean s){login=l;plainNick=n;nickHtml=h;spectator=s;}}
  static class ConsoleLine{String time="",actor="",action="",target="",status="",result="";}
  static class ChatLine{String sourceKey="",login="",nickname="",message="";}
  static class ParsedControl{String mapName="",mapAuthor="",chatError="";int playerCount=0;ArrayList<Player> players=new ArrayList<>();ArrayList<ConsoleLine> console=new ArrayList<>();List<ChatLine> chat=new ArrayList<>();}
}
