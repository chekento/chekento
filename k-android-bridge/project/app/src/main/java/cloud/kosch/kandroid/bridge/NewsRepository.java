package cloud.kosch.kandroid.bridge;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

/** Live AI + AI compliance radar without an API key. */
public class NewsRepository {
    public static class Item{
        public String title,url,source,date,cat; public long when;
        Item(String t,String u,String s,String d,String c,long w){title=t;url=u;source=s;date=d;cat=c;when=w;}
    }
    static final String[][] FEEDS={
        {"AKTUELL","https://news.google.com/rss/search?q=(artificial%20intelligence%20OR%20generative%20AI%20OR%20LLM)%20when%3A2d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"OPENAI","https://news.google.com/rss/search?q=OpenAI%20when%3A7d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"ANTHROPIC","https://news.google.com/rss/search?q=(Anthropic%20OR%20Claude)%20when%3A7d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"GOOGLE / DEEPMIND","https://news.google.com/rss/search?q=(Google%20Gemini%20OR%20DeepMind)%20when%3A7d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"META / LLAMA","https://news.google.com/rss/search?q=(Meta%20AI%20OR%20Llama)%20when%3A7d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"MISTRAL","https://news.google.com/rss/search?q=Mistral%20AI%20when%3A10d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"TOOLS / AGENTS","https://news.google.com/rss/search?q=(AI%20agents%20OR%20agentic%20AI%20OR%20AI%20tools)%20when%3A5d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"ROBOTICS","https://news.google.com/rss/search?q=(humanoid%20robot%20OR%20AI%20robotics)%20when%3A7d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"EU AI ACT","https://news.google.com/rss/search?q=(%22EU%20AI%20Act%22%20OR%20%22AI%20Office%22%20OR%20%22general-purpose%20AI%22)%20when%3A30d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"DSGVO / GDPR + KI","https://news.google.com/rss/search?q=(DSGVO%20OR%20GDPR)%20(KI%20OR%20AI%20OR%20LLM%20OR%20training%20data)%20when%3A30d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"EDPB / EDPS","https://news.google.com/rss/search?q=(EDPB%20OR%20EDPS)%20(AI%20OR%20KI%20OR%20scraping%20OR%20models)%20when%3A45d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"DE DATENSCHUTZ","https://news.google.com/rss/search?q=(BfDI%20OR%20Datenschutzkonferenz%20OR%20DSK)%20(KI%20OR%20AI)%20when%3A45d&hl=de&gl=DE&ceid=DE%3Ade"},
        {"RESEARCH","https://news.google.com/rss/search?q=(AI%20research%20OR%20LLM%20research%20OR%20machine%20learning)%20when%3A7d&hl=de&gl=DE&ceid=DE%3Ade"}
    };
    public static List<Item> fetch(int each)throws Exception{
        List<Item> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(String[] f:FEEDS){try{for(Item i:parse(f[1],f[0],each)){String key=i.title==null?"":i.title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9äöüß]+"," ").trim();if(!key.isEmpty()&&seen.add(key))out.add(i);}}catch(Exception ignored){}}
        Collections.sort(out,(a,b)->Long.compare(b.when,a.when)); return out;
    }
    public static boolean isCompliance(Item i){if(i==null||i.cat==null)return false;String c=i.cat.toUpperCase(Locale.ROOT);return c.contains("AI ACT")||c.contains("DSGVO")||c.contains("GDPR")||c.contains("EDPB")||c.contains("EDPS")||c.contains("DATENSCHUTZ");}
    static long dateMillis(String s){if(s==null)return 0;String[] patterns={"EEE, dd MMM yyyy HH:mm:ss z","EEE, dd MMM yyyy HH:mm:ss Z"};for(String p:patterns)try{return new SimpleDateFormat(p,Locale.US).parse(s).getTime();}catch(Exception ignored){}return 0;}
    static List<Item> parse(String url,String cat,int lim)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","KAndroid/1.0 (+AI-News-Radar)");
        InputStream in=c.getInputStream();XmlPullParser x=Xml.newPullParser();x.setInput(in,"UTF-8");List<Item> r=new ArrayList<>();String t=null,l=null,d=null,s=null;boolean item=false;
        for(int ev=x.getEventType();ev!=XmlPullParser.END_DOCUMENT&&r.size()<lim;ev=x.next()){if(ev==XmlPullParser.START_TAG){String tag=x.getName();if("item".equals(tag)){item=true;t=l=d=s="";}else if(item&&"source".equals(tag))s=x.nextText();else if(item&&"title".equals(tag))t=x.nextText();else if(item&&"link".equals(tag))l=x.nextText();else if(item&&"pubDate".equals(tag))d=x.nextText();}else if(ev==XmlPullParser.END_TAG&&"item".equals(x.getName())){item=false;if(t!=null&&l!=null)r.add(new Item(t,l,s==null?"":s,d==null?"":d,cat,dateMillis(d)));}}
        in.close();c.disconnect();return r;
    }
    public static String pretty(Item i){String src=(i.source==null||i.source.isEmpty())?i.cat:i.source;String time=i.when>0?new SimpleDateFormat("dd.MM · HH:mm",Locale.GERMANY).format(new Date(i.when)):"aktuell";return i.title+"\n"+i.cat+" · "+src+" · "+time;}
}
