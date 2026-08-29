package com.arenacommunity.control;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.ScaleXSpan;
import android.text.style.StyleSpan;
import android.text.style.UpdateAppearance;
import java.util.Locale;

public final class TmnfText {
  private TmnfText() {}

  public static CharSequence render(String raw, int defaultColor) {
    if (raw == null) raw = "";
    SpannableStringBuilder out = new SpannableStringBuilder();
    int color = defaultColor, start = 0;
    boolean bold = false, italic = false, shadow = false, upper = false;
    float scale = 1f;

    for (int i = 0; i < raw.length();) {
      char ch = raw.charAt(i);
      if (ch != '$') {
        int cp = raw.codePointAt(i);
        String v = new String(Character.toChars(cp));
        out.append(upper ? v.toUpperCase(Locale.ROOT) : v);
        i += Character.charCount(cp);
        continue;
      }
      if (i + 1 >= raw.length()) { out.append('$'); break; }
      char code = raw.charAt(i + 1);
      if (code == '$') { out.append('$'); i += 2; continue; }

      if (isHex(code) && i + 3 < raw.length() && isHex(raw.charAt(i + 2)) && isHex(raw.charAt(i + 3))) {
        apply(out,start,out.length(),color,bold,italic,shadow,scale); start=out.length();
        color=Color.rgb(Character.digit(raw.charAt(i+1),16)*17,Character.digit(raw.charAt(i+2),16)*17,Character.digit(raw.charAt(i+3),16)*17);
        i+=4; continue;
      }

      char c=Character.toLowerCase(code);
      if(c=='z'){
        apply(out,start,out.length(),color,bold,italic,shadow,scale); start=out.length();
        color=defaultColor;bold=false;italic=false;shadow=false;upper=false;scale=1f;i+=2;continue;
      }
      if(c=='g'){
        apply(out,start,out.length(),color,bold,italic,shadow,scale); start=out.length();
        color=defaultColor;i+=2;continue;
      }
      if(c=='o'){
        apply(out,start,out.length(),color,bold,italic,shadow,scale); start=out.length();
        bold=true;i+=2;continue;
      }
      if(c=='i'){
        apply(out,start,out.length(),color,bold,italic,shadow,scale); start=out.length();
        italic=true;i+=2;continue;
      }
      if(c=='s'){
        apply(out,start,out.length(),color,bold,italic,shadow,scale); start=out.length();
        shadow=true;i+=2;continue;
      }
      if(c=='t'){
        apply(out,start,out.length(),color,bold,italic,shadow,scale); start=out.length();
        upper=true;i+=2;continue;
      }
      if(c=='w'){
        apply(out,start,out.length(),color,bold,italic,shadow,scale); start=out.length();
        scale=1.22f;i+=2;continue;
      }
      if(c=='n'){
        apply(out,start,out.length(),color,bold,italic,shadow,scale); start=out.length();
        scale=.82f;i+=2;continue;
      }
      if(c=='m'){
        apply(out,start,out.length(),color,bold,italic,shadow,scale); start=out.length();
        scale=1f;i+=2;continue;
      }

      // TMNF hyperlink/control markers are not visible glyphs. Consume the marker while
      // retaining the actual nickname/message text following it.
      if(c=='h'||c=='p'){i+=2;continue;}
      if(c=='l'){
        i+=2;
        if(i<raw.length() && raw.charAt(i)=='['){int end=raw.indexOf(']',i+1);if(end>=0)i=end+1;}
        continue;
      }

      // Unknown '$x' sequence: preserve it literally instead of dropping user text.
      out.append('$'); i++;
    }
    apply(out,start,out.length(),color,bold,italic,shadow,scale);
    return out;
  }

  public static String plain(String raw){ return render(raw,Color.WHITE).toString(); }

  private static void apply(SpannableStringBuilder b,int s,int e,int color,boolean bold,boolean italic,boolean shadow,float scale){
    if(e<=s)return;
    b.setSpan(new ForegroundColorSpan(color),s,e,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    int style=bold&&italic?Typeface.BOLD_ITALIC:bold?Typeface.BOLD:italic?Typeface.ITALIC:Typeface.NORMAL;
    if(style!=Typeface.NORMAL)b.setSpan(new StyleSpan(style),s,e,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    if(Math.abs(scale-1f)>.01f)b.setSpan(new ScaleXSpan(scale),s,e,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    if(shadow)b.setSpan(new ShadowSpan(),s,e,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  private static boolean isHex(char c){return Character.digit(c,16)>=0;}

  private static final class ShadowSpan extends CharacterStyle implements UpdateAppearance {
    @Override public void updateDrawState(TextPaint tp){tp.setShadowLayer(2.2f,1.2f,1.2f,0xff000000);}
  }
}
