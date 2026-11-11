package dev.campus.atlas.core;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Conservative venue extraction; uncertain words are left in the editable OCR text. */
public final class TimetableParser {
    private static final String CAMPUS_NAMES="朝阳|海淀|紫荆|大学城|科技园|东区|西区|南区|北区|中区|本部";
    private static final Pattern CAMPUS=Pattern.compile("(?:[\\u4e00-\\u9fa5]{2,8}(?:大学|学院))?(?:"+CAMPUS_NAMES+")(?:校区)?");
    private static final Pattern ROOM=Pattern.compile("(?<![\\dA-Za-z])([A-Z]?\\d{1,2}\\s*-\\s*(?:[A-Z]\\d{1,3}|\\d{3,4}))(?![\\dA-Za-z])");
    private static final Pattern PLACE=Pattern.compile("教学楼|实验楼|办公楼|大楼|体育馆|羽毛球馆|游泳馆|图书馆|田径场|足球场|篮球场|网球场|排球场|门球场|操场|教室|实验室|(?:体育|活动|实训|实验)中心|楼");
    private static final Pattern TRAILING_ROOM=Pattern.compile("^\\s*([A-Z]?\\d{2,4}[A-Z]?)(?![\\dA-Za-z])(?:$|[\\s,，、;；/（(]).*");
    private static final Pattern METADATA=Pattern.compile("(?:[（(]?第?\\d+(?:\\s*[-,、]\\s*\\d+)*\\s*(?:周|节)|周次|节次|星期|教师[:：]|任课|时间[:：])");
    private static final String GAP="[\\t ]*(?:\\n[\\t ]*[|丨]?[\\t ]*)?";
    private static final Pattern SPLIT_BUILDING=Pattern.compile("科技"+GAP+"实验"+GAP+"大"+GAP+"楼");

    private TimetableParser(){}

    public static List<String> candidates(String text){
        if(text==null||text.trim().isEmpty())return Collections.emptyList();
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        String previous="";
        for(String raw:normalizeOcrText(text).split("\\n",-1)){
            // Blank lines are OCR cell boundaries, so context must never pass through them.
            if(raw.trim().isEmpty()){previous="";continue;}
            String[] cells=raw.split("[\\t|;；]+",-1);
            for(String cell:cells){
                String line=normalizeName(cell);
                if(line.isEmpty()){previous="";continue;}
                for(String segment:splitCampuses(line)){
                    extract(segment,previous,result);
                    // Only an unfinished venue may prefix the next line, never another room.
                    previous=isPrefix(segment)?locationPrefix(segment):"";
                }
                if(cells.length>1)previous="";
            }
        }
        return new ArrayList<>(result.values());
    }

    private static List<String> splitCampuses(String line){
        List<String> out=new ArrayList<>();Matcher m=CAMPUS.matcher(line);int start=0;boolean seen=false;
        while(m.find()){if(seen){out.add(line.substring(start,m.start()).trim());start=m.start();}seen=true;}
        out.add(line.substring(start).trim());return out;
    }

    private static void extract(String line,String previous,LinkedHashMap<String,String> out){
        Matcher metadata=METADATA.matcher(line);
        if(metadata.find())line=line.substring(0,metadata.start()).trim();
        if(line.isEmpty())return;
        String combined=line;
        if(!CAMPUS.matcher(line).find()&&!previous.isEmpty()){
            if(ROOM.matcher(line).lookingAt()||line.matches("[A-Z]?\\d{2,4}[A-Z]?")
                    ||PLACE.matcher(line).find()&&!PLACE.matcher(previous).find())combined=join(previous,line);
        }
        Matcher rooms=ROOM.matcher(combined);boolean emittedRoom=false;String base="";
        while(rooms.find()){
            String prefix=locationPrefix(combined.substring(0,rooms.start()));
            if(!prefix.isEmpty())base=prefix;
            if(!base.isEmpty()){
                if(!previous.isEmpty()&&combined.equals(join(previous,line)))out.remove(placeKey(previous));
                add(out,join(base,rooms.group(1)));emittedRoom=true;
            }
        }
        // A room-qualified venue is one candidate, not an extra building-only row.
        if(emittedRoom)return;
        Matcher places=PLACE.matcher(combined);int from=0;
        while(places.find()){
            String prefix=locationPrefix(combined.substring(from,places.end()));
            if(!prefix.isEmpty()){
                Matcher room=TRAILING_ROOM.matcher(combined.substring(places.end()));
                boolean hasRoom=room.matches();
                if(hasRoom&&!previous.isEmpty()&&combined.equals(join(previous,line)))out.remove(placeKey(previous));
                add(out,hasRoom?join(prefix,room.group(1)):prefix);
            }
            from=places.end();
        }
    }

