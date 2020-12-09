/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.drawee.generic;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.annotation.VisibleForTesting;
import com.facebook.common.internal.Preconditions;
import com.facebook.drawee.drawable.DrawableParent;
import com.facebook.drawee.drawable.FadeDrawable;
import com.facebook.drawee.drawable.ForwardingDrawable;
import com.facebook.drawee.drawable.MatrixDrawable;
import com.facebook.drawee.drawable.ScaleTypeDrawable;
import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.drawee.interfaces.SettableDraweeHierarchy;
import com.facebook.imagepipeline.systrace.FrescoSystrace;
import javax.annotation.Nullable;

/**
 * A SettableDraweeHierarchy that displays placeholder image until the actual image is set. If
 * provided, failure image will be used in case of failure (placeholder otherwise). If provided,
 * retry image will be used in case of failure when retrying is enabled. If provided, progressbar
 * will be displayed until fully loaded. Each image can be displayed with a different scale type (or
 * no scaling at all). Fading between the layers is supported. Rounding is supported.
 *
 * <p>Example hierarchy with a placeholder, retry, failure and the actual image:
 *
 * <pre>
 *  o RootDrawable (top level drawable)
 *  |
 *  +--o FadeDrawable
 *     |
 *     +--o ScaleTypeDrawable (placeholder branch, optional)
 *     |  |
 *     |  +--o Drawable (placeholder image)
 *     |
 *     +--o ScaleTypeDrawable (actual image branch)
 *     |  |
 *     |  +--o ForwardingDrawable (actual image wrapper)
 *     |     |
 *     |     +--o Drawable (actual image)
 *     |
 *     +--o null (progress bar branch, optional)
 *     |
 *     +--o Drawable (retry image branch, optional)
 *     |
 *     +--o ScaleTypeDrawable (failure image branch, optional)
 *        |
 *        +--o Drawable (failure image)
 *  </pre>
 *
 * <p>Note:
 *
 * <ul>
 *   <li>RootDrawable and FadeDrawable are always created.
 *   <li>All branches except the actual image branch are optional (placeholder, failure, retry,
 *       progress bar). If some branch is not specified it won't be created. Index in FadeDrawable
 *       will still be reserved though.
 *   <li>If overlays and/or background are specified, they are added to the same fade drawable, and
 *       are always being displayed.
 *   <li>ScaleType and Matrix transformations will be added only if specified. If both are
 *       unspecified, then the branch for that image is attached to FadeDrawable directly. Matrix
 *       transformation is only supported for the actual image, and it is not recommended to be
 *       used.
 *   <li>Rounding, if specified, is applied to all layers. Rounded drawable can either wrap
 *       FadeDrawable, or if leaf rounding is specified, each leaf drawable will be rounded
 *       separately.
 *   <li>A particular drawable instance should be used by only one DH. If more than one DH is being
 *       built with the same builder, different drawable instances must be specified for each DH.
 * </ul>
 */
public class GenericDraweeHierarchy implements SettableDraweeHierarchy {

  private static final int ACTUAL_IMAGE_INDEX = 2;
  private static final int OVERLAY_IMAGES_INDEX = 6;

  private final Drawable mEmptyActualImageDrawable = new ColorDrawable(Color.TRANSPARENT);

  private final Resources mResources;
  private @Nullable RoundingParams mRoundingParams;

  private final RootDrawable mTopLevelDrawable;
  private final FadeDrawable mFadeDrawable;
  private final ForwardingDrawable mActualImageWrapper;

  GenericDraweeHierarchy(GenericDraweeHierarchyBuilder builder) {
    if (FrescoSystrace.isTracing()) {
      FrescoSystrace.beginSection("GenericDraweeHierarchy()");
    }
    mResources = builder.getResources();
    mRoundingParams = builder.getRoundingParams();

    mActualImageWrapper = new ForwardingDrawable(mEmptyActualImageDrawable);

    int numOverlays = (builder.getOverlays() != null) ? builder.getOverlays().size() : 1;

    // make sure there is at least one overlay to make setOverlayImage(Drawable)
    // method work.
    if (numOverlays == 0) {
      numOverlays = 1;
    }

    numOverlays += (builder.getPressedStateOverlay() != null) ? 1 : 0;

    // layer indices and count
    int numLayers = OVERLAY_IMAGES_INDEX + numOverlays;

    // array of layers
    Drawable[] layers = new Drawable[numLayers];
    layers[ACTUAL_IMAGE_INDEX] =
        buildActualImageBranch(
            mActualImageWrapper,
            builder.getActualImageScaleType(),
            builder.getActualImageFocusPoint(),
            builder.getActualImageColorFilter());

    if (numOverlays > 0) {
      int index = 0;
      if (builder.getOverlays() != null) {
        for (Drawable overlay : builder.getOverlays()) {
          layers[OVERLAY_IMAGES_INDEX + index++] = buildBranch(overlay, null);
        }
      } else {
        index = 1; // reserve space for one overlay
      }
      if (builder.getPressedStateOverlay() != null) {
        layers[OVERLAY_IMAGES_INDEX + index] = buildBranch(builder.getPressedStateOverlay(), null);
      }
    }

    // fade drawable composed of layers
    mFadeDrawable = new FadeDrawable(layers, false, ACTUAL_IMAGE_INDEX);
    mFadeDrawable.setTransitionDuration(builder.getFadeDuration());

    // rounded corners drawable (optional)
    Drawable maybeRoundedDrawable =
        WrappingUtils.maybeWrapWithRoundedOverlayColor(mFadeDrawable, mRoundingParams);

    // top-level drawable
    mTopLevelDrawable = new RootDrawable(maybeRoundedDrawable);
    mTopLevelDrawable.mutate();

    resetFade();
    if (FrescoSystrace.isTracing()) {
      FrescoSystrace.endSection();
    }
  }

