import jdk.jfr.consumer.*; import java.nio.file.*; import java.util.*;
// Histogram of leaf (top) frames for samples whose stack contains Repro.make
public class Leaf { public static void main(String[] a) throws Exception {
  Map<String,Integer> m=new HashMap<>(); int tot=0, all=0;
  try (var f=new RecordingFile(Path.of(a[0]))) { while (f.hasMoreEvents()) { var e=f.readEvent();
    if(!e.getEventType().getName().equals("jdk.ExecutionSample")) continue; all++;
    var fr=e.getStackTrace().getFrames(); int mk=-1;
    for (int i=0;i<fr.size();i++) if (fr.get(i).getMethod().getName().equals("make") && fr.get(i).getMethod().getType().getName().equals("JRepro")) { mk=i; break; }
    if (mk<0) continue; tot++;
    StringBuilder sb=new StringBuilder();
    for (int j=Math.min(mk,Integer.parseInt(a.length>2?a[2]:"12"));j>=0;j--) { var x=fr.get(j); String t=x.getMethod().getType().getName(); sb.append(t.substring(t.lastIndexOf('.')+1)+"."+x.getMethod().getName()+"@"+x.getBytecodeIndex()+"["+x.getType().charAt(0)+"]"+(j>0?" > ":"")); }
    m.merge(sb.toString(),1,Integer::sum); }}
  System.out.println(a[0]+": samples="+all+" under make="+tot);
  m.entrySet().stream().sorted((x,y)->y.getValue()-x.getValue()).limit(Integer.parseInt(a.length>1?a[1]:"25")).forEach(e->System.out.printf("%6d  %s%n",e.getValue(),e.getKey()));
}}
