package com.orz.ridecompare;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Soft paired shadows for a raised surface or a recessed input well. */
public final class NeoDrawable extends Drawable {
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float scale;
  private final boolean inset;
  private boolean pressed;

  public NeoDrawable(float density, boolean recessed) {
    scale = density;
    inset = recessed;
  }

  @Override
  public boolean isStateful() {
    return true;
  }

  @Override
  protected boolean onStateChange(int[] states) {
    boolean next = false;
    for (int s : states) if (s == android.R.attr.state_pressed) next = true;
    boolean changed = next != pressed;
    pressed = next;
    invalidateSelf();
    return changed;
  }

  @Override
  public void draw(Canvas c) {
    Rect b = getBounds();
    float margin = 7 * scale, radius = 22 * scale;
    RectF r = new RectF(b.left + margin, b.top + margin, b.right - margin, b.bottom - margin);
    boolean recessed = inset || pressed;
    p.setStyle(Paint.Style.FILL);
    p.setColor(recessed ? 0xFF191920 : 0xFF24242E);
    if (!recessed) {
      p.setShadowLayer(5 * scale, -2 * scale, -2 * scale, 0xFF30303A);
      c.drawRoundRect(r, radius, radius, p);
      p.setShadowLayer(5 * scale, 3 * scale, 3 * scale, 0xFF13131A);
      c.drawRoundRect(r, radius, radius, p);
      p.clearShadowLayer();
      c.drawRoundRect(r, radius, radius, p);
    } else {
      c.drawRoundRect(r, radius, radius, p);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1f * scale);
      p.setShader(
          new LinearGradient(
              r.left,
              r.top,
              r.right,
              r.bottom,
              new int[] {0xFF15151D, 0xFF393442},
              null,
              Shader.TileMode.CLAMP));
      c.drawRoundRect(r, radius, radius, p);
      p.setShader(null);
      p.setStyle(Paint.Style.FILL);
    }
  }

  @Override
  public void setAlpha(int a) {
    p.setAlpha(a);
  }

  @Override
  public void setColorFilter(ColorFilter f) {
    p.setColorFilter(f);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
