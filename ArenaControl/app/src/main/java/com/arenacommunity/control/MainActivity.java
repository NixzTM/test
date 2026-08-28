package com.arenacommunity.control;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String BASE = "https://arena-community.com";
    private static final String LOGIN = BASE + "/login.php";
    private static final String LIVE = BASE + "/index.php?page=live";
    private static final String PLAYERS = BASE + "/index.php?page=players";
    private static final String SEARCH = BASE + "/index.php?page=home";

    private static final Map<String, String> SERVERS = new LinkedHashMap<>();
    static {
        SERVERS.put("AC3", "LJA");
        SERVERS.put("AC4", "AC4");
        SERVERS.put("AC7", "AC7");
    }

    private WebView web;
    private ProgressBar progress;
    private TextView title;
    private TextView status;
    private String selectedServer = "AC3";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else loadControl(selectedServer);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        web.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " ARENA-Control-Android/1.0");
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (u.startsWith(BASE) || u.startsWith("https://www.arena-community.com")) return false;
                Toast.makeText(MainActivity.this, "External navigation blocked", Toast.LENGTH_SHORT).show();
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                setConnectionText("Loading…");
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                setConnectionText(isOnline() ? "Connected" : "Offline");
                if (url.contains("login.php")) title.setText("ARENA · Login");
                applyMobileAdminCss();
            }
        });
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16,18,22));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(8), dp(10), dp(8));
        header.setBackgroundColor(Color.rgb(22,25,31));

        title = new TextView(this);
        title.setText("ARENA · AC3");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        status = new TextView(this);
        status.setText("Starting");
        status.setTextColor(Color.rgb(151,160,173));
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(status, new LinearLayout.LayoutParams(dp(110), dp(48)));
        column.addView(header, new LinearLayout.LayoutParams(-1, dp(64)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        column.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));

        LinearLayout serverStrip = new LinearLayout(this);
        serverStrip.setOrientation(LinearLayout.HORIZONTAL);
        serverStrip.setPadding(dp(8), dp(5), dp(8), dp(5));
        serverStrip.setBackgroundColor(Color.rgb(18,20,25));
        for (String name : SERVERS.keySet()) {
            Button b = navButton(name);
            b.setOnClickListener(v -> loadControl(name));
            serverStrip.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1));
        }
        column.addView(serverStrip, new LinearLayout.LayoutParams(-1, dp(54)));

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(16,18,22));
        column.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(4), dp(4), dp(4), dp(4));
        bottom.setBackgroundColor(Color.rgb(22,25,31));

        addBottom(bottom, "Server", v -> loadControl(selectedServer));
        addBottom(bottom, "Live", v -> loadUrl(LIVE, "ARENA · Live"));
        addBottom(bottom, "Players", v -> loadUrl(PLAYERS, "ARENA · Players"));
        addBottom(bottom, "Search", v -> loadUrl(SEARCH, "ARENA · Search"));
        addBottom(bottom, "Login", v -> loadUrl(LOGIN, "ARENA · Login"));
        column.addView(bottom, new LinearLayout.LayoutParams(-1, dp(58)));

        setContentView(root);
    }

    private void addBottom(LinearLayout row, String text, View.OnClickListener l) {
        Button b = navButton(text);
        b.setTextSize(12);
        b.setOnClickListener(l);
        row.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private Button navButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private void loadControl(String displayName) {
        selectedServer = displayName;
        String key = SERVERS.get(displayName);
        title.setText("ARENA · " + displayName);
        loadUrl(BASE + "/index.php?page=control&server=" + key, "ARENA · " + displayName);
    }

    private void loadUrl(String url, String heading) {
        title.setText(heading);
        if (!isOnline()) {
            setConnectionText("Offline");
            Toast.makeText(this, "No network connection", Toast.LENGTH_SHORT).show();
        }
        web.loadUrl(url);
    }

    private void applyMobileAdminCss() {
        String js = "(function(){" +
            "var id='arenaAndroidMobile';if(document.getElementById(id))return;" +
            "var s=document.createElement('style');s.id=id;" +
            "s.textContent='body{overscroll-behavior:none} .container,.wrap,main{max-width:100%!important} table{font-size:13px} input,select,textarea,button{font-size:16px!important;min-height:42px} .control-grid{grid-template-columns:1fr!important} .control-card{margin-bottom:10px}';" +
            "document.head.appendChild(s);" +
            "})();";
        web.evaluateJavascript(js, null);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities c = cm.getNetworkCapabilities(n);
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void setConnectionText(String s) { status.setText(s); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else new AlertDialog.Builder(this)
                .setTitle("Close ARENA Control?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close", (d,w) -> finish())
                .show();
    }
}
