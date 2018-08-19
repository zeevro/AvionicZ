package com.zeevro.avionicz;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.widget.ImageView;

class BearingArrow {
    private static final float PI = (float)Math.PI;
    private static final float TAU = 2 * PI;

    private ImageView imageView;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private final Paint mPaint = new Paint();

    private boolean stopAnimationThread = true;

    private float arrowTargetAngle = 0;
    private float arrowCurrentAngle = 0;
    private final float arrowSpeed = 0.25f;

    private void drawFrame() {
        float θ = arrowCurrentAngle - TAU / 4;
        final float cX = mCanvas.getWidth() / 2, cY = mCanvas.getHeight() / 2;
        final float R = Math.min(cX, cY);
        final float r = R * 0.6f;
        final float r2 = r / 2;
        final float tipX = cX + r * (float)Math.cos(θ), tipY = cY + r * (float)Math.sin(θ);

        mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        mCanvas.drawLine(tipX, tipY, tipX - r2 * (float)Math.cos(θ + .5), tipY - r2 * (float)Math.sin(θ + .5), mPaint);
        mCanvas.drawLine(tipX, tipY, tipX - r2 * (float)Math.cos(θ - .5), tipY - r2 * (float)Math.sin(θ - .5), mPaint);
        mCanvas.drawLine(tipX, tipY, cX + r * (float)Math.cos(θ + PI), cY + r * (float)Math.sin(θ + PI), mPaint);

        final float tr1 = R * 0.95f, tr2 = R * 0.75f, tr3 = R * 0.9f;
        int i;
        for (θ = 0, i = 0; θ < TAU; θ += TAU / 12, i++) {
            if (i % 3 == 0) {
                mCanvas.drawLine(cX + tr1 * (float) Math.cos(θ), cY + tr1 * (float) Math.sin(θ), cX + tr2 * (float) Math.cos(θ), cY + tr2 * (float) Math.sin(θ), mPaint);
            } else {
                mCanvas.drawLine(cX + tr1 * (float) Math.cos(θ), cY + tr1 * (float) Math.sin(θ), cX + tr3 * (float) Math.cos(θ), cY + tr3 * (float) Math.sin(θ), mPaint);
            }
        }

        //imageView.setBackground(new BitmapDrawable(imageView.getResources(), mBitmap));
        imageView.setImageBitmap(mBitmap);
    }

    private void animate() {
        if (arrowTargetAngle == arrowCurrentAngle) {
            return;
        }

        if (Math.abs(arrowTargetAngle - arrowCurrentAngle) > Math.abs(arrowTargetAngle - (arrowCurrentAngle + TAU))) {
            arrowCurrentAngle += TAU;
        } else if (Math.abs(arrowTargetAngle - arrowCurrentAngle) > Math.abs(arrowTargetAngle - (arrowCurrentAngle - TAU))) {
            arrowCurrentAngle -= TAU;
        }
        arrowCurrentAngle = arrowCurrentAngle + arrowSpeed * (arrowTargetAngle - arrowCurrentAngle);

        drawFrame();
    }

    BearingArrow (ImageView imageView) {
        this.imageView = imageView;
        mBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);

        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(15);
    }

    void setAngle(float α) {
        arrowTargetAngle = α;
    }

    void setAngleDegrees(float deg) {
        setAngle((float)Math.toRadians(deg));
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
