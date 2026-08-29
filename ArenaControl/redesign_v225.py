from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

# v2.24 intentionally replaces the no-arg chat loader with a wrapper + new server-specific loader,
# while an older server-specific overload is still present from v2.21/v2.22. Keep the LAST overload
# (the v2.24 per-server baseline implementation) and remove all older duplicates.
sig='  private List<ChatLine> loadMobileChat(String srv)throws Exception'
starts=[]
pos=0
while True:
    i=s.find(sig,pos)
    if i<0: break
    starts.append(i)
    pos=i+len(sig)

if len(starts)<2:
    raise SystemExit('expected duplicate loadMobileChat(String srv) after v2.24, got '+str(len(starts)))

# Remove every overload except the last one. Process backwards so offsets stay valid.
for i in reversed(starts[:-1]):
    b=s.find('{',i)
    d=0
    j=b
    while j<len(s):
        if s[j]=='{': d+=1
        elif s[j]=='}':
            d-=1
            if d==0:
                j+=1
                break
        j+=1
    s=s[:i]+s[j:]

if s.count(sig)!=1:
    raise SystemExit('v2.25 server-specific chat loader cleanup failed')
if s.count('private List<ChatLine> loadMobileChat()')!=1:
    raise SystemExit('v2.25 no-arg chat loader count invalid')
if 'chatBaselineKeyByServer' not in s or 'observedChatByServer' not in s:
    raise SystemExit('v2.24 chat isolation implementation was not preserved')

p.write_text(s)
print('v2.25 duplicate chat loader cleanup applied; v2.24 isolation preserved')
