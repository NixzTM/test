package com.arenacommunity.control;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

public final class TmnfText {
  private TmnfText() {}

  public static CharSequence render(String raw, int defaultColor) {
    if (raw == null) raw = "";
    SpannableStringBuilder out = new SpannableStringBuilder();
    int color = defaultColor;
    int start = 0;
    boolean bold = false;
    boolean italic = false;

    for (int i = 0; i < raw.length();) {
      char ch = raw.charAt(i);
      if (ch != '$') {
        out.append(ch);
        i++;
        continue;
      }
      if (i + 1 >= raw.length()) {
        out.append('$');
        break;
      }
      char code = raw.charAt(i + 1);
      if (code == '$') {
        out.append('$');
        i += 2;
        continue;
      }
      if (isHex(code) && i + 3 < raw.length() && isHex(raw.charAt(i + 2)) && isHex(raw.charAt(i + 3))) {
        apply(out, start, out.length(), color, bold, italic);
        start = out.length();
        color = Color.rgb(
            Character.digit(raw.charAt(i + 1), 16) * 17,
            Character.digit(raw.charAt(i + 2), 16) * 17,
            Character.digit(raw.charAt(i + 3), 16) * 17);
        i += 4;
        continue;
      }
      char c = Character.toLowerCase(code);
      if (c == 'z' || c == 'g') {
        apply(out, start, out.length(), color, bold, italic);
        start = out.length();
        color = defaultColor;
        bold = false;
        italic = false;
        i += 2;
        continue;
      }
      if (c == 'o') {
        apply(out, start, out.length(), color, bold, italic);
        start = out.length();
        bold = !bold;
        i += 2;
        continue;
      }
      if (c == 'i') {
        apply(out, start, out.length(), color, bold, italic);
        start = out.length();
        italic = !italic;
        i += 2;
        continue;
      }
      if (c == 's' || c == 'w' || c == 'n' || c == 'm' || c == 'h' || c == 'p' || c == 'l') {
        i += 2;
        continue;
      }
      out.append('$');
      i++;
    }
    apply(out, start, out.length(), color, bold, italic);
    return out;
  }

  public static String plain(String raw) {
    return render(raw, Color.WHITE).toString();
  }

  private static void apply(SpannableStringBuilder b, int s, int e, int color, boolean bold, boolean italic) {
    if (e <= s) return;
    b.setSpan(new ForegroundColorSpan(color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    int style = bold && italic ? Typeface.BOLD_ITALIC : bold ? Typeface.BOLD : italic ? Typeface.ITALIC : Typeface.NORMAL;
    if (style != Typeface.NORMAL) b.setSpan(new StyleSpan(style), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  private static boolean isHex(char c) {
    return Character.digit(c, 16) >= 0;
  }
}
