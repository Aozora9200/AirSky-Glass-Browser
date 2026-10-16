/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.airsky.browser;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View.OnClickListener;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.Button;

import com.google.android.material.color.MaterialColors;

/**
 * 電卓のボタン(Pixel の電卓風)。
 *
 * 役割(数字・演算子・＝・消去・関数)ごとに Material You の色の丸いボタンにし、
 * 押すと丸 → 角の丸い四角へ形が変わりながら少し縮み、離すとぷるんと戻る。
 */
class ColorButton extends Button implements OnClickListener {
    static final int ROLE_DIGIT = 0, ROLE_OPERATOR = 1, ROLE_EQUAL = 2, ROLE_CLEAR = 3, ROLE_FUNCTION = 4;

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);

    float mTextX;
    float mTextY;
    OnClickListener mListener;

    private final GradientDrawable shape = new GradientDrawable();
    private final int inset;
    private final int role;
    /** 0 = 丸(離している), 1 = 押している(角の丸い四角)。 */
    private float press;
    private ValueAnimator pressAnim;

    public ColorButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        Calculator calc = (Calculator) context;
        mListener = calc.mListener;
        setOnClickListener(this);

        float dp = getResources().getDisplayMetrics().density;
        inset = Math.round(4 * dp);
        role = roleOf(getId());
        int[] colors = colorsFor(context, role);
        shape.setColor(colors[0]);
        setTextColor(colors[1]);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf((colors[1] & 0x00FFFFFF) | 0x22000000), shape, null);
        setBackground(new InsetDrawable(ripple, inset));
        setGravity(Gravity.CENTER);
        setAllCaps(false);
        setStateListAnimator(null);
        setElevation(0f);
        setPadding(0, 0, 0, 0);
        setTypeface(Typeface.create(role == ROLE_DIGIT ? "sans-serif" : "sans-serif-medium", Typeface.NORMAL));
        // 文字の大きさは役割で揃える(数字は大きく、関数は小さめ)
        float sp;
        switch (role) {
            case ROLE_DIGIT: sp = 32; break;
            case ROLE_OPERATOR: case ROLE_EQUAL: sp = 30; break;
            case ROLE_CLEAR: sp = 20; break;
            default: sp = 20; break;
        }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    private static int roleOf(int id) {
        if (id == R.id.equal) return ROLE_EQUAL;
        if (id == R.id.clear || id == R.id.del) return ROLE_CLEAR;
        if (id == R.id.plus || id == R.id.minus || id == R.id.mul || id == R.id.div) return ROLE_OPERATOR;
        if (id == R.id.digit0 || id == R.id.digit1 || id == R.id.digit2 || id == R.id.digit3
                || id == R.id.digit4 || id == R.id.digit5 || id == R.id.digit6 || id == R.id.digit7
                || id == R.id.digit8 || id == R.id.digit9 || id == R.id.dot) {
            return ROLE_DIGIT;
        }
        return ROLE_FUNCTION;
    }

    /** {背景, 文字} の色。 */
    private static int[] colorsFor(Context c, int role) {
        switch (role) {
            case ROLE_EQUAL:
                return new int[]{
                        MaterialColors.getColor(c, androidx.appcompat.R.attr.colorPrimary, Color.BLUE),
                        MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)};
            case ROLE_OPERATOR:
                return new int[]{
                        MaterialColors.getColor(c, com.google.android.material.R.attr.colorSecondaryContainer, Color.LTGRAY),
                        MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSecondaryContainer, Color.BLACK)};
            case ROLE_CLEAR:
                return new int[]{
                        MaterialColors.getColor(c, com.google.android.material.R.attr.colorTertiaryContainer, Color.LTGRAY),
                        MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnTertiaryContainer, Color.BLACK)};
            case ROLE_FUNCTION:
                return new int[]{
                        MaterialColors.getColor(c, com.google.android.material.R.attr.colorSurfaceContainer, Color.LTGRAY),
                        MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)};
            default:
                return new int[]{
                        MaterialColors.getColor(c, com.google.android.material.R.attr.colorSurfaceContainerHighest, Color.LTGRAY),
                        MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)};
        }
    }

    public void onClick(View view) {
        mListener.onClick(this);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                animatePress(1f, 140, EMPHASIZED);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animatePress(0f, 420, new OvershootInterpolator(2.4f));
                break;
        }
        return super.onTouchEvent(event);
    }

    private void animatePress(float to, long duration, android.animation.TimeInterpolator interpolator) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(press, to);
        pressAnim.setDuration(duration);
        pressAnim.setInterpolator(interpolator);
        pressAnim.addUpdateListener(a -> {
            press = (float) a.getAnimatedValue();
            applyPress();
        });
        pressAnim.start();
    }

    /** 押し具合から形と大きさを決める。 */
    private void applyPress() {
        int w = getWidth() - inset * 2, h = getHeight() - inset * 2;
        if (w <= 0 || h <= 0) return;
        float round = Math.min(w, h) / 2f;
        float square = Math.min(w, h) * 0.22f;
        shape.setCornerRadius(Math.max(0f, round + (square - round) * press));
        float s = 1f - 0.06f * press;
        setScaleX(s);
        setScaleY(s);
    }

    @Override
    public void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        measureText();
        applyPress();
    }

    private void measureText() {
        Paint paint = getPaint();
        mTextX = (getWidth() - paint.measureText(getText().toString())) / 2;
        mTextY = (getHeight() - paint.ascent() - paint.descent()) / 2;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        measureText();
    }

    /** 画面を開いた時、ボタンが順番にふわっと現れる。 */
    void playEnter(long delay) {
        setAlpha(0f);
        setScaleX(0.6f);
        setScaleY(0.6f);
        animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(380)
                .setInterpolator(new OvershootInterpolator(1.4f)).start();
    }
}
