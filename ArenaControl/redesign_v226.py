from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

old='''  private void buildShell(){\n    root=new FrameLayout(this); root.setBackgroundColor(BG);'''
new='''  private void buildShell(){\n    root=new FrameLayout(this); root.setBackgroundColor(BG);\n    root.setOnApplyWindowInsetsListener((v,insets)->{\n      int l=insets.getSystemWindowInsetLeft(), t=insets.getSystemWindowInsetTop();\n      int r=insets.getSystemWindowInsetRight(), b=insets.getSystemWindowInsetBottom();\n      v.setPadding(l,t,r,b);\n      return insets;\n    });\n    root.requestApplyInsets();'''
if old not in s: raise SystemExit('buildShell marker missing')
s=s.replace(old,new,1)

p.write_text(s)
print('v2.26 system bar safe-area insets applied')
