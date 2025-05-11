/*-
 * #%L
 * Mars kymograph builder.
 * %%
 * Copyright (C) 2025 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpg.biochem.mars.kymograph;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.*;
import net.imglib2.img.ImgFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

/**
 * Utility class with static methods for a few kymograph related image filtering operations.
 * <p>
 * This class provides methods for applying filters to ImgPlus objects with
 * multiple time points and channels. Each filter is applied to 2D XY slices.
 *
 * @author Karl Duderstadt
 */
public class MarsKymographUtils {

    /**
     * Increases the resolution of a 2D image or a multi-dimensional image in the XY plane.
     * This method preserves the number of channels and time points while only interpolating
     * the XY dimensions.
     *
     * @param <T> The pixel type, must extend RealType
     * @param input The input image
     * @param scaleFactor The factor by which to increase the resolution
     * @return A new ImgPlus with increased resolution
     */
    @SuppressWarnings("unchecked")
    public static <T extends RealType<T>> ImgPlus<T> increaseResolution(ImgPlus<T> input, double scaleFactor) {
        // Get dimensions and create output dimensions
        long[] dims = new long[input.numDimensions()];
        input.dimensions(dims);

        // Find X and Y dimensions
        int xDim = input.dimensionIndex(Axes.X);
        int yDim = input.dimensionIndex(Axes.Y);

        if (xDim < 0 || yDim < 0) {
            // If X or Y axis is not explicitly set, assume the first two dimensions
            xDim = 0;
            yDim = 1;
        }

        // Calculate new dimensions
        long[] newDims = dims.clone();
        newDims[xDim] = (long) (dims[xDim] * scaleFactor);
        newDims[yDim] = (long) (dims[yDim] * scaleFactor);

        // Create output image with same factory as input
        ImgFactory<T> factory = (ImgFactory<T>) input.factory();
        ImgPlus<T> output = new ImgPlus<>(factory.create(newDims, input.firstElement().copy()));

        // Copy metadata (without using setScale)
        for (int d = 0; d < input.numDimensions(); d++) {
            output.setAxis(input.axis(d).copy(), d);
        }
        output.setName(input.getName());
        output.setSource(input.getSource());

        // Create interpolator factory
        ClampingNLinearInterpolatorFactory<T> interpolatorFactory = new ClampingNLinearInterpolatorFactory<>();

        // Process each channel and time point separately
        RandomAccessibleInterval<T> inputRAI = input;
        RandomAccessibleInterval<T> outputRAI = output;

        // Determine if we have channel and time dimensions
        int timeDim = input.dimensionIndex(Axes.TIME);
        int channelDim = input.dimensionIndex(Axes.CHANNEL);

        // Define the number of time points and channels
        long timePoints = (timeDim >= 0) ? dims[timeDim] : 1;
        long channels = (channelDim >= 0) ? dims[channelDim] : 1;

        // Process each time point and channel
        for (long t = 0; t < timePoints; t++) {
            for (long c = 0; c < channels; c++) {
                // Extract the current 2D slice
                RandomAccessibleInterval<T> slice = inputRAI;
                RandomAccessibleInterval<T> outputSlice = outputRAI;

                // If we have time dimension, get the appropriate hyperslice
                if (timeDim >= 0) {
                    slice = Views.hyperSlice(slice, timeDim, t);
                    outputSlice = Views.hyperSlice(outputSlice, timeDim, t);
                }

                // If we have channel dimension, get the appropriate hyperslice
                if (channelDim >= 0) {
                    slice = Views.hyperSlice(slice, channelDim, c);
                    outputSlice = Views.hyperSlice(outputSlice, channelDim, c);
                }

                // Interpolate the 2D slice
                interpolateSlice(slice, outputSlice, xDim, yDim, scaleFactor, interpolatorFactory);
            }
        }

        return output;
    }

    /**
     * Helper method to interpolate a single 2D slice.
     */
    private static <T extends RealType<T>> void interpolateSlice(
            RandomAccessibleInterval<T> input,
            RandomAccessibleInterval<T> output,
            int xDim, int yDim, double scaleFactor,
            ClampingNLinearInterpolatorFactory<T> interpolatorFactory) {

        // Extend input with zero boundary extension
        RandomAccessible<T> extendedInput = Views.extendZero(input);

        // Create interpolator
        RealRandomAccessible<T> interpolant = Views.interpolate(extendedInput, interpolatorFactory);

        // Create output cursor
        Cursor<T> outputCursor = Views.iterable(output).localizingCursor();

        // Create accessor for the interpolant (using RealRandomAccess instead of RandomAccess)
        RealRandomAccess<T> interpolatedAccess = interpolant.realRandomAccess();

        // Adjust dimensions for reduced dimensionality after hyperslices
        int dimensionOffset = 0;
        if (xDim > yDim) {
            dimensionOffset = 1;
        }

        // For each position in the output image
        double[] position = new double[output.numDimensions()];
        while (outputCursor.hasNext()) {
            outputCursor.fwd();
            outputCursor.localize(position);

            // Calculate input position
            for (int d = 0; d < position.length; d++) {
                if (d == xDim - dimensionOffset || d == yDim - dimensionOffset) {
                    position[d] = position[d] / scaleFactor;
                }
            }

            // Set interpolated access to calculated position
            for (int d = 0; d < position.length; d++) {
                interpolatedAccess.setPosition(position[d], d);  // Using double position instead of long
            }

            // Set output value
            outputCursor.get().set(interpolatedAccess.get());
        }
    }
}
