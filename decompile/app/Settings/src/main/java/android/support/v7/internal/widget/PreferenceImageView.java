package android.support.v7.internal.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.R$styleable;
import android.util.AttributeSet;
import android.view.View.MeasureSpec;
import android.widget.ImageView;
import com.android.settings.deviceinfo.HwCustMSimSubscriptionStatusTabFragmentImpl;

public class PreferenceImageView extends ImageView {
    private int mMaxHeight;
    private int mMaxWidth;

    public PreferenceImageView(Context context) {
        this(context, null);
    }

    public PreferenceImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreferenceImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mMaxWidth = HwCustMSimSubscriptionStatusTabFragmentImpl.INVALID;
        this.mMaxHeight = HwCustMSimSubscriptionStatusTabFragmentImpl.INVALID;
        TypedArray a = context.obtainStyledAttributes(attrs, R$styleable.PreferenceImageView, defStyleAttr, 0);
        setMaxWidth(a.getDimensionPixelSize(R$styleable.PreferenceImageView_maxWidth, HwCustMSimSubscriptionStatusTabFragmentImpl.INVALID));
        setMaxHeight(a.getDimensionPixelSize(R$styleable.PreferenceImageView_maxHeight, HwCustMSimSubscriptionStatusTabFragmentImpl.INVALID));
        a.recycle();
    }

    public void setMaxWidth(int maxWidth) {
        this.mMaxWidth = maxWidth;
        super.setMaxWidth(maxWidth);
    }

    public int getMaxWidth() {
        return this.mMaxWidth;
    }

    public void setMaxHeight(int maxHeight) {
        this.mMaxHeight = maxHeight;
        super.setMaxHeight(maxHeight);
    }

    public int getMaxHeight() {
        return this.mMaxHeight;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode == Integer.MIN_VALUE || widthMode == 0) {
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            int maxWidth = getMaxWidth();
            if (maxWidth != HwCustMSimSubscriptionStatusTabFragmentImpl.INVALID && (maxWidth < widthSize || widthMode == 0)) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, Integer.MIN_VALUE);
            }
        }
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == Integer.MIN_VALUE || heightMode == 0) {
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            int maxHeight = getMaxHeight();
            if (maxHeight != HwCustMSimSubscriptionStatusTabFragmentImpl.INVALID && (maxHeight < heightSize || heightMode == 0)) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, Integer.MIN_VALUE);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}