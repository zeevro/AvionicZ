package com.zeevro.avionicz;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.widget.ImageView;

class VerticalSpeedGauge {
    private static final float PI = (float)Math.PI;
    private static final float TAU = 2 * PI;

    private static final float VSI_MAX_DEVIATION = PI * (2f/3f);
    private static final float VSI_MAX_VALUE = 2500;
    private static final float VSI_TICKS = 500;


    private ImageView imageView;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private final Paint mPaint = new Paint();

    private boolean stopAnimationThread = true;

    private float vsiArrowTargetAngle = 0;
    private float vsiArrowCurrentAngle = 0;
    @SuppressWarnings("FieldCanBeLocal")
    private final float vsiArrowSpeed = 0.035f;

    private void drawFrame() {
        float θ = PI + vsiArrowCurrentAngle;
        final float w = mCanvas.getWidth(), h = mCanvas.getHeight();
        final float cX = w / 2f, cY = h / 2f;
        final float R = Math.min(cX, cY);
        final float r1 = R * 0.85f;
        final float r2 = R * 0.65f;

        mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        mCanvas.drawLine(cX + r1 * (float)Math.cos(θ), cY + r1 * (float)Math.sin(θ), cX + r2 * (float)Math.cos(θ), cY + r2 * (float)Math.sin(θ), mPaint);

        final float tr1 = R * 0.95f, tr3 = R * 0.9f;
        for (θ = PI - VSI_MAX_DEVIATION; θ <= PI + VSI_MAX_DEVIATION; θ += VSI_MAX_DEVIATION / (VSI_MAX_VALUE / VSI_TICKS)) {
            mCanvas.drawLine(cX + tr1 * (float)Math.cos(θ), cY + tr1 * (float)Math.sin(θ), cX + tr3 * (float)Math.cos(θ), cY + tr3 * (float)Math.sin(θ), mPaint);
        }

        mCanvas.drawArc(new RectF(cX * 0.45f, cY * 0.45f, cX * 1.55f, cY * 1.55f), 90, 180, false, mPaint);
        mCanvas.drawLine(cX, cY * 0.45f, w, cY * 0.45f, mPaint);
        mCanvas.drawLine(cX, cY * 1.55f, w, cY * 1.55f, mPaint);

        imageView.setImageBitmap(mBitmap);
    }

    private void animate() {
        if (vsiArrowTargetAngle == vsiArrowCurrentAngle) return;

        if (Math.abs(vsiArrowTargetAngle - vsiArrowCurrentAngle) <= vsiArrowSpeed) {
            vsiArrowCurrentAngle = vsiArrowTargetAngle;
        } else if (vsiArrowCurrentAngle > vsiArrowTargetAngle) {
            vsiArrowCurrentAngle -= vsiArrowSpeed;
        } else {
            vsiArrowCurrentAngle += vsiArrowSpeed;
        }

        drawFrame();
    }

    VerticalSpeedGauge(ImageView imageView) {
        this.imageView = imageView;
        mBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);

        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(15);
    }

    void setVerticalSpeed(float speed) {
        if (Math.abs(speed) > VSI_MAX_VALUE) {
            speed = Math.copySign(VSI_MAX_VALUE, speed);
        }
        vsiArrowTargetAngle = speed / VSI_MAX_VALUE * VSI_MAX_DEVIATION;
    }

    void startAnimation() {
        if (!stopAnimationThread) {
            return;
        }
        drawFrame();
        stopAnimationThread = false;
        final Handler handler = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (stopAnimationThread) {
                    return;
                }
                animate();
                handler.postDelayed(this, 10);
            }
        }).start();
    }

    void stopAnimation() {
        stopAnimationThread = true;
    }
}