    private static boolean isPrefix(String line){
        if(line.length()>30||ROOM.matcher(line).find()||METADATA.matcher(line).find())return false;
        if(line.matches(".*(?:场|馆|中心)$"))return false;
        if(line.matches(".*\\d{2,4}[A-Z]?$"))return false;
        if(CAMPUS.matcher(line).find())return true;
        return PLACE.matcher(line).find()&&locationPrefix(line).equals(line);
    }

    private static String locationPrefix(String text){
        String value=normalizeName(text).replaceFirst("^(?:上课)?(?:地点|教室|地址)\\s*[:：]\\s*","");
        Matcher room=ROOM.matcher(value);if(room.find())return "";
        Matcher campus=CAMPUS.matcher(value);int start=-1;
        while(campus.find())start=campus.start();
        if(start>=0){
            String name=value.substring(start).trim();
            // In typical venue shorthand, a trailing 救/敎 is a known OCR error for 教.
            name=name.replaceFirst("^((?:[\\u4e00-\\u9fa5]{2,8}(?:大学|学院))?(?:"+CAMPUS_NAMES+")(?:校区)?)[救敎数]$","$1教");
            return name.matches(".*[,，、/].*")?"":name;
        }
        Matcher place=PLACE.matcher(value);int end=-1;
        while(place.find())end=place.end();
        if(end<0)return "";
        value=value.substring(0,end);
        int boundary=Math.max(Math.max(value.lastIndexOf(' '),value.lastIndexOf('，')),Math.max(Math.max(value.lastIndexOf(','),value.lastIndexOf(':')),value.lastIndexOf('：')));
        value=value.substring(boundary+1).replaceFirst("^.*[（(]","").trim();
        return value.length()>=2&&value.length()<=24?value:"";
    }

    private static String join(String left,String right){
        String a=normalizeName(left),b=normalizeName(right);
        if(a.isEmpty())return b;if(b.isEmpty())return a;
        return normalizeName(a+" "+b);
    }

    /** Unify typography without guessing ambiguous OCR letters or changing room numbers. */
    public static String normalizeOcrText(String text){
        if(text==null)return "";
        String normalized=Normalizer.normalize(text,Normalizer.Form.NFKC).replace("\r\n","\n").replace('\r','\n')
                .replace('\u00a0',' ').replaceAll("[‐‑‒–—−－]","-").replace("\u200b","").replace("\ufeff","");
        return SPLIT_BUILDING.matcher(normalized).replaceAll("科技实验大楼");
    }

    public static String normalizeName(String value){
        return normalizeOcrText(value).replaceAll("[\\t ]+"," ")
                .replaceAll("(?<=\\p{IsHan}) +(?=\\p{IsHan})","")
                .replaceAll("\\s*-\\s*","-").replaceAll("^[·,，、\\s]+|[·,，、\\s]+$","").trim();
    }

    /** Exact identity after formatting normalization; campus and room remain significant. */
    public static String placeKey(String value){
        return normalizeName(value)
                .replaceAll("(?:[\\u4e00-\\u9fa5]{2,8}(?:大学|学院))","")
                .replaceAll("("+CAMPUS_NAMES+")校区","$1")
                .replaceAll("([\\u4e00-\\u9fa5]{2,6})校区","$1")
                .replaceAll("\\s+","").toUpperCase(Locale.ROOT);
    }

    public static List<String> uniquePlaces(Collection<String> values){
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        for(String value:values)add(result,value);
        return new ArrayList<>(result.values());
    }

    private static void add(LinkedHashMap<String,String> result,String value){
        String name=normalizeName(value);
        if(name.length()>=2&&name.length()<=48)result.putIfAbsent(placeKey(name),name);
    }
}
