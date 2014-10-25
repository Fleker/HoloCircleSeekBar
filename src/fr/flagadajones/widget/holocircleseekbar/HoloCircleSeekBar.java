/*
 * Copyright 2012 Lars Werkman Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

package fr.flagadajones.widget.holocircleseekbar;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;


public class HoloCircleSeekBar extends View {
    private Drawable mThumb;
    private Drawable mThumbNormal;
    private Drawable mThumbPressed;
    /*
     * Constants used to save/restore the instance state.
     */
    private static final String STATE_PARENT = "parent";
    private static final String STATE_ANGLE = "angle";

    private OnCircleSeekBarChangeListener mOnCircleSeekBarChangeListener;

    /**
     * {@code Paint} instance used to draw the color wheel.
     */
    private Paint mColorWheelPaint;

    /**
     * The stroke width used to paint the color wheel (in pixels).
     */
    private int mColorWheelStrokeWidth;

    /**
     * The radius of the pointer (in pixels).
     */
    private int mPointerRadius;

    /**
     * The rectangle enclosing the color wheel.
     */
    private RectF mColorWheelRectangle = new RectF();
    private Paint mColorCenterHalo;
    private Paint mCircleTextColor;

    private Paint textPaint;
    private Paint mArcColor;

    /**
     * Number of pixels the origin of this view is moved in X- and Y-direction.
     * <p>
     * We use the center of this (quadratic) View as origin of our internal coordinate system. Android uses the upper
     * left corner as origin for the View-specific coordinate system. So this is the value we use to translate from one
     * coordinate system to the other.
     * </p>
     * <p>
     * Note: (Re)calculated in {@link #onMeasure(int, int)}.
     * </p>
     * @see #onDraw(Canvas)
     */
    private float mTranslationOffset;

    /**
     * Radius of the color wheel in pixels.
     * <p>
     * Note: (Re)calculated in {@link #onMeasure(int, int)}.
     * </p>
     */
    private float mColorWheelRadius;

    private int value = 0;
    private float valueDegree = 0;

    private int min = 0;
    private int max = 100;

    private int start_arc = 0;
    private int end_wheel = 360;

    private int rotate_angle = 0;
    private boolean show_text = true;

    private float[] pointerPosition;

    private int wheel_color, unactive_wheel_color, text_size, text_color, init_position;

    public HoloCircleSeekBar(Context context) {
        this(context, null);

    }

    public HoloCircleSeekBar(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.seekBarStyle);

    }

    public HoloCircleSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    public void setThumb(Drawable thumb) {
        boolean needUpdate;
        // This way, calling setThumb again with the same bitmap will result in
        // it recalcuating mThumbOffset (if for example it the bounds of the
        // drawable changed)
        if (mThumb != null && thumb != mThumb) {
            mThumb.setCallback(null);
            needUpdate = true;
        } else {
            needUpdate = false;
        }
        if (thumb != null) {
            thumb.setCallback(this);

            // Assuming the thumb drawable is symmetric, set the thumb offset
            // such that the thumb will hang halfway off either edge of the
            // progress bar.

            // If we're updating get the new states
            if (needUpdate
                    && (thumb.getIntrinsicWidth() != mThumb.getIntrinsicWidth() || thumb.getIntrinsicHeight() != mThumb
                            .getIntrinsicHeight())) {
                requestLayout();
            }
        }
        mThumb = thumb;
        //mPointerRadius = thumb.getIntrinsicWidth() / 2;
        mPointerRadius = 32;
        invalidate();
        if (needUpdate) {
            // updateThumbPos(getWidth(), getHeight());
            if (thumb.isStateful()) {
                // Note that if the states are different this won't work.
                // For now, let's consider that an app bug.
                int[] state = getDrawableState();
                thumb.setState(state);
            }
        }
    }

    public void setRotate(int rotation) {
        rotate_angle = rotation;
        invalidate();
    }

    public void setMaxAngle(int angle) {
        end_wheel = angle;
        if (valueDegree > end_wheel) {

            valueDegree = end_wheel;
            value = calculateValueFromAngle(valueDegree);
            pointerPosition = calculatePointerPosition((float) Math.toRadians(valueDegree));
        }
        invalidate();
    }

    private void init(AttributeSet attrs, int defStyle) {
        final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.HoloCircleSeekBar, defStyle, 0);

        Drawable thumb = a.getDrawable(R.styleable.HoloCircleSeekBar_thumb);
        if (thumb == null) {
            thumb = this.getResources().getDrawable(R.drawable.seek_thumb_normal);
        }
        mThumbNormal = thumb;
        thumb.setBounds(0, 0, 48, 48);
        
        setThumb(thumb);

        thumb = a.getDrawable(R.styleable.HoloCircleSeekBar_thumbPressed);
        if (thumb == null) {
            thumb = this.getResources().getDrawable(R.drawable.seek_thumb_pressed);
        }
        thumb.setBounds(0, 0, 48, 48);
        mThumbPressed = thumb;

        initAttributes(a);

        a.recycle();

        mColorWheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mColorWheelPaint.setColor(unactive_wheel_color);
        mColorWheelPaint.setStyle(Paint.Style.STROKE);
        mColorWheelPaint.setStrokeWidth(2/* mColorWheelStrokeWidth/2 */);

        mColorCenterHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
        mColorCenterHalo.setColor(Color.CYAN);
        mColorCenterHalo.setAlpha(0xCC);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
        textPaint.setColor(text_color);
        textPaint.setStyle(Style.FILL_AND_STROKE);
        textPaint.setTextAlign(Align.LEFT);
        textPaint.setTextSize(text_size);

        mArcColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        mArcColor.setColor(wheel_color);
        mArcColor.setStyle(Paint.Style.STROKE);
        mArcColor.setStrokeWidth(mColorWheelStrokeWidth);

        mCircleTextColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCircleTextColor.setColor(Color.WHITE);
        mCircleTextColor.setStyle(Paint.Style.FILL);

        valueDegree = (float) calculateAngleFromValue(init_position);

        pointerPosition = calculatePointerPosition(calculateRadianFromDegree(valueDegree));
        value = init_position;

        invalidate();
    }

    private void initAttributes(TypedArray a) {
        String wheel_color_attr;
        String wheel_unactive_color_attr;
        String text_color_attr;

        min = a.getInteger(R.styleable.HoloCircleSeekBar_min, 00);
        max = a.getInteger(R.styleable.HoloCircleSeekBar_max, 100);

        start_arc = a.getInteger(R.styleable.HoloCircleSeekBar_start_angle, 0);
        end_wheel = a.getInteger(R.styleable.HoloCircleSeekBar_end_angle, 360);

        rotate_angle = a.getInteger(R.styleable.HoloCircleSeekBar_rotate_angle, 0);

        show_text = a.getBoolean(R.styleable.HoloCircleSeekBar_show_text, true);

        mColorWheelStrokeWidth = a.getInteger(R.styleable.HoloCircleSeekBar_wheel_size, 4);
        wheel_color_attr = a.getString(R.styleable.HoloCircleSeekBar_wheel_active_color);
        wheel_unactive_color_attr = a.getString(R.styleable.HoloCircleSeekBar_wheel_unactive_color);

        text_color_attr = a.getString(R.styleable.HoloCircleSeekBar_text_color);
        text_size = a.getInteger(R.styleable.HoloCircleSeekBar_text_size, 95);

        init_position = a.getInteger(R.styleable.HoloCircleSeekBar_init_position, 0);

        if (rotate_angle < 0)
            rotate_angle = rotate_angle + 360;
        if (init_position < min) {
            init_position = min;
        }
        if (init_position > max) {
            init_position = max;
        }

        if (wheel_color_attr != null) {
            try {
                wheel_color = Color.parseColor(wheel_color_attr);
            } catch (IllegalArgumentException e) {
                wheel_color = Color.DKGRAY;
            }

        } else {
            wheel_color = Color.DKGRAY;
        }
        if (wheel_unactive_color_attr != null) {
            try {
                unactive_wheel_color = Color.parseColor(wheel_unactive_color_attr);
            } catch (IllegalArgumentException e) {
                unactive_wheel_color = Color.CYAN;
            }

        } else {
            unactive_wheel_color = Color.CYAN;
        }

        if (text_color_attr != null) {
            try {
                text_color = Color.parseColor(text_color_attr);
            } catch (IllegalArgumentException e) {
                text_color = Color.CYAN;
            }
        } else {
            text_color = Color.CYAN;
        }

    }

    @Override
    protected void onDraw(Canvas canvas) {
        // All of our positions are using our internal coordinate system.
        // Instead of translating
        // them we let Canvas do the work for us.

        canvas.translate(mTranslationOffset, mTranslationOffset-mPointerRadius);
        if (rotate_angle != 0)
            canvas.rotate(rotate_angle);
        // Draw the color wheel.
        //canvas.drawArc(mColorWheelRectangle, start_arc, end_wheel - (start_arc), false, mColorWheelPaint);
       canvas.drawArc(mColorWheelRectangle, valueDegree, end_wheel - (valueDegree), false, mColorWheelPaint);

        canvas.drawArc(mColorWheelRectangle, start_arc, valueDegree - start_arc, false, mArcColor);
   
        // draw the thumb
        int height =(int)(32 );// mThumb.getIntrinsicHeight() / 2;
        int width = (int)(32 );//mThumb.getIntrinsicWidth() / 2;

        mThumb.setBounds((int) pointerPosition[0] - width, (int) pointerPosition[1] - height, (int) pointerPosition[0]
                + width, (int) pointerPosition[1] + height);
        if (rotate_angle != 0){
        canvas.translate(pointerPosition[0], pointerPosition[1]);
        
            canvas.rotate(rotate_angle);
        canvas.translate(-pointerPosition[0],- pointerPosition[1]);
        }
        mThumb.draw(canvas);

        if (rotate_angle != 0)
            canvas.rotate(-rotate_angle);
        Rect bounds = new Rect();

        if (show_text) {
            String text = String.valueOf(value);
            textPaint.getTextBounds(text, 0, text.length(), bounds);
            canvas.drawText(text, (mColorWheelRectangle.centerX()) - (textPaint.measureText(text) / 2),
                    mColorWheelRectangle.centerY() + bounds.height() / 2, textPaint);
        }
        
     

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
//        int min = Math.min(width, height);
//        setMeasuredDimension(min, min);
        int max = Math.max(width, height);
        setMeasuredDimension(width, height);

        //mTranslationOffset = min * 0.5f;
        mTranslationOffset = max * 0.5f;
        mColorWheelRadius = mTranslationOffset - 2*mPointerRadius;
        //mColorWheelRadius = mTranslationOffset - mPointerRadius;


        mColorWheelRectangle.set(-mColorWheelRadius, -mColorWheelRadius, mColorWheelRadius, mColorWheelRadius);

        pointerPosition = calculatePointerPosition(calculateRadianFromDegree(valueDegree));

    }

    private int calculateValueFromAngle(float angle) {
        float m = angle - start_arc;

        float f = (float) ((end_wheel - start_arc) / m);

        return Math.round(max / f)+min;
    }

    private int calculateValueFromStartAngle(float angle) {
        float m = angle;

        float f = (float) ((end_wheel - start_arc) / m);

        return (int) (max / f)+min;
    }

    private double calculateAngleFromValue(int position) {
        return (1.0 * position) / (max - min) * (end_wheel - start_arc);

    }

    private float calculateDegreeFromRadian(float radian) {
        float angleD = (float) (180 * (radian) / Math.PI);
        if (angleD < 0)
            angleD += 360;
        return angleD;

    }

    private float calculateRadianFromDegree(float degree) {
        return (float) (Math.PI * degree / 180);
    }

    /**
     * Get the selected value
     * @return the value between 0 and max
     */
    public int getValue() {
        return value;
    }

    public void setProgress(int value){

        this.value=value;
        valueDegree = (float) calculateAngleFromValue(value);
        pointerPosition = calculatePointerPosition(calculateRadianFromDegree(valueDegree));
        invalidate();
    }
    public void setMax(int value){
        this.max=value;
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Convert coordinates to our internal coordinate system
        double x = event.getX() - mTranslationOffset;
        double y = event.getY() - mTranslationOffset;

        double angleRadian = Math.toRadians(-rotate_angle);

        double cosa = Math.cos(angleRadian);
        double sina = Math.sin(angleRadian);
        double oldX = x;
        x = x * cosa - y * sina;
        y = oldX * sina + y * cosa;

        float distancePoint = (float) Math.sqrt(x * x + y * y);
//        mColorWheelRadius = mTranslationOffset - mPointerRadius;
        if (distancePoint > mColorWheelRadius + mPointerRadius || distancePoint < mColorWheelRadius - mPointerRadius*2)
            return true;

        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mThumb = mThumbPressed;
        case MotionEvent.ACTION_MOVE:

            valueDegree = calculateDegreeFromRadian((float) (java.lang.Math.atan2(y, x)));
            if (valueDegree > end_wheel) {

                valueDegree = end_wheel;
            }
            if (valueDegree < start_arc) {
                valueDegree = start_arc;
            }

            value = calculateValueFromAngle(valueDegree);
            pointerPosition = calculatePointerPosition((float) Math.toRadians(valueDegree)/* mAngle */);
            invalidate();
            if (mOnCircleSeekBarChangeListener != null)
                mOnCircleSeekBarChangeListener.onProgressChanged(this, value, true);

            break;
        case MotionEvent.ACTION_UP:
            mThumb = mThumbNormal;
            invalidate();
            break;
        }
        // Fix scrolling
        if (event.getAction() == MotionEvent.ACTION_MOVE && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        return true;
    }

    /**
     * Calculate the pointer's coordinates on the color wheel using the supplied angle.
     * @param angle The position of the pointer expressed as angle (in rad).
     * @return The coordinates of the pointer's center in our internal coordinate system.
     */
    private float[] calculatePointerPosition(float angle) {
        // if (calculateRadiansFromAngle(angle) > end_wheel)
        // angle = calculateAngleFromRadians(end_wheel);
        float x = (float) (mColorWheelRadius * Math.cos(angle));
        float y = (float) (mColorWheelRadius * Math.sin(angle));

        return new float[] { x, y };
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        Bundle state = new Bundle();
        state.putParcelable(STATE_PARENT, superState);
        state.putFloat(STATE_ANGLE, valueDegree);

        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        Bundle savedState = (Bundle) state;

        Parcelable superState = savedState.getParcelable(STATE_PARENT);
        super.onRestoreInstanceState(superState);

        valueDegree = savedState.getFloat(STATE_ANGLE);
        // valueDegree = calculateDegreeFromRadian(mAngle);
        value = calculateValueFromAngle(valueDegree);
        pointerPosition = calculatePointerPosition(calculateRadianFromDegree(valueDegree));

        // mPointerColor.setColor(pointer_color);
    }

    public void setOnSeekBarChangeListener(OnCircleSeekBarChangeListener l) {
        mOnCircleSeekBarChangeListener = l;
    }

    public interface OnCircleSeekBarChangeListener {

        public abstract void onProgressChanged(HoloCircleSeekBar seekBar, int progress, boolean fromUser);

    }

}
