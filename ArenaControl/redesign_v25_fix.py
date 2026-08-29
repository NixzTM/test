from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()
s=s.replace('new Player(lg,TmnfText.plain(nn),"",x.optBoolean("spectator",false))','new Player(lg,nn,"",x.optBoolean("spectator",false))')
p.write_text(s)
print('v2.5 raw TMNF nicknames preserved')
