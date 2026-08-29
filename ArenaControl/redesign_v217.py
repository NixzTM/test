from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

# Remove stale pre-v2.15 loadMobileChat implementation left behind after chatKey replacement.
sig='  private List<ChatLine> loadMobileChat()throws Exception{HttpResult r=mobileRequest("GET","/v1/chat?limit=120",null);'
idx=s.find(sig)
if idx>=0:
    b=s.find('{',idx); d=0; j=b
    while j<len(s):
        if s[j]=='{': d+=1
        elif s[j]=='}':
            d-=1
            if d==0:
                j+=1; break
        j+=1
    s=s[:idx]+s[j:]

# The PM button in fullPlayerRow must bind to its row's server rather than referencing a variable
# that only exists inside showPlayerActions.
old='''  private View fullPlayerRow(Player p){LinearLayout r=new LinearLayout(this);'''
new='''  private View fullPlayerRow(Player p){final String rowServer=server;LinearLayout r=new LinearLayout(this);'''
if old not in s: raise SystemExit('fullPlayerRow marker missing')
s=s.replace(old,new,1)
s=s.replace('mobileActionFor(actionServer,"player.message",p.login,m,0)','mobileActionFor(rowServer,"player.message",p.login,m,0)',1)

# Sanity guards: only one chat loader and no stale pre-v2.15 field references may remain.
if s.count('private List<ChatLine> loadMobileChat()') != 1:
    raise SystemExit('unexpected loadMobileChat count')
for stale in ('lastServerChatKey;', 'localSentChat)'):
    if stale in s:
        raise SystemExit('stale symbol remains: '+stale)

p.write_text(s)
print('v2.17 cleanup applied; v2.16 feature set preserved')
