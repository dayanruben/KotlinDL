/*
 * Copyright 2022 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.kotlinx.dl.impl.preprocessing.image

import org.jetbrains.kotlinx.dl.api.core.shape.TensorShape
import org.jetbrains.kotlinx.dl.api.preprocessing.Operation
import java.awt.image.BufferedImage

/**
 * Converts [BufferedImage] to float array representation.
 */
public class ConvertToFloatArray : Operation<BufferedImage, Pair<FloatArray, TensorShape>> {
    override fun apply(input: BufferedImage): Pair<FloatArray, TensorShape> {
        return ImageConverter.toRawFloatArray(input) to input.getTensorShape()
    }

    override fun getOutputShape(inputShape: TensorShape): TensorShape {
        return inputShape
    }
}