package com.ezral.halo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.media.MediaPlayer;
import android.view.*;
import android.widget.FrameLayout;
import android.content.res.AssetFileDescriptor;

/** Swipeable stock-video demonstration feed in the test APK, not the YouTube app. */
public class ShortsBackdropActivity extends Activity {
    final MediaPlayer[] players = new MediaPlayer[2];
    final FrameLayout[] cards = new FrameLayout[2];
    FrameLayout root;
    int current=0;
    float downY;
    boolean switching=false;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(1024,1024);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(12,13,15));setContentView(root);
        for(int i=0;i<2;i++) {
            final int index=i;
            FrameLayout card=new FrameLayout(this);cards[i]=card;root.addView(card,new FrameLayout.LayoutParams(-1,-1));
            TextureView video=new TextureView(this);
            FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);card.addView(video,vp);
            video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h) {
                    int vw=(int)(h*9f/16f);
                    FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)video.getLayoutParams();lp.width=vw;video.setLayoutParams(lp);
                    try {
                        MediaPlayer mp=new MediaPlayer();players[index]=mp;
                        AssetFileDescriptor fd=getAssets().openFd("short"+(index+1)+".mp4");
                        mp.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());fd.close();
                        Surface surface=new Surface(st);mp.setSurface(surface);surface.release();
                        mp.setLooping(true);mp.setVolume(0,0);mp.setOnPreparedListener(m->{if(index==0)m.start();else m.seekTo(0);});mp.prepareAsync();
                    } catch(Exception e) { throw new RuntimeException(e); }
                }
                public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h) {}
                public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
                public void onSurfaceTextureUpdated(SurfaceTexture s) {}
            });
            card.addView(new View(this) {
                final Paint p=new Paint(3);
                void text(Canvas c,String t,float x,float y,float size,boolean bold) {
                    p.setColor(Color.WHITE);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?1:0));c.drawText(t,x,y,p);
                }
                @Override protected void onDraw(Canvas c) {
                    float scale=getWidth()/850f;c.save();c.scale(scale,scale);float h=getHeight()/scale;
                    float left=(850-h*9/16)/2;
                    p.setShader(new LinearGradient(0,h-230,0,h,new int[]{0,0xDD000000},null,Shader.TileMode.CLAMP));c.drawRect(left,h-230,850-left,h,p);p.setShader(null);
                    text(c,index==0?"Dumbbell curls":"One more rep",left+24,h-100,23,true);
                    text(c,"Training clips",left+24,h-65,15,false);
                    for(int n=0;n<3;n++) {
                        float x=850-left+42,y=h-280+n*80;p.setColor(0xFF252629);c.drawCircle(x,y,23,p);
                        p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);
                        if(n==0) { Path heart=new Path();heart.moveTo(x,y+10);heart.cubicTo(x-27,y-6,x-9,y-20,x,y-8);heart.cubicTo(x+9,y-20,x+27,y-6,x,y+10);c.drawPath(heart,p); }
                        else if(n==1) {c.drawRoundRect(x-10,y-9,x+10,y+7,3,3,p);c.drawLine(x-6,y+7,x-10,y+13,p);}
                        else {c.drawLine(x-10,y+8,x+9,y-9,p);c.drawLine(x-2,y-9,x+9,y-9,p);c.drawLine(x+9,y-9,x+9,y+3,p);}
                        p.setStyle(Paint.Style.FILL);
                    }
                    c.restore();
                }
            },new FrameLayout.LayoutParams(-1,-1));
        }
        root.post(()->cards[1].setTranslationY(root.getHeight()));
        root.addView(new View(this) {
            final Paint p=new Paint(3);
            @Override protected void onDraw(Canvas c) {
                float s=getWidth()/850f;c.save();c.scale(s,s);p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans-serif",1));p.setTextSize(23);c.drawText("Shorts",38,52,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(774,42,9,p);c.drawLine(781,49,788,56,p);p.setStyle(Paint.Style.FILL);
                for(int n=0;n<3;n++)c.drawCircle(814,34+n*7,1.7f,p);
                c.restore();
            }
        },new FrameLayout.LayoutParams(-1,-1));
        root.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN) { downY=event.getY();return true; }
            if(event.getAction()==MotionEvent.ACTION_UP && downY-event.getY()>80 && !switching) {
                switching=true;int next=1-current;int height=root.getHeight();if(players[next]!=null)players[next].start();cards[next].setTranslationY(height);
                cards[current].animate().translationY(-height).setDuration(420).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).start();
                cards[next].animate().translationY(0).setDuration(420).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).withEndAction(()->{if(players[current]!=null)players[current].pause();current=next;switching=false;root.setContentDescription("Video "+(current+1));}).start();
            }
            return true;
        });
    }
    @Override public void onDestroy() { for(MediaPlayer m:players)if(m!=null)m.release();super.onDestroy(); }
}
