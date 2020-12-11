/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.drawee.controller;

import com.facebook.common.internal.Supplier;

public class ImageLoader {

    private boolean mInitialised = false;

    private static Supplier<? extends AbstractDraweeControllerBuilder>
            sDraweecontrollerbuildersupplier;

    /**
     * Initializes {@link ImageLoader} with supplier of Drawee controller builders.
     */
    public static void initialize(
            Supplier<? extends AbstractDraweeControllerBuilder> draweeControllerBuilderSupplier) {
        sDraweecontrollerbuildersupplier = draweeControllerBuilderSupplier;
    }

    public static AbstractDraweeControllerBuilder get() {
        return sDraweecontrollerbuildersupplier.get();
    }


    public static void shutDown() {
        sDraweecontrollerbuildersupplier = null;
    }
}
