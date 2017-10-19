package com.zeevro.avionicz;

class LowPassFilter {
    private float alpha = 0.25f;
    private float output = 0;

    LowPassFilter() { }

    LowPassFilter(float alpha) {
        this.alpha = alpha;
    }

    float getOutput(float input) {
        if (output == 0) {
            output = input;
        } else {
            output = output + alpha * (input - output);
        }

        return output;
    }

    float getOutput() {
        return output;
    }

    float getAlpha() {
        return alpha;
    }

    void setAlpha(float alpha) {
        this.alpha = alpha;
    }
}
