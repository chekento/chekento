package cloud.kosch.kandroid.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * K Android 1.0 mouth renderer.
 * One visual row only: 15 LCARS capsules follow one shallow smile curve.
 */
public class EqOverlayView extends View implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int N = 15;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rr = new RectF();
    private final float[] bands = new float[N];
    private final float[] displayed = new float[N];
    private float rms = 0f, displayedRms = 0f, mouthY = .625f, mouthW = .24f, gain = 1.35f;
    private String mode = "AUTO";
    private long lastSignalMs = 0L;
    private SharedPreferences prefs;
    private static final int[] COLORS = {0xff55e9ff,0xff55e9ff,0xff65cfff,0xff7aa9ff,0xffa579ff,0xffd968ff,0xffff65c7,0xffff87b9,0xffffad4e,0xffff87b9,0xffff65c7,0xffd968ff,0xffa579ff,0xff65cfff,0xff55e9ff};

    public EqOverlayView(Context c) { super(c); setLayerType(LAYER_TYPE_SOFTWARE, null); prefs=c.getSharedPreferences("kbridge",0); prefs.registerOnSharedPreferenceChangeListener(this); reload(); }
    private void reload(){ mouthY=prefs.getFloat("mouthY",.625f); mouthW=prefs.getFloat("mouthW",.24f); gain=prefs.getFloat("gain",1.35f); mode=prefs.getString("mode","AUTO"); invalidate(); }
    @Override public void onSharedPreferenceChanged(SharedPreferences sp,String key){reload();}
    public void setSpectrum(float[] b,float r){if(b!=null)System.arraycopy(b,0,bands,0,Math.min(b.length,bands.length));rms=Math.max(0f,r);if(rms>.012f)lastSignalMs=SystemClock.uptimeMillis();postInvalidateOnAnimation();}
    @Override protected void onDetachedFromWindow(){if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(this);super.onDetachedFromWindow();}
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c); float density=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),cx=w*.5f,cy=h*mouthY,total=w*mouthW,step=total/(N-1f);
        float targetRms=clamp(rms*gain*6.6f,0f,1f),envK=targetRms>displayedRms?.42f:.105f;displayedRms+=(targetRms-displayedRms)*envK;
        boolean active=SystemClock.uptimeMillis()-lastSignalMs<220||displayedRms>.025f;
        float broad=0f,mid=0f;for(int i=0;i<N;i++){broad+=bands[i];if(i>=4&&i<=10)mid+=bands[i];}broad/=N;mid/=7f;
        boolean music="MUSIC".equals(mode)||("AUTO".equals(mode)&&active&&broad>.12f&&broad>mid*.72f),speech="SPEECH".equals(mode)||(!music&&active);
        for(int i=0;i<N;i++){
            float t=i/(N-1f),central=(float)Math.pow(Math.sin(Math.PI*t),.62),raw=clamp(bands[i]*(music?1.95f:1.62f)+displayedRms*.28f,0f,1f),target=active?raw:0f;
            if(speech)target*=.56f+.44f*central;float k=target>displayed[i]?.54f:.13f;displayed[i]+=(target-displayed[i])*k;
            float x=cx-total/2f+i*step,arc=(float)Math.sin(Math.PI*t),y=cy+arc*(music?4.4f:5.3f)*density,idle=.045f+central*.018f,e=active?Math.max(idle,displayed[i]):idle;
            float barH=3.6f*density+e*(music?27f:19f)*density,barW=Math.max(3f*density,Math.min(step*.47f,5.5f*density)),radius=barW*.52f;int col=COLORS[i];
            halo.setColor(((active?0x45:0x22)<<24)|0x00ffffff);halo.setShadowLayer((active?8.5f:4f)*density,0,0,col);rr.set(x-barW*.65f,y-barH*.57f,x+barW*.65f,y+barH*.57f);c.drawRoundRect(rr,radius,radius,halo);
            paint.setColor(col);paint.setAlpha(active?245:118);paint.setShadowLayer((active?5.8f:2.8f)*density,0,0,col);rr.set(x-barW/2f,y-barH/2f,x+barW/2f,y+barH/2f);c.drawRoundRect(rr,radius,radius,paint);
        }
        if(displayedRms>.01f||active)postInvalidateOnAnimation();
    }
}
