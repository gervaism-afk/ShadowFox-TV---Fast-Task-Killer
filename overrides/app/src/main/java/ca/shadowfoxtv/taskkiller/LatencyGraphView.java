package ca.shadowfoxtv.taskkiller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class LatencyGraphView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final List<Long> samples=new ArrayList<>();
    public LatencyGraphView(Context c, AttributeSet a){super(c,a);}
    public void addSample(long ms){ if(samples.size()>=18)samples.remove(0); samples.add(Math.max(0,ms)); invalidate(); }
    @Override protected void onDraw(Canvas c){ super.onDraw(c); if(samples.size()<2)return; float w=getWidth(),h=getHeight(); long max=1; for(long s:samples)max=Math.max(max,s); Path path=new Path(); for(int i=0;i<samples.size();i++){float x=i*w/17f; float y=h-8-(samples.get(i)/(float)max)*(h-16); if(i==0)path.moveTo(x,y); else path.lineTo(x,y);} p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4f);p.setColor(Color.rgb(72,255,145));p.setShadowLayer(8,0,0,Color.rgb(72,255,145));c.drawPath(path,p);p.clearShadowLayer(); }
}