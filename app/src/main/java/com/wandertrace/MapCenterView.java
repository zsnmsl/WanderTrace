package com.wandertrace;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Non-interactive overlay: the exact selection is the center of the crosshair. */
public final class MapCenterView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public MapCenterView(Context context,AttributeSet attrs) {super(context,attrs);}
    @Override protected void onDraw(Canvas canvas) {
        float d=getResources().getDisplayMetrics().density,x=getWidth()/2f,y=getHeight()/2f;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xEEFFFFFF);paint.setStrokeWidth(6*d);
        drawTarget(canvas,x,y,d);
        paint.setColor(0xFF087F6D);paint.setStrokeWidth(2.5f*d);
        drawTarget(canvas,x,y,d);
        paint.setStyle(Paint.Style.FILL);canvas.drawCircle(x,y,2*d,paint);
    }
    private void drawTarget(Canvas c,float x,float y,float d) {
        c.drawCircle(x,y,9*d,paint);
        c.drawLine(x-17*d,y,x-5*d,y,paint);c.drawLine(x+5*d,y,x+17*d,y,paint);
        c.drawLine(x,y-17*d,x,y-5*d,paint);c.drawLine(x,y+5*d,x,y+17*d,paint);
    }
}
