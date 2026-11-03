package br.com.mirabilhar;
import android.content.Context;
import android.content.SharedPreferences;
final class Prefs {
    static final String AUTO="auto",CUSHION="cushion",THREE="three",EDGES="edges",THICK="thick";
    private Prefs(){}
    static SharedPreferences get(Context context){return context.getSharedPreferences("ajustes",Context.MODE_PRIVATE);}
    static boolean enabled(Context c,String key){return get(c).getBoolean(key,!THREE.equals(key));}
    static void set(Context c,String key,boolean v){get(c).edit().putBoolean(key,v).apply();}
    static int thickness(Context c){return get(c).getInt(THICK,0);}
    static void thickness(Context c,int v){get(c).edit().putInt(THICK,v).apply();}
}
