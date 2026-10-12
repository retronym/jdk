import jdk.jfr.consumer.*; import java.nio.file.*; import java.util.*;
// For samples under make(): histogram of leaf PC -> attributed Java leaf frame
public class Pc { public static void main(String[] a) throws Exception {
  Map<String,Integer> m=new HashMap<>();
  try (var f=new RecordingFile(Path.of(a[0]))) { while (f.hasMoreEvents()) { var e=f.readEvent();
    if(!e.getEventType().getName().equals("jdk.ExecutionSample")) continue; var fr=e.getStackTrace().getFrames();
    if (fr.stream().noneMatch(x->x.getMethod().getName().equals("make"))) continue;
    var pc=fr.get(0).getMethod().getName(); if (!pc.startsWith("0x")) continue;
    int i=1; while (i<fr.size() && !fr.get(i).isJavaFrame()) i++;
    if (i>1) continue; // PC directly in Java code only
    var x=fr.get(1); String t=x.getMethod().getType().getName();
    m.merge(pc+"  "+t.substring(t.lastIndexOf('.')+1)+"."+x.getMethod().getName()+"@"+x.getBytecodeIndex()+" ["+x.getType()+"]",1,Integer::sum);
  }}
  m.entrySet().stream().sorted((p,q)->q.getValue()-p.getValue()).limit(Integer.parseInt(a[1])).forEach(e->System.out.printf("%6d  %s%n",e.getValue(),e.getKey()));
}}
