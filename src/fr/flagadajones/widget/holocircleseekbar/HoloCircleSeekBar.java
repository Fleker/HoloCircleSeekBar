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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Displays a holo-themed color picker.
 * <p>
 * Use {@link #getColor()} to retrieve the selected color.
 * </p>
 */
public class HoloCircleSeekBar extends View {
	private Drawable mThumb;
	private Drawable mThumbNormal;
	private Drawable mThumbPressed;
	private int mThumbOffset;
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
	 * {@code Paint} instance used to draw the pointer's "halo".
	 */
	private Paint mPointerHaloPaint;

	/**
	 * {@code Paint} instance used to draw the pointer (the selected color).
	 */
	private Paint mPointerColor;

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

	/**
	 * {@code true} if the user clicked on the pointer to start the move mode.
	 * {@code false} once the user stops touching the screen.
	 * 
	 * @see #onTouchEvent(MotionEvent)
	 */
	private boolean mUserIsMovingPointer = false;

	/**
	 * The ARGB value of the currently selected color.
	 */
	private int mColor;

	/**
	 * Number of pixels the origin of this view is moved in X- and Y-direction.
	 * <p>
	 * We use the center of this (quadratic) View as origin of our internal
	 * coordinate system. Android uses the upper left corner as origin for the
	 * View-specific coordinate system. So this is the value we use to translate
	 * from one coordinate system to the other.
	 * </p>
	 * <p>
	 * Note: (Re)calculated in {@link #onMeasure(int, int)}.
	 * </p>
	 * 
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

	/**
	 * The pointer's position expressed as angle (in rad).
	 */
	private float mAngle;
	private Paint textPaint;
	private int value = 0;
	private float valueDegree = 0;

	private int max = 100;
	private int min = 0;
	private String color_attr;
	private int color;
	private SweepGradient s;
	private Paint mArcColor;
	private String wheel_color_attr, wheel_unactive_color_attr,
			pointer_color_attr, pointer_halo_color_attr, text_color_attr;
	private int wheel_color, unactive_wheel_color, pointer_color,
			pointer_halo_color, text_size, text_color, init_position;
	private boolean block_end = false;
	private double lastX;
	private int last_radians = 0;
	private boolean block_start = false;

	private int start_arc = 270;

	private float[] pointerPosition;
	private Paint mColorCenterHalo;
	private RectF mColorCenterHaloRectangle = new RectF();
	private Paint mCircleTextColor;
	private int end_wheel;
	private int rotate_angle;

	private boolean show_text = true;

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
			mThumbOffset = thumb.getIntrinsicWidth() / 2;

			// If we're updating get the new states
			if (needUpdate
					&& (thumb.getIntrinsicWidth() != mThumb.getIntrinsicWidth() || thumb
							.getIntrinsicHeight() != mThumb
							.getIntrinsicHeight())) {
				requestLayout();
			}
		}
		mThumb = thumb;
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

	/*
	 * private void updateThumbPos(int w, int h) { Drawable d =
	 * getCurrentDrawable(); Drawable thumb = mThumb; int thumbHeight = thumb ==
	 * null ? 0 : thumb.getIntrinsicHeight(); // The max height does not
	 * incorporate padding, whereas the height // parameter does int trackHeight
	 * = Math.min(mMaxHeight, h - mPaddingTop - mPaddingBottom);
	 * 
	 * //int max = getMax(); float scale = max > 0 ? (float) getProgress() /
	 * (float) max : 0;
	 * 
	 * if (thumbHeight > trackHeight) { if (thumb != null) { setThumbPos(w,
	 * thumb, scale, 0); } int gapForCenteringTrack = (thumbHeight -
	 * trackHeight) / 2; if (d != null) { // Canvas will be translated by the
	 * padding, so 0,0 is where we start drawing // d.setBounds(0,
	 * gapForCenteringTrack, // w - mPaddingRight - mPaddingLeft, h -
	 * mPaddingBottom - gapForCenteringTrack // - mPaddingTop); } } else { if (d
	 * != null) { // Canvas will be translated by the padding, so 0,0 is where
	 * we start drawing // d.setBounds(0, 0, w - mPaddingRight - mPaddingLeft, h
	 * - mPaddingBottom // - mPaddingTop); } int gap = (trackHeight -
	 * thumbHeight) / 2; if (thumb != null) { setThumbPos(w, thumb, scale, gap);
	 * } } }
	 */
	/*
	 * private void setThumbPos(int w, Drawable thumb, float scale, int gap) {
	 * int available = w - mPaddingLeft - mPaddingRight; int thumbWidth =
	 * thumb.getIntrinsicWidth(); int thumbHeight = thumb.getIntrinsicHeight();
	 * available -= thumbWidth;
	 * 
	 * // The extra space for the thumb to move on the track available +=
	 * mThumbOffset * 2;
	 * 
	 * int thumbPos = (int) (scale * available);
	 * 
	 * int topBound, bottomBound; if (gap == Integer.MIN_VALUE) { Rect oldBounds
	 * = thumb.getBounds(); topBound = oldBounds.top; bottomBound =
	 * oldBounds.bottom; } else { topBound = gap; bottomBound = gap +
	 * thumbHeight; }
	 * 
	 * // Canvas will be translated, so 0,0 is where we start drawing
	 * thumb.setBounds(thumbPos, topBound, thumbPos + thumbWidth, bottomBound);
	 * }
	 */
	public int getThumbOffset() {
		return mThumbOffset;
	}

	public void setThumbOffset(int thumbOffset) {
		mThumbOffset = thumbOffset;
		invalidate();
	}

	public void setRotate(int rotation) {
		rotate_angle = rotation;
		invalidate();
	}

	public void setMaxAngle(int angle) {
		end_wheel = angle;
		if (valueDegree > end_wheel) {

			valueDegree = end_wheel;
value=calculateValueFromAngle(valueDegree);
pointerPosition = calculatePointerPosition((float)Math.toRadians(valueDegree));
		}
		invalidate();
	}

	private void init(AttributeSet attrs, int defStyle) {
		final TypedArray a = getContext().obtainStyledAttributes(attrs,
				R.styleable.HoloCircleSeekBar, defStyle, 0);

		Drawable thumb = a.getDrawable(R.styleable.HoloCircleSeekBar_thumb);
		if (thumb == null) {
			thumb = this.getResources().getDrawable(
					R.drawable.seek_thumb_normal);
		}
		mThumbNormal = thumb;
		setThumb(thumb); // will guess mThumbOffset if thumb != null...
		// ...but allow layout to override this

		thumb = a.getDrawable(R.styleable.HoloCircleSeekBar_thumbPressed);
		if (thumb == null) {
			thumb = this.getResources().getDrawable(
					R.drawable.seek_thumb_pressed);
		}
		mThumbPressed = thumb;
		int thumbOffset = a.getDimensionPixelOffset(
				R.styleable.HoloCircleSeekBar_thumbOffset, getThumbOffset());
		setThumbOffset(thumbOffset);
		initAttributes(a);

		a.recycle();

		mColorWheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mColorWheelPaint.setShader(s);
		mColorWheelPaint.setColor(unactive_wheel_color);
		mColorWheelPaint.setStyle(Paint.Style.STROKE);
		mColorWheelPaint.setStrokeWidth(2/* mColorWheelStrokeWidth/2 */);

		mColorCenterHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
		mColorCenterHalo.setColor(Color.CYAN);
		mColorCenterHalo.setAlpha(0xCC);
		// mColorCenterHalo.setStyle(Paint.Style.STROKE);
		// mColorCenterHalo.setStrokeWidth(mColorCenterHaloRectangle.width() /
		// 2);

		mPointerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPointerHaloPaint.setColor(pointer_halo_color);
		mPointerHaloPaint.setStrokeWidth(mPointerRadius + 10);
		// mPointerHaloPaint.setAlpha(150);

		textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
		textPaint.setColor(text_color);
		textPaint.setStyle(Style.FILL_AND_STROKE);
		textPaint.setTextAlign(Align.LEFT);
		textPaint.setTextSize(text_size);

		mPointerColor = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPointerColor.setStrokeWidth(mPointerRadius);

		mPointerColor.setColor(pointer_color);

		mArcColor = new Paint(Paint.ANTI_ALIAS_FLAG);
		mArcColor.setColor(wheel_color);
		mArcColor.setStyle(Paint.Style.STROKE);
		mArcColor.setStrokeWidth(mColorWheelStrokeWidth);

		mCircleTextColor = new Paint(Paint.ANTI_ALIAS_FLAG);
		mCircleTextColor.setColor(Color.WHITE);
		mCircleTextColor.setStyle(Paint.Style.FILL);

		valueDegree = (float) calculateAngleFromValue(init_position);

		mAngle = calculateRadianFromDegree(valueDegree);
		pointerPosition = calculatePointerPosition(mAngle);
		value = init_position;

		invalidate();
	}

	private void initAttributes(TypedArray a) {
		mColorWheelStrokeWidth = a.getInteger(
				R.styleable.HoloCircleSeekBar_wheel_size, 16);
		mPointerRadius = a.getInteger(
				R.styleable.HoloCircleSeekBar_pointer_size, 48);
		max = a.getInteger(R.styleable.HoloCircleSeekBar_max, 100);
		min = a.getInteger(R.styleable.HoloCircleSeekBar_min, 00);

		color_attr = a.getString(R.styleable.HoloCircleSeekBar_color);
		wheel_color_attr = a
				.getString(R.styleable.HoloCircleSeekBar_wheel_active_color);
		wheel_unactive_color_attr = a
				.getString(R.styleable.HoloCircleSeekBar_wheel_unactive_color);
		pointer_color_attr = a
				.getString(R.styleable.HoloCircleSeekBar_pointer_color);
		pointer_halo_color_attr = a
				.getString(R.styleable.HoloCircleSeekBar_pointer_halo_color);

		text_color_attr = a.getString(R.styleable.HoloCircleSeekBar_text_color);

		text_size = a.getInteger(R.styleable.HoloCircleSeekBar_text_size, 95);

		init_position = a.getInteger(
				R.styleable.HoloCircleSeekBar_init_position, 0);

		start_arc = a.getInteger(R.styleable.HoloCircleSeekBar_start_angle, 0);
		end_wheel = a.getInteger(R.styleable.HoloCircleSeekBar_end_angle, 360);
		rotate_angle = a.getInteger(R.styleable.HoloCircleSeekBar_rotate_angle,
				0);

		show_text = a.getBoolean(R.styleable.HoloCircleSeekBar_show_text, true);

		last_radians = end_wheel;

		if (rotate_angle < 0)
			rotate_angle = rotate_angle + 360;
		if (init_position < min) {
			init_position = min;
		}
		if (init_position > max) {
			init_position = max;
		}

		if (color_attr != null) {
			try {
				color = Color.parseColor(color_attr);
			} catch (IllegalArgumentException e) {
				color = Color.CYAN;
			}
			color = Color.parseColor(color_attr);
		} else {
			color = Color.CYAN;
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
				unactive_wheel_color = Color
						.parseColor(wheel_unactive_color_attr);
			} catch (IllegalArgumentException e) {
				unactive_wheel_color = Color.CYAN;
			}

		} else {
			unactive_wheel_color = Color.CYAN;
		}

		if (pointer_color_attr != null) {
			try {
				pointer_color = Color.parseColor(pointer_color_attr);
			} catch (IllegalArgumentException e) {
				pointer_color = Color.CYAN;
			}

		} else {
			pointer_color = Color.CYAN;
		}

		if (pointer_halo_color_attr != null) {
			try {
				pointer_halo_color = Color.parseColor(pointer_halo_color_attr);
			} catch (IllegalArgumentException e) {
				pointer_halo_color = Color.CYAN;
			}

		} else {
			pointer_halo_color = Color.DKGRAY;
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

		canvas.translate(mTranslationOffset, mTranslationOffset);
		if (rotate_angle != 0)
			canvas.rotate(rotate_angle);
		// Draw the color wheel.
		// canvas.drawOval(mColorWheelRectangle, mColorWheelPaint);
		canvas.drawArc(mColorWheelRectangle, start_arc /* + 270 */, end_wheel
				- (start_arc), false, mColorWheelPaint);

		canvas.drawArc(mColorWheelRectangle, start_arc /* + 270 */,/*
																	 * (
																	 * arc_finish_radians
																	 * ) >
																	 * (end_wheel
																	 * ) ?
																	 * end_wheel
																	 * -
																	 * (start_arc
																	 * ) :
																	 */
				valueDegree - start_arc, false, mArcColor);

		int height = mThumb.getIntrinsicHeight() / 2;
		int width = mThumb.getIntrinsicWidth() / 2;
		mThumb.setBounds((int) pointerPosition[0] - width,
				(int) pointerPosition[1] - height, (int) pointerPosition[0]
						+ width, (int) pointerPosition[1] + height);
		mThumb.draw(canvas);
		// Draw the pointer's "halo"
		// canvas.drawCircle(pointerPosition[0], pointerPosition[1],
		// mPointerRadius, mPointerHaloPaint);

		// Draw the pointer (the currently selected color) slightly smaller on
		// top.

		// canvas.drawCircle(pointerPosition[0], pointerPosition[1], (float)
		// (mPointerRadius / 1.2), mPointerColor);

		if (rotate_angle != 0)
			canvas.rotate(-rotate_angle);
		Rect bounds = new Rect();
		String text = String.valueOf(value);
		textPaint.getTextBounds(text, 0, text.length(), bounds);
		// canvas.drawCircle(mColorWheelRectangle.centerX(),
		// mColorWheelRectangle.centerY(), (bounds.width() / 2) + 5,
		// mCircleTextColor);
		if (show_text)
			canvas.drawText(
					text,
					(mColorWheelRectangle.centerX())
							- (textPaint.measureText(text) / 2),
					mColorWheelRectangle.centerY() + bounds.height() / 2,
					textPaint);

		// last_radians = calculateRadiansFromAngle(mAngle);

	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int height = getDefaultSize(getSuggestedMinimumHeight(),
				heightMeasureSpec);
		int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		int min = Math.min(width, height);
		setMeasuredDimension(min, min);

		mTranslationOffset = min * 0.5f;
		mColorWheelRadius = mTranslationOffset - mPointerRadius;

		mColorWheelRectangle.set(-mColorWheelRadius, -mColorWheelRadius,
				mColorWheelRadius, mColorWheelRadius);

		mColorCenterHaloRectangle.set(-mColorWheelRadius / 2,
				-mColorWheelRadius / 2, mColorWheelRadius / 2,
				mColorWheelRadius / 2);

		pointerPosition = calculatePointerPosition(mAngle);

	}

	private int calculateValueFromAngle(float angle) {
		float m = angle - start_arc;

		float f = (float) ((end_wheel - start_arc) / m);

		return Math.round(max / f);
	}

	private int calculateValueFromStartAngle(float angle) {
		float m = angle;

		float f = (float) ((end_wheel - start_arc) / m);

		return (int) (max / f);
	}

	private double calculateAngleFromValue(int position) {
		// if (position == min || position >= max)
		// return (float) 90;
		//
		// double f = (double) max / (double) position;
		//
		// double f_r = 360 / f;
		//
		// double ang = f_r + 90;
		//
		// return ang;
		return (1.0 * position) / (max - min) * (end_wheel - start_arc);

	}

	private float calculateDegreeFromRadian(float radian) {
		// float unit = (float) (angle / (2 * Math.PI));
		// if (unit < 0) {
		// unit += 1;
		// }
		// int radians = (int) ((unit * 360) - ((360 / 4) * 3));
		// if (radians < 0)
		// radians += 360;
		// return radians;
		float angleD = (float) (180 * (radian) / Math.PI);
		if (angleD < 0)
			angleD += 360;
		return angleD;

	}

	private float calculateRadianFromDegree(float degree) {
		// return (float) (((radians /* + 270 */) * (2 * Math.PI)) / 360);
		return (float) (Math.PI * degree / 180);
	}

	/**
	 * Get the selected value
	 * 
	 * @return the value between 0 and max
	 */
	public int getValue() {
		return value;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Convert coordinates to our internal coordinate system
		double x = event.getX() - mTranslationOffset;
		double y = event.getY() - mTranslationOffset;

		double angleRadian = Math.toRadians(-rotate_angle);
		;
		double cosa = Math.cos(angleRadian);
		double sina = Math.sin(angleRadian);
		// double x = event.getX() * cosa - event.getY() * sina;
		// double y = event.getX() * sina + event.getY() * cosa;
		double oldX = x;
		x = x * cosa - y * sina;
		y = oldX * sina + y * cosa;

		// x = x - mTranslationOffset;
		// y = y - mTranslationOffset;
		float distancePoint=(float)Math.sqrt(x*x+y*y);
		mColorWheelRadius = mTranslationOffset - mPointerRadius;
		if (distancePoint >mColorWheelRadius+mPointerRadius || distancePoint<mColorWheelRadius-mPointerRadius)
			return true;
		
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			mThumb = mThumbPressed;
		case MotionEvent.ACTION_MOVE:

			// Check whether the user pressed on (or near) the pointer
			// mAngle = (float) java.lang.Math.atan2(y, x);
			mAngle = (float) (java.lang.Math.atan2(y, x));

			block_end = false;
			block_start = false;
			mUserIsMovingPointer = true;

			valueDegree = calculateDegreeFromRadian(mAngle);
			if (valueDegree > end_wheel ) {

				valueDegree = end_wheel;
				// else
//				block_end = true;
			}
			if (valueDegree < start_arc) {
				// if ((start_arc <= 10 && (valueDegree > 0 || valueDegree>350))
				// || (start_arc > 10 && valueDegree < start_arc - 10))
				valueDegree = start_arc;
				// else
//				block_start = true;
			}

			
			if (!block_end && !block_start) {
				value = calculateValueFromAngle(valueDegree);
				pointerPosition = calculatePointerPosition((float)Math.toRadians(valueDegree)/*mAngle*/);
				invalidate();
				if (mOnCircleSeekBarChangeListener != null)
					mOnCircleSeekBarChangeListener.onProgressChanged(this,
							value, true);

			}
			break;
		// case MotionEvent.ACTION_MOVE:
		// if (mUserIsMovingPointer) {
		// mAngle = (float) java.lang.Math.atan2(y, x);
		//
		// int radians = calculateDegreeFromRadian(mAngle);
		//
		// if (last_radians > radians && radians < (360 / 6) && x > lastX &&
		// last_radians > (360 / 6)) {
		//
		// if (!block_end && !block_start)
		// block_end = true;
		// // if (block_start)
		// // block_start = false;
		// } else if (last_radians >= start_arc && last_radians <= (360 / 4) &&
		// radians <= (360 - 1)
		// && radians >= ((360 / 4) * 3) && x < lastX) {
		// if (!block_start && !block_end)
		// block_start = true;
		// // if (block_end)
		// // block_end = false;
		//
		// } else if (radians >= end_wheel && !block_start && last_radians <
		// radians) {
		// block_end = true;
		// } else if (radians < end_wheel && block_end && last_radians >
		// end_wheel) {
		// block_end = false;
		// } else if (radians < start_arc && last_radians > radians &&
		// !block_end) {
		// block_start = true;
		// } else if (block_start && last_radians < radians && radians >
		// start_arc && radians < end_wheel) {
		// block_start = false;
		// }
		//
		// if (block_end) {
		//
		// valueDegree = end_wheel - 1;
		// value = max;
		// mAngle = calculateRadianFromDegree(valueDegree);
		// pointerPosition = calculatePointerPosition(mAngle);
		// } else if (block_start) {
		//
		// valueDegree = start_arc;
		// mAngle = calculateRadianFromDegree(valueDegree);
		// value=0;
		// pointerPosition = calculatePointerPosition(mAngle);
		// } else {
		// // text = String.valueOf(calculateTextFromAngle(mAngle));
		// valueDegree = calculateDegreeFromRadian(mAngle);
		// value = calculateValueFromAngle(valueDegree);
		// pointerPosition = calculatePointerPosition(mAngle);
		// }
		// invalidate();
		// if (mOnCircleSeekBarChangeListener != null)
		// mOnCircleSeekBarChangeListener.onProgressChanged(this, value, true);
		//
		// last_radians = radians;
		//
		// }
		// break;
		case MotionEvent.ACTION_UP:
			mThumb = mThumbNormal;
			invalidate();
			mUserIsMovingPointer = false;
			break;
		}
		// Fix scrolling
		if (event.getAction() == MotionEvent.ACTION_MOVE && getParent() != null) {
			getParent().requestDisallowInterceptTouchEvent(true);
		}
		lastX = x;

		return true;
	}

	/**
	 * Calculate the pointer's coordinates on the color wheel using the supplied
	 * angle.
	 * 
	 * @param angle
	 *            The position of the pointer expressed as angle (in rad).
	 * @return The coordinates of the pointer's center in our internal
	 *         coordinate system.
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
		state.putFloat(STATE_ANGLE, mAngle);

		return state;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle savedState = (Bundle) state;

		Parcelable superState = savedState.getParcelable(STATE_PARENT);
		super.onRestoreInstanceState(superState);

		mAngle = savedState.getFloat(STATE_ANGLE);
		valueDegree = calculateDegreeFromRadian(mAngle);
		value = calculateValueFromAngle(valueDegree);
		pointerPosition = calculatePointerPosition(mAngle);

		// mPointerColor.setColor(pointer_color);
	}

	public void setOnSeekBarChangeListener(OnCircleSeekBarChangeListener l) {
		mOnCircleSeekBarChangeListener = l;
	}

	public interface OnCircleSeekBarChangeListener {

		public abstract void onProgressChanged(HoloCircleSeekBar seekBar,
				int progress, boolean fromUser);

	}

}
