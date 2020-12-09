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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.annotation.VisibleForTesting;
import com.facebook.common.internal.Preconditions;
import com.facebook.drawee.drawable.DrawableParent;
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

  GenericDraweeHierarchy(GenericDraweeHierarchyBuilder builder) {
    if (FrescoSystrace.isTracing()) {
      FrescoSystrace.beginSection("GenericDraweeHierarchy()");
    }
    mResources = builder.getResources();
    mRoundingParams = builder.getRoundingParams();


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
            mEmptyActualImageDrawable;

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

    // top-level drawable
    mTopLevelDrawable = new RootDrawable(mEmptyActualImageDrawable);
    mTopLevelDrawable.mutate();

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
    return drawable;
  }

  /** Applies scale type and rounding (both if specified). */
  @Nullable
  private Drawable buildBranch(
      @Nullable Drawable drawable, @Nullable ScalingUtils.ScaleType scaleType) {
    return drawable;
  }

  private void resetActualImages() {
  }

  // SettableDraweeHierarchy interface

  @Override
  public Drawable getTopLevelDrawable() {
    return mTopLevelDrawable;
  }

  @Override
  public void reset() {
    resetActualImages();
  }

  @Override
  public void setImage(Drawable drawable, float progress, boolean immediate) {

  }


  @Override
  public void setFailure(Throwable throwable) {
  }

  @Override
  public void setRetry(Throwable throwable) {
  }

  @Override
  public void setControllerOverlay(@Nullable Drawable drawable) {
    mTopLevelDrawable.setControllerOverlay(drawable);
  }

  @Override
  public Rect getBounds() {
    return mTopLevelDrawable.getBounds();
  }

  @VisibleForTesting
  public boolean hasImage() {
    return true;
  }

}
