package com.fencing.scores.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/** Route drags before clickable match boxes and the enclosing pager compete for them. */
public final class KoViewport extends ScrollView {
    private float downX, downY, lastX, lastY;
    private int axis;
    private final int slop;

    public KoViewport(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = lastX = x;
            downY = lastY = y;
            axis = 0;
            getParent().requestDisallowInterceptTouchEvent(true);
            super.dispatchTouchEvent(event);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (axis == 0) {
                float dx = Math.abs(x - downX), dy = Math.abs(y - downY);
                if (Math.max(dx, dy) <= slop) return true;
                axis = dx > dy ? 1 : 2;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
            if (axis == 1) {
                HorizontalScrollView horizontal = (HorizontalScrollView) getChildAt(0);
                int distance = Math.round(lastX - x);
                if (distance != 0) {
                    boolean atEdge = !horizontal.canScrollHorizontally(Integer.signum(distance));
                    horizontal.scrollBy(distance, 0);
                    getParent().requestDisallowInterceptTouchEvent(!atEdge);
                }
            } else {
                getParent().requestDisallowInterceptTouchEvent(true);
                scrollBy(0, Math.round(lastY - y));
            }
            lastX = x;
            lastY = y;
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            getParent().requestDisallowInterceptTouchEvent(false);
            if (axis != 0) return true;
        }
        return super.dispatchTouchEvent(event);
    }
}
