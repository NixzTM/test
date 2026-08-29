package com.arenacommunity.control;

import android.app.*;
import android.os.Bundle;
import android.text.InputType;
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
  private static final int BG=Color.rgb(6,11,17), PANEL=Color.rgb(12,20,30), BORDER=Color.rgb(29,53,66), CYAN=Color.rgb(90,231,245), MUTED=Color.rgb(135,157,177);
  private static final LinkedHashMap<String,String> KEYS=new LinkedHashMap<>();
  static { KEYS.put("AC3","LJA"); KEYS.put("AC4","AC4"); KEYS.put("AC7","AC7"); }

  private final ExecutorService io=Executors.newSingleThreadExecutor();
  private final java.net.CookieManager cookies=new java.net.CookieManager(null, CookiePolicy.ACCEPT_ALL);
  private String server="AC3", login="", role="";
  private FrameLayout root; private LinearLayout body,nav; private TextView topStatus,title; private ProgressBar busy;

  @Override public void onCreate(Bundle b){ super.onCreate(b); CookieHandler.setDefault(cookies); buildShell(); showLogin(); }

  private void buildShell(){
    root=new FrameLayout(this); root.setBackgroundColor(BG);
    LinearLayout main=new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); root.addView(main,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(14),dp(8),dp(14),dp(8)); head.setBackgroundColor(Color.rgb(8,15,23));
    ImageView logo=new ImageView(this); logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
    try{byte[] logoBytes=Base64.decode(new String(readRaw(R.raw.arena_logo_b64),StandardCharsets.UTF_8).trim(),Base64.DEFAULT);logo.setImageBitmap(BitmapFactory.decodeByteArray(logoBytes,0,logoBytes.length));}catch(Exception ignored){}
    head.addView(logo,new LinearLayout.LayoutParams(dp(54),dp(54)));
    title=txt("ΛRENA CONTROL",20,Color.WHITE,true); title.setPadding(dp(12),0,0,0); head.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));
    topStatus=txt("OFFLINE",11,Color.rgb(125,144,161),true); topStatus.setGravity(Gravity.END|Gravity.CENTER_VERTICAL); head.addView(topStatus,new LinearLayout.LayoutParams(dp(110),dp(54))); main.addView(head,new LinearLayout.LayoutParams(-1,dp(70)));
    busy=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); busy.setIndeterminate(true); busy.setVisibility(View.GONE); main.addView(busy,new LinearLayout.LayoutParams(-1,dp(2)));
    ScrollView sv=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16),dp(16),dp(16),dp(24)); sv.addView(body); main.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    nav=new LinearLayout(this); nav.setPadding(dp(4),dp(4),dp(4),dp(4)); nav.setBackgroundColor(Color.rgb(8,15,23)); main.addView(nav,new LinearLayout.LayoutParams(-1,dp(60)));
    addNav("Servers",this::showDashboard); addNav("Players",this::loadControl); addNav("Announce",this::showAnnounce); addNav("Refresh",this::loadControl); addNav("Logout",this::logout);
    setContentView(root);
  }

  private void showLogin(){
    nav.setVisibility(View.GONE); body.removeAllViews(); title.setText("ΛRENA CONTROL"); topStatus.setText("OFFLINE");
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
      HttpResult control=request("GET",BASE+"/control.php?server=LJA",null);
      if(control.code==403 || control.body.contains("Admin access is required") || control.body.contains("Access denied"))throw new Exception("Access denied: owner/admin website account required");
      if(control.finalUrl.contains("login.php") || control.body.contains("name=\"tmnf_login\""))throw new Exception("Website authentication did not create a valid session");
      String detected=extract(control.body,"website role\\s*<code>([^<]+)</code>");
      if(detected.isEmpty())detected=extract(control.body,"Role\\s*<strong>([^<]+)</strong>");
      detected=htmlDecode(detected).trim().toLowerCase(Locale.ROOT);
      if(!(detected.equals("owner")||detected.equals("admin")))throw new Exception("Access denied: website role is "+(detected.isEmpty()?"not owner/admin":detected));
      login=user; role=detected;
      runOnUiThread(()->{setBusy(false,"CONNECTED");nav.setVisibility(View.VISIBLE);showDashboard();});
    }catch(Exception e){runOnUiThread(()->{setBusy(false,"FAILED");toast(msg(e));});}
  }); }

  private void showDashboard(){ if(!ready())return; title.setText("ΛRENA · SERVERS"); body.removeAllViews(); body.addView(txt("Server control",28,Color.WHITE,true)); body.addView(txt("Logged in as "+login+" · "+role,13,MUTED,false),lpTop(4));
    for(String s:KEYS.keySet()){LinearLayout c=card();c.addView(txt(s,24,Color.WHITE,true));c.addView(txt(s.equals("AC3")?"Lucky Jump ARENA":s.equals("AC4")?"Lucky Jump HUNT":"Lucky Jump HUNT #2",14,MUTED,false));Button b=secondary("OPEN "+s);b.setOnClickListener(v->{server=s;loadControl();});c.addView(b,lpTop(10));body.addView(c,lpTop(10));}
  }

  private void loadControl(){ if(!ready())return; setBusy(true,"LOADING"); io.submit(()->{try{String key=KEYS.get(server);HttpResult r=request("GET",BASE+"/control.php?server="+enc(key),null);ensureAdmin(r);ParsedControl pc=parseControl(r.body);runOnUiThread(()->{setBusy(false,"CONNECTED");renderControl(pc);});}catch(Exception e){runOnUiThread(()->{setBusy(false,"CONNECTED");toast("Load failed: "+msg(e));});}}); }

  private void renderControl(ParsedControl pc){ title.setText("ΛRENA · "+server);body.removeAllViews();
    LinearLayout map=card();map.addView(txt("CURRENT MAP",11,CYAN,true));TextView mn=txt("",22,Color.WHITE,true);mn.setText(TmnfText.render(pc.mapName.isEmpty()?"Unknown":pc.mapName,Color.WHITE));map.addView(mn);map.addView(txt(pc.playerCount+" players",13,MUTED,false));body.addView(map);
    body.addView(txt(pc.players.size()+" online players",23,Color.WHITE,true),lpTop(14));
    for(Player p:pc.players){LinearLayout r=playerRow(p.nick,p.login,p.spectator);r.setOnClickListener(v->showPlayerActions(p));body.addView(r,lpTop(7));}
    LinearLayout controls=card();controls.addView(txt("SERVER ACTIONS",11,CYAN,true));Button ann=secondary("ANNOUNCE");ann.setOnClickListener(v->promptText("Announcement","Message",m->submit("chat.announce","",m,"0")));controls.addView(ann,lpTop(8));Button rst=secondary("RESTART MAP");rst.setOnClickListener(v->confirm("Restart current map?",()->submit("map.restart","","Website app","0")));controls.addView(rst,lpTop(7));Button nxt=secondary("SKIP / NEXT MAP");nxt.setOnClickListener(v->confirm("Skip to next map?",()->submit("map.skip","","Website app","0")));controls.addView(nxt,lpTop(7));body.addView(controls,lpTop(14));
  }

  private void showPlayerActions(Player p){String[] a={"Private message","Warn","Mute","Unmute","Kick","Ban","Unban"};new AlertDialog.Builder(this).setTitle(TmnfText.plain(p.nick)+"\n"+p.login).setItems(a,(d,i)->{switch(i){case 0:promptText("PM · "+p.login,"Message",m->submit("player.message",p.login,m,"0"));break;case 1:promptText("Warn · "+p.login,"Warning text",m->submit("player.warn",p.login,m,"0"));break;case 2:promptModeration("Mute",p.login,"player.mute");break;case 3:confirm("Unmute "+p.login+"?",()->submit("player.unmute",p.login,"App unmute","0"));break;case 4:promptText("Kick · "+p.login,"Reason",m->submit("player.kick",p.login,m,"0"));break;case 5:promptModeration("Ban",p.login,"player.ban");break;case 6:confirm("Unban "+p.login+"?",()->submit("player.unban",p.login,"App unban","0"));break;}}).show();}

  private void promptModeration(String label,String login,String action){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);EditText min=input("Minutes (0 = permanent)",false),reason=input("Reason",false);box.addView(min);box.addView(reason,lpTop(8));new AlertDialog.Builder(this).setTitle(label+" · "+login).setView(box).setNegativeButton("Cancel",null).setPositiveButton(label.toUpperCase(Locale.ROOT),(d,w)->submit(action,login,reason.getText().toString(),min.getText().toString().trim().isEmpty()?"0":min.getText().toString().trim())).show();}

  private void showAnnounce(){ if(!ready())return; body.removeAllViews();title.setText("ΛRENA · ANNOUNCE");body.addView(txt("Send server announcement",26,Color.WHITE,true));Spinner sp=new Spinner(this);ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(KEYS.keySet()));sp.setAdapter(ad);sp.setSelection(new ArrayList<>(KEYS.keySet()).indexOf(server));body.addView(sp,lpTop(12));EditText m=input("Message",false);m.setSingleLine(false);m.setMinLines(3);body.addView(m,lpTop(10));Button b=primary("SEND");body.addView(b,lpTop(10));b.setOnClickListener(v->{server=(String)sp.getSelectedItem();String x=m.getText().toString().trim();if(!x.isEmpty())submit("chat.announce","",x,"0");});}

  private void submit(String action,String target,String text,String minutes){ if(!ready())return;setBusy(true,"SENDING");io.submit(()->{try{String key=KEYS.get(server);HttpResult page=request("GET",BASE+"/control.php?server="+enc(key),null);ensureAdmin(page);String csrf=hidden(page.body,"csrf");if(csrf.isEmpty())throw new Exception("Control CSRF token not found");String reason=text,message="";if(action.equals("chat.announce")||action.equals("player.message")){message=text;reason=action.equals("player.message")?"Android app PM":"Android app";}String form="csrf="+enc(csrf)+"&server_key="+enc(key)+"&control_action="+enc(action)+"&target_login="+enc(target)+"&reason="+enc(reason)+"&message="+enc(message)+"&minutes="+enc(minutes);HttpResult r=request("POST",BASE+"/control.php?server="+enc(key),form);ensureAdmin(r);if(r.body.contains("class=\"error\"")||r.body.contains("control-error"))throw new Exception("Control action rejected");runOnUiThread(()->{setBusy(false,"CONNECTED");toast("Queued: "+action);loadControl();});}catch(Exception e){runOnUiThread(()->{setBusy(false,"CONNECTED");toast("Action failed: "+msg(e));});}});}

  private ParsedControl parseControl(String html){ParsedControl p=new ParsedControl();p.mapName=htmlDecode(extract(html,"Map:\\s*([^<]+)</p>"));String pc=extract(html,"Players:\\s*([0-9]+)");try{p.playerCount=Integer.parseInt(pc);}catch(Exception ignored){}
    Pattern row=Pattern.compile("<tr>\\s*<td><span class=\"control-login\">([^<]+)</span></td>\\s*<td><div class=\"control-nick\">(.*?)</div></td>\\s*<td>(.*?)</td>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);Matcher m=row.matcher(html);while(m.find()){String login=htmlDecode(strip(m.group(1))).trim();String nick=htmlDecode(strip(m.group(2))).trim();boolean spec=m.group(3).toLowerCase(Locale.ROOT).contains("spect");if(!login.isEmpty())p.players.add(new Player(login,nick.isEmpty()?login:nick,spec));}return p;}

  private void ensureAdmin(HttpResult r) throws Exception {if(r.code==403||r.finalUrl.contains("login.php")||r.body.contains("Admin access is required")||r.body.contains("Access denied"))throw new Exception("Admin session expired or access denied");}
  private HttpResult request(String method,String url,String form) throws Exception {HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(true);c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","ArenaControl-Android/2.1");c.setRequestProperty("Accept","text/html,application/json");if("POST".equals(method)){c.setDoOutput(true);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");byte[] b=form.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(b.length);try(OutputStream o=c.getOutputStream()){o.write(b);}}int code=c.getResponseCode();InputStream in=(code>=400?c.getErrorStream():c.getInputStream());String body=in==null?"":new String(readAll(in),StandardCharsets.UTF_8);return new HttpResult(code,c.getURL().toString(),body);}

  private String hidden(String html,String name){Matcher m=Pattern.compile("<input[^>]*name=[\"']"+Pattern.quote(name)+"[\"'][^>]*value=[\"']([^\"']*)[\"'][^>]*>",Pattern.CASE_INSENSITIVE).matcher(html);if(m.find())return htmlDecode(m.group(1));m=Pattern.compile("<input[^>]*value=[\"']([^\"']*)[\"'][^>]*name=[\"']"+Pattern.quote(name)+"[\"'][^>]*>",Pattern.CASE_INSENSITIVE).matcher(html);return m.find()?htmlDecode(m.group(1)):"";}
  private String extract(String s,String re){Matcher m=Pattern.compile(re,Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(s);return m.find()?m.group(1):"";}
  private String strip(String s){return s.replaceAll("<[^>]+>","");}
  private String htmlDecode(String s){return s.replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&#039;","'").replace("&nbsp;"," ");}
  private String enc(String s)throws Exception{return URLEncoder.encode(s==null?"":s,"UTF-8");}
  private byte[] readAll(InputStream in)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)o.write(b,0,n);return o.toByteArray();}

  private void logout(){cookies.getCookieStore().removeAll();login="";role="";showLogin();}
  private boolean ready(){if(login.isEmpty()){showLogin();return false;}return true;}
  private void setBusy(boolean on,String s){busy.setVisibility(on?View.VISIBLE:View.GONE);topStatus.setText(s);}
  private String msg(Exception e){return e.getMessage()==null?e.toString():e.getMessage();}
  private void promptText(String title,String hint,java.util.function.Consumer<String> done){EditText e=input(hint,false);new AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancel",null).setPositiveButton("OK",(d,w)->{String x=e.getText().toString().trim();if(!x.isEmpty())done.accept(x);}).show();}
  private void confirm(String s,Runnable r){new AlertDialog.Builder(this).setTitle(s).setNegativeButton("Cancel",null).setPositiveButton("CONFIRM",(d,w)->r.run()).show();}
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private TextView txt(String s,int z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(b)v.setTypeface(null,Typeface.BOLD);return v;}
  private EditText input(String hint,boolean password){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(92,108,132));e.setTextColor(Color.WHITE);e.setTextSize(16);e.setBackground(round(PANEL,1,BORDER,14));e.setPadding(dp(15),dp(12),dp(15),dp(12));if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
  private Button primary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.rgb(3,25,30));b.setTypeface(null,Typeface.BOLD);b.setBackground(round(CYAN,0,0,14));return b;}
  private Button secondary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(Color.rgb(16,28,40),1,BORDER,13));return b;}
  private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(round(PANEL,1,BORDER,16));return c;}
  private LinearLayout playerRow(String nick,String login,boolean spec){LinearLayout r=card();TextView n=txt("",17,Color.WHITE,true);n.setText(TmnfText.render(nick,Color.WHITE));r.addView(n);r.addView(txt(login+(spec?" · spectator":""),12,MUTED,false));return r;}
  private GradientDrawable round(int fill,int sw,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(sw>0)g.setStroke(dp(sw),stroke);return g;}
  private LinearLayout.LayoutParams lp(){return new LinearLayout.LayoutParams(-1,dp(56));}
  private LinearLayout.LayoutParams lpTop(int n){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(n);return p;}
  private void addNav(String s,Runnable r){Button b=secondary(s);b.setTextSize(11);b.setBackgroundColor(Color.TRANSPARENT);b.setOnClickListener(v->r.run());nav.addView(b,new LinearLayout.LayoutParams(0,-1,1));}
  private String readRaw(int id)throws Exception{try(InputStream in=getResources().openRawResource(id)){return new String(readAll(in),StandardCharsets.UTF_8);}}
  @Override public void onDestroy(){super.onDestroy();io.shutdownNow();}

  static class HttpResult{final int code;final String finalUrl,body;HttpResult(int c,String u,String b){code=c;finalUrl=u;body=b;}}
  static class Player{final String login,nick;final boolean spectator;Player(String l,String n,boolean s){login=l;nick=n;spectator=s;}}
  static class ParsedControl{String mapName="";int playerCount=0;final ArrayList<Player> players=new ArrayList<>();}
}
