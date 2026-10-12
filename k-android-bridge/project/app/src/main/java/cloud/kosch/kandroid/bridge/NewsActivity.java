package cloud.kosch.kandroid.bridge;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Native LCARS-like live headline reader: actual headlines, not a link wall. */
public class NewsActivity extends Activity {
    private LinearLayout feed;
    private TextView status;
    private final ArrayList<NewsRepository.Item> all=new ArrayList<>();
    private String lane="ALL";
    private final int cyan=0xff58e6ff, pink=0xffff69c7, amber=0xffffae4a, lavender=0xffa97cff, ink=0xff03101f;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(14),dp(12),dp(14),dp(8)); root.setBackgroundColor(ink);
        TextView h=txt("K ANDROID 1.0 · AI NEWS",24,Color.WHITE,true); root.addView(h);
        root.addView(txt("LIVE RADAR · MODELS · TOOLS · RESEARCH · EU AI ACT · DSGVO/GDPR",12,cyan,false));
        status=txt("Aktualisiere aktuelle Meldungen …",12,0xff9fc0d9,false); root.addView(status);

        HorizontalScrollView hs=new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        addTab(tabs,"AKTUELL","ALL",cyan); addTab(tabs,"MODELLE","MODEL",pink); addTab(tabs,"TOOLS","TOOLS",lavender); addTab(tabs,"COMPLIANCE","COMPLIANCE",amber); addTab(tabs,"RESEARCH","RESEARCH",cyan);
        hs.addView(tabs); root.addView(hs,new LinearLayout.LayoutParams(-1,dp(48)));

        HorizontalScrollView ps=new HorizontalScrollView(this); ps.setHorizontalScrollBarEnabled(false);
        LinearLayout primary=new LinearLayout(this); primary.setOrientation(LinearLayout.HORIZONTAL);
        String[][] links={
            {"EU AI OFFICE","https://digital-strategy.ec.europa.eu/en/policies/ai-office"},
            {"EU AI ACT","https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai"},
            {"EUR-LEX","https://eur-lex.europa.eu/eli/reg/2024/1689/oj"},
            {"EDPB","https://www.edpb.europa.eu/news/news_en"},
            {"EDPS","https://www.edps.europa.eu/press-publications/press-news/news_en"},
            {"BfDI","https://www.bfdi.bund.de/DE/Service/Presse/Pressemitteilungen/pressemitteilungen_node.html"},
            {"DSK","https://www.datenschutzkonferenz-online.de/"}
        };
        for(String[] z:links) addPrimary(primary,z[0],z[1]);
        ps.addView(primary); root.addView(ps,new LinearLayout.LayoutParams(-1,dp(44)));

        ScrollView sv=new ScrollView(this); feed=new LinearLayout(this); feed.setOrientation(LinearLayout.VERTICAL); sv.addView(feed); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        Button refresh=new Button(this); refresh.setAllCaps(false); refresh.setText("↻  LIVE FEED AKTUALISIEREN"); refresh.setTextSize(14); refresh.setOnClickListener(v->load()); root.addView(refresh);
        setContentView(root); load();
    }

    private void addTab(LinearLayout row,String label,String value,int color){
        TextView v=chip(label,color); v.setOnClickListener(x->{lane=value;render();}); row.addView(v,new LinearLayout.LayoutParams(-2,dp(40)));
    }
    private void addPrimary(LinearLayout row,String label,String url){
        TextView v=chip(label,amber); v.setOnClickListener(x->open(url)); row.addView(v,new LinearLayout.LayoutParams(-2,dp(36)));
    }
    private TextView chip(String s,int color){
        TextView t=txt(s,11,color,true); t.setGravity(Gravity.CENTER); t.setPadding(dp(14),0,dp(14),0);
        GradientDrawable g=new GradientDrawable(); g.setColor(0xff071b30); g.setStroke(dp(1),color); g.setCornerRadius(dp(7)); t.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1); lp.setMargins(dp(3),dp(2),dp(3),dp(2)); t.setLayoutParams(lp); return t;
    }
    private TextView txt(String s,float size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4)); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private void open(String u){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception ignored){}}

    private boolean matches(NewsRepository.Item i){
        if("ALL".equals(lane))return true;
        if("COMPLIANCE".equals(lane))return NewsRepository.isCompliance(i);
        String c=i.cat==null?"":i.cat.toUpperCase(Locale.ROOT);
        if("TOOLS".equals(lane))return c.contains("TOOLS")||c.contains("AGENTS")||c.contains("ROBOTICS");
        if("RESEARCH".equals(lane))return c.contains("RESEARCH");
        if("MODEL".equals(lane))return c.contains("OPENAI")||c.contains("ANTHROPIC")||c.contains("GOOGLE")||c.contains("META")||c.contains("MISTRAL");
        return true;
    }

    private void card(NewsRepository.Item i){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(12),dp(9),dp(12),dp(9));
        int accent=NewsRepository.isCompliance(i)?amber:(i.cat.contains("TOOLS")||i.cat.contains("ROBOTICS")?lavender:cyan);
        GradientDrawable gd=new GradientDrawable(); gd.setColor(0xff06192d); gd.setStroke(dp(1),accent); gd.setCornerRadius(dp(9)); c.setBackground(gd);
        TextView title=txt(i.title,15,0xfff5f7ff,true); title.setMaxLines(3); c.addView(title);
        String src=(i.source==null||i.source.isEmpty())?i.cat:i.source;
        String tm=i.when>0?new SimpleDateFormat("dd.MM.yyyy · HH:mm",Locale.GERMANY).format(new Date(i.when)):"aktuell";
        c.addView(txt(i.cat+"  ·  "+src+"  ·  "+tm,10.5f,accent,false));
        c.setOnClickListener(v->open(i.url));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(4),0,dp(5)); feed.addView(c,lp);
    }

    private void render(){
        feed.removeAllViews(); int n=0;
        for(NewsRepository.Item i:all){ if(matches(i)){card(i); if(++n>=28)break;} }
        status.setText(n+" Meldungen in dieser Ansicht · tippen zum Öffnen · Stand "+new SimpleDateFormat("HH:mm",Locale.GERMANY).format(new Date()));
        if(n==0) feed.addView(txt("Keine Meldung in dieser Lane geladen. ↻ erneut aktualisieren.",14,0xffa9cbe2,false));
    }

    private void load(){
        status.setText("Live-Radar lädt …"); feed.removeAllViews();
        new Thread(()->{try{
            List<NewsRepository.Item> n=NewsRepository.fetch(4);
            runOnUiThread(()->{all.clear();all.addAll(n);render();});
        }catch(Exception e){runOnUiThread(()->status.setText("Feed derzeit nicht erreichbar · ↻ erneut versuchen"));}}).start();
    }
}
