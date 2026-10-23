package com.hasanhoca.canliders;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** A local teaching board. Share the Android screen to show it to the meeting. */
public class WhiteboardActivity extends Activity {
    private Board board;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true); root.setBackgroundColor(Color.WHITE);
        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER);
        Button back = button("Derse dön"), undo = button("Geri al"), clear = button("Temizle"), color = button("Renk");
        for (Button b : new Button[]{back,undo,clear,color}) tools.addView(b,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(tools); board = new Board(); root.addView(board,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        back.setOnClickListener(v -> finish());
        undo.setOnClickListener(v -> { if (!board.strokes.isEmpty()) board.strokes.remove(board.strokes.size()-1); board.invalidate(); });
        clear.setOnClickListener(v -> new android.app.AlertDialog.Builder(this).setMessage("Çizimleri temizle?")
            .setNegativeButton("Vazgeç",null).setPositiveButton("Temizle",(d,w) -> { board.strokes.clear(); board.invalidate(); }).show());
        color.setOnClickListener(v -> new android.app.AlertDialog.Builder(this).setTitle("Kalem rengi")
            .setItems(new String[]{"Siyah","Mavi","Kırmızı","Yeşil"},(d,w) -> board.ink = new int[]{Color.BLACK,Color.BLUE,Color.RED,Color.rgb(0,130,70)}[w]).show());
        if (state != null) {
            @SuppressWarnings("unchecked") ArrayList<Stroke> saved = (ArrayList<Stroke>) state.getSerializable("strokes");
            if (saved != null) board.strokes.addAll(saved);
            board.ink = state.getInt("ink",Color.BLACK);
        }
    }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putSerializable("strokes",board.strokes); out.putInt("ink",board.ink); }
    private static class Stroke implements java.io.Serializable {
        final ArrayList<Float> coordinates = new ArrayList<>(); final int ink;
        Stroke(int color) { ink=color; }
    }
    private class Board extends View {
        final ArrayList<Stroke> strokes = new ArrayList<>(); final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int ink=Color.BLACK; Stroke active;
        Board() { super(WhiteboardActivity.this); setContentDescription("Çizim tahtası"); setBackgroundColor(Color.WHITE); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4 * getResources().getDisplayMetrics().density);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            for (Stroke s : strokes) {
                paint.setColor(s.ink); Path path=new Path();
                for(int i=0;i+1<s.coordinates.size();i+=2) {
                    float x=s.coordinates.get(i)*getWidth(),y=s.coordinates.get(i+1)*getHeight();
                    if(i==0)path.moveTo(x,y); else path.lineTo(x,y);
                }
                c.drawPath(path,paint);
                if(s.coordinates.size()==2) c.drawPoint(s.coordinates.get(0)*getWidth(),s.coordinates.get(1)*getHeight(),paint);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){active=new Stroke(ink);strokes.add(active);}
            if(active!=null){active.coordinates.add(e.getX()/Math.max(1,getWidth()));active.coordinates.add(e.getY()/Math.max(1,getHeight()));invalidate();}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){active=null;performClick();}
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)active=null;
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