  @Nullable
  private Drawable buildActualImageBranch(
      Drawable drawable,
      @Nullable ScalingUtils.ScaleType scaleType,
      @Nullable PointF focusPoint,
      @Nullable ColorFilter colorFilter) {
    drawable.setColorFilter(colorFilter);
    drawable = WrappingUtils.maybeWrapWithScaleType(drawable, scaleType, focusPoint);
    return drawable;
  }

  /** Applies scale type and rounding (both if specified). */
  @Nullable
  private Drawable buildBranch(
      @Nullable Drawable drawable, @Nullable ScalingUtils.ScaleType scaleType) {
    drawable = WrappingUtils.maybeApplyLeafRounding(drawable, mRoundingParams, mResources);
    drawable = WrappingUtils.maybeWrapWithScaleType(drawable, scaleType);
    return drawable;
  }

  private void resetActualImages() {
    mActualImageWrapper.setDrawable(mEmptyActualImageDrawable);
  }

  private void resetFade() {
    if (mFadeDrawable != null) {
      mFadeDrawable.beginBatchMode();
      // turn on all layers (backgrounds, branches, overlays)
      mFadeDrawable.fadeInAllLayers();
      // turn off branches (leaving backgrounds and overlays on)
      fadeOutBranches();
      mFadeDrawable.finishTransitionImmediately();
      mFadeDrawable.endBatchMode();
    }
  }

  private void fadeOutBranches() {
    fadeOutLayer(ACTUAL_IMAGE_INDEX);
  }

  private void fadeInLayer(int index) {
    if (index >= 0) {
      mFadeDrawable.fadeInLayer(index);
    }
  }

  private void fadeOutLayer(int index) {
    if (index >= 0) {
      mFadeDrawable.fadeOutLayer(index);
    }
  }


  // SettableDraweeHierarchy interface

  @Override
  public Drawable getTopLevelDrawable() {
    return mTopLevelDrawable;
  }

  @Override
  public void reset() {
    resetActualImages();
    resetFade();
  }

  @Override
  public void setImage(Drawable drawable, float progress, boolean immediate) {
    drawable = WrappingUtils.maybeApplyLeafRounding(drawable, mRoundingParams, mResources);
    drawable.mutate();
    mActualImageWrapper.setDrawable(drawable);
    mFadeDrawable.beginBatchMode();
    fadeOutBranches();
    fadeInLayer(ACTUAL_IMAGE_INDEX);
    if (immediate) {
      mFadeDrawable.finishTransitionImmediately();
    }
    mFadeDrawable.endBatchMode();
  }


  @Override
  public void setFailure(Throwable throwable) {
    mFadeDrawable.beginBatchMode();
    fadeOutBranches();
    mFadeDrawable.endBatchMode();
  }

  @Override
  public void setRetry(Throwable throwable) {
    mFadeDrawable.beginBatchMode();
    fadeOutBranches();
    mFadeDrawable.endBatchMode();
  }

  @Override
  public void setControllerOverlay(@Nullable Drawable drawable) {
    mTopLevelDrawable.setControllerOverlay(drawable);
  }

  @Override
  public Rect getBounds() {
    return mTopLevelDrawable.getBounds();
  }

  // Helper methods for accessing layers

  /**
   * Gets the lowest parent drawable for the layer at the specified index.
   *
   * <p>Following drawables are considered as parents: FadeDrawable, MatrixDrawable,
   * ScaleTypeDrawable. This is because those drawables are added automatically by the hierarchy (if
   * specified), whereas their children are created externally by the client code. When we need to
   * change the previously set drawable this is the parent whose child needs to be replaced.
   */
  private DrawableParent getParentDrawableAtIndex(int index) {
    DrawableParent parent = mFadeDrawable.getDrawableParentForIndex(index);
    if (parent.getDrawable() instanceof MatrixDrawable) {
      parent = (MatrixDrawable) parent.getDrawable();
    }
    if (parent.getDrawable() instanceof ScaleTypeDrawable) {
      parent = (ScaleTypeDrawable) parent.getDrawable();
    }
    return parent;
  }

