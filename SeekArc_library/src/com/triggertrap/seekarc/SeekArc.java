/*******************************************************************************
 * The MIT License (MIT)
 * 
 * Copyright (c) 2013 Triggertrap Ltd
 * Author Neil Davies
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 ******************************************************************************/
package com.triggertrap.seekarc;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * 
 * SeekArc.java
 * 
 * This is a class that functions much like a SeekBar but
 * follows a circle path instead of a straight line.
 * 
 * @author Neil Davies
 * @author Michael Rubinsky
 * 
 */
public class SeekArc extends View {

	/**
	 * Logger TAG
	 */
	private static final String TAG = SeekArc.class.getSimpleName();

	/**
	 * Invalid value constant
	 */
	private static int INVALID_PROGRESS_VALUE = -1;

	/**
	 * The initial rotational offset -90 means we start at 12 o'clock
	 */
	private final int mAngleOffset = -90;

	/**
	 * The Drawable for the seek arc thumbnail
	 */
	private Drawable mThumb;

	/**
	 * The Maximum value that this SeekArc can be set to
	 */
	private int mMax = 100;
	
	/**
	 * The Current value that the SeekArc is set to
	 */
	private int mProgress = 0;
	
	/**
	 * The Angle to start drawing this arc from
	 */
	private int mStartAngle = 0;
	
	/**
	 * The Angle through which to draw the arc (Max is 360)
	 */
	private int mSweepAngle = 360;
	
	/**
	 * The rotation of the SeekArc- 0 is twelve o'clock
	 */
	private int mRotation = 0;
	
	/**
	 * Give the SeekArc rounded edges
	 */
	private boolean mRoundedEdges = false;
	
	/**
	 * Enable touch inside the SeekArc
	 */
	private boolean mTouchInside = true;

	/**
	 * Enable thumbnail only touch events.
	 * Setting this to false will prevent touch events anywhere
	 * other than directly on the thumbnail.
	 */
	private boolean mThumbnailTouch = false;

    /**
     * Private flag to indicate we are touching the thumbnail.
     */
    private boolean mThumbnailActive = false;

	/**
	 * Will the progress increase clockwise or anti-clockwise
	 */
	private boolean mClockwise = true;

	/**
	 * Allow touch events to immediately set the progress?
	 * If false, progress will only be updatable via drag operations.
	 */
	private boolean mTouchUpdate = true;

    /**
     * Flag to indicate if value can "rollover" or if hard stops at min/max.
     */
    private boolean mRollover = true;

	/**
	 * Flag to indicate if the control is enabled/touchable
 	 */
	private boolean mEnabled = true;

	// Internal variables
	private int mArcRadius = 0;
	private float mProgressSweep = 0;
	private RectF mArcRect = new RectF();
	private Paint mArcPaint;
	private Paint mProgressPaint;
	private int mTranslateX;
	private int mTranslateY;
	private int mThumbXPos;
	private int mThumbYPos;
	private float mTouchIgnoreRadius;
	private OnSeekArcChangeListener mOnSeekArcChangeListener;

	public interface OnSeekArcChangeListener
	{
		/**
		 * Notification that the progress level has changed. Clients can use the
		 * fromUser parameter to distinguish user-initiated changes from those
		 * that occurred programmatically.
		 * 
		 * @param seekArc
		 *            The SeekArc whose progress has changed
		 * @param progress
		 *            The current progress level. This will be in the range
		 *            0..max where max was set by
		 *            {@link SeekArc#setMax(int)}. (The default value for
		 *            max is 100.)
		 * @param fromUser
		 *            True if the progress change was initiated by the user.
		 */
		void onProgressChanged(SeekArc seekArc, int progress, boolean fromUser);

		/**
		 * Notification that the user has started a touch gesture. Clients may
		 * want to use this to disable advancing the seekbar.
		 * 
		 * @param seekArc
		 *            The SeekArc in which the touch gesture began
		 */
		void onStartTrackingTouch(SeekArc seekArc);

