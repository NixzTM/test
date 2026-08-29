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
import com.jcraft.jsch.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  private static final int BG=Color.rgb(6,11,17), PANEL=Color.rgb(12,20,30), BORDER=Color.rgb(29,53,66), CYAN=Color.rgb(90,231,245), MUTED=Color.rgb(135,157,177);
  private static final Map<String,Integer> PORTS=new LinkedHashMap<>();
  static { PORTS.put("AC3",15146); PORTS.put("AC4",15300); PORTS.put("AC7",15320); }
  private final ExecutorService io=Executors.newSingleThreadExecutor();
  private Session ssh; private String server="AC3"; private FrameLayout root; private LinearLayout body,nav; private TextView topStatus,title; private ProgressBar busy; private SharedPreferences prefs;
  private String pendingHost,pendingUser,pendingPass,pendingKey,pendingFingerprint;

  @Override public void onCreate(Bundle b){ super.onCreate(b); prefs=getSharedPreferences("arena_control",MODE_PRIVATE); buildShell(); showLogin(); }

  private void buildShell(){
    root=new FrameLayout(this); root.setBackgroundColor(BG);
    LinearLayout main=new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); root.addView(main,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(14),dp(8),dp(14),dp(8)); head.setBackgroundColor(Color.rgb(8,15,23));
    ImageView logo=new ImageView(this); logo.setScaleType(ImageView.ScaleType.CENTER_CROP); byte[] logoBytes=Base64.decode(new String(readRaw(R.raw.arena_logo_b64),StandardCharsets.UTF_8).trim(),Base64.DEFAULT); logo.setImageBitmap(BitmapFactory.decodeByteArray(logoBytes,0,logoBytes.length)); head.addView(logo,new LinearLayout.LayoutParams(dp(54),dp(54)));
    title=txt("ΛRENA CONTROL",20,Color.WHITE,true); title.setPadding(dp(12),0,0,0); head.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));
    topStatus=txt("OFFLINE",11,Color.rgb(125,144,161),true); topStatus.setGravity(Gravity.END|Gravity.CENTER_VERTICAL); head.addView(topStatus,new LinearLayout.LayoutParams(dp(110),dp(54))); main.addView(head,new LinearLayout.LayoutParams(-1,dp(70)));
    busy=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); busy.setIndeterminate(true); busy.setVisibility(View.GONE); main.addView(busy,new LinearLayout.LayoutParams(-1,dp(2)));
    ScrollView sv=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16),dp(16),dp(16),dp(24)); sv.addView(body); main.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    nav=new LinearLayout(this); nav.setPadding(dp(4),dp(4),dp(4),dp(4)); nav.setBackgroundColor(Color.rgb(8,15,23)); main.addView(nav,new LinearLayout.LayoutParams(-1,dp(60)));
    addNav("Servers",this::showDashboard); addNav("Players",this::loadState); addNav("Chat",this::loadChat); addNav("Search",this::showSearch); addNav("Logout",this::logout);
    setContentView(root);
  }

  private void showLogin(){
    nav.setVisibility(View.GONE); body.removeAllViews(); title.setText("ΛRENA CONTROL"); topStatus.setText("OFFLINE");
    body.addView(txt("Game VPS login",28,Color.WHITE,true)); TextView s=txt("Direct encrypted SSH connection. No website login, WebView or scraping.",14,MUTED,false); s.setPadding(0,dp(6),0,dp(16)); body.addView(s);
    EditText host=input("Game VPS host",false); host.setText(prefs.getString("host","87.237.52.153"));
    EditText user=input("SSH user",false); user.setText(prefs.getString("user","root"));
    EditText pass=input("SSH password (leave blank for key)",true);
    EditText key=input("OpenSSH private key (optional)",false); key.setSingleLine(false); key.setMinLines(4); key.setGravity(Gravity.TOP);
    body.addView(host,lp()); body.addView(user,lpTop(8)); body.addView(pass,lpTop(8)); body.addView(key,new LinearLayout.LayoutParams(-1,dp(130)));
    CheckBox remember=new CheckBox(this); remember.setText("Remember host + user"); remember.setTextColor(MUTED); remember.setChecked(true); body.addView(remember,lpTop(8));
    Button go=primary("CONNECT"); body.addView(go,lpTop(10));
    TextView note=txt("The app only shows CONNECTED after SSH authentication and a VPS verification command succeed.",12,Color.rgb(104,132,150),false); note.setPadding(0,dp(10),0,0); body.addView(note);
    go.setOnClickListener(v->{ String h=host.getText().toString().trim(),u=user.getText().toString().trim(),p=pass.getText().toString(),k=key.getText().toString(); if(h.isEmpty()||u.isEmpty()||(p.isEmpty()&&k.trim().isEmpty())){toast("Enter host, user and password or private key");return;} if(remember.isChecked())prefs.edit().putString("host",h).putString("user",u).apply(); connect(h,u,p,k); });
  }

  private void connect(String host,String user,String pass,String key){ setBusy(true,"AUTHENTICATING"); pendingHost=host;pendingUser=user;pendingPass=pass;pendingKey=key; io.submit(()->{
      try{ Session s=openSession(host,user,pass,key); String fp=fingerprint(s.getHostKey().getKey()); String saved=prefs.getString("hostfp:"+host,""); if(!saved.isEmpty()&&!saved.equals(fp)){s.disconnect(); throw new Exception("SSH host key changed. Expected "+saved+" but received "+fp);} if(saved.isEmpty()){s.disconnect(); pendingFingerprint=fp; runOnUiThread(()->confirmFingerprint(fp)); return;} ssh=s; String proof=exec("printf 'ARENA_OK:'; hostname; test -r /opt/ac3/config/ac.yaml && printf ':AC3_OK'"); if(!proof.contains("ARENA_OK:")||!proof.contains("AC3_OK")) throw new Exception("SSH connected, but game VPS verification failed"); runOnUiThread(()->{setBusy(false,"CONNECTED");nav.setVisibility(View.VISIBLE);showDashboard();}); }
      catch(Exception e){runOnUiThread(()->{setBusy(false,"FAILED");toast(e.getMessage()==null?e.toString():e.getMessage());});}
  }); }

  private Session openSession(String host,String user,String pass,String key) throws Exception { JSch j=new JSch(); if(key!=null&&!key.trim().isEmpty())j.addIdentity("arena-mobile",key.getBytes(StandardCharsets.UTF_8),null,pass.isEmpty()?null:pass.getBytes(StandardCharsets.UTF_8)); Session s=j.getSession(user,host,22); if(key==null||key.trim().isEmpty())s.setPassword(pass); Properties c=new Properties();c.put("StrictHostKeyChecking","no");c.put("PreferredAuthentications","publickey,password,keyboard-interactive");s.setConfig(c);s.connect(10000);return s; }
  private void confirmFingerprint(String fp){ new AlertDialog.Builder(this).setTitle("Trust game VPS?").setMessage("SSH host fingerprint:\n\n"+fp+"\n\nConfirm this is your game VPS. It will be pinned for future logins.").setNegativeButton("Cancel",null).setPositiveButton("TRUST",(d,w)->{prefs.edit().putString("hostfp:"+pendingHost,fp).apply();connect(pendingHost,pendingUser,pendingPass,pendingKey);}).show(); }
  private String fingerprint(String b64) throws Exception { byte[] k=java.util.Base64.getDecoder().decode(b64);byte[] h=MessageDigest.getInstance("SHA-256").digest(k);return "SHA256:"+java.util.Base64.getEncoder().withoutPadding().encodeToString(h); }

  private void showDashboard(){ if(!ready())return; title.setText("ΛRENA · SERVERS"); body.removeAllViews(); body.addView(txt("Server control",28,Color.WHITE,true)); TextView intro=txt("AC3 · AC4 · AC7",13,MUTED,true); intro.setPadding(0,0,0,dp(10));body.addView(intro);
    for(String s:PORTS.keySet()){ LinearLayout c=card(); TextView n=txt(s,24,Color.WHITE,true); c.addView(n); c.addView(txt(s.equals("AC3")?"Lucky Jump ARENA":s.equals("AC4")?"Lucky Jump HUNT":"Lucky Jump HUNT #2",14,MUTED,false)); TextView st=txt("Tap to load live state",12,Color.rgb(91,198,158),false); st.setPadding(0,dp(8),0,0);c.addView(st); Button b=secondary("OPEN "+s);c.addView(b,lpTop(10));b.setOnClickListener(v->{server=s;loadState();});body.addView(c,lpTop(10)); }
  }

  private void loadState(){ if(!ready())return; title.setText("ΛRENA · "+server); body.removeAllViews(); body.addView(txt("Loading live state…",16,MUTED,false)); remote("state",server,new String[0],json->{ try{ JSONObject o=new JSONObject(json); renderState(o);}catch(Exception e){toast("Invalid state response: "+e.getMessage());}}, "Live state failed"); }
  private void renderState(JSONObject o) throws Exception { body.removeAllViews(); JSONObject map=o.optJSONObject("map"); if(map!=null){LinearLayout c=card();c.addView(txt("CURRENT MAP",11,CYAN,true));TextView mn=txt("",22,Color.WHITE,true);mn.setText(TmnfText.render(map.optString("Name","Unknown"),Color.WHITE));c.addView(mn);c.addView(txt("by "+map.optString("Author","Unknown"),13,MUTED,false));body.addView(c);} JSONArray p=o.optJSONArray("players"); body.addView(txt((p==null?0:p.length())+" online players",23,Color.WHITE,true),lpTop(14)); if(p!=null)for(int i=0;i<p.length();i++){JSONObject x=p.getJSONObject(i);LinearLayout r=playerRow(x.optString("NickName",x.optString("Login")),x.optString("Login"),x.optInt("SpectatorStatus",0)!=0);r.setOnClickListener(v->showPlayerActions(x.optString("Login"),x.optString("NickName")));body.addView(r,lpTop(7));} LinearLayout controls=card();controls.addView(txt("SERVER ACTIONS",11,CYAN,true));Button ann=secondary("ANNOUNCE");ann.setOnClickListener(v->promptText("Announcement","Message",m->rpc("announce","",m)));controls.addView(ann,lpTop(8));Button rst=secondary("RESTART MAP");rst.setOnClickListener(v->confirm("Restart current map?",()->rpc("restart","","")));controls.addView(rst,lpTop(7));Button next=secondary("SKIP / NEXT MAP");next.setOnClickListener(v->confirm("Skip to next map?",()->rpc("next","","")));controls.addView(next,lpTop(7));Button timer=secondary("SET TIMER");timer.setOnClickListener(v->promptText("Set timer","Minutes",m->{try{int mins=Integer.parseInt(m.trim());remote("timer",server,new String[]{String.valueOf(mins*60)},x->toast("Timer changed"),"Timer failed");}catch(Exception e){toast("Enter minutes as a number");}}));controls.addView(timer,lpTop(7));body.addView(controls,lpTop(14)); }

  private void showPlayerActions(String login,String nick){ String shown=TmnfText.plain(nick); String[] a={"Private message","Warn","Mute","Unmute","Kick","Ban","Unban"}; new AlertDialog.Builder(this).setTitle(shown+"\n"+login).setItems(a,(d,i)->{switch(i){case 0:promptText("PM · "+login,"Message",m->rpc("pm",login,m));break;case 1:promptText("Warn · "+login,"Warning text",m->rpc("warn",login,m));break;case 2:promptMute(login);break;case 3:confirm("Unmute "+login+"?",()->remote("unmute",server,new String[]{login},x->toast("Unmuted"),"Unmute failed"));break;case 4:promptText("Kick · "+login,"Reason",m->rpc("kick",login,m));break;case 5:toast("Ban is intentionally blocked here: direct DB/RPC would bypass the existing global-ban controller hook.");break;case 6:toast("Unban is intentionally blocked here for the same reason.");break;}}).show(); }
  private void promptMute(String login){ LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int pad=dp(18);box.setPadding(pad,0,pad,0);EditText min=input("Minutes (0 = permanent)",false),reason=input("Reason",false);box.addView(min);box.addView(reason,lpTop(8));new AlertDialog.Builder(this).setTitle("Mute · "+login).setView(box).setNegativeButton("Cancel",null).setPositiveButton("MUTE",(d,w)->remote("mute",server,new String[]{login,min.getText().toString().trim(),reason.getText().toString()},x->toast("Muted"),"Mute failed")).show(); }
  private void rpc(String action,String login,String text){ remote(action,server,new String[]{login,text},x->toast("Done"),action+" failed"); }

  private void loadChat(){ if(!ready())return; title.setText("ΛRENA · CHAT"); body.removeAllViews(); body.addView(txt("Loading cross-server chat…",16,MUTED,false)); remote("chat",server,new String[]{"0"},json->{try{JSONObject o=new JSONObject(json);JSONArray a=o.optJSONArray("messages");body.removeAllViews();body.addView(txt("Recent game chat",26,Color.WHITE,true));if(a==null||a.length()==0){body.addView(txt("No messages returned.",14,MUTED,false),lpTop(10));return;}for(int i=Math.max(0,a.length()-80);i<a.length();i++){JSONObject m=a.getJSONObject(i);String src=m.optString("sourceKey");if(!(src.equalsIgnoreCase("LJA")||src.equalsIgnoreCase("AC3")||src.equalsIgnoreCase("AC4")||src.equalsIgnoreCase("AC7")))continue;LinearLayout c=card();TextView n=txt(src+"  "+m.optString("nickname",m.optString("login")),14,CYAN,true);c.addView(n);TextView msg=txt("",15,Color.WHITE,false);msg.setText(TmnfText.render(m.optString("message"),Color.WHITE));c.addView(msg);body.addView(c,lpTop(6));}}catch(Exception e){toast("Chat parse failed: "+e.getMessage());}},"Chat failed"); }

  private void showSearch(){ if(!ready())return; title.setText("ΛRENA · SEARCH");body.removeAllViews();body.addView(txt("Player database",27,Color.WHITE,true));EditText q=input("Login or nickname",false);body.addView(q,lpTop(10));Button b=primary("SEARCH");body.addView(b,lpTop(10));b.setOnClickListener(v->{String term=q.getText().toString().trim();if(term.isEmpty())return;remote("search",server,new String[]{term},json->{try{JSONArray a=new JSONArray(json);body.removeAllViews();body.addView(txt("Search results",26,Color.WHITE,true));for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);body.addView(playerRow(x.optString("nick",x.optString("login")),x.optString("login"),false),lpTop(6));}}catch(Exception e){toast("Search parse failed");}},"Search failed");}); }

  private void remote(String action,String srv,String[] args,java.util.function.Consumer<String> ok,String fail){ setBusy(true,"WORKING"); io.submit(()->{try{ if(!readySession())throw new Exception("SSH session disconnected");StringBuilder cmd=new StringBuilder();String scriptB64=java.util.Base64.getEncoder().encodeToString(readRaw(R.raw.remote_admin));cmd.append("printf '%s' '").append(scriptB64).append("' | base64 -d | python3 - '").append(action).append("' '").append(srv).append("'");for(String a:args){String b64=java.util.Base64.getEncoder().encodeToString((a==null?"":a).getBytes(StandardCharsets.UTF_8));cmd.append(" '").append(b64).append("'");}String out=exec(cmd.toString());if(out.startsWith("ERR:"))throw new Exception(out.substring(4));runOnUiThread(()->{setBusy(false,"CONNECTED");ok.accept(out.trim());});}catch(Exception e){runOnUiThread(()->{setBusy(false,"CONNECTED");toast(fail+": "+e.getMessage());});}}); }
  private String exec(String command) throws Exception { ChannelExec ch=(ChannelExec)ssh.openChannel("exec");ch.setCommand(command);ByteArrayOutputStream err=new ByteArrayOutputStream();ch.setErrStream(err);InputStream in=ch.getInputStream();ch.connect(5000);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];while(true){while(in.available()>0){int n=in.read(b);if(n<0)break;out.write(b,0,n);}if(ch.isClosed())break;Thread.sleep(20);}int code=ch.getExitStatus();ch.disconnect();String s=out.toString(StandardCharsets.UTF_8);String es=err.toString(StandardCharsets.UTF_8);if(code!=0)throw new Exception(es.isEmpty()?"Remote command failed ("+code+")":es.trim());return s; }
  private boolean readySession(){return ssh!=null&&ssh.isConnected();} private boolean ready(){if(!readySession()){showLogin();toast("Not connected");return false;}return true;}
  private void logout(){if(ssh!=null)ssh.disconnect();ssh=null;showLogin();}

  private void promptText(String title,String hint,java.util.function.Consumer<String> cb){EditText e=input(hint,false);new AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancel",null).setPositiveButton("OK",(d,w)->{String x=e.getText().toString().trim();if(!x.isEmpty())cb.accept(x);}).show();}
  private void confirm(String text,Runnable r){new AlertDialog.Builder(this).setMessage(text).setNegativeButton("Cancel",null).setPositiveButton("CONFIRM",(d,w)->r.run()).show();}
  private void setBusy(boolean on,String s){busy.setVisibility(on?View.VISIBLE:View.GONE);topStatus.setText(s);topStatus.setTextColor(on?CYAN:(s.equals("CONNECTED")?Color.rgb(92,225,156):Color.rgb(225,103,103)));}
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
  private TextView txt(String s,int z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
  private EditText input(String hint,boolean password){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(90,111,126));e.setTextColor(Color.WHITE);e.setTextSize(15);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(round(PANEL,1,BORDER,14));if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
  private Button primary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTypeface(null,Typeface.BOLD);b.setTextColor(Color.rgb(4,26,31));b.setBackground(round(CYAN,0,0,14));return b;}
  private Button secondary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackground(round(Color.rgb(18,31,44),1,BORDER,13));return b;}
  private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(round(PANEL,1,BORDER,16));return c;}
  private LinearLayout playerRow(String nick,String login,boolean spec){LinearLayout r=card();TextView n=txt("",17,Color.WHITE,true);n.setText(TmnfText.render(nick,Color.WHITE));r.addView(n);TextView l=txt(login+(spec?"  · spectator":""),12,MUTED,false);l.setPadding(0,dp(3),0,0);r.addView(l);return r;}
  private GradientDrawable round(int fill,int sw,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(sw>0)g.setStroke(dp(sw),stroke);return g;}
  private LinearLayout.LayoutParams lp(){return new LinearLayout.LayoutParams(-1,dp(54));}
  private LinearLayout.LayoutParams lpTop(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p;}
  private void addNav(String label,Runnable r){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(MUTED);b.setBackgroundColor(Color.TRANSPARENT);b.setOnClickListener(v->r.run());nav.addView(b,new LinearLayout.LayoutParams(0,-1,1));}
  private byte[] readRaw(int id){try(InputStream in=getResources().openRawResource(id);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toByteArray();}catch(Exception e){throw new RuntimeException(e);}}
  @Override public void onDestroy(){super.onDestroy();if(ssh!=null)ssh.disconnect();io.shutdownNow();}
}
