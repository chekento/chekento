package cloud.kosch.kandroid.bridge;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;

public class OverlayTestService extends Service {
    private WindowManager wm; private EqOverlayView view; private Handler h=new Handler(Looper.getMainLooper()); private Runnable tick; private long start;
    @Override public void onCreate(){super.onCreate();makeChannel();}
    private void makeChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("kbridge_test","K Android Overlay Test",NotificationManager.IMPORTANCE_LOW);c.setSound(null,null);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification n(){return new Notification.Builder(this,"kbridge_test").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("K Android · Overlay Test").setContentText("15 s EQ demo").build();}
    @Override public int onStartCommand(Intent in,int flags,int id){
        if(Build.VERSION.SDK_INT>=34)startForeground(91,n(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);else startForeground(91,n());
        if(!Settings.canDrawOverlays(this)){getSharedPreferences("kbridge",0).edit().putString("diag_stage","TEST ERROR").putString("diag_detail","overlay permission missing").apply();stopSelf();return START_NOT_STICKY;}
        try{
            wm=(WindowManager)getSystemService(WINDOW_SERVICE);view=new EqOverlayView(this);
            float safe=getSharedPreferences("kbridge",0).getFloat("safeH",.44f);int hh=(int)(getResources().getDisplayMetrics().heightPixels*safe);
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,hh,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.START;wm.addView(view,lp);
            getSharedPreferences("kbridge",0).edit().putString("diag_stage","TEST OVERLAY").putString("diag_detail","visible for 15 s").apply();
            start=SystemClock.uptimeMillis();tick=new Runnable(){public void run(){long ms=SystemClock.uptimeMillis()-start;if(ms>15000){stopSelf();return;}float[] b=new float[15];double ph=ms/170.0;for(int i=0;i<15;i++)b[i]=(float)(.18+.72*Math.abs(Math.sin(ph+i*.63))*Math.pow(Math.sin(Math.PI*i/14.0),.35));view.setSpectrum(b,.16f+.10f*(float)Math.abs(Math.sin(ph*.55)));h.postDelayed(this,45);}};h.post(tick);
        }catch(Throwable t){getSharedPreferences("kbridge",0).edit().putString("diag_stage","TEST ERROR").putString("diag_detail",t.getClass().getSimpleName()+": "+(t.getMessage()==null?"":t.getMessage())).apply();stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){if(tick!=null)h.removeCallbacks(tick);if(view!=null&&wm!=null)try{wm.removeView(view);}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
