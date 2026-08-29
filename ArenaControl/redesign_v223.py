from pathlib import Path

root=Path('.')
p=root/'app/src/main/java/com/arenacommunity/control/MainActivity.java'
s=p.read_text()

# --- Hard chat clearing on server switch ---
# The bug is UI-state persistence: the old server's chat view remains visible until the new
# server response arrives. Clear the actual list immediately and invalidate its ownership/signature.
old='''  private void switchServer(String srv){
    if(srv==null||!KEYS.containsKey(srv))return;
    stopLive();
    server=srv;pane="chat";
    chatList=null;chatScroller=null;chatComposer=null;chatViewServer="";
    lastChatSignature="";chatSavedY=0;chatStickBottom=true;chatRenderedOnce=false;chatHoldUntil=0L;
    screen=Screen.SERVER;title.setText("ΛRENA · "+srv);body.removeAllViews();
    LinearLayout loading=card();loading.addView(txt("GAME CHAT",11,CYAN,true));loading.addView(txt("Loading "+serverLabel(srv)+" chatlog…",14,MUTED,false),lpTop(8));body.addView(loading);
    loadControl();
  }'''
new='''  private void switchServer(String srv){
    if(srv==null||!KEYS.containsKey(srv))return;
    stopLive();
    // Clear the currently rendered chat rows BEFORE changing server state.
    if(chatList!=null)chatList.removeAllViews();
    server=srv;pane="chat";
    chatList=null;chatScroller=null;chatComposer=null;chatViewServer="";
    lastChatSignature="";chatSavedY=0;chatStickBottom=true;chatRenderedOnce=false;chatHoldUntil=0L;
    screen=Screen.SERVER;title.setText("ΛRENA · "+srv);body.removeAllViews();
    LinearLayout loading=card();loading.addView(txt("GAME CHAT",11,CYAN,true));loading.addView(txt("Loading "+serverLabel(srv)+" chatlog…",14,MUTED,false),lpTop(8));body.addView(loading);
    loadControl();
  }'''
if old not in s: raise SystemExit('switchServer v2.22 body not found')
s=s.replace(old,new,1)

# Make chat signatures server-specific. A feed that happens to have the same text as another
# server must never suppress a repaint after switching.
needle='  private String chatViewServer="";'
if needle not in s: raise SystemExit('chatViewServer marker missing')
s=s.replace(needle,needle+'\n  private final java.util.concurrent.ConcurrentHashMap<String,String> chatSignatureByServer=new java.util.concurrent.ConcurrentHashMap<>();',1)

old='''  private void updateChatListInPlace(List<ChatLine> lines){if(chatList==null||chatViewServer==null||!chatViewServer.equals(server))return;String sig=chatSignature(lines);if(sig.equals(lastChatSignature))return;boolean bottom=chatStickBottom;int y=chatSavedY;lastChatSignature=sig;chatList.removeAllViews();'''
new='''  private void updateChatListInPlace(List<ChatLine> lines){if(chatList==null||chatViewServer==null||!chatViewServer.equals(server))return;String owner=chatViewServer;String sig=chatSignature(lines);String prev=chatSignatureByServer.getOrDefault(owner,"");if(sig.equals(prev)&&sig.equals(lastChatSignature))return;boolean bottom=chatStickBottom;int y=chatSavedY;chatSignatureByServer.put(owner,sig);lastChatSignature=sig;chatList.removeAllViews();'''
if old not in s: raise SystemExit('updateChatListInPlace v2.20 body not found')
s=s.replace(old,new,1)

# renderChatPane must always start from an empty list for the selected server, never from a prior view.
old='''    lastChatSignature="";updateChatListInPlace(pc.chat);'''
new='''    lastChatSignature="";chatSignatureByServer.remove(paneServer);list.removeAllViews();updateChatListInPlace(pc.chat);'''
if old not in s: raise SystemExit('render chat initial update marker missing')
s=s.replace(old,new,1)

# --- Preserve authenticated Activity across orientation changes ---
# Rotation was recreating MainActivity, destroying in-memory auth/session state. Handle orientation
# and screen-size changes in-place instead of recreating the Activity.
manifest=root/'app/src/main/AndroidManifest.xml'
m=manifest.read_text()
oldm='android:screenOrientation="unspecified">'
newm='android:screenOrientation="unspecified"\n            android:configChanges="orientation|screenSize|keyboardHidden">'
if oldm not in m:
    if 'android:configChanges=' not in m: raise SystemExit('manifest orientation marker missing')
else:
    m=m.replace(oldm,newm,1)
manifest.write_text(m)

p.write_text(s)
print('v2.23 hard chat clearing + per-server signatures + rotation session preservation applied')