		/**
		 * Notification that the user has finished a touch gesture. Clients may
		 * want to use this to re-enable advancing the seekarc.
		 * 
		 * @param seekArc
		 *            The SeekArc in which the touch gesture began
		 */
		void onStopTrackingTouch(SeekArc seekArc);
	}

	public SeekArc(Context context)
	{
		super(context);
		init(context, null, 0);
	}

	public SeekArc(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init(context, attrs, R.attr.seekArcStyle);
	}

	public SeekArc(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init(context, attrs, defStyle);
	}

	private void init(Context context, AttributeSet attrs, int defStyle)
	{
		Log.d(TAG, "Initialising SeekArc");

		final Resources res = getResources();

		// Attribute initialization
		final TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.SeekArc, defStyle, 0);

		// Get the Thumb drawable and prepare it to be drawn.
		Drawable thumb = a.getDrawable(R.styleable.SeekArc_thumb);
		if (thumb == null) {
			mThumb = res.getDrawable(R.drawable.seek_arc_control_selector);
		} else {
			mThumb = thumb;
		}
		int thumbHalfHeight = mThumb.getIntrinsicHeight() / 2;
		int thumbHalfWidth = mThumb.getIntrinsicWidth() / 2;

		// HACK for physically large screens. Looks weird if it's not
		// scaled up.
		if((res.getConfiguration().screenLayout &
			Configuration.SCREENLAYOUT_SIZE_XLARGE) ==
			Configuration.SCREENLAYOUT_SIZE_XLARGE) {
			thumbHalfHeight *= 1.4;
			thumbHalfWidth *= 1.4;
		}

		mThumb.setBounds(
			-thumbHalfWidth, -thumbHalfHeight, thumbHalfWidth, thumbHalfHeight
		);

		// Get the maximum and current progress values.
		mMax = a.getInteger(R.styleable.SeekArc_max, mMax);
		mProgress = a.getInteger(R.styleable.SeekArc_progress, mProgress);

		// Calculate pixel stroke width based on screen density.
		float progressWidth = a.getDimensionPixelSize(
			R.styleable.SeekArc_progressWidth,
			getResources().getDimensionPixelSize(R.dimen.default_stroke)
		);

		float arcWidth = a.getDimensionPixelSize(
			R.styleable.SeekArc_arcWidth,
			getResources().getDimensionPixelSize(R.dimen.default_stroke)
		);

		mStartAngle = a.getInt(R.styleable.SeekArc_startAngle, mStartAngle);
		mSweepAngle = a.getInt(R.styleable.SeekArc_sweepAngle, mSweepAngle);
		mRotation = a.getInt(R.styleable.SeekArc_rotation, mRotation);
		mRoundedEdges = a.getBoolean(R.styleable.SeekArc_roundEdges, mRoundedEdges);
		mTouchInside = a.getBoolean(R.styleable.SeekArc_touchInside, mTouchInside);
		mClockwise = a.getBoolean(R.styleable.SeekArc_clockwise, mClockwise);
		mEnabled = a.getBoolean(R.styleable.SeekArc_enabled, mEnabled);
		mTouchUpdate = a.getBoolean(R.styleable.SeekArc_touchUpdate, mTouchUpdate);
		mThumbnailTouch = a.getBoolean(R.styleable.SeekArc_thumbnailTouch, mThumbnailTouch);
		mRollover = a.getBoolean(R.styleable.SeekArc_rollOver, mRollover);

		mProgress = (mProgress > mMax) ? mMax : mProgress;
		mProgress = (mProgress < 0) ? 0 : mProgress;

		mSweepAngle = (mSweepAngle > 360) ? 360 : mSweepAngle;
		mSweepAngle = (mSweepAngle < 0) ? 0 : mSweepAngle;

		mProgressSweep = (float) mProgress / mMax * mSweepAngle;

		mStartAngle = (mStartAngle > 360) ? 0 : mStartAngle;
		mStartAngle = (mStartAngle < 0) ? 0 : mStartAngle;

