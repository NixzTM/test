from pathlib import Path
p=Path('app/src/main/java/com/arenacommunity/control/MainActivity.java')
s=p.read_text()

# Inject safe-area handling immediately after root is created, regardless of surrounding formatting.
needle='root=new FrameLayout(this);'
if needle not in s:
    raise SystemExit('root creation marker missing')
insert='''root=new FrameLayout(this);\n    root.setOnApplyWindowInsetsListener((v,insets)->{\n      int l=insets.getSystemWindowInsetLeft(), t=insets.getSystemWindowInsetTop();\n      int r=insets.getSystemWindowInsetRight(), b=insets.getSystemWindowInsetBottom();\n      v.setPadding(l,t,r,b);\n      return insets;\n    });\n    root.requestApplyInsets();'''
s=s.replace(needle,insert,1)

p.write_text(s)
print('v2.26 system bar safe-area insets applied')
