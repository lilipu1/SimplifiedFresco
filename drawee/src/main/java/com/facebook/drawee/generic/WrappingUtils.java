/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.drawee.generic;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import com.facebook.drawee.drawable.DrawableParent;

import javax.annotation.Nullable;

/** A class that contains helper methods for wrapping and rounding. */
public class WrappingUtils {

  private static final String TAG = "WrappingUtils";

  // Empty drawable to be temporarily used for hierarchy manipulations.
  //
  // Since drawables are allowed to have at most one parent, and this is a static instance, this
  // drawable may only be used temporarily while carrying some hierarchy manipulations. After those
  // manipulations are done, the drawable must not be owned by any parent anymore.
  //
  // The reason why we need this drawable at all is as follows:
  // Consider Drawable A and its child X. Suppose we want to put X under a new parent B. If we just
  // do B.setCurrent(X), the old parent A still considers X to be its child. If at some later point
  // we do A.setChild(Y), drawable A will clear Drawable.Callback from its old child X, and will set
  // callback to its new child Y. But X is no longer a child of A, and so will A incorrectly remove
  // the callback that B set on X. To avoid that, before setting X as a child of B, we must first
  // remove it from A like so: A.setCurrent(empty); B.setCurrent(X);. In cases where we can't set a
  // null child, we use an empty drawable.
  private static final Drawable sEmptyDrawable = new ColorDrawable(Color.TRANSPARENT);


}
