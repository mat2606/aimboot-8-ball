package br.com.mirabilhar;
import java.util.ArrayList;
import java.util.List;

/** Modelo geométrico 2-D, sem compensação de efeito, força ou atrito. */
public final class PathMath {
    public static final class Segment {
        public final float x1,y1,x2,y2;
        public final boolean objectBall;
        Segment(float a,float b,float c,float d,boolean obj){x1=a;y1=b;x2=c;y2=d;objectBall=obj;}
    }
    public static List<Segment> calculate(VisionEngine.Result r,boolean cushion,boolean longGuide) {
        ArrayList<Segment> result=new ArrayList<>();
        if(r==null)return result;
        float margin=r.radius+1.5f;
        float l=r.left+margin,t=r.top+margin,rr=r.right-margin,b=r.bottom-margin;
        float x=r.cueX,y=r.cueY,dx=(float)Math.cos(r.angle),dy=(float)Math.sin(r.angle);
        int bounces=(cushion&&longGuide)?3:0;
        for(int k=0;k<=bounces;k++) {
            float tx=Float.POSITIVE_INFINITY,ty=Float.POSITIVE_INFINITY;
            if(dx>1e-5)tx=(rr-x)/dx;
            else if(dx<-1e-5)tx=(l-x)/dx;
            if(dy>1e-5)ty=(b-y)/dy;
            else if(dy<-1e-5)ty=(t-y)/dy;
            float wall=Math.min(tx,ty);
            if(!Float.isFinite(wall)||wall<=0)break;
            VisionEngine.Ball collision=null;float collisionAt=Float.POSITIVE_INFINITY;
            if(k==0)for(VisionEngine.Ball ball:r.objects) {
                float ox=ball.x-x,oy=ball.y-y;
                float projected=ox*dx+oy*dy;
                if(projected<r.radius*1.7||projected>=wall)continue;
                float cross2=ox*ox+oy*oy-projected*projected;
                float radius=r.radius*2.05f;
                if(cross2>radius*radius)continue;
                float distance=projected-(float)Math.sqrt(Math.max(0,radius*radius-cross2));
                if(distance>r.radius&&distance<collisionAt){collisionAt=distance;collision=ball;}
            }
            float end=Math.min(wall,collisionAt);
            float ex=x+dx*end,ey=y+dy*end;
            result.add(new Segment(x,y,ex,ey,false));
            if(collision!=null&&collisionAt<wall) {
                // Bola alvo continua na normal de impacto. Apenas aproximação geométrica.
                float vx=collision.x-ex,vy=collision.y-ey;
                float len=(float)Math.hypot(vx,vy);
                if(len>1){vx/=len;vy/=len;
                    float a=Float.POSITIVE_INFINITY,z=Float.POSITIVE_INFINITY;
                    if(vx>1e-4)a=(rr-collision.x)/vx;else if(vx<-1e-4)a=(l-collision.x)/vx;
                    if(vy>1e-4)z=(b-collision.y)/vy;else if(vy<-1e-4)z=(t-collision.y)/vy;
                    float reach=Math.min(a,z);
                    if(Float.isFinite(reach)&&reach>0)result.add(new Segment(collision.x,collision.y,collision.x+vx*reach,collision.y+vy*reach,true));
                }
                break;
            }
            if(k==bounces||pocket(ex,ey,l,t,rr,b,r.radius))break;
            if(Math.abs(wall-tx)<=1.5)dx=-dx;
            if(Math.abs(wall-ty)<=1.5)dy=-dy;
            x=ex+dx*.4f;y=ey+dy*.4f;
        }
        return result;
    }
    private static boolean pocket(float x,float y,float l,float t,float r,float b,float radius){
        float near=radius*2.8f;
        boolean side=Math.abs(x-l)<radius*1.5||Math.abs(x-r)<radius*1.5;
        boolean topBottom=Math.abs(y-t)<radius*1.5||Math.abs(y-b)<radius*1.5;
        if(side&&topBottom)return true;
        return topBottom&&Math.abs(x-(l+r)*.5f)<near;
    }
}
