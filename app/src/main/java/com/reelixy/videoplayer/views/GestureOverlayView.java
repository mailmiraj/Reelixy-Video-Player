package com.reelixy.videoplayer.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Transparent view placed above the PlayerView to capture all touch gestures:
 * - single tap: toggle controls visibility
 * - double tap left/right third: seek -10s / +10s, middle third: play/pause
 * - vertical drag on left half: brightness, right half: volume
 * - horizontal drag: scrub/seek preview
 *
 * This view does not know about the player or brightness/volume systems —
 * it only reports gesture intents via the Listener so PlayerActivity stays
 * the single place that touches ExoPlayer, WindowManager, and AudioManager.
 */
public class GestureOverlayView extends View {

    public interface Listener {
        void onSingleTap();
        void onDoubleTapLeft();
        void onDoubleTapCenter();
        void onDoubleTapRight();
        void onVerticalSwipeLeft(float deltaY, boolean isStart);   // brightness
        void onVerticalSwipeRight(float deltaY, boolean isStart);  // volume
        void onHorizontalSwipe(float deltaX, boolean isStart);     // seek preview
        void onGestureEnd();
        void onPinchZoom(float scaleFactor, boolean isStart);
    }

    private static final float SWIPE_MIN_DISTANCE_TO_LOCK = 12f; // px, direction lock threshold

    private Listener listener;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private boolean pinchActive = false;

    private float lastX, lastY;
    private boolean verticalGestureActive = false;
    private boolean horizontalGestureActive = false;
    private boolean directionLocked = false;

    public GestureOverlayView(Context context) {
        super(context);
        init(context);
    }

    public GestureOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                pinchActive = true;
                if (listener != null) listener.onPinchZoom(1f, true);
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (listener != null) listener.onPinchZoom(detector.getScaleFactor(), false);
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                pinchActive = false;
                if (listener != null) listener.onGestureEnd();
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onSingleTap();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (listener == null) return true;
                float width = getWidth();
                if (e.getX() < width / 3f) {
                    listener.onDoubleTapLeft();
                } else if (e.getX() > width * 2f / 3f) {
                    listener.onDoubleTapRight();
                } else {
                    listener.onDoubleTapCenter();
                }
                return true;
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        scaleGestureDetector.onTouchEvent(event);
        if (!pinchActive && event.getPointerCount() <= 1) {
            gestureDetector.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureEndPending = false;
                lastX = event.getX();
                lastY = event.getY();
                verticalGestureActive = false;
                horizontalGestureActive = false;
                directionLocked = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() > 1 || pinchActive) break; // pinch owns multi-touch
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;

                if (!directionLocked) {
                    if (Math.abs(dx) > SWIPE_MIN_DISTANCE_TO_LOCK || Math.abs(dy) > SWIPE_MIN_DISTANCE_TO_LOCK) {
                        directionLocked = true;
                        if (Math.abs(dx) > Math.abs(dy)) {
                            horizontalGestureActive = true;
                        } else {
                            verticalGestureActive = true;
                        }
                    }
                }

                if (listener != null) {
                    if (verticalGestureActive) {
                        boolean isStart = !verticalGestureActiveReported;
                        if (event.getX() < getWidth() / 2f) {
                            listener.onVerticalSwipeLeft(dy, isStart);
                        } else {
                            listener.onVerticalSwipeRight(dy, isStart);
                        }
                        verticalGestureActiveReported = true;
                        lastY = event.getY();
                    } else if (horizontalGestureActive) {
                        boolean isStart = !horizontalGestureActiveReported;
                        listener.onHorizontalSwipe(dx, isStart);
                        horizontalGestureActiveReported = true;
                        lastX = event.getX();
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if ((verticalGestureActive || horizontalGestureActive || pinchActive) && listener != null && !gestureEndPending) {
                    gestureEndPending = true;
                    listener.onGestureEnd();
                }
                verticalGestureActive = false;
                horizontalGestureActive = false;
                directionLocked = false;
                verticalGestureActiveReported = false;
                horizontalGestureActiveReported = false;
                break;
        }
        return true;
    }

    private boolean verticalGestureActiveReported = false;
    private boolean horizontalGestureActiveReported = false;
    private boolean gestureEndPending = false;
}
