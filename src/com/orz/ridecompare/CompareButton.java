package com.orz.ridecompare;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** One accessible button. Service icons come from the user's installed apps. */
public class CompareButton extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Drawable uberIcon, lyftIcon;
  private boolean busy, ready;

  public CompareButton(Context c) {
    super(c);
    setClickable(true);
    setFocusable(true);
    setContentDescription("比较价格，同时打开 Uber 和 Lyft 双屏看价");
    uberIcon = loadIcon(c, "com.ubercab");
    lyftIcon = loadIcon(c, "me.lyft.android");
  }

  private Drawable loadIcon(Context c, String pkg) {
    try {
      return c.getPackageManager().getApplicationIcon(pkg).mutate();
    } catch (Exception e) {
      return null;
    }
  }

  private float dp(float v) {
    return v * getResources().getDisplayMetrics().density;
  }

  private float sp(float v) {
    return v * getResources().getDisplayMetrics().scaledDensity;
  }

  public void setReady(boolean value) {
    ready = value;
    refreshState();
  }

  public void setBusy(boolean value) {
    busy = value;
    refreshState();
  }

  private void refreshState() {
    setEnabled(ready && !busy);
    setAlpha(1f);
    setContentDescription(busy ? "正在准备行程" : ready ? "比较价格，同时打开 Uber 和 Lyft 双屏看价" : "比较价格，尚未选中目的地");
    invalidate();
  }

  @Override
  public CharSequence getAccessibilityClassName() {
    return android.widget.Button.class.getName();
  }

  @Override
  protected void drawableStateChanged() {
    super.drawableStateChanged();
    invalidate();
  }

  private void text(Canvas c, String s, float x, float y, int size, int color, boolean bold) {
    paint.setColor(color);
    paint.setTextSize(sp(size));
    paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
    paint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics fm = paint.getFontMetrics();
    c.drawText(s, x, y - (fm.ascent + fm.descent) / 2, paint);
  }

  private void appIcon(Canvas c, Drawable icon, float x, float y, String fallback, int color) {
    float half = dp(32);
    RectF bounds = new RectF(x - half, y - half, x + half, y + half);
    Path path = new Path();
    path.addRoundRect(bounds, dp(16), dp(16), Path.Direction.CW);
    c.save();
    c.clipPath(path);
    if (icon != null) {
      icon.setBounds(
          Math.round(bounds.left),
          Math.round(bounds.top),
          Math.round(bounds.right),
          Math.round(bounds.bottom));
      icon.draw(c);
    } else {
      paint.setColor(color);
      c.drawRect(bounds, paint);
      text(c, fallback, x, y, 18, Color.WHITE, true);
    }
    c.restore();
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float w = getWidth(), h = getHeight(), center = w / 2;
    RectF body = new RectF(dp(7), dp(7), w - dp(7), h - dp(7));
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(
        new LinearGradient(
            0,
            0,
            w,
            h,
            ready ? new int[] {0xFF211B2B, 0xFF432558} : new int[] {0xFF19191F, 0xFF292332},
            null,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(body, dp(22), dp(22), paint);
    paint.setShader(null);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(1.2f));
    paint.setColor(isPressed() || isFocused() ? 0xFFD0B7FF : ready ? 0xFF9864C7 : 0xFF4B4358);
    c.drawRoundRect(body, dp(22), dp(22), paint);
    paint.setStyle(Paint.Style.FILL);
    float gap = Math.min(dp(82), w * .28f), y = dp(43);
    appIcon(c, uberIcon, center - gap, y, "Uber", 0xFF08080C);
    appIcon(c, lyftIcon, center + gap, y, "lyft", 0xFF713DE8);
    // Two framed panes communicate the result: both apps open side by side.
    RectF window = new RectF(center - dp(16), y - dp(12), center + dp(16), y + dp(12));
    paint.setColor(0xFF19171F);
    c.drawRoundRect(window, dp(4), dp(4), paint);
    paint.setColor(0xFFB39ACB);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(1.4f));
    c.drawRoundRect(window, dp(4), dp(4), paint);
    c.drawLine(center, window.top, center, window.bottom, paint);
    c.drawLine(window.left, y - dp(6), window.right, y - dp(6), paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xFF08080C);
    c.drawRoundRect(
        new RectF(window.left + dp(3), y - dp(3), center - dp(3), window.bottom - dp(3)),
        dp(2),
        dp(2),
        paint);
    paint.setColor(0xFF713DE8);
    c.drawRoundRect(
        new RectF(center + dp(3), y - dp(3), window.right - dp(3), window.bottom - dp(3)),
        dp(2),
        dp(2),
        paint);
    text(c, "比较价格", center, dp(94), 19, ready ? 0xFFF5EFFD : 0xFF9E95AC, true);
    if (busy) {
      float cx = center + paint.measureText("比较价格") / 2 + dp(16), cy = dp(94), r = dp(7);
      paint.setColor(0xFFB18AFF);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(2));
      paint.setStrokeCap(Paint.Cap.ROUND);
      c.drawArc(
          new RectF(cx - r, cy - r, cx + r, cy + r),
          (android.os.SystemClock.uptimeMillis() % 1000) * .36f,
          260,
          false,
          paint);
      paint.setStrokeCap(Paint.Cap.BUTT);
      paint.setStyle(Paint.Style.FILL);
      postInvalidateDelayed(32);
    }

    if (isPressed() || isFocused()) {
      paint.setColor(0x227F55CB);
      c.drawRoundRect(body, dp(22), dp(22), paint);
    }
  }
}