		mArcPaint = new Paint();
		mArcPaint.setColor(a.getColor(
			R.styleable.SeekArc_arcColor,
			res.getColor(R.color.progress_gray))
		);
		mArcPaint.setAntiAlias(true);
		mArcPaint.setStyle(Paint.Style.STROKE);
		mArcPaint.setStrokeWidth(arcWidth);

		mProgressPaint = new Paint();
		mProgressPaint.setColor(a.getColor(
			R.styleable.SeekArc_progressColor,
			res.getColor(R.color.default_blue_light))
		);
		mProgressPaint.setAntiAlias(true);
		mProgressPaint.setStyle(Paint.Style.STROKE);
		mProgressPaint.setStrokeWidth(progressWidth);

		if (mRoundedEdges) {
			mArcPaint.setStrokeCap(Paint.Cap.ROUND);
			mProgressPaint.setStrokeCap(Paint.Cap.ROUND);
		}

		a.recycle();
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		if(!mClockwise) {
			canvas.scale(-1, 1, mArcRect.centerX(), mArcRect.centerY() );
		}
		
		// Draw the arcs
		final int arcStart = mStartAngle + mAngleOffset + mRotation;
		final int arcSweep = mSweepAngle;
		canvas.drawArc(mArcRect, arcStart, arcSweep, false, mArcPaint);
		canvas.drawArc(mArcRect, arcStart, mProgressSweep, false, mProgressPaint);

		// Draw the thumb nail
		canvas.translate(mTranslateX - mThumbXPos, mTranslateY - mThumbYPos);
		mThumb.draw(canvas);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		// Calculate the minimum length of a the view dimensions.
		final int height = getDefaultSize(
			getSuggestedMinimumHeight(),
			heightMeasureSpec
		);
		final int width = getDefaultSize(
			getSuggestedMinimumWidth(),
			widthMeasureSpec
		);
		final int min = Math.min(width, height);

		// Calculate the translations for the bounds.
		mTranslateX = (int) (width * 0.5f);
		mTranslateY = (int) (height * 0.5f);

		// Adjust diameter of arc to fit within any padding on the
		// view. Must take screen density into account. Also, try to
		// account for the size of the thumb drawable too so it doesn't get
		// clipped
		int arcDiameter = min - getPaddingLeft() - getPaddingRight()
			- (mThumb.getBounds().height() / 2)
			- (int)mArcPaint.getStrokeWidth();

		mArcRadius = arcDiameter / 2;

		float top = height / 2 - (arcDiameter / 2);
		float left = width / 2 - (arcDiameter / 2);
		mArcRect.set(
			left, top, left + arcDiameter, top + arcDiameter
		);

		int arcStart = (int)mProgressSweep + mStartAngle  + mRotation + 90;
		mThumbXPos = (int) (mArcRadius * Math.cos(Math.toRadians(arcStart)));
		mThumbYPos = (int) (mArcRadius * Math.sin(Math.toRadians(arcStart)));

