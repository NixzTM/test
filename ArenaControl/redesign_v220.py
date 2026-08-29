from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

# v2.20: hard-isolate the rendered chat pane itself by server. v2.18 bound requests to a server,
# but the stable in-place chat widget still had global view/signature state. Never let a widget
# created for AC4 survive or accept data after switching to AC7 (or vice versa).
needle='  private String chatNickname="";'
if needle not in s: raise SystemExit('chat nickname state marker missing')
s=s.replace(needle, needle+'\n  private String chatViewServer="";',1)

# On every server switch invalidate/remove every chat view reference before any asynchronous load.
old='''    chatList=null;chatScroller=null;chatComposer=null;
    lastChatSignature="";chatSavedY=0;chatStickBottom=true;chatRenderedOnce=false;chatHoldUntil=0L;'''
new='''    chatList=null;chatScroller=null;chatComposer=null;chatViewServer="";
    lastChatSignature="";chatSavedY=0;chatStickBottom=true;chatRenderedOnce=false;chatHoldUntil=0L;'''
if old not in s: raise SystemExit('switchServer chat reset marker missing')
s=s.replace(old,new,1)

# Ensure all server buttons still route through switchServer even after later UI transforms.
# This catches both the dashboard OPEN buttons and the in-server selector buttons.
s=s.replace('b.setOnClickListener(v->{server=ss;pane="chat";loadControl();});','b.setOnClickListener(v->switchServer(ss));')
s=s.replace('b.setOnClickListener(v->{server=s;pane="chat";loadControl();});','b.setOnClickListener(v->switchServer(s));')

# The chat pane records which server owns the current list widget.
old='''  private void renderChatPane(LinearLayout live,ParsedControl pc){
    final String paneServer=server;'''
new='''  private void renderChatPane(LinearLayout live,ParsedControl pc){
    final String paneServer=server;
    chatViewServer=paneServer;'''
if old not in s: raise SystemExit('renderChatPane marker missing')
s=s.replace(old,new,1)

# In-place updates must only touch a widget owned by the currently selected server.
old='''  private void updateChatListInPlace(List<ChatLine> lines){if(chatList==null)return;String sig=chatSignature(lines);'''
new='''  private void updateChatListInPlace(List<ChatLine> lines){if(chatList==null||chatViewServer==null||!chatViewServer.equals(server))return;String sig=chatSignature(lines);'''
if old not in s: raise SystemExit('updateChatListInPlace marker missing')
s=s.replace(old,new,1)

# Guard renderControl too: if its stable chat pane belongs to another server, discard it before rendering.
old='''  private void renderControl(ParsedControl pc){
    if(screen!=Screen.SERVER)return;'''
new='''  private void renderControl(ParsedControl pc){
    if(screen!=Screen.SERVER)return;
    if(!chatViewServer.isEmpty()&&!chatViewServer.equals(server)){chatList=null;chatScroller=null;chatComposer=null;chatViewServer="";lastChatSignature="";}'''
if old not in s:
    # tolerate compact signature from older transform
    old='''  private void renderControl(ParsedControl pc){ if(screen!=Screen.SERVER)return;'''
    new='''  private void renderControl(ParsedControl pc){ if(screen!=Screen.SERVER)return;if(!chatViewServer.isEmpty()&&!chatViewServer.equals(server)){chatList=null;chatScroller=null;chatComposer=null;chatViewServer="";lastChatSignature="";}'''
    if old not in s: raise SystemExit('renderControl marker missing')
    s=s.replace(old,new,1)
else:
    s=s.replace(old,new,1)

# Build-time sanity checks.
if 'chatViewServer=paneServer;' not in s: raise SystemExit('chat owner guard missing')
if 'b.setOnClickListener(v->{server=ss;pane="chat";loadControl();});' in s: raise SystemExit('stale selector handler remains')

p.write_text(s)
print('v2.20 hard chat-view server isolation applied')
