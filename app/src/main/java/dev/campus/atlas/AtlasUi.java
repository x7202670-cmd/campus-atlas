package dev.campus.atlas;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Native components shared by the four Morandi screens. */
final class AtlasUi {
    static final int BLUE=0xff6e8b7b, INK=0xff3a3d39, MUTED=0xff7a7e78,
        BG=0xfffaf8f5, LINE=0xffe2ddd4, SOFT=0xffe5ece8, RED=0xff8c5452, AMBER=0xff8a614a;
    private AtlasUi(){}
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int fill,int border,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));
        if(border!=0)d.setStroke(dp(c,1),border);return d;
    }
    static GradientDrawable gradient(Context c,int radius){
        return shape(c,BLUE,0,radius);
    }
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static TextView text(Context c,String value,int size,int color){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setIncludeFontPadding(false);t.setLineSpacing(dp(c,3),1);return t;
    }
    static TextView strong(Context c,String value,int size,int color){TextView t=text(c,value,size,color);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    static TextView mono(Context c,String value,int size,int color){TextView t=text(c,value,size,color);t.setTypeface(Typeface.MONOSPACE);return t;}
    static LinearLayout panel(Context c){
        LinearLayout l=column(c);l.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,16));
        l.setBackground(shape(c,Color.WHITE,LINE,20));return l;
    }
    static void space(LinearLayout parent,int height){View v=new View(parent.getContext());parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));}
    static void weight(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));}
    static void equal(LinearLayout parent,View v,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(parent.getContext(),height),1);p.setMargins(dp(parent.getContext(),3),0,dp(parent.getContext(),3),0);parent.addView(v,p);}
    static Button button(Context c,String title,int style,Runnable action){
        Button b=new Button(c);b.setText(title);b.setTextSize(12);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setAllCaps(false);b.setMinHeight(dp(c,40));b.setMinimumHeight(dp(c,40));b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(c,10),dp(c,7),dp(c,10),dp(c,7));b.setStateListAnimator(null);b.setBackgroundTintList(null);
        int color=style==1?Color.WHITE:style==2?RED:INK;
        ColorStateList colors=new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffa3a6a1,color});
        b.setTextColor(colors);
        Drawable normal=style==1?gradient(c,14):shape(c,style==2?0xfff4e8e8:0xfff2efe9,style==2?0xffd8b4b2:0,14);
        StateListDrawable backgrounds=new StateListDrawable();backgrounds.addState(new int[]{-android.R.attr.state_enabled},shape(c,0xfff2efe9,0,14));backgrounds.addState(new int[]{},normal);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226e8b7b),backgrounds,null));
        b.setOnClickListener(v->action.run());return b;
    }
    static EditText field(Context c,String value,String hint,boolean numeric){
        EditText e=new EditText(c);e.setTextSize(14);e.setTextColor(INK);e.setHintTextColor(0xffa3a6a1);e.setText(value);e.setHint(hint);
        e.setSingleLine(true);e.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));e.setMinHeight(dp(c,40));e.setBackgroundTintList(null);
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},shape(c,Color.WHITE,BLUE,12));states.addState(new int[]{},shape(c,BG,LINE,12));e.setBackground(states);
        if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }
    static TextView badge(Context c,String label,int color,int fill){TextView t=strong(c,label,11,color);t.setPadding(dp(c,9),dp(c,6),dp(c,9),dp(c,6));t.setBackground(shape(c,fill,0,20));return t;}
    static View icon(Context c,String type,int color,int size){Icon v=new Icon(c,type,color);v.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));return v;}

    /** Small stroked vector icons, independent of font glyph availability. */
    private static final class Icon extends View {
        private final String type;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final int color;
        Icon(Context c,String type,int color){super(c);this.type=type;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,p);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            switch(type){
                case "location":
                    path.reset();path.moveTo(12,22);path.cubicTo(9,18,4,13,4,9);path.cubicTo(4,-1,20,-1,20,9);path.cubicTo(20,13,15,18,12,22);canvas.drawPath(path,p);canvas.drawCircle(12,9,3,p);break;
                case "route":
                    canvas.drawCircle(5,5,2.5f,p);canvas.drawCircle(19,19,2.5f,p);path.reset();path.moveTo(8,5);path.lineTo(16,5);path.cubicTo(23,5,23,12,16,12);path.lineTo(8,12);path.cubicTo(1,12,1,19,8,19);path.lineTo(16,19);canvas.drawPath(path,p);break;
                case "schedule":
                    canvas.drawRoundRect(3,4,21,21,3,3,p);line(canvas,3,10,21,10);line(canvas,8,2,8,6);line(canvas,16,2,16,6);line(canvas,7,14,10,14);line(canvas,14,14,17,14);line(canvas,7,18,10,18);break;
                case "space":
                    canvas.drawRoundRect(3,3,10,10,2,2,p);canvas.drawRoundRect(14,3,21,10,2,2,p);canvas.drawRoundRect(3,14,10,21,2,2,p);canvas.drawRoundRect(14,14,21,21,2,2,p);break;
                case "search":canvas.drawCircle(10,10,6,p);line(canvas,15,15,21,21);break;
                case "target":canvas.drawCircle(12,12,7,p);canvas.drawCircle(12,12,2,p);line(canvas,12,1,12,6);line(canvas,12,18,12,23);line(canvas,1,12,6,12);line(canvas,18,12,23,12);break;
                case "play":path.reset();path.moveTo(8,4);path.lineTo(20,12);path.lineTo(8,20);path.close();canvas.drawPath(path,p);break;
                case "copy":canvas.drawRoundRect(8,8,21,21,2,2,p);line(canvas,4,16,3,16);line(canvas,3,16,3,3);line(canvas,3,3,16,3);line(canvas,16,3,16,4);break;
                default:line(canvas,5,12,19,12);line(canvas,12,5,12,19);
            }canvas.restore();
        }
    }
}
