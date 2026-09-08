package ca.shadowfoxtv.taskkiller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class SpeedGaugeView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float value = 0f;
    public SpeedGaugeView(Context c, AttributeSet a) { super(c,a); setLayerType(LAYER_TYPE_SOFTWARE, null); }
    public void setMbps(float mbps) { value = Math.max(0f, Math.min(250f, mbps)); invalidate(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); float w=getWidth(), h=getHeight(); float cx=w/2f, cy=h*.82f; float r=Math.min(w*.40f,h*.72f);
        RectF arc=new RectF(cx-r,cy-r,cx+r,cy+r);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(22f); p.setColor(Color.rgb(25,55,65)); c.drawArc(arc,200,140,false,p);
        p.setShadowLayer(18,0,0,Color.rgb(0,229,255)); p.setColor(Color.rgb(0,229,255)); c.drawArc(arc,200,70,false,p);
        p.setShadowLayer(18,0,0,Color.rgb(255,109,0)); p.setColor(Color.rgb(255,109,0)); c.drawArc(arc,270,70,false,p); p.clearShadowLayer();
        float angle=(float)Math.toRadians(200f + (value/250f)*140f); float nx=cx+(float)Math.cos(angle)*r*.82f, ny=cy+(float)Math.sin(angle)*r*.82f;
        p.setStrokeWidth(8f); p.setColor(Color.rgb(0,229,255)); p.setShadowLayer(14,0,0,Color.rgb(0,229,255)); c.drawLine(cx,cy,nx,ny,p); p.clearShadowLayer(); p.setStyle(Paint.Style.FILL); c.drawCircle(cx,cy,14f,p);
    }
}