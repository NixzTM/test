from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

sig='  private List<ChatLine> loadMobileChat(String srv)throws Exception'
starts=[]
pos=0
while True:
    i=s.find(sig,pos)
    if i<0: break
    starts.append(i)
    pos=i+len(sig)

if len(starts)<2:
    raise SystemExit('expected duplicate live-chat loader after v2.27, got '+str(len(starts)))

# redesign_v227 replaces the no-arg loader with wrapper + NEW server-specific loader,
# so the new snapshot implementation is the FIRST server-specific overload. Remove older later ones.
for i in reversed(starts[1:]):
    b=s.find('{',i)
    if b<0: raise SystemExit('loader body missing')
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
    raise SystemExit('v2.28 duplicate live-chat loader cleanup failed')
if s.count('private List<ChatLine> loadMobileChat()')!=1:
    raise SystemExit('v2.28 no-arg loader count invalid')
if 'chatSnapshotKeysByServer.get(srv)' not in s or 'chatSnapshotKeysByServer.put(srv' not in s:
    raise SystemExit('v2.27 snapshot loader was not preserved')
if 'observedChatByServer' not in s:
    raise SystemExit('observed chat state missing')

p.write_text(s)
print('v2.28 duplicate live-chat loader cleanup applied; snapshot loader preserved')
