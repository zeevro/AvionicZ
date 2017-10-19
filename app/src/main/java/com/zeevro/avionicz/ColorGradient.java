package com.zeevro.avionicz;

import android.util.Log;

class ColorGradient {
    private int negative;
    private int neutral;
    private int positive;

    ColorGradient(int negative, int neutral, int positive) {
        this.negative = negative;
        this.neutral = neutral;
        this.positive = positive;
    }

    int colorForValue(float value) {
        float intensity = Math.min(Math.abs(value), 1);
        int max = value > 0 ? positive : negative;
        int ret = 0;
        for (int i = 0; i < 4; i++) {
            ret += toComponent(calculateComponent(getComponent(max, i), intensity), i);
            ret += toComponent(calculateComponent(getComponent(neutral, i), 1 - intensity), i);
            //Log.d("ColorTAG", "i: " + i + ", Ret: " + Integer.toHexString(ret));
        }
        //Log.d("ColorTAG", "Value: " + value + ", Intensity: " + intensity + ", Max: " + Integer.toHexString(max) + ", Ret: " + Integer.toHexString(ret));
        return ret;
    }

    int calculateComponent(int source, float intensity) {
        return (int)((float)source * intensity);
    }

    private int getComponent(int color, int component) {
        return (color >> (component * 8)) & 0xFF;
    }

    private int toComponent(int value, int component) {
        return (value & 0xFF) << (component * 8);
    }
}
