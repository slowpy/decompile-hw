package com.android.server.wm;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.MagnificationSpec;
import android.view.Surface;
import android.view.Surface.OutOfResourcesException;
import android.view.SurfaceControl;
import android.view.ViewConfiguration;
import android.view.WindowInfo;
import android.view.WindowManager;
import android.view.WindowManagerInternal.MagnificationCallbacks;
import android.view.WindowManagerInternal.WindowsForAccessibilityCallback;
import android.view.WindowManagerPolicy;
import android.view.animation.DecelerateInterpolator;
import com.android.internal.os.SomeArgs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AccessibilityController {
    private static final float[] sTempFloats = new float[9];
    private DisplayMagnifier mDisplayMagnifier;
    private final WindowManagerService mWindowManagerService;
    private WindowsForAccessibilityObserver mWindowsForAccessibilityObserver;

    private static final class DisplayMagnifier {
        private static final boolean DEBUG_LAYERS = false;
        private static final boolean DEBUG_RECTANGLE_REQUESTED = false;
        private static final boolean DEBUG_ROTATION = false;
        private static final boolean DEBUG_VIEWPORT_WINDOW = false;
        private static final boolean DEBUG_WINDOW_TRANSITIONS = false;
        private static final String LOG_TAG = "WindowManager";
        private final MagnificationCallbacks mCallbacks;
        private final Context mContext;
        private final Handler mHandler;
        private final long mLongAnimationDuration;
        private final MagnifiedViewport mMagnifedViewport;
        private final Rect mTempRect1 = new Rect();
        private final Rect mTempRect2 = new Rect();
        private final Region mTempRegion1 = new Region();
        private final Region mTempRegion2 = new Region();
        private final Region mTempRegion3 = new Region();
        private final Region mTempRegion4 = new Region();
        private final WindowManagerService mWindowManagerService;

        private final class MagnifiedViewport {
            private final float mBorderWidth;
            private final Path mCircularPath;
            private final int mDrawBorderInset;
            private boolean mFullRedrawNeeded;
            private final int mHalfBorderWidth;
            private final Region mMagnificationRegion = new Region();
            private final MagnificationSpec mMagnificationSpec = MagnificationSpec.obtain();
            private final Region mOldMagnificationRegion = new Region();
            private final Matrix mTempMatrix = new Matrix();
            private final Point mTempPoint = new Point();
            private final RectF mTempRectF = new RectF();
            private final SparseArray<WindowState> mTempWindowStates = new SparseArray();
            private final ViewportWindow mWindow;
            private final WindowManager mWindowManager;

            private final class ViewportWindow {
                private static final String SURFACE_TITLE = "Magnification Overlay";
                private int mAlpha;
                private final AnimationController mAnimationController;
                private final Region mBounds = new Region();
                private final Rect mDirtyRect = new Rect();
                private boolean mInvalidated;
                private final Paint mPaint = new Paint();
                private boolean mShown;
                private final Surface mSurface = new Surface();
                private final SurfaceControl mSurfaceControl;

                private final class AnimationController extends Handler {
                    private static final int MAX_ALPHA = 255;
                    private static final int MIN_ALPHA = 0;
                    private static final int MSG_FRAME_SHOWN_STATE_CHANGED = 1;
                    private static final String PROPERTY_NAME_ALPHA = "alpha";
                    private final ValueAnimator mShowHideFrameAnimator;

                    public AnimationController(Context context, Looper looper) {
                        super(looper);
                        this.mShowHideFrameAnimator = ObjectAnimator.ofInt(ViewportWindow.this, PROPERTY_NAME_ALPHA, new int[]{0, 255});
                        long longAnimationDuration = (long) context.getResources().getInteger(17694722);
                        this.mShowHideFrameAnimator.setInterpolator(new DecelerateInterpolator(2.5f));
                        this.mShowHideFrameAnimator.setDuration(longAnimationDuration);
                    }

                    public void onFrameShownStateChanged(boolean shown, boolean animate) {
                        int i;
                        int i2 = 0;
                        if (shown) {
                            i = 1;
                        } else {
                            i = 0;
                        }
                        if (animate) {
                            i2 = 1;
                        }
                        obtainMessage(1, i, i2).sendToTarget();
                    }

                    public void handleMessage(Message message) {
                        boolean animate = true;
                        switch (message.what) {
                            case 1:
                                boolean shown = message.arg1 == 1;
                                if (message.arg2 != 1) {
                                    animate = false;
                                }
                                if (!animate) {
                                    this.mShowHideFrameAnimator.cancel();
                                    if (shown) {
                                        ViewportWindow.this.setAlpha(255);
                                        return;
                                    } else {
                                        ViewportWindow.this.setAlpha(0);
                                        return;
                                    }
                                } else if (this.mShowHideFrameAnimator.isRunning()) {
                                    this.mShowHideFrameAnimator.reverse();
                                    return;
                                } else if (shown) {
                                    this.mShowHideFrameAnimator.start();
                                    return;
                                } else {
                                    this.mShowHideFrameAnimator.reverse();
                                    return;
                                }
                            default:
                                return;
                        }
                    }
                }

                public ViewportWindow(Context context) {
                    SurfaceControl surfaceControl;
                    try {
                        MagnifiedViewport.this.mWindowManager.getDefaultDisplay().getRealSize(MagnifiedViewport.this.mTempPoint);
                        surfaceControl = new SurfaceControl(DisplayMagnifier.this.mWindowManagerService.mFxSession, SURFACE_TITLE, MagnifiedViewport.this.mTempPoint.x, MagnifiedViewport.this.mTempPoint.y, -3, 4);
                    } catch (OutOfResourcesException e) {
                        surfaceControl = null;
                    }
                    this.mSurfaceControl = surfaceControl;
                    this.mSurfaceControl.setLayerStack(MagnifiedViewport.this.mWindowManager.getDefaultDisplay().getLayerStack());
                    this.mSurfaceControl.setLayer(DisplayMagnifier.this.mWindowManagerService.mPolicy.windowTypeToLayerLw(2027) * 10000);
                    this.mSurfaceControl.setPosition(0.0f, 0.0f);
                    this.mSurface.copyFrom(this.mSurfaceControl);
                    this.mAnimationController = new AnimationController(context, DisplayMagnifier.this.mWindowManagerService.mH.getLooper());
                    TypedValue typedValue = new TypedValue();
                    context.getTheme().resolveAttribute(16843664, typedValue, true);
                    int borderColor = context.getColor(typedValue.resourceId);
                    this.mPaint.setStyle(Style.STROKE);
                    this.mPaint.setStrokeWidth(MagnifiedViewport.this.mBorderWidth);
                    this.mPaint.setColor(borderColor);
                    this.mInvalidated = true;
                }

                public void setShown(boolean shown, boolean animate) {
                    synchronized (DisplayMagnifier.this.mWindowManagerService.mWindowMap) {
                        if (this.mShown == shown) {
                            return;
                        }
                        this.mShown = shown;
                        this.mAnimationController.onFrameShownStateChanged(shown, animate);
                    }
                }

                public int getAlpha() {
                    int i;
                    synchronized (DisplayMagnifier.this.mWindowManagerService.mWindowMap) {
                        i = this.mAlpha;
                    }
                    return i;
                }

                public void setAlpha(int alpha) {
                    synchronized (DisplayMagnifier.this.mWindowManagerService.mWindowMap) {
                        if (this.mAlpha == alpha) {
                            return;
                        }
                        this.mAlpha = alpha;
                        invalidate(null);
                    }
                }

                public void setBounds(Region bounds) {
                    synchronized (DisplayMagnifier.this.mWindowManagerService.mWindowMap) {
                        if (this.mBounds.equals(bounds)) {
                            return;
                        }
                        this.mBounds.set(bounds);
                        invalidate(this.mDirtyRect);
                    }
                }

                public void updateSize() {
                    synchronized (DisplayMagnifier.this.mWindowManagerService.mWindowMap) {
                        MagnifiedViewport.this.mWindowManager.getDefaultDisplay().getRealSize(MagnifiedViewport.this.mTempPoint);
                        this.mSurfaceControl.setSize(MagnifiedViewport.this.mTempPoint.x, MagnifiedViewport.this.mTempPoint.y);
                        invalidate(this.mDirtyRect);
                    }
                }

                public void invalidate(Rect dirtyRect) {
                    if (dirtyRect != null) {
                        this.mDirtyRect.set(dirtyRect);
                    } else {
                        this.mDirtyRect.setEmpty();
                    }
                    this.mInvalidated = true;
                    DisplayMagnifier.this.mWindowManagerService.scheduleAnimationLocked();
                }

                /* JADX WARNING: inconsistent code. */
                /* Code decompiled incorrectly, please refer to instructions dump. */
                public void drawIfNeeded() {
                    synchronized (DisplayMagnifier.this.mWindowManagerService.mWindowMap) {
                        if (this.mInvalidated) {
                            this.mInvalidated = false;
                            Canvas canvas = null;
                            try {
                                if (this.mDirtyRect.isEmpty()) {
                                    this.mBounds.getBounds(this.mDirtyRect);
                                }
                                this.mDirtyRect.inset(-MagnifiedViewport.this.mHalfBorderWidth, -MagnifiedViewport.this.mHalfBorderWidth);
                                canvas = this.mSurface.lockCanvas(this.mDirtyRect);
                            } catch (IllegalArgumentException e) {
                            } catch (OutOfResourcesException e2) {
                            }
                            if (canvas == null) {
                                return;
                            }
                            canvas.drawColor(0, Mode.CLEAR);
                            this.mPaint.setAlpha(this.mAlpha);
                            canvas.drawPath(this.mBounds.getBoundaryPath(), this.mPaint);
                            this.mSurface.unlockCanvasAndPost(canvas);
                            if (this.mAlpha > 0) {
                                this.mSurfaceControl.show();
                            } else {
                                this.mSurfaceControl.hide();
                            }
                        }
                    }
                }

                public void releaseSurface() {
                    this.mSurfaceControl.release();
                    this.mSurface.release();
                }
            }

            public MagnifiedViewport() {
                this.mWindowManager = (WindowManager) DisplayMagnifier.this.mContext.getSystemService("window");
                this.mBorderWidth = DisplayMagnifier.this.mContext.getResources().getDimension(17105031);
                this.mHalfBorderWidth = (int) Math.ceil((double) (this.mBorderWidth / 2.0f));
                this.mDrawBorderInset = ((int) this.mBorderWidth) / 2;
                this.mWindow = new ViewportWindow(DisplayMagnifier.this.mContext);
                if (DisplayMagnifier.this.mContext.getResources().getConfiguration().isScreenRound()) {
                    this.mCircularPath = new Path();
                    this.mWindowManager.getDefaultDisplay().getRealSize(this.mTempPoint);
                    int centerXY = this.mTempPoint.x / 2;
                    this.mCircularPath.addCircle((float) centerXY, (float) centerXY, (float) centerXY, Direction.CW);
                } else {
                    this.mCircularPath = null;
                }
                recomputeBoundsLocked();
            }

            public void getMagnificationRegionLocked(Region outMagnificationRegion) {
                outMagnificationRegion.set(this.mMagnificationRegion);
            }

            public void updateMagnificationSpecLocked(MagnificationSpec spec) {
                if (spec != null) {
                    this.mMagnificationSpec.initialize(spec.scale, spec.offsetX, spec.offsetY);
                } else {
                    this.mMagnificationSpec.clear();
                }
                if (!DisplayMagnifier.this.mHandler.hasMessages(5)) {
                    setMagnifiedRegionBorderShownLocked(isMagnifyingLocked(), true);
                }
            }

            public void recomputeBoundsLocked() {
                this.mWindowManager.getDefaultDisplay().getRealSize(this.mTempPoint);
                int screenWidth = this.mTempPoint.x;
                int screenHeight = this.mTempPoint.y;
                this.mMagnificationRegion.set(0, 0, 0, 0);
                Region availableBounds = DisplayMagnifier.this.mTempRegion1;
                availableBounds.set(0, 0, screenWidth, screenHeight);
                if (this.mCircularPath != null) {
                    availableBounds.setPath(this.mCircularPath, availableBounds);
                }
                Region nonMagnifiedBounds = DisplayMagnifier.this.mTempRegion4;
                nonMagnifiedBounds.set(0, 0, 0, 0);
                SparseArray<WindowState> visibleWindows = this.mTempWindowStates;
                visibleWindows.clear();
                populateWindowsOnScreenLocked(visibleWindows);
                for (int i = visibleWindows.size() - 1; i >= 0; i--) {
                    WindowState windowState = (WindowState) visibleWindows.valueAt(i);
                    if (windowState.mAttrs.type != 2027) {
                        Matrix matrix = this.mTempMatrix;
                        AccessibilityController.populateTransformationMatrixLocked(windowState, matrix);
                        Region touchableRegion = DisplayMagnifier.this.mTempRegion3;
                        windowState.getTouchableRegion(touchableRegion);
                        Rect touchableFrame = DisplayMagnifier.this.mTempRect1;
                        touchableRegion.getBounds(touchableFrame);
                        RectF windowFrame = this.mTempRectF;
                        windowFrame.set(touchableFrame);
                        windowFrame.offset((float) (-windowState.mFrame.left), (float) (-windowState.mFrame.top));
                        matrix.mapRect(windowFrame);
                        Region windowBounds = DisplayMagnifier.this.mTempRegion2;
                        windowBounds.set((int) windowFrame.left, (int) windowFrame.top, (int) windowFrame.right, (int) windowFrame.bottom);
                        Region portionOfWindowAlreadyAccountedFor = DisplayMagnifier.this.mTempRegion3;
                        portionOfWindowAlreadyAccountedFor.set(this.mMagnificationRegion);
                        portionOfWindowAlreadyAccountedFor.op(nonMagnifiedBounds, Op.UNION);
                        windowBounds.op(portionOfWindowAlreadyAccountedFor, Op.DIFFERENCE);
                        if (DisplayMagnifier.this.mWindowManagerService.mPolicy.canMagnifyWindow(windowState.mAttrs.type)) {
                            this.mMagnificationRegion.op(windowBounds, Op.UNION);
                            this.mMagnificationRegion.op(availableBounds, Op.INTERSECT);
                        } else {
                            nonMagnifiedBounds.op(windowBounds, Op.UNION);
                            availableBounds.op(windowBounds, Op.DIFFERENCE);
                        }
                        Region accountedBounds = DisplayMagnifier.this.mTempRegion2;
                        accountedBounds.set(this.mMagnificationRegion);
                        accountedBounds.op(nonMagnifiedBounds, Op.UNION);
                        accountedBounds.op(0, 0, screenWidth, screenHeight, Op.INTERSECT);
                        if (accountedBounds.isRect()) {
                            Rect accountedFrame = DisplayMagnifier.this.mTempRect1;
                            accountedBounds.getBounds(accountedFrame);
                            if (accountedFrame.width() == screenWidth && accountedFrame.height() == screenHeight) {
                                break;
                            }
                        }
                        continue;
                    }
                }
                visibleWindows.clear();
                this.mMagnificationRegion.op(this.mDrawBorderInset, this.mDrawBorderInset, screenWidth - this.mDrawBorderInset, screenHeight - this.mDrawBorderInset, Op.INTERSECT);
                if (!this.mOldMagnificationRegion.equals(this.mMagnificationRegion)) {
                    this.mWindow.setBounds(this.mMagnificationRegion);
                    Rect dirtyRect = DisplayMagnifier.this.mTempRect1;
                    if (this.mFullRedrawNeeded) {
                        this.mFullRedrawNeeded = false;
                        dirtyRect.set(this.mDrawBorderInset, this.mDrawBorderInset, screenWidth - this.mDrawBorderInset, screenHeight - this.mDrawBorderInset);
                        this.mWindow.invalidate(dirtyRect);
                    } else {
                        Region dirtyRegion = DisplayMagnifier.this.mTempRegion3;
                        dirtyRegion.set(this.mMagnificationRegion);
                        dirtyRegion.op(this.mOldMagnificationRegion, Op.UNION);
                        dirtyRegion.op(nonMagnifiedBounds, Op.INTERSECT);
                        dirtyRegion.getBounds(dirtyRect);
                        this.mWindow.invalidate(dirtyRect);
                    }
                    this.mOldMagnificationRegion.set(this.mMagnificationRegion);
                    SomeArgs args = SomeArgs.obtain();
                    args.arg1 = Region.obtain(this.mMagnificationRegion);
                    DisplayMagnifier.this.mHandler.obtainMessage(1, args).sendToTarget();
                }
            }

            public void onRotationChangedLocked() {
                if (isMagnifyingLocked()) {
                    setMagnifiedRegionBorderShownLocked(false, false);
                    long delay = (long) (((float) DisplayMagnifier.this.mLongAnimationDuration) * DisplayMagnifier.this.mWindowManagerService.getWindowAnimationScaleLocked());
                    DisplayMagnifier.this.mHandler.sendMessageDelayed(DisplayMagnifier.this.mHandler.obtainMessage(5), delay);
                }
                recomputeBoundsLocked();
                this.mWindow.updateSize();
            }

            public void setMagnifiedRegionBorderShownLocked(boolean shown, boolean animate) {
                if (shown) {
                    this.mFullRedrawNeeded = true;
                    this.mOldMagnificationRegion.set(0, 0, 0, 0);
                }
                this.mWindow.setShown(shown, animate);
            }

            public void getMagnifiedFrameInContentCoordsLocked(Rect rect) {
                MagnificationSpec spec = this.mMagnificationSpec;
                this.mMagnificationRegion.getBounds(rect);
                rect.offset((int) (-spec.offsetX), (int) (-spec.offsetY));
                rect.scale(1.0f / spec.scale);
            }

            public boolean isMagnifyingLocked() {
                return this.mMagnificationSpec.scale > 1.0f;
            }

            public MagnificationSpec getMagnificationSpecLocked() {
                return this.mMagnificationSpec;
            }

            public void drawWindowIfNeededLocked() {
                recomputeBoundsLocked();
                this.mWindow.drawIfNeeded();
            }

            public void destroyWindow() {
                this.mWindow.releaseSurface();
            }

            private void populateWindowsOnScreenLocked(SparseArray<WindowState> outWindows) {
                WindowList windowList = DisplayMagnifier.this.mWindowManagerService.getDefaultDisplayContentLocked().getWindowList();
                int windowCount = windowList.size();
                for (int i = 0; i < windowCount; i++) {
                    WindowState windowState = (WindowState) windowList.get(i);
                    if (windowState.isOnScreen() && !windowState.mWinAnimator.mEnterAnimationPending) {
                        outWindows.put(windowState.mLayer, windowState);
                    }
                }
            }
        }

        private class MyHandler extends Handler {
            public static final int MESSAGE_NOTIFY_MAGNIFICATION_REGION_CHANGED = 1;
            public static final int MESSAGE_NOTIFY_RECTANGLE_ON_SCREEN_REQUESTED = 2;
            public static final int MESSAGE_NOTIFY_ROTATION_CHANGED = 4;
            public static final int MESSAGE_NOTIFY_USER_CONTEXT_CHANGED = 3;
            public static final int MESSAGE_SHOW_MAGNIFIED_REGION_BOUNDS_IF_NEEDED = 5;

            public MyHandler(Looper looper) {
                super(looper);
            }

            public void handleMessage(Message message) {
                switch (message.what) {
                    case 1:
                        Region magnifiedBounds = message.obj.arg1;
                        DisplayMagnifier.this.mCallbacks.onMagnificationRegionChanged(magnifiedBounds);
                        magnifiedBounds.recycle();
                        return;
                    case 2:
                        SomeArgs args = (SomeArgs) message.obj;
                        DisplayMagnifier.this.mCallbacks.onRectangleOnScreenRequested(args.argi1, args.argi2, args.argi3, args.argi4);
                        args.recycle();
                        return;
                    case 3:
                        DisplayMagnifier.this.mCallbacks.onUserContextChanged();
                        return;
                    case 4:
                        DisplayMagnifier.this.mCallbacks.onRotationChanged(message.arg1);
                        return;
                    case 5:
                        synchronized (DisplayMagnifier.this.mWindowManagerService.mWindowMap) {
                            if (DisplayMagnifier.this.mMagnifedViewport.isMagnifyingLocked()) {
                                DisplayMagnifier.this.mMagnifedViewport.setMagnifiedRegionBorderShownLocked(true, true);
                                DisplayMagnifier.this.mWindowManagerService.scheduleAnimationLocked();
                            }
                        }
                        return;
                    default:
                        return;
                }
            }
        }

        public DisplayMagnifier(WindowManagerService windowManagerService, MagnificationCallbacks callbacks) {
            this.mContext = windowManagerService.mContext;
            this.mWindowManagerService = windowManagerService;
            this.mCallbacks = callbacks;
            this.mHandler = new MyHandler(this.mWindowManagerService.mH.getLooper());
            this.mMagnifedViewport = new MagnifiedViewport();
            this.mLongAnimationDuration = (long) this.mContext.getResources().getInteger(17694722);
        }

        public void setMagnificationSpecLocked(MagnificationSpec spec) {
            this.mMagnifedViewport.updateMagnificationSpecLocked(spec);
            this.mMagnifedViewport.recomputeBoundsLocked();
            this.mWindowManagerService.scheduleAnimationLocked();
        }

        public void onRectangleOnScreenRequestedLocked(Rect rectangle) {
            if (this.mMagnifedViewport.isMagnifyingLocked()) {
                Rect magnifiedRegionBounds = this.mTempRect2;
                this.mMagnifedViewport.getMagnifiedFrameInContentCoordsLocked(magnifiedRegionBounds);
                if (!magnifiedRegionBounds.contains(rectangle)) {
                    SomeArgs args = SomeArgs.obtain();
                    args.argi1 = rectangle.left;
                    args.argi2 = rectangle.top;
                    args.argi3 = rectangle.right;
                    args.argi4 = rectangle.bottom;
                    this.mHandler.obtainMessage(2, args).sendToTarget();
                }
            }
        }

        public void onWindowLayersChangedLocked() {
            this.mMagnifedViewport.recomputeBoundsLocked();
            this.mWindowManagerService.scheduleAnimationLocked();
        }

        public void onRotationChangedLocked(DisplayContent displayContent, int rotation) {
            this.mMagnifedViewport.onRotationChangedLocked();
            this.mHandler.sendEmptyMessage(4);
        }

        public void onAppWindowTransitionLocked(WindowState windowState, int transition) {
            if (this.mMagnifedViewport.isMagnifyingLocked()) {
                switch (transition) {
                    case 6:
                    case 8:
                    case 10:
                    case 12:
                    case 13:
                    case 14:
                        this.mHandler.sendEmptyMessage(3);
                        return;
                    default:
                        return;
                }
            }
        }

        public void onWindowTransitionLocked(WindowState windowState, int transition) {
            boolean magnifying = this.mMagnifedViewport.isMagnifyingLocked();
            int type = windowState.mAttrs.type;
            switch (transition) {
                case 1:
                case 3:
                    if (magnifying) {
                        switch (type) {
                            case 2:
                            case 1000:
                            case 1001:
                            case 1002:
                            case 1003:
                            case 1005:
                            case 2001:
                            case 2002:
                            case 2003:
                            case 2005:
                            case 2006:
                            case 2007:
                            case 2008:
                            case 2009:
                            case 2010:
                            case 2020:
                            case 2024:
                            case 2035:
                                Rect magnifiedRegionBounds = this.mTempRect2;
                                this.mMagnifedViewport.getMagnifiedFrameInContentCoordsLocked(magnifiedRegionBounds);
                                Rect touchableRegionBounds = this.mTempRect1;
                                windowState.getTouchableRegion(this.mTempRegion1);
                                this.mTempRegion1.getBounds(touchableRegionBounds);
                                if (!magnifiedRegionBounds.intersect(touchableRegionBounds)) {
                                    this.mCallbacks.onRectangleOnScreenRequested(touchableRegionBounds.left, touchableRegionBounds.top, touchableRegionBounds.right, touchableRegionBounds.bottom);
                                    return;
                                }
                                return;
                            default:
                                return;
                        }
                    }
                    return;
                default:
                    return;
            }
        }

        public MagnificationSpec getMagnificationSpecForWindowLocked(WindowState windowState) {
            MagnificationSpec spec = this.mMagnifedViewport.getMagnificationSpecLocked();
            if (!(spec == null || spec.isNop())) {
                WindowManagerPolicy policy = this.mWindowManagerService.mPolicy;
                int windowType = windowState.mAttrs.type;
                if ((policy.isTopLevelWindow(windowType) || windowState.mAttachedWindow == null || policy.canMagnifyWindow(windowType)) && policy.canMagnifyWindow(windowState.mAttrs.type)) {
                    return spec;
                }
                return null;
            }
            return spec;
        }

        public void getMagnificationRegionLocked(Region outMagnificationRegion) {
            this.mMagnifedViewport.getMagnificationRegionLocked(outMagnificationRegion);
        }

        public void destroyLocked() {
            this.mMagnifedViewport.destroyWindow();
        }

        public void drawMagnifiedRegionBorderIfNeededLocked() {
            this.mMagnifedViewport.drawWindowIfNeededLocked();
        }
    }

    private static final class WindowsForAccessibilityObserver {
        private static final boolean DEBUG = false;
        private static final String LOG_TAG = "WindowManager";
        private final WindowsForAccessibilityCallback mCallback;
        private final Context mContext;
        private final Handler mHandler;
        private final List<WindowInfo> mOldWindows = new ArrayList();
        private final long mRecurringAccessibilityEventsIntervalMillis;
        private final Set<IBinder> mTempBinderSet = new ArraySet();
        private final Matrix mTempMatrix = new Matrix();
        private final Point mTempPoint = new Point();
        private final Rect mTempRect = new Rect();
        private final RectF mTempRectF = new RectF();
        private final Region mTempRegion = new Region();
        private final Region mTempRegion1 = new Region();
        private final SparseArray<WindowState> mTempWindowStates = new SparseArray();
        private final WindowManagerService mWindowManagerService;

        private class MyHandler extends Handler {
            public static final int MESSAGE_COMPUTE_CHANGED_WINDOWS = 1;

            public MyHandler(Looper looper) {
                super(looper, null, false);
            }

            public void handleMessage(Message message) {
                switch (message.what) {
                    case 1:
                        WindowsForAccessibilityObserver.this.computeChangedWindows();
                        return;
                    default:
                        return;
                }
            }
        }

        public WindowsForAccessibilityObserver(WindowManagerService windowManagerService, WindowsForAccessibilityCallback callback) {
            this.mContext = windowManagerService.mContext;
            this.mWindowManagerService = windowManagerService;
            this.mCallback = callback;
            this.mHandler = new MyHandler(this.mWindowManagerService.mH.getLooper());
            this.mRecurringAccessibilityEventsIntervalMillis = ViewConfiguration.getSendRecurringAccessibilityEventsInterval();
            computeChangedWindows();
        }

        public void performComputeChangedWindowsNotLocked() {
            this.mHandler.removeMessages(1);
            computeChangedWindows();
        }

        public void scheduleComputeChangedWindowsLocked() {
            if (!this.mHandler.hasMessages(1)) {
                this.mHandler.sendEmptyMessageDelayed(1, this.mRecurringAccessibilityEventsIntervalMillis);
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void computeChangedWindows() {
            boolean windowsChanged = false;
            List<WindowInfo> windows = new ArrayList();
            synchronized (this.mWindowManagerService.mWindowMap) {
                if (this.mWindowManagerService.mCurrentFocus == null) {
                    return;
                }
                int i;
                ((WindowManager) this.mContext.getSystemService("window")).getDefaultDisplay().getRealSize(this.mTempPoint);
                int screenWidth = this.mTempPoint.x;
                int screenHeight = this.mTempPoint.y;
                Region unaccountedSpace = this.mTempRegion;
                unaccountedSpace.set(0, 0, screenWidth, screenHeight);
                SparseArray<WindowState> visibleWindows = this.mTempWindowStates;
                populateVisibleWindowsOnScreenLocked(visibleWindows);
                Set<IBinder> addedWindows = this.mTempBinderSet;
                addedWindows.clear();
                boolean focusedWindowAdded = false;
                int visibleWindowCount = visibleWindows.size();
                HashSet<Integer> skipRemainingWindowsForTasks = new HashSet();
                for (i = visibleWindowCount - 1; i >= 0; i--) {
                    Rect boundsInScreen;
                    WindowState windowState = (WindowState) visibleWindows.valueAt(i);
                    int flags = windowState.mAttrs.flags;
                    Task task = windowState.getTask();
                    if ((task == null || !skipRemainingWindowsForTasks.contains(Integer.valueOf(task.mTaskId))) && (flags & 16) == 0) {
                        boundsInScreen = this.mTempRect;
                        computeWindowBoundsInScreen(windowState, boundsInScreen);
                        if (!unaccountedSpace.quickReject(boundsInScreen)) {
                            if (isReportedWindowType(windowState.mAttrs.type)) {
                                WindowInfo window = obtainPopulatedWindowInfo(windowState, boundsInScreen);
                                addedWindows.add(window.token);
                                windows.add(window);
                                if (windowState.isFocused()) {
                                    focusedWindowAdded = true;
                                }
                            }
                            if (windowState.mAttrs.type != 2032) {
                                unaccountedSpace.op(boundsInScreen, unaccountedSpace, Op.REVERSE_DIFFERENCE);
                            }
                            if (!unaccountedSpace.isEmpty()) {
                                if ((flags & 40) == 0) {
                                    if (task == null) {
                                        break;
                                    }
                                    skipRemainingWindowsForTasks.add(Integer.valueOf(task.mTaskId));
                                } else {
                                    continue;
                                }
                            } else {
                                break;
                            }
                        }
                        continue;
                    }
                }
                if (!focusedWindowAdded) {
                    for (i = visibleWindowCount - 1; i >= 0; i--) {
                        windowState = (WindowState) visibleWindows.valueAt(i);
                        if (windowState.isFocused()) {
                            boundsInScreen = this.mTempRect;
                            computeWindowBoundsInScreen(windowState, boundsInScreen);
                            window = obtainPopulatedWindowInfo(windowState, boundsInScreen);
                            addedWindows.add(window.token);
                            windows.add(window);
                            break;
                        }
                    }
                }
                int windowCount = windows.size();
                for (i = 0; i < windowCount; i++) {
                    window = (WindowInfo) windows.get(i);
                    if (!addedWindows.contains(window.parentToken)) {
                        window.parentToken = null;
                    }
                    if (window.childTokens != null) {
                        for (int j = window.childTokens.size() - 1; j >= 0; j--) {
                            if (!addedWindows.contains(window.childTokens.get(j))) {
                                window.childTokens.remove(j);
                            }
                        }
                    }
                }
                visibleWindows.clear();
                addedWindows.clear();
                if (this.mOldWindows.size() != windows.size()) {
                    windowsChanged = true;
                } else if (!this.mOldWindows.isEmpty() || !windows.isEmpty()) {
                    for (i = 0; i < windowCount; i++) {
                        if (windowChangedNoLayer((WindowInfo) this.mOldWindows.get(i), (WindowInfo) windows.get(i))) {
                            windowsChanged = true;
                            break;
                        }
                    }
                }
                if (windowsChanged) {
                    cacheWindows(windows);
                }
            }
        }

        private void computeWindowBoundsInScreen(WindowState windowState, Rect outBounds) {
            Region touchableRegion = this.mTempRegion1;
            windowState.getTouchableRegion(touchableRegion);
            Rect touchableFrame = this.mTempRect;
            touchableRegion.getBounds(touchableFrame);
            RectF windowFrame = this.mTempRectF;
            windowFrame.set(touchableFrame);
            windowFrame.offset((float) (-windowState.mFrame.left), (float) (-windowState.mFrame.top));
            Matrix matrix = this.mTempMatrix;
            AccessibilityController.populateTransformationMatrixLocked(windowState, matrix);
            matrix.mapRect(windowFrame);
            outBounds.set((int) windowFrame.left, (int) windowFrame.top, (int) windowFrame.right, (int) windowFrame.bottom);
        }

        private static WindowInfo obtainPopulatedWindowInfo(WindowState windowState, Rect boundsInScreen) {
            WindowInfo window = WindowInfo.obtain();
            window.type = windowState.mAttrs.type;
            window.layer = windowState.mLayer;
            window.token = windowState.mClient.asBinder();
            window.title = windowState.mAttrs.accessibilityTitle;
            window.accessibilityIdOfAnchor = windowState.mAttrs.accessibilityIdOfAnchor;
            WindowState attachedWindow = windowState.mAttachedWindow;
            if (attachedWindow != null) {
                window.parentToken = attachedWindow.mClient.asBinder();
            }
            window.focused = windowState.isFocused();
            window.boundsInScreen.set(boundsInScreen);
            int childCount = windowState.mChildWindows.size();
            if (childCount > 0) {
                if (window.childTokens == null) {
                    window.childTokens = new ArrayList();
                }
                for (int j = 0; j < childCount; j++) {
                    window.childTokens.add(((WindowState) windowState.mChildWindows.get(j)).mClient.asBinder());
                }
            }
            return window;
        }

        private void cacheWindows(List<WindowInfo> windows) {
            int i;
            for (i = this.mOldWindows.size() - 1; i >= 0; i--) {
                ((WindowInfo) this.mOldWindows.remove(i)).recycle();
            }
            int newWindowCount = windows.size();
            for (i = 0; i < newWindowCount; i++) {
                this.mOldWindows.add(WindowInfo.obtain((WindowInfo) windows.get(i)));
            }
        }

        private boolean windowChangedNoLayer(WindowInfo oldWindow, WindowInfo newWindow) {
            if (oldWindow == newWindow) {
                return false;
            }
            if (oldWindow == null || newWindow == null || oldWindow.type != newWindow.type || oldWindow.focused != newWindow.focused) {
                return true;
            }
            if (oldWindow.token == null) {
                if (newWindow.token != null) {
                    return true;
                }
            } else if (!oldWindow.token.equals(newWindow.token)) {
                return true;
            }
            if (oldWindow.parentToken == null) {
                if (newWindow.parentToken != null) {
                    return true;
                }
            } else if (!oldWindow.parentToken.equals(newWindow.parentToken)) {
                return true;
            }
            if (oldWindow.boundsInScreen.equals(newWindow.boundsInScreen)) {
                return ((oldWindow.childTokens == null || newWindow.childTokens == null || oldWindow.childTokens.equals(newWindow.childTokens)) && TextUtils.equals(oldWindow.title, newWindow.title) && oldWindow.accessibilityIdOfAnchor == newWindow.accessibilityIdOfAnchor) ? false : true;
            } else {
                return true;
            }
        }

        private static void clearAndRecycleWindows(List<WindowInfo> windows) {
            for (int i = windows.size() - 1; i >= 0; i--) {
                ((WindowInfo) windows.remove(i)).recycle();
            }
        }

        private static boolean isReportedWindowType(int windowType) {
            return (windowType == 2029 || windowType == 2013 || windowType == 2021 || windowType == 2026 || windowType == 2016 || windowType == 2022 || windowType == 2018 || windowType == 2027 || windowType == 1004 || windowType == 2015 || windowType == 2030) ? false : true;
        }

        private void populateVisibleWindowsOnScreenLocked(SparseArray<WindowState> outWindows) {
            WindowList windowList = this.mWindowManagerService.getDefaultDisplayContentLocked().getWindowList();
            int windowCount = windowList.size();
            for (int i = 0; i < windowCount; i++) {
                WindowState windowState = (WindowState) windowList.get(i);
                if (windowState.isVisibleLw()) {
                    outWindows.put(windowState.mLayer, windowState);
                }
            }
        }
    }

    public AccessibilityController(WindowManagerService service) {
        this.mWindowManagerService = service;
    }

    public void setMagnificationCallbacksLocked(MagnificationCallbacks callbacks) {
        if (callbacks != null) {
            if (this.mDisplayMagnifier != null) {
                throw new IllegalStateException("Magnification callbacks already set!");
            }
            this.mDisplayMagnifier = new DisplayMagnifier(this.mWindowManagerService, callbacks);
        } else if (this.mDisplayMagnifier == null) {
            throw new IllegalStateException("Magnification callbacks already cleared!");
        } else {
            this.mDisplayMagnifier.destroyLocked();
            this.mDisplayMagnifier = null;
        }
    }

    public void setWindowsForAccessibilityCallback(WindowsForAccessibilityCallback callback) {
        if (callback != null) {
            if (this.mWindowsForAccessibilityObserver != null) {
                throw new IllegalStateException("Windows for accessibility callback already set!");
            }
            this.mWindowsForAccessibilityObserver = new WindowsForAccessibilityObserver(this.mWindowManagerService, callback);
        } else if (this.mWindowsForAccessibilityObserver == null) {
            throw new IllegalStateException("Windows for accessibility callback already cleared!");
        } else {
            this.mWindowsForAccessibilityObserver = null;
        }
    }

    public void setMagnificationSpecLocked(MagnificationSpec spec) {
        if (this.mDisplayMagnifier != null) {
            this.mDisplayMagnifier.setMagnificationSpecLocked(spec);
        }
        if (this.mWindowsForAccessibilityObserver != null) {
            this.mWindowsForAccessibilityObserver.scheduleComputeChangedWindowsLocked();
        }
    }

    public void getMagnificationRegionLocked(Region outMagnificationRegion) {
        if (this.mDisplayMagnifier != null) {
            this.mDisplayMagnifier.getMagnificationRegionLocked(outMagnificationRegion);
        }
    }

    public void onRectangleOnScreenRequestedLocked(Rect rectangle) {
        if (this.mDisplayMagnifier != null) {
            this.mDisplayMagnifier.onRectangleOnScreenRequestedLocked(rectangle);
        }
    }

    public void onWindowLayersChangedLocked() {
        if (this.mDisplayMagnifier != null) {
            this.mDisplayMagnifier.onWindowLayersChangedLocked();
        }
        if (this.mWindowsForAccessibilityObserver != null) {
            this.mWindowsForAccessibilityObserver.scheduleComputeChangedWindowsLocked();
        }
    }

    public void onRotationChangedLocked(DisplayContent displayContent, int rotation) {
        if (this.mDisplayMagnifier != null) {
            this.mDisplayMagnifier.onRotationChangedLocked(displayContent, rotation);
        }
        if (this.mWindowsForAccessibilityObserver != null) {
            this.mWindowsForAccessibilityObserver.scheduleComputeChangedWindowsLocked();
        }
    }

    public void onAppWindowTransitionLocked(WindowState windowState, int transition) {
        if (this.mDisplayMagnifier != null) {
            this.mDisplayMagnifier.onAppWindowTransitionLocked(windowState, transition);
        }
    }

    public void onWindowTransitionLocked(WindowState windowState, int transition) {
        if (this.mDisplayMagnifier != null) {
            this.mDisplayMagnifier.onWindowTransitionLocked(windowState, transition);
        }
        if (this.mWindowsForAccessibilityObserver != null) {
            this.mWindowsForAccessibilityObserver.scheduleComputeChangedWindowsLocked();
        }
    }

    public void onWindowFocusChangedNotLocked() {
        synchronized (this.mWindowManagerService) {
            WindowsForAccessibilityObserver observer = this.mWindowsForAccessibilityObserver;
        }
        if (observer != null) {
            observer.performComputeChangedWindowsNotLocked();
        }
    }

    public void onSomeWindowResizedOrMovedLocked() {
        if (this.mWindowsForAccessibilityObserver != null) {
            this.mWindowsForAccessibilityObserver.scheduleComputeChangedWindowsLocked();
        }
    }

    public void drawMagnifiedRegionBorderIfNeededLocked() {
        if (this.mDisplayMagnifier != null) {
            this.mDisplayMagnifier.drawMagnifiedRegionBorderIfNeededLocked();
        }
    }

    public MagnificationSpec getMagnificationSpecForWindowLocked(WindowState windowState) {
        if (this.mDisplayMagnifier != null) {
            return this.mDisplayMagnifier.getMagnificationSpecForWindowLocked(windowState);
        }
        return null;
    }

    public boolean hasCallbacksLocked() {
        if (this.mDisplayMagnifier == null && this.mWindowsForAccessibilityObserver == null) {
            return false;
        }
        return true;
    }

    private static void populateTransformationMatrixLocked(WindowState windowState, Matrix outMatrix) {
        sTempFloats[0] = windowState.mWinAnimator.mDsDx;
        sTempFloats[3] = windowState.mWinAnimator.mDtDx;
        sTempFloats[1] = windowState.mWinAnimator.mDsDy;
        sTempFloats[4] = windowState.mWinAnimator.mDtDy;
        sTempFloats[2] = (float) windowState.mShownPosition.x;
        sTempFloats[5] = (float) windowState.mShownPosition.y;
        sTempFloats[6] = 0.0f;
        sTempFloats[7] = 0.0f;
        sTempFloats[8] = 1.0f;
        outMatrix.setValues(sTempFloats);
    }
}
