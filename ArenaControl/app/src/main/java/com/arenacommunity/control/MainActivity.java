package com.arenacommunity.control;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/**
 * Native ARENA admin client. The WebView is never shown to the user; it is used only as an
 * authenticated transport to the existing website forms/pages so production needs no changes.
 */
public final class MainActivity extends Activity {
  static final String BASE="https://arena-community.com";
  static final LinkedHashMap<String,String> KEYS=new LinkedHashMap<>();
  static { KEYS.put("AC3","LJA"); KEYS.put("AC4","AC4"); KEYS.put("AC7","AC7"); }

  FrameLayout root; LinearLayout body,nav; TextView title,status; WebView transport; ProgressBar busy;
  String server="AC3", pendingMode="dashboard"; boolean loggedIn=false;
  final ArrayList<Player> players=new ArrayList<>();

  @Override public void onCreate(Bundle b){super.onCreate(b); buildShell(); setupTransport(); showLogin();}

  void buildShell(){
    root=new FrameLayout(this); root.setBackgroundColor(Color.rgb(11,15,22));
    LinearLayout main=new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); root.addView(main,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(18),dp(10),dp(14),dp(10)); head.setBackgroundColor(Color.rgb(17,23,34));
    title=t("ARENA CONTROL",21,Color.WHITE,true); head.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));
    status=t("Disconnected",12,Color.rgb(135,150,170),false); status.setGravity(Gravity.END|Gravity.CENTER_VERTICAL); head.addView(status,new LinearLayout.LayoutParams(dp(130),dp(54))); main.addView(head,new LinearLayout.LayoutParams(-1,dp(72)));
    busy=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); busy.setIndeterminate(true); busy.setVisibility(View.GONE); main.addView(busy,new LinearLayout.LayoutParams(-1,dp(2)));
    ScrollView scroll=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16),dp(14),dp(16),dp(24)); scroll.addView(body); main.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    nav=new LinearLayout(this); nav.setBackgroundColor(Color.rgb(17,23,34)); nav.setPadding(dp(4),dp(4),dp(4),dp(4)); main.addView(nav,new LinearLayout.LayoutParams(-1,dp(62)));
    addNav("Servers",()->showDashboard()); addNav("Players",()->loadPlayers()); addNav("Search",()->showSearch()); addNav("Announce",()->showAnnounce()); addNav("Logout",()->logout());
    transport=new WebView(this); transport.setAlpha(0.01f); transport.setVisibility(View.VISIBLE); FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(1,1); hp.gravity=Gravity.BOTTOM|Gravity.RIGHT; root.addView(transport,hp);
    setContentView(root);
  }

  @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"}) void setupTransport(){
    CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(transport,false);
    WebSettings s=transport.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    transport.addJavascriptInterface(new Bridge(),"ArenaNative");
    transport.setWebViewClient(new WebViewClient(){
      @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap f){busy.setVisibility(View.VISIBLE); status.setText("Connecting…");}
      @Override public void onPageFinished(WebView v,String u){busy.setVisibility(View.GONE); status.setText("Connected"); if(u.contains("login.php")){ if(!pendingMode.equals("login")) {loggedIn=false; showLogin();} } else {loggedIn=true; if((pendingMode.equals("action")||pendingMode.equals("announceAction"))&&transport.getTag() instanceof Pending){maybeRunAction();} else handleLoaded(u);} }
    });
  }

  void showLogin(){
    title.setText("ARENA CONTROL"); body.removeAllViews(); nav.setVisibility(View.GONE);
    TextView h=t("Admin login",28,Color.WHITE,true); body.addView(h);
    TextView sub=t("Use your existing ARENA website account.",14,Color.rgb(150,164,184),false); sub.setPadding(0,dp(4),0,dp(18)); body.addView(sub);
    EditText user=input("Username",false), pass=input("Password",true); body.addView(user,lp()); body.addView(pass,lp());
    Button go=primary("LOGIN"); body.addView(go,lpTop(14));
    go.setOnClickListener(v->{String u=user.getText().toString().trim(),p=pass.getText().toString(); if(u.isEmpty()||p.isEmpty()){toast("Enter username and password");return;} pendingMode="login"; status.setText("Signing in…"); transport.loadUrl(BASE+"/login.php"); transport.setTag(new String[]{u,p});});
  }

  void handleLoaded(String url){
    if(pendingMode.equals("login")){
      if(url.contains("login.php")){Object tag=transport.getTag(); if(tag instanceof String[]){String[] a=(String[])tag; transport.setTag(null); js("(function(){var u=document.querySelector('input[name*=user i],input[name*=login i],input[type=email],input[type=text]');var p=document.querySelector('input[type=password]');if(!u||!p){ArenaNative.error('Login form not found');return;}u.value="+q(a[0])+";p.value="+q(a[1])+";var f=p.form||u.form||document.querySelector('form');if(f)f.submit();else ArenaNative.error('Login form not found');})();"); return;}}
      pendingMode="dashboard"; nav.setVisibility(View.VISIBLE); showDashboard(); return;
    }
    if(pendingMode.equals("players")) scrapeControl();
    else if(pendingMode.equals("action")) {toast("Action submitted"); loadPlayers();}
    else if(pendingMode.equals("announceAction")) {toast("Announcement submitted"); showAnnounce();}
    else if(pendingMode.equals("search")) scrapeSearch();
  }

  void showDashboard(){
    if(!requireLogin())return; pendingMode="dashboard"; title.setText("ARENA · SERVERS"); body.removeAllViews();
    TextView h=t("Servers",26,Color.WHITE,true); h.setPadding(0,0,0,dp(10)); body.addView(h);
    for(String s:KEYS.keySet()){
      LinearLayout card=card(); TextView n=t(s,24,Color.WHITE,true); TextView desc=t(s.equals("AC3")?"Lucky Jump ARENA":s.equals("AC4")?"HUNT":"HUNT #2",14,Color.rgb(145,163,188),false); card.addView(n);card.addView(desc);
      Button open=secondary("OPEN PLAYERS / ADMIN"); card.addView(open,lpTop(12)); open.setOnClickListener(v->{server=s; loadPlayers();}); body.addView(card,lpTop(10));
    }
  }

  void loadPlayers(){
    if(!requireLogin())return; pendingMode="players"; title.setText("ARENA · "+server); body.removeAllViews(); body.addView(t("Loading "+server+" players…",17,Color.LTGRAY,false)); transport.loadUrl(BASE+"/index.php?page=control&server="+KEYS.get(server));
  }

  void scrapeControl(){
    // Generic semantic scraper: pulls table/list rows with login/nickname-ish cells while preserving only visible text.
    js("(function(){var out=[];var rows=document.querySelectorAll('tr');rows.forEach(function(r){var c=[].map.call(r.querySelectorAll('td'),function(x){return (x.innerText||'').trim()}).filter(Boolean);if(c.length>=2)out.push(c)});ArenaNative.players(JSON.stringify(out));})();");
  }

  void renderPlayers(String json){
    players.clear(); try{JSONArray a=new JSONArray(json); for(int i=0;i<a.length();i++){JSONArray r=a.getJSONArray(i);String nick=r.optString(0),login=r.length()>1?r.optString(1):nick;if(looksHeader(nick,login))continue;players.add(new Player(nick,login));}}catch(Exception e){}
    body.removeAllViews();
    LinearLayout selector=new LinearLayout(this); selector.setGravity(Gravity.CENTER); for(String s:KEYS.keySet()){Button b=chip(s);b.setEnabled(!s.equals(server));b.setOnClickListener(v->{server=s;loadPlayers();});selector.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));} body.addView(selector);
    body.addView(t(server+" · Online players",24,Color.WHITE,true),lpTop(14));
    EditText filter=input("Filter players",false); body.addView(filter,lpTop(10)); LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list,lpTop(8));
    Runnable rr=()->{list.removeAllViews();String f=filter.getText().toString().toLowerCase(Locale.ROOT);for(Player p:players){if(!f.isEmpty()&&!p.nick.toLowerCase().contains(f)&&!p.login.toLowerCase().contains(f))continue;Button row=secondary(p.nick+(p.login.equals(p.nick)?"":"\n"+p.login));row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setOnClickListener(v->showPlayerActions(p));list.addView(row,lpTop(6));}if(list.getChildCount()==0)list.addView(t("No player rows could be read from the existing control page.",14,Color.GRAY,false));}; rr.run();
    filter.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){rr.run();}public void afterTextChanged(android.text.Editable e){}});
  }

  boolean looksHeader(String a,String b){String x=(a+" "+b).toLowerCase(); return x.contains("nickname")||x.contains("login")||x.contains("player name");}

  void showPlayerActions(Player p){
    String[] actions={"Private message","Warn","Mute","Unmute","Kick","Ban","Unban"};
    new AlertDialog.Builder(this).setTitle(p.nick+"\n"+p.login).setItems(actions,(d,w)->{
      switch(w){case 0: textAction(p,"PM","player.message","Message",false);break;case 1:textAction(p,"WARN","player.warn","Reason / warning text",false);break;case 2:textAction(p,"MUTE","player.mute","Reason",true);break;case 3:confirmAction(p,"UNMUTE","player.unmute","");break;case 4:textAction(p,"KICK","player.kick","Reason",true);break;case 5:textAction(p,"BAN","player.ban","Reason",true);break;case 6:confirmAction(p,"UNBAN","player.unban","");break;}
    }).show();
  }

  void textAction(Player p,String label,String action,String hint,boolean destructive){
    EditText e=input(hint,false); new AlertDialog.Builder(this).setTitle(label+" · "+p.login).setView(e).setNegativeButton("Cancel",null).setPositiveButton(label,(d,w)->submitAction(action,p.login,e.getText().toString())).show();
  }
  void confirmAction(Player p,String label,String action,String extra){new AlertDialog.Builder(this).setTitle(label+" "+p.login+"?").setNegativeButton("Cancel",null).setPositiveButton(label,(d,w)->submitAction(action,p.login,extra)).show();}

  void submitAction(String action,String login,String text){
    pendingMode="action"; String url=BASE+"/index.php?page=control&server="+KEYS.get(server); transport.loadUrl(url); transport.setTag(new Pending(action,login,text));
  }

  void runPendingAction(){
    Object o=transport.getTag(); if(!(o instanceof Pending))return; Pending p=(Pending)o; transport.setTag(null);
    String script="(function(){var A="+q(p.action)+",L="+q(p.login)+",T="+q(p.text)+";var forms=[].slice.call(document.forms);function norm(x){return (x||'').toLowerCase().replace(/[^a-z]/g,'')};var want=norm(A);var f=forms.find(function(z){return norm(z.innerText).indexOf(want.replace('player',''))>=0||[].some.call(z.elements,function(e){return norm(e.value||e.name).indexOf(want.replace('player',''))>=0})});if(!f){ArenaNative.error('Action form not found: '+A);return;}[].forEach.call(f.elements,function(e){var n=(e.name||'').toLowerCase();if(n.indexOf('login')>=0||n.indexOf('player')>=0||n.indexOf('target')>=0)e.value=L;else if(n.indexOf('reason')>=0||n.indexOf('message')>=0||n.indexOf('text')>=0)e.value=T;else if(n==='action'||n.indexOf('action')>=0)e.value=A;});f.submit();})();";
    js(script);
  }

  void showSearch(){
    if(!requireLogin())return; title.setText("ARENA · SEARCH"); body.removeAllViews(); body.addView(t("Player database search",25,Color.WHITE,true));
    EditText q=input("Login or nickname",false);body.addView(q,lpTop(12));Button b=primary("SEARCH");body.addView(b,lpTop(10));
    b.setOnClickListener(v->{if(q.getText().toString().trim().isEmpty())return;pendingMode="search";transport.loadUrl(BASE+"/index.php?page=home");transport.setTag(q.getText().toString().trim());});
  }

  void scrapeSearch(){
    Object o=transport.getTag(); if(o instanceof String){String term=(String)o;transport.setTag(null);js("(function(){var q="+q(term)+";var i=document.querySelector('input[type=search],input[name*=search i],input[placeholder*=search i]');if(!i){ArenaNative.error('Search box not found');return;}i.value=q;var f=i.form||document.querySelector('form');if(f)f.submit();else ArenaNative.error('Search form not found');})();");return;}
    js("(function(){var a=[];document.querySelectorAll('a,tr,.player,.result').forEach(function(e){var t=(e.innerText||'').trim();if(t&&t.length<220)a.push(t)});ArenaNative.search(JSON.stringify(a.slice(0,150)));})();");
  }
  void renderSearch(String json){body.removeAllViews();body.addView(t("Search results",25,Color.WHITE,true));try{JSONArray a=new JSONArray(json);LinkedHashSet<String> uniq=new LinkedHashSet<>();for(int i=0;i<a.length();i++){String x=a.optString(i).trim();if(x.length()>2)uniq.add(x);}for(String x:uniq){Button r=secondary(x);r.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);body.addView(r,lpTop(6));}}catch(Exception e){body.addView(t("Could not parse results.",14,Color.GRAY,false));}}

  void showAnnounce(){
    if(!requireLogin())return; title.setText("ARENA · ANNOUNCE"); body.removeAllViews(); body.addView(t("Send server message",25,Color.WHITE,true));
    Spinner sp=new Spinner(this);ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(KEYS.keySet()));sp.setAdapter(ad);sp.setSelection(new ArrayList<>(KEYS.keySet()).indexOf(server));body.addView(sp,lpTop(12));
    EditText msg=input("Message",false);msg.setMinLines(3);msg.setGravity(Gravity.TOP);body.addView(msg,lpTop(10));Button send=primary("SEND ANNOUNCEMENT");body.addView(send,lpTop(10));send.setOnClickListener(v->{server=(String)sp.getSelectedItem();String m=msg.getText().toString().trim();if(m.isEmpty())return;pendingMode="announceAction";transport.loadUrl(BASE+"/index.php?page=control&server="+KEYS.get(server));transport.setTag(new Pending("chat.announce","",m));});
  }

  void logout(){CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();loggedIn=false;transport.loadUrl("about:blank");showLogin();status.setText("Logged out");}
  boolean requireLogin(){if(!loggedIn){showLogin();return false;}nav.setVisibility(View.VISIBLE);return true;}

  class Bridge{
    @JavascriptInterface public void players(String x){runOnUiThread(()->renderPlayers(x));}
    @JavascriptInterface public void search(String x){runOnUiThread(()->renderSearch(x));}
    @JavascriptInterface public void error(String x){runOnUiThread(()->{toast(x);status.setText("Error");});}
  }

  @Override public void onBackPressed(){showDashboard();}
  void js(String x){transport.evaluateJavascript(x,null);}
  String q(String s){return JSONObject.quote(s==null?"":s);}
  void toast(String x){Toast.makeText(this,x,Toast.LENGTH_LONG).show();}
  int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  TextView t(String s,int size,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(c);if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
  EditText input(String hint,boolean password){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(100,116,140));e.setTextColor(Color.WHITE);e.setBackgroundColor(Color.rgb(15,21,31));e.setPadding(dp(14),dp(10),dp(14),dp(10));if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
  Button primary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.BLACK);b.setTextSize(15);b.setAllCaps(false);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setBackgroundColor(Color.rgb(73,213,230));return b;}
  Button secondary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setAllCaps(false);b.setBackgroundColor(Color.rgb(25,34,49));return b;}
  Button chip(String s){Button b=secondary(s);b.setTextSize(13);return b;}
  LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackgroundColor(Color.rgb(18,27,41));return c;}
  LinearLayout.LayoutParams lp(){return new LinearLayout.LayoutParams(-1,dp(54));}
  LinearLayout.LayoutParams lpTop(int n){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(n);return p;}
  void addNav(String s,Runnable r){Button b=secondary(s);b.setTextSize(11);b.setBackgroundColor(Color.TRANSPARENT);b.setOnClickListener(v->r.run());nav.addView(b,new LinearLayout.LayoutParams(0,-1,1));}
  static class Player{final String nick,login;Player(String n,String l){nick=n;login=l;}}
  static class Pending{final String action,login,text;Pending(String a,String l,String t){action=a;login=l;text=t;}}

  // Whenever a control page finishes loading and an action is waiting, run the action form.
  void maybeRunAction(){if((pendingMode.equals("action")||pendingMode.equals("announceAction"))&&transport.getTag() instanceof Pending)runPendingAction();}
}
