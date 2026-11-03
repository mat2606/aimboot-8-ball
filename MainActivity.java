package br.com.mirabilhar;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

/** Tela de configuração em português — Android 12+. */
public final class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE=101, REQUEST_OVERLAY=102;
    private TextView status;
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){
        GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(radius));return b;
    }
    private TextView text(String str,int size,int color){
        TextView v=new TextView(this);v.setText(str);v.setTextColor(color);v.setTextSize(size);return v;
    }
    @Override public void onCreate(Bundle bundle){super.onCreate(bundle);drawScreen();}
    private void drawScreen(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(12,20,36));
        LinearLayout main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);main.setPadding(dp(23),dp(25),dp(23),dp(25));
        scroll.addView(main);
        TextView title=text("MIRA DE BILHAR",25,Color.WHITE);title.setTypeface(null,1);main.addView(title);
        TextView sub=text("Assistente visual de trajetória • Android 12",13,Color.rgb(115,202,246));sub.setPadding(0,dp(5),0,dp(22));main.addView(sub);
        status=text("",14,Color.WHITE);status.setPadding(dp(13),dp(13),dp(13),dp(13));status.setBackground(bg(Color.rgb(29,50,72),12));main.addView(status);
        TextView note=text("Usa captura autorizada da tela e traça linhas sobre a mesa. Sem calibração manual. O reconhecimento depende do visual do jogo e pode falhar em alguns temas, resoluções ou efeitos.",14,Color.LTGRAY);
        note.setPadding(0,dp(15),0,dp(18));main.addView(note);
        Button permission=button("1. Permitir sobreposição",Color.rgb(43,77,118));
        permission.setOnClickListener(v->{
            Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
            startActivityForResult(i,REQUEST_OVERLAY);
        });main.addView(permission);
        Button start=button("2. Iniciar captura e mira",Color.rgb(13,137,193));
        start.setOnClickListener(v->{
            if(!Settings.canDrawOverlays(this)){
                Toast.makeText(this,"Autorize a sobreposição antes de iniciar.",Toast.LENGTH_LONG).show();return;
            }
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(m.createScreenCaptureIntent(),REQUEST_CAPTURE);
        });main.addView(start);
        Button stop=button("Parar captura",Color.rgb(84,55,64));
        stop.setOnClickListener(v->{stopService(new Intent(this,AimService.class));refreshStatus();});main.addView(stop);
        TextView config=text("AJUSTES DA MIRA",17,Color.WHITE);config.setTypeface(null,1);config.setPadding(0,dp(22),0,dp(6));main.addView(config);
        makeToggle(main,"Alongar automaticamente",Prefs.AUTO);
        makeToggle(main,"Linhas após ricochete",Prefs.CUSHION);
        makeToggle(main,"Mostrar 3 linhas",Prefs.THREE);
        makeToggle(main,"Mostrar contorno das bolas",Prefs.EDGES);
        TextView thick=text("Espessura da linha",14,Color.LTGRAY);thick.setPadding(0,dp(12),0,dp(9));main.addView(thick);
        RadioGroup group=new RadioGroup(this);group.setOrientation(RadioGroup.HORIZONTAL);
        String[] choices={"Fina","Normal","Grossa"};
        for(int i=0;i<choices.length;i++){RadioButton rb=new RadioButton(this);rb.setText(choices[i]);rb.setTextColor(Color.WHITE);rb.setId(200+i);group.addView(rb);}
        group.check(200+Prefs.thickness(this));group.setOnCheckedChangeListener((g,id)->Prefs.thickness(this,id-200));main.addView(group);
        TextView footer=text("Uso: conceda as duas permissões, abra o 8 Ball Pool e toque no botão azul flutuante para abrir o menu. A captura pode ser encerrada também pela notificação. Não requer root e não altera o jogo.",13,Color.rgb(170,184,198));
        footer.setPadding(0,dp(25),0,dp(15));main.addView(footer);
        setContentView(scroll);refreshStatus();
    }
    private Button button(String label,int color){Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(bg(color,10));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.bottomMargin=dp(9);b.setLayoutParams(lp);return b;}
    private void makeToggle(LinearLayout parent,String label,String key){
        Switch toggle=new Switch(this);toggle.setText(label);toggle.setTextSize(14);toggle.setTextColor(Color.WHITE);
        toggle.setPadding(0,dp(7),0,dp(7));toggle.setChecked(Prefs.enabled(this,key));
        toggle.setOnCheckedChangeListener((view,checked)->Prefs.set(this,key,checked));parent.addView(toggle);
    }
    private void refreshStatus(){
        if(status==null)return;
        String permission=Settings.canDrawOverlays(this)?"Sobreposição autorizada":"Sobreposição ainda não autorizada";
        status.setText(permission+"\n"+(AimService.running?"Mira em execução":"Mira parada"));
    }
    @Override protected void onResume(){super.onResume();refreshStatus();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_CAPTURE&&resultCode==RESULT_OK&&data!=null){
            Intent service=new Intent(this,AimService.class).setAction(AimService.ACTION_START);
            service.putExtra(AimService.EXTRA_CODE,resultCode);service.putExtra(AimService.EXTRA_DATA,data);
            startForegroundService(service);
            Toast.makeText(this,"Captura iniciada. Abra o jogo.",Toast.LENGTH_SHORT).show();
            refreshStatus();
        }else if(requestCode==REQUEST_OVERLAY){refreshStatus();}
    }
}
