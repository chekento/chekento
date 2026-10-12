package cloud.kosch.kandroid.bridge;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    LinearLayout box; TextView status, diag; SharedPreferences sp;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); sp=getSharedPreferences("kbridge",0);
        box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(26,24,26,24); box.setBackgroundColor(Color.rgb(3,15,31));
        ScrollView sv=new ScrollView(this); sv.addView(box); setContentView(sv);

        title("K ANDROID 1.0.3 · AUDIO BRIDGE",24,0xff58e6ff);
        title("Companion Gate · Claude Mic Auto · 15-band one-row studio EQ · diagnostics",14,0xffff69c7);
        status=title("Status",13,0xffa9cbe2);
        diag=title("Diagnose",12,0xffffad4e);

        button("0 · OVERLAY TEST 15s",v->{
            Intent s=new Intent(this,OverlayTestService.class);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(s); else startService(s);
        });
        button("1 · OVERLAY ERLAUBEN",v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:"+getPackageName()))));
        button("2 · MIKROFON-FALLBACK ERLAUBEN",v->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},12));
        button("3 · COMPANION GATE ERLAUBEN",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button("4 · OUTPUT / MUSIC CAPTURE START",v->startActivity(new Intent(this,CaptureActivity.class)));
        button("MIC / AMBIENT FALLBACK START",v->{
            Intent s=new Intent(this,AudioBridgeService.class).setAction(AudioBridgeService.ACTION_MIC);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);
        });
        button("STOP",v->{
            stopService(new Intent(this,AudioBridgeService.class));
            stopService(new Intent(this,OverlayTestService.class));
        });
        button("AI NEWS LIVE",v->startActivity(new Intent(this,NewsActivity.class)));

        title("SMART VISIBILITY",16,0xffffa93a);
        check("Overlay in externen KI-Apps", "externalAiOverlay", true,
                "Claude/ChatGPT/Gemini/Grok/Perplexity/Meta AI/NotebookLM. YouTube und Browser bleiben ausgeblendet.");
        check("Claude → Mikrofon automatisch", "autoClaudeMic", true,
                "Bei Claude nutzt der Mund automatisch den Mic-Fallback; beim Verlassen zurück zu Playback.");

        title("EQ MODE",16,0xffffa93a);
        Spinner modes=new Spinner(this); String[] ms={"AUTO","SPEECH","MUSIC"};
        modes.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,ms));
        modes.setSelection(java.util.Arrays.asList(ms).indexOf(sp.getString("mode","AUTO")));
        modes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> a){}
            public void onItemSelected(android.widget.AdapterView<?> a,View v,int p,long id){
                sp.edit().putString("mode",ms[p]).apply();
            }
        }); box.addView(modes);

        seek("Mund-Höhe",(int)(sp.getFloat("mouthY",.625f)*100),52,78,"mouthY");
        seek("Mund-Breite",(int)(sp.getFloat("mouthW",.24f)*100),16,40,"mouthW");
        seek("Reaktion / Gain",(int)(sp.getFloat("gain",1.35f)*100),60,300,"gain");
        seek("Companion Safe-Zone Höhe",(int)(sp.getFloat("safeH",.44f)*100),38,55,"safeH");

        title("WICHTIG: Nach Installation einmal COMPANION GATE ERLAUBEN und in Android den Dienst „K Companion Gate“ aktivieren. Dann ist der Mund im Total Launcher nur auf dem Companion-Tab sichtbar. In YouTube bleibt er unsichtbar; Claude schaltet bei erteilter Mic-Berechtigung automatisch auf Mikrofon-Fallback.",12,0xffa9cbe2);
        refresh();
    }

    TextView title(String s,int z,int c){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);
        t.setPadding(0,8,0,8);box.addView(t);return t;
    }

    void button(String s,View.OnClickListener l){
        Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setOnClickListener(l);
        box.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }

    void check(String label,String key,boolean def,String help){
        CheckBox c=new CheckBox(this);c.setText(label);c.setTextColor(0xfff3f7ff);c.setTextSize(14);
        c.setChecked(sp.getBoolean(key,def));
        c.setOnCheckedChangeListener((v,on)->sp.edit().putBoolean(key,on).apply());
        box.addView(c);
        title(help,11,0xff8fb1c8);
    }

    void seek(String name,int val,int min,int max,String key){
        title(name,13,0xfff3f7ff);SeekBar q=new SeekBar(this);q.setMax(max-min);q.setProgress(val-min);
        q.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int p,boolean u){
                sp.edit().putFloat(key,(p+min)/100f).apply();
            }
        });box.addView(q);
    }

    void refresh(){
        String gate=sp.getBoolean("gateEnabled",false)?"ON":"AUS";
        String vis=sp.getBoolean("gateVisible",false)?"sichtbar":"versteckt";
        status.setText("Overlay: "+(Settings.canDrawOverlays(this)?"OK":"FEHLT")
                +" · Mic: "+(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED?"OK":"optional")
                +" · Gate: "+gate+" / "+vis
                +" · Safe Zone: "+Math.round(sp.getFloat("safeH",.44f)*100)+" %");

        String stage=sp.getString("diag_stage","noch kein Test");
        String detail=sp.getString("diag_detail","");
        String source=sp.getString("diag_source","IDLE");
        String reason=sp.getString("gateReason","Gate noch nicht aktiv");
        String frames=String.valueOf(sp.getLong("diag_frames",0));
        float rms=sp.getFloat("diag_rms",0f);
        diag.setText("Bridge: "+stage+(detail.length()>0?" · "+detail:"")
                +" · Source "+source+" · Gate "+reason
                +" · Frames "+frames+" · RMS "+String.format(java.util.Locale.US,"%.4f",rms));
    }

    @Override protected void onResume(){super.onResume();if(status!=null)refresh();}
}
