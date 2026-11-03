import br.com.mirabilhar.VisionEngine;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
public class AnalisarReferencias {
 public static void main(String[]args) throws Exception {
  for(String f:args){BufferedImage b=ImageIO.read(new File(f));int w=b.getWidth(),h=b.getHeight();int[] rgb=b.getRGB(0,0,w,h,null,0,w);
   long start=System.nanoTime();VisionEngine.Result r=new VisionEngine().analyze(rgb,w,h);
   System.out.println(f+" -> "+(r==null?"sem detecção":String.format("mesa=(%.0f,%.0f) a (%.0f,%.0f), branca=(%.0f,%.0f), ângulo=%.1f°, confiança=%.2f, objetos=%d",r.left,r.top,r.right,r.bottom,r.cueX,r.cueY,Math.toDegrees(r.angle),r.confidence,r.objects.size()))+" ["+(System.nanoTime()-start)/1e6+"ms]"); if(r!=null) for(VisionEngine.Ball ball:r.objects) System.out.printf(" (%.0f,%.0f:%.0f)",ball.x,ball.y,ball.score);System.out.println();
  }
 }
}
