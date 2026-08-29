from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

# AC4 and AC7 now run the same pinned-TLS mobile.admin addon as AC3 on their own ports.
needle='  private static final String MOBILE_AC3="https://87.237.52.153:47832";'
if needle not in s:
    raise SystemExit('MOBILE_AC3 constant missing')
s=s.replace(needle, needle+'\n  private static final String MOBILE_AC4="https://87.237.52.153:47834";\n  private static final String MOBILE_AC7="https://87.237.52.153:47837";',1)

# Every app-supported server now has a direct addon. Keep this centralized so no action can
# accidentally fall back to the website control transport for AC4/AC7.
mark='  private LinearLayout pair(String l1,String a1,String l2,String a2)'
if mark not in s:
    raise SystemExit('direct helper insertion marker missing')
helper='''  private boolean isDirectServer(){return server.equals("AC3")||server.equals("AC4")||server.equals("AC7");}\n  private String mobileBase(){if(server.equals("AC4"))return MOBILE_AC4;if(server.equals("AC7"))return MOBILE_AC7;return MOBILE_AC3;}\n'''
s=s.replace(mark,helper+mark,1)

# Promote all existing AC3-only direct paths (state/live/actions/player directory/bans) to the
# supported direct-server set. This intentionally does not change website login/session validation.
s=s.replace('server.equals("AC3")','isDirectServer()')
s=s.replace('!isDirectServer()','!isDirectServer()')

# The global replacement above also touched mobileBase's first AC3 comparison if present; normalize
# the helper to explicit server routing.
old='''  private boolean isDirectServer(){return isDirectServer()||server.equals("AC4")||server.equals("AC7");}\n  private String mobileBase(){if(server.equals("AC4"))return MOBILE_AC4;if(server.equals("AC7"))return MOBILE_AC7;return MOBILE_AC3;}\n'''
new='''  private boolean isDirectServer(){return server.equals("AC3")||server.equals("AC4")||server.equals("AC7");}\n  private String mobileBase(){if(server.equals("AC4"))return MOBILE_AC4;if(server.equals("AC7"))return MOBILE_AC7;return MOBILE_AC3;}\n'''
if old in s:
    s=s.replace(old,new,1)

# Use the selected server endpoint instead of AC3's fixed port.
if 'new URL(MOBILE_AC3+path)' not in s:
    raise SystemExit('mobileRequest fixed AC3 URL marker missing')
s=s.replace('new URL(MOBILE_AC3+path)','new URL(mobileBase()+path)',1)

# Chat rows should identify the server they actually came from.
s=s.replace('c.sourceKey="AC3";','c.sourceKey=server;')

# User-facing transport errors are no longer AC3-specific.
s=s.replace('"AC3 addon HTTP "+r.code','"Mobile addon HTTP "+r.code')
s=s.replace('"AC3 mobile certificate mismatch"','"Mobile admin certificate mismatch"')
s=s.replace('"This direct control is staged on AC3 first."','"Direct control is unavailable for this server."')
s=s.replace('"Offline search direct API is staged on LJA first."','"Offline search is unavailable for this server."')
s=s.replace('"Ban management direct API is staged on LJA first."','"Ban management is unavailable for this server."')
s=s.replace('server.equals("AC3")?"Waiting for game chat…":"Direct chat addon is currently AC3 only."','isDirectServer()?"Waiting for game chat…":"Direct chat unavailable."')

# Make the client identifier match the new release while preserving website auth behavior.
s=s.replace('ArenaControl-Android/2.5','ArenaControl-Android/2.14')

p.write_text(s)
print('v2.14 AC3/AC4/AC7 direct mobile transport applied')
