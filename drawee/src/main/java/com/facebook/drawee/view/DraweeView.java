/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.drawee.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import com.facebook.common.internal.Objects;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.interfaces.DraweeHierarchy;
import com.facebook.imagepipeline.systrace.FrescoSystrace;
import javax.annotation.Nullable;

/**
 * View that displays a {@link DraweeHierarchy}.
 *
 * <p>Hierarchy should be set prior to using this view. See {@code setHierarchy}. Because creating a
 * hierarchy is an expensive operation, it is recommended this be done once per view, typically near
 * creation time.
 *
 * <p>In order to display an image, controller has to be set. See {@code setController}.
 *
 * <p>Although ImageView is subclassed instead of subclassing View directly, this class does not
 * support ImageView's setImageXxx, setScaleType and similar methods. Extending ImageView is a short
 * term solution in order to inherit some of its implementation (padding calculations, etc.). This
 * class is likely to be converted to extend View directly in the future, so avoid using ImageView's
 * methods and properties.
 */
public class DraweeView<DH extends DraweeHierarchy> extends ImageView {

  private DraweeHolder<DH> mDraweeHolder;
  private boolean mInitialised = false;


  public DraweeView(Context context) {
    super(context);
    init(context);
  }

  public DraweeView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public DraweeView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public DraweeView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init(context);
  }

  /** This method is idempotent so it only has effect the first time it's called */
  private void init(Context context) {
    try {
      if (FrescoSystrace.isTracing()) {
        FrescoSystrace.beginSection("DraweeView#init");
      }
      if (mInitialised) {
        return;
      }
      mInitialised = true;
      mDraweeHolder = DraweeHolder.create(null, context);
    } finally {
      if (FrescoSystrace.isTracing()) {
        FrescoSystrace.endSection();
      }
    }
  }

  /** Sets the hierarchy. */
  public void setHierarchy(DH hierarchy) {
    mDraweeHolder.setHierarchy(hierarchy);
    super.setImageDrawable(mDraweeHolder.getTopLevelDrawable());
  }

  /** Gets the hierarchy if set, throws NPE otherwise. */
  public DH getHierarchy() {
    return mDraweeHolder.getHierarchy();
  }

  /** Returns whether the hierarchy is set or not. */
  public boolean hasHierarchy() {
    return mDraweeHolder.hasHierarchy();
  }

  /** Gets the top-level drawable if hierarchy is set, null otherwise. */
  @Nullable
  public Drawable getTopLevelDrawable() {
    return mDraweeHolder.getTopLevelDrawable();
  }

  /** Sets the controller. */
  public void setController(@Nullable DraweeController draweeController) {
    mDraweeHolder.setController(draweeController);
    super.setImageDrawable(mDraweeHolder.getTopLevelDrawable());
  }

  /** Gets the controller if set, null otherwise. */
  @Nullable
  public DraweeController getController() {
    return mDraweeHolder.getController();
  }

  /** Returns whether the controller is set or not. */
  public boolean hasController() {
    return mDraweeHolder.getController() != null;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("holder", mDraweeHolder != null ? mDraweeHolder.toString() : "<no holder set>")
        .toString();
  }
}
