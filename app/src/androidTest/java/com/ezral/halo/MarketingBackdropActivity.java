package com.ezral.halo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.View;

/** Standalone test APK backdrop. Java avoids dependencies supplied only by the target APK. */
public class MarketingBackdropActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(246,243,235));
        getWindow().setNavigationBarColor(Color.rgb(246,243,235));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            void text(Canvas c, String t, float x, float y, float size, boolean bold) {
                p.setColor(Color.rgb(34,55,48)); p.setTextSize(size);
                p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
                c.drawText(t,x,y,p);
            }
            @Override protected void onDraw(Canvas c) {
                float s=getWidth()/400f; c.save(); c.scale(s,s); c.drawColor(Color.rgb(246,243,235));
                text(c,"S L O W   M O R N I N G S",28,40,11,true);
                p.setColor(Color.rgb(212,216,204)); c.drawRect(28,60,372,61,p);
                text(c,"A better",28,116,42,true); text(c,"morning cup.",28,164,42,true);
                text(c,"A simple pour-over ritual.",28,199,16,false);
                p.setColor(Color.rgb(222,227,205)); c.drawRoundRect(28,228,372,445,24,24,p);
                p.setColor(Color.rgb(163,173,143)); c.drawOval(102,385,300,420,p);
                p.setColor(Color.rgb(245,239,222)); c.drawRoundRect(267,286,319,359,24,24,p);
                p.setColor(Color.rgb(222,227,205)); c.drawRoundRect(277,300,307,346,13,13,p);
                p.setColor(Color.rgb(250,247,235)); c.drawRoundRect(125,280,282,397,36,36,p); c.drawOval(125,267,282,307,p);
                p.setColor(Color.rgb(112,73,49)); c.drawOval(139,279,268,303,p);
                text(c,"01",28,491,13,true); text(c,"Bloom",72,492,21,true);
                text(c,"Wet the grounds. Let the coffee open up.",72,519,13,false);
                text(c,"02",28,568,13,true); text(c,"Slow pour",72,569,21,true);
                text(c,"Pour gently, in small circles.",72,596,13,false);
                text(c,"03",28,645,13,true); text(c,"Enjoy",72,646,21,true);
                text(c,"Take your time. You have earned it.",72,673,13,false);
                text(c,"SAMPLE RECIPE  /  OVERLAY DEMONSTRATION",28,752,9,false);
                c.restore();
            }
        });
    }
}
