from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

sig='  private List<ChatLine> loadMobileChat(String srv)throws Exception'
starts=[]
pos=0
while True:
    i=s.find(sig,pos)
    if i<0: break
    starts.append(i); pos=i+len(sig)

if len(starts)<2:
    raise SystemExit('expected duplicate loadMobileChat(String srv) after v2.21')

# Remove every duplicate overload after the first, preserving the v2.21 persisted-chatlog implementation.
for i in reversed(starts[1:]):
    b=s.find('{',i); d=0; j=b
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
    raise SystemExit('loadMobileChat(String srv) cleanup failed')
if s.count('private List<ChatLine> loadMobileChat()')!=1:
    raise SystemExit('loadMobileChat() wrapper count invalid')

p.write_text(s)
print('v2.22 duplicate chat loader cleanup applied')