		setTouchInSide(mTouchInside);
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	private boolean touchIsInThumb(MotionEvent event)
    {
        float x = mTranslateX - event.getX();
        float y = mTranslateY - event.getY();
        Rect bounds = new Rect(
            mThumbXPos - 50,
            mThumbYPos - 50,
            mThumbXPos + mThumb.getBounds().width() + 100,
            mThumbYPos + mThumb.getBounds().height() + 100
        );

        return bounds.contains((int)x, (int)y);
    }

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (mEnabled) {
			this.getParent().requestDisallowInterceptTouchEvent(true);

			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					onStartTrackingTouch();

					// Set the thumb active if we require thumb touch.
					if (this.mThumbnailTouch) {
                        this.mThumbnailActive = this.touchIsInThumb(event);
                    }

                    // Update progress if we allow touch anywhere.
                    if (this.mTouchUpdate) {
                        this.updateOnTouch(event);
                    }
					break;
                case MotionEvent.ACTION_MOVE:
                    updateOnTouch(event);
					break;
				case MotionEvent.ACTION_UP:
					onStopTrackingTouch();
					setPressed(false);
					this.mThumbnailActive = false;
					this.getParent().requestDisallowInterceptTouchEvent(false);
					break;
				case MotionEvent.ACTION_CANCEL:
					onStopTrackingTouch();
					this.mThumbnailActive = false;
					setPressed(false);
					this.getParent().requestDisallowInterceptTouchEvent(false);
					break;
			}
			return true;
		}
		return false;
	}

	@Override
	protected void drawableStateChanged()
	{
		super.drawableStateChanged();
		if (mThumb != null && mThumb.isStateful()) {
			int[] state = getDrawableState();
			mThumb.setState(state);
		}
		invalidate();
	}

	private void onStartTrackingTouch()
	{
		if (mOnSeekArcChangeListener != null) {
			mOnSeekArcChangeListener.onStartTrackingTouch(this);
		}
	}

	private void onStopTrackingTouch()
	{
		if (mOnSeekArcChangeListener != null) {
			mOnSeekArcChangeListener.onStopTrackingTouch(this);
		}
	}

	private void updateOnTouch(MotionEvent event)
	{
		boolean ignoreTouch = ignoreTouch(event.getX(), event.getY());
		if (ignoreTouch) {
			return;
		}

		setPressed(true);
		int progress = getProgressForAngle(
			getTouchDegrees(event.getX(), event.getY())
		);

		if (!mRollover) {
		    if ((mProgress >= mMax * 0.7 && progress <= mMax * .3)) {
		        progress = mMax;
            } else if (mProgress <= mMax * .3 && progress >= mMax * 0.7) {
		        progress = 0;
            }
        }
		onProgressRefresh(progress, true);
	}

	private boolean ignoreTouch(float xPos, float yPos)
	{
		// First see if any of the special flags apply:
        if ((this.mThumbnailTouch && !this.mThumbnailActive) ||
			!this.mEnabled) {
            return true;
        }
	    boolean ignore = false;
		float x = xPos - mTranslateX;
		float y = yPos - mTranslateY;

		float touchRadius = (float) Math.sqrt(((x * x) + (y * y)));
		if (touchRadius < mTouchIgnoreRadius) {
			ignore = true;
		}
		return ignore;
	}

	private double getTouchDegrees(float xPos, float yPos)
	{
		float x = xPos - mTranslateX;
		float y = yPos - mTranslateY;

		// invert the x-coord if we are rotating anti-clockwise
		x = (mClockwise) ? x:-x;

		// convert to arc Angle
		double angle = Math.toDegrees(
			Math.atan2(y, x) + (Math.PI / 2) - Math.toRadians(mRotation)
		);
		if (angle < 0) {
			angle = 360 + angle;
		}
		angle -= mStartAngle;

		return angle;
	}

	private int getProgressForAngle(double angle)
	{
		int touchProgress = (int) Math.round(valuePerDegree() * angle);

		touchProgress = (touchProgress < 0)
			? INVALID_PROGRESS_VALUE
			: touchProgress;
		touchProgress = (touchProgress > mMax)
			? INVALID_PROGRESS_VALUE
			: touchProgress;

		return touchProgress;
	}

	private float valuePerDegree()
	{
		return (float) mMax / mSweepAngle;
	}

	private void onProgressRefresh(int progress, boolean fromUser)
	{
		updateProgress(progress, fromUser);
	}


	private void updateThumbPosition()
	{
		int thumbAngle = (int) (mStartAngle + mProgressSweep + mRotation + 90);
		mThumbXPos = (int) (mArcRadius * Math.cos(Math.toRadians(thumbAngle)));
		mThumbYPos = (int) (mArcRadius * Math.sin(Math.toRadians(thumbAngle)));
	}

	/**
	 * Update the progress value, updating the position of the thumb, and
	 * calling the change listener.
	 *
	 * @param progress  The new progress value.
	 * @param fromUser  True if this is the result of user interaction.
	 */
	private void updateProgress(int progress, boolean fromUser)
	{
		if (progress == INVALID_PROGRESS_VALUE) {
			return;
		}

		progress = (progress > mMax) ? mMax : progress;
		progress = (progress < 0) ? 0 : progress;
		mProgress = progress;

		if (mOnSeekArcChangeListener != null) {
			mOnSeekArcChangeListener
					.onProgressChanged(this, progress, fromUser);
		}

		mProgressSweep = (float) progress / mMax * mSweepAngle;

		updateThumbPosition();

		invalidate();
	}

	/**
	 * Sets a listener to receive notifications of changes to the SeekArc's
	 * progress level. Also provides notifications of when the user starts and
	 * stops a touch gesture within the SeekArc.
	 * 
	 * @param l
	 *            The seek bar notification listener
	 * 
	 * @see SeekArc.OnSeekArcChangeListener
	 */
	public void setOnSeekArcChangeListener(OnSeekArcChangeListener l)
	{
		mOnSeekArcChangeListener = l;
	}

	/**
	 * Manually set the progress value of the control.
	 *
	 * @param progress
	 */
	public void setProgress(int progress)
	{
		updateProgress(progress, true);
	}

	/**
	 * Get the current progress value.
	 *
	 * @return
	 */
	public int getProgress()
	{
		return mProgress;
	}

	/**
	 * Set the width of the progress stroke.
	 *
	 * @param progressWidth  The width in DP.
	 */
	public void setProgressWidth(int progressWidth)
	{
		float ratio = getResources().getDisplayMetrics().density;
		float width = progressWidth * ratio + 0.5f;
		mProgressPaint.setStrokeWidth(width);
	}

	/**
	 * Return the pixel width of the progressArc stroke.
	 *
	 * @return  The pixel width.
	 */
	public float getProgressPixelWidth()
	{
		return mProgressPaint.getStrokeWidth();
	}

	/**
	 * @deprecated Use SeekArc#getProgressPixelWidth()
	 * @return
	 */
	public int getProgressWidth()
	{
		return (int)getProgressPixelWidth();
	}

	/**
	 * Set the width, in DP, of the Arc stroke width.
	 *
	 * @param arcWidth  The width in DP to draw the stroke.
	 */
	public void setArcWidth(int arcWidth)
	{
		float ratio = getResources().getDisplayMetrics().density;
		float width = arcWidth * ratio + 0.5f;
		mArcPaint.setStrokeWidth(width);
	}

	/**
	 * Get the pixel width of the Arc stroke.
	 *
	 * @return  The stroke width in pixels.
	 */
	public float getArcPixelWidth()
	{
		return mArcPaint.getStrokeWidth();
	}

	/**
	 * @deprecated @see SeekArc#getArcPixelWidth()
	 * @return
	 */
	public int getArcWidth()
	{
		return (int)getArcPixelWidth();
	}

	/**
	 * Return the radius of the arc.
	 *
	 */
	public int getArcRadius()
	{
		return mArcRadius;
	}

	/**
	 * Get the arc rotation.
	 *
	 * @return
	 */
	public int getArcRotation()
	{
		return mRotation;
	}

	/**
	 * Set the arc rotation.
	 *
	 * @param mRotation
	 */
	public void setArcRotation(int mRotation)
	{
		this.mRotation = mRotation;
		updateThumbPosition();
	}

	/**
	 * Get the start angle of the arc.
	 *
	 * @return
	 */
	public int getStartAngle()
	{
		return mStartAngle;
	}

	/**
	 * Set the start angle of the arc.
	 *
	 * @param mStartAngle
	 */
	public void setStartAngle(int mStartAngle)
	{
		this.mStartAngle = mStartAngle;
		updateThumbPosition();
	}

	/**
	 * Get the current sweep angle.
	 *
	 * @return
	 */
	public int getSweepAngle()
	{
		return mSweepAngle;
	}

	/**
	 * Set the current sweep angle.
	 *
	 * @param sweepAngle
	 */
	public void setSweepAngle(int sweepAngle)
	{
		this.mSweepAngle = sweepAngle;
		updateThumbPosition();
	}

	/**
	 * Set flag to determine if we display rounded edges.
	 *
	 * @param isEnabled  True if rounded, otherwise false.
	 */
	public void setRoundedEdges(boolean isEnabled)
	{
		mRoundedEdges = isEnabled;
		if (mRoundedEdges) {
			mArcPaint.setStrokeCap(Paint.Cap.ROUND);
			mProgressPaint.setStrokeCap(Paint.Cap.ROUND);
		} else {
			mArcPaint.setStrokeCap(Paint.Cap.SQUARE);
			mProgressPaint.setStrokeCap(Paint.Cap.SQUARE);
		}
	}

	/**
	 * Set the flag to determine if touches anywhere inside the arc are
	 * registered.
	 *
	 * @param isEnabled  If true, touches will be responded to anywhere within
	 *                   the arc. Otherwise, only touches on the thumb control
	 *                	 will be honored.
	 */
	public void setTouchInSide(boolean isEnabled)
	{
		int thumbHalfheight = (int) mThumb.getIntrinsicHeight() / 2;
		int thumbHalfWidth = (int) mThumb.getIntrinsicWidth() / 2;
		mTouchInside = isEnabled;
		if (mTouchInside) {
			mTouchIgnoreRadius = (float) mArcRadius / 4;
		} else {
			// Don't use the exact radius makes interaction too tricky
			mTouchIgnoreRadius = mArcRadius
					- Math.min(thumbHalfWidth, thumbHalfheight);
		}
	}

	/**
	 * Set if the control should act in a clockwise fashion.
	 *
	 * @param isClockwise True if clockwise, otherwise false.
	 */
	public void setClockwise(boolean isClockwise)
	{
		mClockwise = isClockwise;
	}

	/**
	 * Return if this is a clockwise control.
	 *
	 * @return
	 */
	public boolean isClockwise()
	{
		return mClockwise;
	}

	/**
	 * Return the enabled flag.
	 *
	 * @return True if enabled, otherwise false.
	 */
	public boolean isEnabled()
	{
		return mEnabled;
	}

	/**
	 * Set the enabled flag.
	 *
	 * @param enabled  True if enabled, otherwise false.
	 */
	public void setEnabled(boolean enabled)
	{
		this.mEnabled = enabled;
	}

	/**
	 * Get the current progress stroke color.
	 *
	 * @return
	 */
	public int getProgressColor()
	{
		return mProgressPaint.getColor();
	}

	/**
	 * Set the progress color.
	 *
	 * @param color  A color resource identifier.
	 */
	public void setProgressColor(int color)
	{
		mProgressPaint.setColor(color);
		invalidate();
	}

	/**
	 * Return the current arc color.
	 *
	 * @return
	 */
	public int getArcColor()
	{
		return mArcPaint.getColor();
	}

	/**
	 * Set the arc color.
	 *
	 * @param color  A color resource identifier.
	 */
	public void setArcColor(int color)
	{
		mArcPaint.setColor(color);
		invalidate();
	}

	/**
	 * Return the current max value for the progress control.
	 *
	 * @return  The max value.
	 */
	public int getMax()
	{
		return mMax;
	}

	/**
	 * Set the maximum progress value.
	 *
	 * @param mMax  The maximum value for the progress control.
	 */
	public void setMax(int mMax)
	{
		this.mMax = mMax;
	}

	/**
	 * Set the stroke color of the thumb seeker. Thumb drawable must
	 * support the GradientDrawable interface or this call will be ignored.
	 *
	 * @param color  The color value to set the stroke to.
	 * @param width  The width of the stroke, in DP.
	 */
	public void setThumbStroke(@ColorInt int color, int width)
	{
		try {
			float d = getResources().getDisplayMetrics().density;
			int w = (int) (width * d + 0.5f);
			GradientDrawable gd = (GradientDrawable) mThumb;
			gd.setStroke(w, color);
		} catch (ClassCastException e) {
			Log.e(TAG, e.getMessage());
		}
	}

}
