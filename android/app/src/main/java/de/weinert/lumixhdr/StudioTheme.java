package de.weinert.lumixhdr;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** Small native design system: semantic colors, scalable type and generous touch targets. */
final class StudioTheme {
    final Activity activity;
    final boolean dark;
    final int background,surface,text,secondary,line,accent,soft,success,destructive;
    StudioTheme(Activity activity){
        this.activity=activity;dark=(activity.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        background=Color.parseColor(dark?"#101113":"#F5F5F7");surface=Color.parseColor(dark?"#1C1D20":"#FFFFFF");
        text=Color.parseColor(dark?"#F5F5F7":"#1D1D1F");secondary=Color.parseColor(dark?"#A9ACB3":"#686B73");
        line=Color.parseColor(dark?"#33353B":"#E7E8EC");accent=Color.parseColor(dark?"#70A7FF":"#075AD7");
        soft=Color.parseColor(dark?"#243248":"#EAF1FE");success=Color.parseColor(dark?"#78D8AA":"#176A47");
        destructive=Color.parseColor(dark?"#FF827B":"#B42318");
    }
    int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView text(String value,float size,int color,boolean bold){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setTypeface(android.graphics.Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL));t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1);return t;}
    TextView heading(String value){TextView t=text(value,32,text,true);t.setLetterSpacing(-.035f);t.setAccessibilityHeading(true);return t;}
    TextView caption(String value){return text(value,14,secondary,false);}
    void gap(LinearLayout root,int height){Space s=new Space(activity);root.addView(s,new LinearLayout.LayoutParams(1,dp(height)));}
    GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    GradientDrawable outline(int color,int radius){GradientDrawable g=shape(color,radius);g.setStroke(dp(1),line);return g;}
    LinearLayout card(LinearLayout root){LinearLayout c=column();c.setPadding(dp(20),dp(20),dp(20),dp(20));c.setBackground(shape(surface,24));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);root.addView(c,lp);return c;}
    void fill(View v,int height){v.setLayoutParams(new LinearLayout.LayoutParams(-1,height<0?height:dp(height)));}
    TextView action(String label,boolean primary,View.OnClickListener listener){
        TextView b=text(label,16,primary?Color.WHITE:accent,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(18),dp(15),dp(18),dp(15));b.setMinHeight(dp(54));b.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x22FFFFFF:0x12000000),shape(primary?Color.parseColor("#075AD7"):soft,16),null));
        b.setOnClickListener(listener);b.setFocusable(true);buttonAccessibility(b);fill(b,-2);return b;
    }
    TextView link(String label,View.OnClickListener listener){TextView b=text(label,15,accent,true);b.setMinHeight(dp(48));b.setGravity(Gravity.CENTER_VERTICAL);b.setOnClickListener(listener);b.setFocusable(true);buttonAccessibility(b);return b;}
    void buttonAccessibility(View view){view.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}});}
    void enabled(View v,boolean on){v.setEnabled(on);v.setAlpha(on?1:.38f);}
    void divider(LinearLayout root){View v=new View(activity);v.setBackgroundColor(line);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.topMargin=dp(12);p.bottomMargin=dp(12);root.addView(v,p);}
    LinearLayout screen(String title,String subtitle){LinearLayout c=column();c.setPadding(dp(24),dp(22),dp(24),dp(28));c.addView(heading(title));gap(c,8);c.addView(caption(subtitle));gap(c,28);return c;}
    View icon(String kind,int color,int size){View v=new Glyph(activity,kind,color);v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
    static final class Glyph extends View {
        private final String kind;private final Paint paint=new Paint(3);private final Path pencil=new Path();private int color;
        Glyph(Activity context,String kind,int color){super(context);this.kind=kind;this.color=color;}
        void tint(int color){this.color=color;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float scale=Math.min(getWidth(),getHeight())/24f;c.save();c.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);c.scale(scale,scale);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.65f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            switch(kind){
                case "camera":c.drawRoundRect(3,7,21,20,4,4,paint);c.drawCircle(12,13.5f,3.6f,paint);c.drawLine(8,7,9.5f,4,paint);c.drawLine(9.5f,4,14.5f,4,paint);c.drawLine(14.5f,4,16,7,paint);break;
                case "photos":c.drawRoundRect(5,3,22,20,3,3,paint);c.drawLine(2,7,2,21,paint);c.drawLine(2,21,17,21,paint);c.drawCircle(10,8,1.2f,paint);Path p=new Path();p.moveTo(6,17);p.lineTo(12,11);p.lineTo(17,16);p.lineTo(20,13);p.lineTo(22,15);c.drawPath(p,paint);break;
                case "settings":c.drawCircle(12,12,3,paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine((float)(12+8*Math.cos(a)),(float)(12+8*Math.sin(a)),(float)(12+10*Math.cos(a)),(float)(12+10*Math.sin(a)),paint);}c.drawCircle(12,12,8,paint);break;
                case "arrow":c.drawLine(9,6,15,12,paint);c.drawLine(15,12,9,18,paint);break;
                case "check":c.drawLine(5,12,10,17,paint);c.drawLine(10,17,20,6,paint);break;
                case "plus":c.drawLine(12,5,12,19,paint);c.drawLine(5,12,19,12,paint);break;
                case "pencil":pencil.reset();pencil.moveTo(5,15);pencil.lineTo(16,4);pencil.quadTo(17,3,18,4);pencil.lineTo(20,6);pencil.quadTo(21,7,20,8);pencil.lineTo(9,19);pencil.lineTo(4,20);pencil.close();c.drawPath(pencil,paint);c.drawLine(14,6,18,10,paint);break;
                case "trash":c.drawLine(4,6,20,6,paint);c.drawRoundRect(8,3,16,6,1,1,paint);c.drawRoundRect(6,6,18,21,2,2,paint);c.drawLine(10,10,10,17,paint);c.drawLine(14,10,14,17,paint);break;
                default:c.drawCircle(12,12,7.6f,paint);for(int i=0;i<6;i++){double a=i*Math.PI/3;float x=(float)(12+7.6*Math.cos(a)),y=(float)(12+7.6*Math.sin(a));float xx=(float)(12+2.5*Math.cos(a+Math.PI/2)),yy=(float)(12+2.5*Math.sin(a+Math.PI/2));c.drawLine(x,y,xx,yy,paint);}break;
            }c.restore();}
    }
}
