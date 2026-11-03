package br.com.mirabilhar;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Captura autorizada pelo sistema. Nada é transmitido para servidores. */
public final class AimService extends Service {
    static final String ACTION_START="br.com.mirabilhar.START";
    static final String ACTION_STOP="br.com.mirabilhar.STOP";
    static final String EXTRA_CODE="capture_result";
    static final String EXTRA_DATA="capture_data";
    static volatile boolean running=false;
    private WindowManager windowManager;
    private DisplayManager displayManager;
    private MediaProjection projection;
    private MediaProjection.Callback projectionCallback;
    private DisplayManager.DisplayListener displayListener;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread worker;
    private Handler workHandler;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final VisionEngine engine=new VisionEngine();
    private AimOverlayView aimView;
    private TextView bubble;
    private LinearLayout panel;
    private WindowManager.LayoutParams aimParams,bubbleParams,panelParams;
    private Bitmap rawBitmap,smallBitmap;
    private Canvas smallCanvas;
    private Paint resizePaint;
    private int[] pixels;
    private int screenW,screenH,smallW,smallH;
    private long lastFrame=0;
    private volatile boolean closing=false;
    private VisionEngine.Result previous;
    private int misses;

    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();
        windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
        displayManager=(DisplayManager)getSystemService(DISPLAY_SERVICE);
        NotificationChannel channel=new NotificationChannel("mira","Captura da mesa",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        worker=new HandlerThread("analise-bilhar",Process.THREAD_PRIORITY_DISPLAY);
        worker.start();workHandler=new Handler(worker.getLooper());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if(ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(!ACTION_START.equals(intent.getAction())||running)return START_NOT_STICKY;
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        int code=intent.getIntExtra(EXTRA_CODE,Activity.RESULT_CANCELED);
        Intent data=intent.getParcelableExtra(EXTRA_DATA);
        if(code!=Activity.RESULT_OK||data==null){stopSelf();return START_NOT_STICKY;}
        startForeground(37,notification());
        try {
            MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=manager.getMediaProjection(code,data);
            if(projection==null)throw new IllegalStateException("Permissão de captura não autorizada");
            projectionCallback=new MediaProjection.Callback(){@Override public void onStop(){ui.post(()->stopSelf());}};
            projection.registerCallback(projectionCallback,ui);
            createViews();
            setupCapture();
            running=true;
        }catch(Exception e){android.util.Log.e("MiraBilhar","Falha ao iniciar",e);stopSelf();}
        return START_NOT_STICKY;
    }
    private Notification notification(){
        Intent stop=new Intent(this,AimService.class).setAction(ACTION_STOP);
        PendingIntent pending=PendingIntent.getService(this,3,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"mira")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Mira de Bilhar ativa")
            .setContentText("Captura local autorizada • toque para parar")
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Encerrar",pending).build())
            .build();
    }
    @SuppressWarnings("deprecation") private android.graphics.Point size(){
        android.graphics.Point p=new android.graphics.Point();windowManager.getDefaultDisplay().getRealSize(p);return p;
    }
    private void setupCapture(){
        android.graphics.Point p=size();screenW=p.x;screenH=p.y;
        allocateProcessing();
        imageReader=ImageReader.newInstance(screenW,screenH,PixelFormat.RGBA_8888,2);
        imageReader.setOnImageAvailableListener(reader->onFrame(reader),workHandler);
        virtualDisplay=projection.createVirtualDisplay("MiraBilharScreen",screenW,screenH,getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,imageReader.getSurface(),null,workHandler);
        displayListener=new DisplayManager.DisplayListener(){
            @Override public void onDisplayAdded(int id){}
            @Override public void onDisplayRemoved(int id){}
            @Override public void onDisplayChanged(int id){
                workHandler.post(()->resizeCapture());
            }
        };
        displayManager.registerDisplayListener(displayListener,ui);
    }
    private void allocateProcessing(){
        smallW=Math.min(800,screenW);smallH=Math.max(1,Math.round((float)screenH*smallW/screenW));
        smallBitmap=Bitmap.createBitmap(smallW,smallH,Bitmap.Config.ARGB_8888);
        smallCanvas=new Canvas(smallBitmap);
        resizePaint=new Paint(Paint.FILTER_BITMAP_FLAG);
        pixels=new int[smallW*smallH];
        previous=null;misses=0;
    }
    private void resizeCapture(){
        if(closing||virtualDisplay==null)return;
        android.graphics.Point p=size();
        if(p.x==screenW&&p.y==screenH)return;
        screenW=p.x;screenH=p.y;
        if(imageReader!=null){imageReader.setOnImageAvailableListener(null,null);imageReader.close();}
        if(rawBitmap!=null){rawBitmap.recycle();rawBitmap=null;}
        if(smallBitmap!=null)smallBitmap.recycle();
        allocateProcessing();
        imageReader=ImageReader.newInstance(screenW,screenH,PixelFormat.RGBA_8888,2);
        imageReader.setOnImageAvailableListener(reader->onFrame(reader),workHandler);
        virtualDisplay.resize(screenW,screenH,getResources().getDisplayMetrics().densityDpi);
        virtualDisplay.setSurface(imageReader.getSurface());
        ui.post(()->{
            if(aimView!=null){aimParams.width=screenW;aimParams.height=screenH;windowManager.updateViewLayout(aimView,aimParams);aimView.showShot(null);}
        });
    }
    private void onFrame(ImageReader reader){
        Image image=null;
        try {
            image=reader.acquireLatestImage();
            if(image==null||closing)return;
            long now=SystemClock.uptimeMillis();
            // 10-12 análises/s e descarte imediato de frames antigos.
            if(now-lastFrame<88)return;
            lastFrame=now;
            Image.Plane plane=image.getPlanes()[0];
            int pixelStride=plane.getPixelStride();if(pixelStride!=4)return;
            int widthWithPadding=plane.getRowStride()/pixelStride;
            if(rawBitmap==null||rawBitmap.getWidth()!=widthWithPadding||rawBitmap.getHeight()!=image.getHeight()){
                if(rawBitmap!=null)rawBitmap.recycle();
                rawBitmap=Bitmap.createBitmap(widthWithPadding,image.getHeight(),Bitmap.Config.ARGB_8888);
            }
            java.nio.ByteBuffer buffer=plane.getBuffer();buffer.rewind();rawBitmap.copyPixelsFromBuffer(buffer);
            smallCanvas.drawBitmap(rawBitmap,new Rect(0,0,image.getWidth(),image.getHeight()),new Rect(0,0,smallW,smallH),resizePaint);
            smallBitmap.getPixels(pixels,0,smallW,0,0,smallW,smallH);
            VisionEngine.Result found=engine.analyze(pixels,smallW,smallH);
            VisionEngine.Result next=smooth(found);
            AimOverlayView view=aimView; if(view!=null)view.post(()->view.showShot(next));
        }catch(Exception e){android.util.Log.w("MiraBilhar","Frame ignorado",e);}
        finally{if(image!=null)image.close();}
    }
    private VisionEngine.Result smooth(VisionEngine.Result found){
        if(found==null){
            misses++;if(misses>3){previous=null;return null;}return previous;
        }
        misses=0;
        if(previous!=null){
            float motion=(float)Math.hypot(found.cueX-previous.cueX,found.cueY-previous.cueY);
            float da=(float)Math.atan2(Math.sin(found.angle-previous.angle),Math.cos(found.angle-previous.angle));
            if(motion<found.radius*4f&&Math.abs(da)<.35f){
                float alpha=.43f;
                float a=previous.angle+da*alpha;
                found=new VisionEngine.Result(found.width,found.height,
                   mix(previous.left,found.left,.3f),mix(previous.top,found.top,.3f),
                   mix(previous.right,found.right,.3f),mix(previous.bottom,found.bottom,.3f),
                   mix(previous.cueX,found.cueX,alpha),mix(previous.cueY,found.cueY,alpha),a,
                   found.radius,found.confidence,found.objects);
            }
        }
        previous=found;return found;
    }
    private static float mix(float a,float b,float alpha){return a+(b-a)*alpha;}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void createViews(){
        aimView=new AimOverlayView(this);
        int type=WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int passive=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|
                WindowManager.LayoutParams.FLAG_SECURE;
        android.graphics.Point s=size();
        aimParams=new WindowManager.LayoutParams(s.x,s.y,type,passive,PixelFormat.TRANSLUCENT);
        aimParams.gravity=Gravity.TOP|Gravity.LEFT;
        // Android 12 só permite toques por overlays translúcidos (opacidade <= 0.8).
        aimParams.alpha=.65f;
        windowManager.addView(aimView,aimParams);
        bubble=new TextView(this);
        bubble.setText("◉");bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(Color.WHITE);bubble.setTextSize(27);bubble.setElevation(dp(5));
        bubble.setBackground(background(Color.rgb(19,143,205),18));
        bubbleParams=new WindowManager.LayoutParams(dp(49),dp(49),type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity=Gravity.TOP|Gravity.LEFT;
        bubbleParams.x=dp(18);bubbleParams.y=dp(130);
        bubble.setOnTouchListener(new View.OnTouchListener(){
            float downX,downY;int startX,startY;boolean moved;
            @Override public boolean onTouch(View v,android.view.MotionEvent ev){
                switch(ev.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        downX=ev.getRawX();downY=ev.getRawY();startX=bubbleParams.x;startY=bubbleParams.y;moved=false;return true;
                    case MotionEvent.ACTION_MOVE:
                        int x=startX+Math.round(ev.getRawX()-downX), y=startY+Math.round(ev.getRawY()-downY);
                        if(Math.abs(x-startX)>dp(5)||Math.abs(y-startY)>dp(5))moved=true;
                        bubbleParams.x=Math.max(0,Math.min(screenW-dp(49),x));
                        bubbleParams.y=Math.max(0,Math.min(screenH-dp(49),y));
                        windowManager.updateViewLayout(bubble,bubbleParams);
                        if(panel!=null)updatePanelPosition();return true;
                    case MotionEvent.ACTION_UP:
                        if(!moved)togglePanel();return true;
                }return false;
            }
        });
        windowManager.addView(bubble,bubbleParams);
    }
    private android.graphics.drawable.GradientDrawable background(int color,int radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private void togglePanel(){
        if(panel!=null){windowManager.removeView(panel);panel=null;return;}
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(15),dp(13),dp(15),dp(13));
        panel.setBackground(background(Color.rgb(20,31,49),16));panel.setElevation(dp(8));
        TextView title=new TextView(this);title.setText("MIRA DE BILHAR");title.setTextColor(Color.WHITE);title.setTextSize(16);title.setTypeface(null,1);
        panel.addView(title);
        addToggle("Alongar linha automaticamente",Prefs.AUTO);
        addToggle("Ricochete na tabela",Prefs.CUSHION);
        addToggle("Mostrar 3 linhas",Prefs.THREE);
        addToggle("Contorno das bolas",Prefs.EDGES);
        TextView thickness=new TextView(this);thickness.setText("Espessura das linhas");thickness.setTextColor(Color.LTGRAY);
        thickness.setPadding(0,dp(8),0,0);panel.addView(thickness);
        RadioGroup options=new RadioGroup(this);options.setOrientation(RadioGroup.HORIZONTAL);
        String[] labels={"Fina","Média","Grossa"};
        for(int i=0;i<3;i++){
            RadioButton rb=new RadioButton(this);rb.setText(labels[i]);rb.setTextSize(12);rb.setTextColor(Color.WHITE);
            rb.setId(100+i);options.addView(rb);
        }
        options.check(100+Prefs.thickness(this));
        options.setOnCheckedChangeListener((group,id)->Prefs.thickness(this,id-100));
        panel.addView(options);
        Button stop=new Button(this);stop.setAllCaps(false);stop.setText("Encerrar captura");
        stop.setOnClickListener(v->stopSelf());panel.addView(stop);
        panelParams=new WindowManager.LayoutParams(dp(290),WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity=Gravity.TOP|Gravity.LEFT;updatePanelPosition();windowManager.addView(panel,panelParams);
    }
    private void addToggle(String label,String key){
        Switch toggle=new Switch(this);toggle.setText(label);toggle.setTextColor(Color.WHITE);toggle.setTextSize(13);
        toggle.setPadding(0,dp(7),0,dp(5));toggle.setChecked(Prefs.enabled(this,key));
        toggle.setOnCheckedChangeListener((b,v)->Prefs.set(this,key,v));panel.addView(toggle);
    }
    private void updatePanelPosition(){
        if(panelParams==null)return;
        panelParams.x=Math.max(0,Math.min(Math.max(0,screenW-dp(290)),bubbleParams.x+dp(55)));
        panelParams.y=Math.max(0,Math.min(Math.max(0,screenH-dp(290)),bubbleParams.y));
        if(panel!=null&&panel.getParent()!=null)windowManager.updateViewLayout(panel,panelParams);
    }
    @Override public void onDestroy(){
        closing=true;running=false;
        if(displayManager!=null&&displayListener!=null)try{displayManager.unregisterDisplayListener(displayListener);}catch(Exception ignored){}
        ImageReader readerToClose=imageReader; imageReader=null;
        VirtualDisplay displayToClose=virtualDisplay; virtualDisplay=null;
        if(workHandler!=null)workHandler.post(()->{
            if(readerToClose!=null){readerToClose.setOnImageAvailableListener(null,null);readerToClose.close();}
            if(displayToClose!=null)displayToClose.release();
            if(rawBitmap!=null){rawBitmap.recycle();rawBitmap=null;}
            if(smallBitmap!=null){smallBitmap.recycle();smallBitmap=null;}
        });
        if(projection!=null){
            try{projection.unregisterCallback(projectionCallback);}catch(Exception ignored){}
            try{projection.stop();}catch(Exception ignored){}projection=null;
        }
        if(panel!=null)try{windowManager.removeView(panel);}catch(Exception ignored){}
        if(bubble!=null)try{windowManager.removeView(bubble);}catch(Exception ignored){}
        if(aimView!=null)try{windowManager.removeView(aimView);}catch(Exception ignored){}
        panel=null;bubble=null;aimView=null;
        if(worker!=null)worker.quitSafely();
        super.onDestroy();
    }
}
