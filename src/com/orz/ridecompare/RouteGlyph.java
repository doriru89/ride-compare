package com.orz.ridecompare;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class RouteGlyph extends View {
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int type;

  public RouteGlyph(Context c, int t) {
    super(c);
    type = t;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float s = Math.min(getWidth(), getHeight());
    canvas.save();
    canvas.translate((getWidth() - s) / 2, (getHeight() - s) / 2);
    canvas.scale(s / 40, s / 40);
    p.setStyle(Paint.Style.FILL);
    if (type == 1) {
      p.setColor(0xFF352B4C);
      canvas.drawRoundRect(new RectF(0, 0, 40, 40), 13, 13, p);
      p.setColor(0xFFB18AFF);
      p.setStrokeWidth(1.8f);
      p.setStyle(Paint.Style.STROKE);
      canvas.drawCircle(20, 20, 8, p);
      p.setStrokeCap(Paint.Cap.ROUND);
      canvas.drawLine(20, 8, 20, 12, p);
      canvas.drawLine(20, 28, 20, 32, p);
      canvas.drawLine(8, 20, 12, 20, p);
      canvas.drawLine(28, 20, 32, 20, p);
      p.setStyle(Paint.Style.FILL);
      canvas.drawCircle(20, 20, 3, p);
    } else {
      p.setColor(0xFF111116);
      canvas.drawRoundRect(new RectF(0, 1, 18, 39), 6, 6, p);
      p.setColor(0xFFB18AFF);
      canvas.drawRoundRect(new RectF(22, 1, 40, 39), 6, 6, p);
      p.setColor(Color.WHITE);
      p.setStrokeWidth(2);
      p.setStrokeCap(Paint.Cap.ROUND);
      canvas.drawLine(8, 20, 32, 20, p);
      canvas.drawLine(28, 16, 32, 20, p);
      canvas.drawLine(28, 24, 32, 20, p);
    }
    canvas.restore();
  }
}
