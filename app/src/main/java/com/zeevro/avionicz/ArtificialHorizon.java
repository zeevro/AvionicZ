package com.zeevro.avionicz;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.widget.ImageView;

public class ArtificialHorizon {
    private static final float PI = (float)Math.PI;
    private static final float TAU = 2 * PI;

    private ImageView imageView;
    private Bitmap mBitmap;
    private Canvas mCanvas;

    private final Paint mLinePaint = new Paint();
    private final Paint mGroundPaint = new Paint();
    private final int mSkyColor;

    private float pitch = 0;
    private float roll = 0;

    private void drawFrame() {
        final float w = mCanvas.getWidth(), h = mCanvas.getHeight();
        final float cX = w / 2f, cY = h / 2f;

        mCanvas.drawColor(mSkyColor);
        mCanvas.save();
        mCanvas.rotate(roll, cX, cY);
        mCanvas.translate(0, cY * (pitch / 90f));
        mCanvas.drawRect(-w, cY, w * 2f, h * 2f, mGroundPaint);
        mCanvas.restore();
        mCanvas.drawLine(w * 0.3f, cY, w * 0.4f, cY, mLinePaint);
        mCanvas.drawLine(w * 0.6f, cY, w * 0.7f, cY, mLinePaint);
        mCanvas.drawLine(cX, cY, cX - w * 0.07f, cY + h * 0.02f, mLinePaint);
        mCanvas.drawLine(cX, cY, cX + w * 0.07f, cY + h * 0.02f, mLinePaint);

        mCanvas.drawText(String.format("pitch: %f, roll: %f", pitch, roll), 0, mCanvas.getHeight() * 0.8f, mLinePaint);

        imageView.setImageBitmap(mBitmap);
    }

    ArtificialHorizon (ImageView imageView) {
        this.imageView = imageView;
        mBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);

        mSkyColor = Color.parseColor("#36B4DD");

        mLinePaint.setColor(Color.BLACK);
        mLinePaint.setStrokeWidth(5);
        mLinePaint.setTextSize(30);

        mGroundPaint.setStyle(Paint.Style.FILL);
        mGroundPaint.setColor(Color.parseColor("#865B4B"));
    }

    void setAttitude(float pitch, float roll) {
        if (pitch == this.pitch && roll == this.roll) return;
        this.pitch = pitch;
        this.roll = roll;
        drawFrame();
    }

}
