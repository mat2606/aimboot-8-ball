package br.com.mirabilhar;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.List;

final class AimOverlayView extends View {
    private final Paint glow=new Paint(3), line=new Paint(3), out=new Paint(3),ballPaint=new Paint(3);
    private volatile VisionEngine.Result shot;
    AimOverlayView(Context c){super(c);setWillNotDraw(false);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void showShot(VisionEngine.Result r){shot=r;postInvalidateOnAnimation();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        VisionEngine.Result r=shot;
        if(r==null||r.confidence<.25f)return;
        c.save();c.scale((float)getWidth()/r.width,(float)getHeight()/r.height);
        boolean cushion=Prefs.enabled(getContext(),Prefs.CUSHION),auto=Prefs.enabled(getContext(),Prefs.AUTO);
        boolean three=Prefs.enabled(getContext(),Prefs.THREE),edges=Prefs.enabled(getContext(),Prefs.EDGES);
        float sw=(Prefs.thickness(getContext())==0?1.2f:Prefs.thickness(getContext())==1?2.0f:3.1f)*r.width/900f;
        List<PathMath.Segment> segments=PathMath.calculate(r,cushion,auto);
        glow.setStyle(Paint.Style.STROKE);glow.setStrokeWidth(sw*6);glow.setColor(Color.argb(50,65,210,255));
        line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(sw);line.setColor(Color.argb(242,255,255,255));
        out.setStyle(Paint.Style.STROKE);out.setStrokeWidth(sw*.72f);out.setColor(Color.argb(120,225,246,255));
        ballPaint.setStyle(Paint.Style.STROKE);ballPaint.setColor(Color.argb(165,95,218,255));ballPaint.setStrokeWidth(sw*.8f);
        for(PathMath.Segment s:segments){
            if(s.objectBall) {line.setColor(Color.argb(195,255,220,135));glow.setColor(Color.argb(47,255,193,93));}
            else {line.setColor(Color.argb(238,250,253,255));glow.setColor(Color.argb(48,70,215,255));}
            c.drawLine(s.x1,s.y1,s.x2,s.y2,glow);
            c.drawLine(s.x1,s.y1,s.x2,s.y2,line);
            if(three){
                float dx=s.x2-s.x1,dy=s.y2-s.y1;
                float len=(float)Math.hypot(dx,dy);
                if(len<1)continue;
                float nx=-dy/len*r.radius,ny=dx/len*r.radius;
                c.drawLine(s.x1+nx,s.y1+ny,s.x2+nx,s.y2+ny,out);
                c.drawLine(s.x1-nx,s.y1-ny,s.x2-nx,s.y2-ny,out);
            }
        }
        if(edges)for(VisionEngine.Ball b:r.objects) {
            if(b.score>96)c.drawCircle(b.x,b.y,r.radius,ballPaint);
        }
        ballPaint.setColor(Color.argb(220,255,255,255));
        c.drawCircle(r.cueX,r.cueY,r.radius+1.8f,ballPaint);
        c.restore();
    }
}
