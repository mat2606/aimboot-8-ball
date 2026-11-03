import br.com.mirabilhar.VisionEngine;
import br.com.mirabilhar.PathMath;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.List;
public final class TesteDeteccao {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Informe referências azul e cinza");
        float[][] expected={{523,158,162},{355,295,355}};
        for(int i=0;i<2;i++){
            BufferedImage image=ImageIO.read(new File(args[i]));
            int w=image.getWidth(),h=image.getHeight();
            int[] pixels=image.getRGB(0,0,w,h,null,0,w);
            VisionEngine.Result r=new VisionEngine().analyze(pixels,w,h);
            if(r==null)throw new AssertionError("Nenhuma detecção na referência "+i);
            float deviation=(float)Math.hypot(expected[i][0]-r.cueX,expected[i][1]-r.cueY);
            float angle=(float)Math.toDegrees(r.angle);
            float error=Math.abs(((angle-expected[i][2]+540)%360)-180);
            if(deviation>12)throw new AssertionError("Desvio de bola branca: "+deviation);
            if(error>9)throw new AssertionError("Desvio de mira: "+error);
            List<PathMath.Segment> path=PathMath.calculate(r,true,true);
            if(path.isEmpty())throw new AssertionError("Sem trajetória calculada");
            System.out.printf("OK referência %d | branca (%.0f, %.0f) | mira %.1f° | confiança %.2f | %d segmentos%n",i+1,r.cueX,r.cueY,angle,r.confidence,path.size());
        }
    }
}
