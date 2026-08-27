package com.talktranslate.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class FlowLayout extends ViewGroup {

    private final int horizontalSpacing = 16;
    private final int verticalSpacing = 12;

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int childWidthSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        int lineWidth = 0;
        int lineHeight = 0;
        int totalHeight = getPaddingTop() + getPaddingBottom();
        int count = getChildCount();

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            child.measure(childWidthSpec, childHeightSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            if (lineWidth > 0 && lineWidth + horizontalSpacing + childWidth > availableWidth) {
                totalHeight += lineHeight + verticalSpacing;
                lineWidth = childWidth;
                lineHeight = childHeight;
            } else {
                lineWidth += (lineWidth > 0 ? horizontalSpacing : 0) + childWidth;
                lineHeight = Math.max(lineHeight, childHeight);
            }
        }
        totalHeight += lineHeight;

        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, totalHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int availableWidth = r - l - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;
        int count = getChildCount();

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            if (x > getPaddingLeft() && x - getPaddingLeft() + childWidth > availableWidth) {
                x = getPaddingLeft();
                y += lineHeight + verticalSpacing;
                lineHeight = 0;
            }

            child.layout(x, y, x + childWidth, y + childHeight);
            x += childWidth + horizontalSpacing;
            lineHeight = Math.max(lineHeight, childHeight);
        }
    }
}
