package dev.campus.atlas.core;

import java.util.*;

/** Keeps nearby OCR lines in their own column before extracting venue fragments. */
public final class TimetableLayout {
    public static final class Line {
        public final String text;
        public final int left,top,right,bottom;
        public Line(String text,int left,int top,int right,int bottom){
            this.text=text;this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
        private int height(){return Math.max(1,bottom-top);}
        private int width(){return Math.max(1,right-left);}
    }
    private TimetableLayout(){}

    public static String text(List<Line> input){
        List<Line> lines=new ArrayList<>(input);
        lines.sort(Comparator.comparingInt((Line l)->l.top).thenComparingInt(l->l.left));
        List<List<Line>> groups=new ArrayList<>();List<Line> accepted=new ArrayList<>();
        for(Line line:lines){
            if(line.text==null||line.text.trim().isEmpty())continue;
            boolean duplicate=false;
            for(Line old:accepted){
                if(old.bottom<line.top)continue;
                if(TimetableParser.placeKey(old.text).equals(TimetableParser.placeKey(line.text))&&overlap(old,line)>.75){duplicate=true;break;}
            }
            if(duplicate)continue;
            accepted.add(line);
            List<Line> best=null;double distance=Double.MAX_VALUE;
            for(List<Line> group:groups){
                Line tail=group.get(group.size()-1);double score=connection(tail,line);
                if(score<distance){distance=score;best=group;}
            }
            if(best==null){best=new ArrayList<>();groups.add(best);}
            best.add(line);
        }
        StringBuilder result=new StringBuilder();
        for(List<Line> group:groups){
            if(result.length()>0)result.append("\n\n");
            for(int i=0;i<group.size();i++){if(i>0)result.append('\n');result.append(group.get(i).text.trim());}
        }
        return TimetableParser.normalizeOcrText(result.toString());
    }

    private static double connection(Line a,Line b){
        int h=Math.max(a.height(),b.height()),gap=b.top-a.bottom;
        if(gap<-.2*Math.min(a.height(),b.height())||gap>1.6*h)return Double.MAX_VALUE;
        int intersection=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left));
        double center=Math.abs((a.left+(double)a.right-b.left-b.right)/2);
        if(intersection<.6*Math.min(a.width(),b.width())||center>Math.max(2*h,.25*Math.max(a.width(),b.width())))return Double.MAX_VALUE;
        return Math.max(0,gap)+center;
    }

    private static double overlap(Line a,Line b){
        double area=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left))
                *(double)Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
        return area/Math.min((double)a.width()*a.height(),(double)b.width()*b.height());
    }
}
