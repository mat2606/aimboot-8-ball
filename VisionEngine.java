package br.com.mirabilhar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Processamento local: pixels RGB -> posição da mesa, bola branca e direção da mira.
 *  Independente de bibliotecas externas e sem armazenar imagens.
 */
public final class VisionEngine {
    public static final class Ball {
        public final float x, y;
        public final float score;
        Ball(float x, float y, float score) { this.x=x; this.y=y; this.score=score; }
    }
    public static final class Result {
        public final int width, height;
        public final float left, top, right, bottom, cueX, cueY, angle, radius, confidence;
        public final List<Ball> objects;
        Result(int w,int h,float l,float t,float r,float b,float cx,float cy,float a,float rad,float conf,List<Ball> obj) {
            width=w; height=h; left=l;top=t;right=r;bottom=b;cueX=cx;cueY=cy;angle=a;radius=rad;confidence=conf;objects=obj;
        }
    }
    private int[] pixels;
    private int width,height;
    private float r;

    public Result analyze(int[] argb,int w,int h) {
        pixels=argb; width=w; height=h;
        float left=findEdge(true,true),right=findEdge(true,false),top=findEdge(false,true),bottom=findEdge(false,false);
        if(left<0||right<0||top<0||bottom<0) return null;
        // Bordas de dentro do pano, não a moldura da mesa.
        left+=w*.012f; right-=w*.008f; top+=h*.025f; bottom-=h*.033f;
        float tw=right-left, th=bottom-top;
        if(tw<w*.52||tw>w*.87||th<h*.44||th>h*.82||tw/th<1.65||tw/th>2.65) return null;
        r=Math.max(4.2f,tw*.013f);
        List<Ball> whites=new ArrayList<>();
        int ir=Math.max(3,Math.round(r*.82f));
        for(int y=(int)(top+r+2);y<bottom-r-2;y+=3) {
            for(int x=(int)(left+r+2);x<right-r-2;x+=3) {
                int p=pixel(x,y);
                if(light(p)<125) continue;
                float white=whiteDisk(x,y,ir);
                if(white>.61f) whites.add(new Ball(x,y,white));
            }
        }
        whites.sort(Comparator.comparingDouble((Ball b)->b.score).reversed());
        List<Ball> uniqueWhites=new ArrayList<>();
        for(Ball b:whites) {
            boolean near=false;
            for(Ball u:uniqueWhites) if(dist2(b.x,b.y,u.x,u.y)<r*r*.92f){near=true;break;}
            if(!near)uniqueWhites.add(b);
            if(uniqueWhites.size()>=14)break;
        }
        Ball cue=null; float best=-1, bestAngle=0;
        for(Ball b:uniqueWhites) {
            // Direção detectada pelo traço claro que sai da bola branca.
            for(int deg=0;deg<360;deg+=3) {
                float a=(float)Math.toRadians(deg);
                float trace=traceScore(b.x,b.y,a,left,top,right,bottom);
                float score=trace*.72f+b.score*.28f;
                if(trace>.19f && score>best) {best=score;cue=b;bestAngle=a;}
            }
        }
        if(cue==null||best<.31f)return null;
        // Refinamento local de 0,5 grau reduz tremulação angular.
        float refined=bestAngle, sharp=-1;
        for(int i=-8;i<=8;i++) {
            float a=bestAngle+(float)Math.toRadians(i*.5);
            float v=traceScore(cue.x,cue.y,a,left,top,right,bottom);
            if(v>sharp){sharp=v;refined=a;}
        }
        List<Ball> objects=detectObjects(left,top,right,bottom,cue.x,cue.y);
        return new Result(w,h,left,top,right,bottom,cue.x,cue.y,refined,r,Math.min(1f,sharp),objects);
    }
    private float whiteDisk(int cx,int cy,int ir) {
        int neutral=0, colored=0, samples=0;
        for(int dy=-ir;dy<=ir;dy+=2)for(int dx=-ir;dx<=ir;dx+=2){
            if(dx*dx+dy*dy>ir*ir)continue;
            int p=pixel(cx+dx,cy+dy),R=p>>16&255,G=p>>8&255,B=p&255;
            int low=Math.min(R,Math.min(G,B)), high=Math.max(R,Math.max(G,B));
            if(low>143&&high-low<53)neutral++;
            if(high-low>88)colored++;
            samples++;
        }
        return (neutral-.55f*colored)/Math.max(1,samples);
    }
    private float traceScore(float x,float y,float angle,float l,float t,float right,float b){
        double dx=Math.cos(angle),dy=Math.sin(angle);
        double nx=-dy,ny=dx;
        float total=0;int count=0;
        float max=Math.min(width*.18f,(right-l)*.24f);
        for(float s=r+5;s<max;s+=2.5f) {
            float px=x+(float)dx*s,py=y+(float)dy*s;
            if(px<l+r||px>right-r||py<t+r||py>b-r)break;
            float base=(light(pixel(px+(float)nx*5,py+(float)ny*5))+
                       light(pixel(px-(float)nx*5,py-(float)ny*5)))*.5f;
            float center=Math.max(light(pixel(px,py)),
                         Math.max(light(pixel(px+(float)nx,py+(float)ny)),light(pixel(px-(float)nx,py-(float)ny))));
            total+=Math.max(0,Math.min(1,(center-base-17f)/64f));count++;
        }
        if(count<9)return 0;
        // Descartar traços curtos perto de uma bola alvo ou da borda.
        return (total/count)*Math.min(1f,count/22f);
    }
    private List<Ball> detectObjects(float l,float t,float right,float b,float cueX,float cueY) {
        ArrayList<Ball> candidates=new ArrayList<>();
        int rad=Math.max(3,Math.round(r));
        for(int y=(int)(t+r+3);y<b-r-3;y+=4)for(int x=(int)(l+r+3);x<right-r-3;x+=4){
            if(dist2(x,y,cueX,cueY)<r*r*4)continue;
            float coreR=0,coreG=0,coreB=0,ringR=0,ringG=0,ringB=0;
            int in=0,out=0;
            for(int dy=-rad*2;dy<=rad*2;dy+=Math.max(2,rad/2))
                for(int dx=-rad*2;dx<=rad*2;dx+=Math.max(2,rad/2)){
                    float d2=dx*dx+dy*dy;
                    int p=pixel(x+dx,y+dy);
                    if(d2<rad*rad*.5f){coreR+=p>>16&255;coreG+=p>>8&255;coreB+=p&255;in++;}
                    else if(d2>rad*rad*2.4f&&d2<rad*rad*3.4f){ringR+=p>>16&255;ringG+=p>>8&255;ringB+=p&255;out++;}
                }
            if(in==0||out==0)continue;
            float dr=coreR/in-ringR/out,dg=coreG/in-ringG/out,db=coreB/in-ringB/out;
            float contrast=(float)Math.sqrt(dr*dr+dg*dg+db*db);
            // Rejeita gradientes fracos do pano/decorativos.
            if(contrast>68)candidates.add(new Ball(x,y,contrast));
        }
        candidates.sort(Comparator.comparingDouble((Ball z)->z.score).reversed());
        ArrayList<Ball> distinct=new ArrayList<>();
        for(Ball b0:candidates){
            boolean close=false;
            for(Ball q:distinct)if(dist2(b0.x,b0.y,q.x,q.y)<r*r*3.8f){close=true;break;}
            if(!close)distinct.add(b0);
            if(distinct.size()>=23)break;
        }
        return distinct;
    }
    private float findEdge(boolean vertical,boolean first){
        int rangeStart,rangeEnd;
        float[] cross = vertical? new float[]{.38f,.53f,.68f,.8f}:new float[]{.27f,.37f,.63f,.73f};
        if(vertical){rangeStart=(int)(width*(first?.12f:.73f));rangeEnd=(int)(width*(first?.28f:.93f));}
        else {rangeStart=(int)(height*(first?.185f:.80f));rangeEnd=(int)(height*(first?.35f:.985f));}
        float best=0;int edge=-1;
        float[] values=new float[4];
        for(int k=rangeStart;k<rangeEnd;k++){
            for(int i=0;i<4;i++) {
                int fixed=(int)((vertical?height:width)*cross[i]);
                int c1=vertical?average(k-6,fixed):average(fixed,k-6);
                int c2=vertical?average(k+6,fixed):average(fixed,k+6);
                float dr=(c1>>16&255)-(c2>>16&255), dg=(c1>>8&255)-(c2>>8&255),db=(c1&255)-(c2&255);
                values[i]=(float)Math.sqrt(dr*dr+dg*dg+db*db);
            }
            // Mediana de quatro medidas: elimina bolas e linhas ocasionais.
            java.util.Arrays.sort(values);
            float score=(values[1]+values[2])*.5f;
            if(score>best){best=score;edge=k;}
        }
        return best>43?edge:-1;
    }
    private int average(int x,int y){
        int sr=0,sg=0,sb=0;
        for(int dy=-2;dy<=2;dy+=2)for(int dx=-2;dx<=2;dx+=2){
            int p=pixel(x+dx,y+dy);sr+=p>>16&255;sg+=p>>8&255;sb+=p&255;
        }
        return ((sr/9)<<16)|((sg/9)<<8)|(sb/9);
    }
    private int pixel(float fx,float fy){
        int x=(int)(fx+.5f),y=(int)(fy+.5f);
        x=Math.max(0,Math.min(width-1,x));y=Math.max(0,Math.min(height-1,y));
        return pixels[y*width+x];
    }
    private float light(int p){return (p>>16&255)*.2126f+(p>>8&255)*.7152f+(p&255)*.0722f;}
    private static float dist2(float x,float y,float x2,float y2){float a=x-x2,b=y-y2;return a*a+b*b;}
}
