package cloud.kosch.kandroid.bridge;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;

/** System playback / music -> realtime 15-band K Android mouth. */
public class AudioBridgeService extends Service {
    public static final String EXTRA_CODE="code", EXTRA_DATA="data";
    public static final String ACTION_STOP="cloud.kosch.kandroid.bridge.STOP";
    public static final String ACTION_MIC="cloud.kosch.kandroid.bridge.MIC";
    private AudioRecord rec; private MediaProjection projection; private Thread worker; private volatile boolean running; private WindowManager wm; private EqOverlayView view;
    private SharedPreferences sp;
    @Override public void onCreate(){ super.onCreate(); sp=getSharedPreferences("kbridge",0); makeChannel(); diag("SERVICE","created"); }
    private void diag(String stage,String detail){sp.edit().putString("diag_stage",stage).putString("diag_detail",detail==null?"":detail).putLong("diag_ms",System.currentTimeMillis()).apply();}
    private void sampleDiag(long frames,float rms){sp.edit().putLong("diag_frames",frames).putFloat("diag_rms",rms).apply();}
    private void makeChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("kbridge","K Android Audio Bridge",NotificationManager.IMPORTANCE_LOW);c.setDescription("Audio-reactive one-row EQ mouth for K Android Companion");c.setSound(null,null);c.enableVibration(false);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification notification(){PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,AudioBridgeService.class).setAction(ACTION_STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"kbridge").setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).setContentTitle("K Android · Audio EQ live").setContentText("Systemaudio / Musik → Companion-Mund").setContentIntent(open).addAction(new Notification.Action.Builder(null,"STOP",stop).build()).build();}
    @Override public int onStartCommand(Intent in,int flags,int id){
        if(in!=null&&ACTION_STOP.equals(in.getAction())){diag("STOP","requested");stopSelf();return START_NOT_STICKY;}
        boolean micMode=in!=null&&ACTION_MIC.equals(in.getAction());
        int type=micMode?ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE:ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        try{if(Build.VERSION.SDK_INT>=29)startForeground(90,notification(),type);else startForeground(90,notification());}
        catch(Throwable t){diag("FGS ERROR",t.getClass().getSimpleName()+": "+safeMsg(t));stopSelf();return START_NOT_STICKY;}
        showOverlay();
        if(micMode)startMicFallback();
        else if(in!=null&&in.hasExtra(EXTRA_DATA)){
            int code=in.getIntExtra(EXTRA_CODE,Activity.RESULT_CANCELED);
            Intent data;if(Build.VERSION.SDK_INT>=33)data=in.getParcelableExtra(EXTRA_DATA,Intent.class);else data=(Intent)in.getParcelableExtra(EXTRA_DATA);
            startPlayback(code,data);
        } else diag("WAIT","no capture token");
        return START_NOT_STICKY;
    }
    private static String safeMsg(Throwable t){String m=t.getMessage();return m==null?"":m.replace('\n',' ');}
    private void showOverlay(){
        if(!Settings.canDrawOverlays(this)){diag("OVERLAY","permission missing");return;}
        if(view!=null)return;
        try{
            wm=(WindowManager)getSystemService(WINDOW_SERVICE);view=new EqOverlayView(this);
            float safe=getSharedPreferences("kbridge",0).getFloat("safeH",.44f);
            int hh=(int)(getResources().getDisplayMetrics().heightPixels*safe);
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,hh,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
            lp.gravity=Gravity.TOP|Gravity.START;wm.addView(view,lp);diag("OVERLAY","visible · "+hh+"px");
        }catch(Throwable t){view=null;diag("OVERLAY ERROR",t.getClass().getSimpleName()+": "+safeMsg(t));}
    }
    private void startPlayback(int code,Intent data){
        stopRecord(); if(data==null){diag("CAPTURE ERROR","empty token");return;}
        try{
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=m.getMediaProjection(code,data);
            if(projection==null){diag("CAPTURE ERROR","MediaProjection null");return;}
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){diag("CAPTURE","projection stopped");stopRecord();}},new Handler(Looper.getMainLooper()));
            AudioPlaybackCaptureConfiguration cfg=new AudioPlaybackCaptureConfiguration.Builder(projection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
            diag("CAPTURE","projection acquired"); startRecord(cfg);
        }catch(Throwable t){diag("CAPTURE ERROR",t.getClass().getSimpleName()+": "+safeMsg(t));}
    }
    private void startRecord(AudioPlaybackCaptureConfiguration cfg){
        int[] rates={48000,44100}; Throwable last=null;
        for(int sr:rates){
            try{
                AudioFormat f=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
                int raw=AudioRecord.getMinBufferSize(sr,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT); int min=Math.max(raw>0?raw:sr/5,sr/5);
                AudioRecord r=new AudioRecord.Builder().setAudioFormat(f).setBufferSizeInBytes(min*2).setAudioPlaybackCaptureConfig(cfg).build();
                if(r.getState()==AudioRecord.STATE_INITIALIZED){rec=r;begin(sr);return;} r.release();
            }catch(Throwable t){last=t;}
        }
        diag("AUDIO ERROR",last==null?"AudioRecord not initialized":last.getClass().getSimpleName()+": "+safeMsg(last));
    }
    private void startMicFallback(){
        stopRecord();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){diag("MIC","permission missing");return;}
        final int sr=48000;int min=Math.max(AudioRecord.getMinBufferSize(sr,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),sr/5);
        try{rec=new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED,sr,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,min*2);if(rec.getState()!=AudioRecord.STATE_INITIALIZED){rec.release();rec=null;}}catch(Throwable ignored){rec=null;}
        if(rec==null){try{rec=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,sr,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,min*2);}catch(Throwable t){diag("MIC ERROR",t.getClass().getSimpleName()+": "+safeMsg(t));return;}}
        begin(sr);
    }
    private void begin(final int sr){
        if(rec==null||rec.getState()!=AudioRecord.STATE_INITIALIZED){diag("AUDIO ERROR","recorder not initialized");return;}
        try{running=true;rec.startRecording();}catch(Throwable t){diag("AUDIO ERROR","start: "+t.getClass().getSimpleName()+": "+safeMsg(t));running=false;return;}
        diag("AUDIO","recording "+sr+" Hz");
        worker=new Thread(()->{
            short[] buf=new short[Math.max(882,sr/50)];float[] smooth=new float[15],noise=new float[15];float peak=.035f;final double[] hz={120,180,260,380,550,800,1150,1650,2350,3300,4500,6000,7800,9800,12000};
            long frames=0,lastDiag=0;
            while(running){
                int n; try{n=rec.read(buf,0,buf.length);}catch(Throwable t){diag("READ ERROR",t.getClass().getSimpleName()+": "+safeMsg(t));break;}
                if(n<=0)continue;frames++;
                double ss=0;for(int i=0;i<n;i++){double v=buf[i]/32768.0;ss+=v*v;}
                float rms=(float)Math.sqrt(ss/Math.max(1,n));peak=Math.max(rms,Math.max(.025f,peak*.995f));float agc=Math.min(7.5f,1f/Math.max(.035f,peak));float[] out=new float[15];
                for(int k=0;k<15;k++){double omega=2*Math.PI*hz[k]/sr,coeff=2*Math.cos(omega),q0=0,q1=0,q2=0;for(int i=0;i<n;i++){q0=coeff*q1-q2+buf[i]/32768.0;q2=q1;q1=q0;}double pow=Math.max(0,q1*q1+q2*q2-coeff*q1*q2);float e=(float)Math.log1p(Math.sqrt(pow)*agc*1.75)/2.15f;e=Math.max(0f,Math.min(1f,e));noise[k]=noise[k]*.998f+Math.min(noise[k]+.001f,e)*.002f;e=Math.max(0f,e-noise[k]*.58f);float a=e>smooth[k]?.52f:.12f;smooth[k]+=a*(e-smooth[k]);out[k]=smooth[k];}
                if(view!=null)view.setSpectrum(out,rms*agc);
                long now=SystemClock.uptimeMillis();if(now-lastDiag>600){sampleDiag(frames,rms*agc);lastDiag=now;}
            }
        },"KAndroid-EQ-50Hz");worker.setPriority(Thread.MAX_PRIORITY);worker.start();
    }
    private void stopRecord(){
        running=false;if(worker!=null){try{worker.interrupt();}catch(Exception ignored){}worker=null;}
        if(rec!=null){try{rec.stop();}catch(Exception ignored){}try{rec.release();}catch(Exception ignored){}rec=null;}
        if(projection!=null){MediaProjection p=projection;projection=null;try{p.stop();}catch(Exception ignored){}}
    }
    @Override public void onDestroy(){stopRecord();if(view!=null&&wm!=null){try{wm.removeView(view);}catch(Exception ignored){}view=null;}diag("SERVICE","stopped");super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