  /**
   * Sets the drawable at the specified index while keeping the old scale type and rounding. In case
   * the given drawable is null, scale type gets cleared too.
   */
  private void setChildDrawableAtIndex(int index, @Nullable Drawable drawable) {
    if (drawable == null) {
      mFadeDrawable.setDrawable(index, null);
      return;
    }
    drawable = WrappingUtils.maybeApplyLeafRounding(drawable, mRoundingParams, mResources);
    getParentDrawableAtIndex(index).setDrawable(drawable);
  }

  /**
   * Gets the ScaleTypeDrawable at the specified index. In case there is no child at the specified
   * index, a NullPointerException is thrown. In case there is a child, but the ScaleTypeDrawable
   * does not exist, the child will be wrapped with a new ScaleTypeDrawable.
   */
  private ScaleTypeDrawable getScaleTypeDrawableAtIndex(int index) {
    DrawableParent parent = getParentDrawableAtIndex(index);
    if (parent instanceof ScaleTypeDrawable) {
      return (ScaleTypeDrawable) parent;
    } else {
      return WrappingUtils.wrapChildWithScaleType(parent, ScalingUtils.ScaleType.FIT_XY);
    }
  }

  /** Returns whether the given layer has a scale type drawable. */
  private boolean hasScaleTypeDrawableAtIndex(int index) {
    DrawableParent parent = getParentDrawableAtIndex(index);
    return (parent instanceof ScaleTypeDrawable);
  }

  // Mutability

  /** Sets the fade duration. */
  public void setFadeDuration(int durationMs) {
    mFadeDrawable.setTransitionDuration(durationMs);
  }

  /** Gets the fade duration. */
  public int getFadeDuration() {
    return mFadeDrawable.getTransitionDuration();
  }

  /** Sets the actual image focus point. */
  public void setActualImageFocusPoint(PointF focusPoint) {
    Preconditions.checkNotNull(focusPoint);
    getScaleTypeDrawableAtIndex(ACTUAL_IMAGE_INDEX).setFocusPoint(focusPoint);
  }

  /** Sets the actual image scale type. */
  public void setActualImageScaleType(ScalingUtils.ScaleType scaleType) {
    Preconditions.checkNotNull(scaleType);
    getScaleTypeDrawableAtIndex(ACTUAL_IMAGE_INDEX).setScaleType(scaleType);
  }

  public @Nullable ScalingUtils.ScaleType getActualImageScaleType() {
    if (!hasScaleTypeDrawableAtIndex(ACTUAL_IMAGE_INDEX)) {
      return null;
    }
    return getScaleTypeDrawableAtIndex(ACTUAL_IMAGE_INDEX).getScaleType();
  }

  public @Nullable PointF getActualImageFocusPoint() {
    if (!hasScaleTypeDrawableAtIndex(ACTUAL_IMAGE_INDEX)) {
      return null;
    }
    return getScaleTypeDrawableAtIndex(ACTUAL_IMAGE_INDEX).getFocusPoint();
  }

  /** Sets the color filter to be applied on the actual image. */
  public void setActualImageColorFilter(ColorFilter colorfilter) {
    mActualImageWrapper.setColorFilter(colorfilter);
  }

  /** Gets the non-cropped post-scaling bounds of the actual image. */
  public void getActualImageBounds(RectF outBounds) {
    mActualImageWrapper.getTransformedBounds(outBounds);
  }


  /**
   * Sets a new overlay image at the specified index.
   *
   * <p>This method will throw if the given index is out of bounds.
   *
   * @param drawable background image
   */
  public void setOverlayImage(int index, @Nullable Drawable drawable) {
    // Note that overlays are by definition top-most and therefore the last elements in the array.
    Preconditions.checkArgument(
        index >= 0 && OVERLAY_IMAGES_INDEX + index < mFadeDrawable.getNumberOfLayers(),
        "The given index does not correspond to an overlay image.");
    setChildDrawableAtIndex(OVERLAY_IMAGES_INDEX + index, drawable);
  }

  /** Sets the overlay image if allowed. */
  public void setOverlayImage(@Nullable Drawable drawable) {
    setOverlayImage(0, drawable);
  }

  /** Sets the rounding params. */
  public void setRoundingParams(@Nullable RoundingParams roundingParams) {
    mRoundingParams = roundingParams;
    WrappingUtils.updateOverlayColorRounding(mTopLevelDrawable, mRoundingParams);
    for (int i = 0; i < mFadeDrawable.getNumberOfLayers(); i++) {
      WrappingUtils.updateLeafRounding(getParentDrawableAtIndex(i), mRoundingParams, mResources);
    }
  }

  /** Gets the rounding params. */
  @Nullable
  public RoundingParams getRoundingParams() {
    return mRoundingParams;
  }

  @VisibleForTesting
  public boolean hasImage() {
    return mActualImageWrapper.getDrawable() != mEmptyActualImageDrawable;
  }

  public void setOnFadeListener(FadeDrawable.OnFadeListener onFadeFinished) {
    mFadeDrawable.setOnFadeListener(onFadeFinished);
  }
}
