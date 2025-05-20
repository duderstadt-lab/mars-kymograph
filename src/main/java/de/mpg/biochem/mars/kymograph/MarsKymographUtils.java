/*-
 * #%L
 * Mars kymograph builder.
 * %%
 * Copyright (C) 2023 - 2025 Karl Duderstadt
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
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.img.ImgFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import net.imagej.axis.CalibratedAxis;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
     * Apply a median filter to an ImgPlus with multiple time points and channels.
     *
     * @param <T>        The pixel type
     * @param input      The input image
     * @param radius     The radius of the filter
     * @param numThreads Number of threads to use (1 for single-threaded)
     * @return A new ImgPlus with the filtered data
     */
    public static <T extends RealType<T>> ImgPlus<T> applyMedianFilter(
            ImgPlus<T> input, int radius, int numThreads) {

        // Create the filter shape
        final RectangleShape shape = new RectangleShape(radius, false);

        // Create output image
        final Img<T> outputImg = createEmptyImgLike(input);
        final ImgPlus<T> output = wrapWithMetadata(outputImg, input, "Median Filtered");

        // Apply filter to each frame and channel
        processTimeChannelFrames(input, output, (inputFrame, outputFrame) -> {
            try {
                // Manual implementation using neighborhoods
                final RandomAccessible<Neighborhood<T>> neighborhoods =
                        shape.neighborhoodsRandomAccessible(Views.extendMirrorSingle(inputFrame));
                final RandomAccessibleInterval<Neighborhood<T>> neighborhoodsInterval =
                        Views.interval(neighborhoods, inputFrame);

                // Make the interval iterable
                final IterableInterval<Neighborhood<T>> iterableNeighborhoods =
                        Views.iterable(neighborhoodsInterval);

                // Create cursor for neighborhoods
                final Cursor<Neighborhood<T>> cursor = iterableNeighborhoods.cursor();

                // Create random access for output
                final RandomAccess<T> outputRA = outputFrame.randomAccess();

                // For each pixel
                while (cursor.hasNext()) {
                    final Neighborhood<T> neighborhood = cursor.next();
                    outputRA.setPosition(cursor);

                    // Collect all values in the neighborhood
                    List<Double> values = new ArrayList<>();
                    for (final T value : neighborhood) {
                        values.add(value.getRealDouble());
                    }

                    // Sort and find median
                    Collections.sort(values);
                    double median = values.size() % 2 == 0 ?
                            (values.get(values.size() / 2) + values.get(values.size() / 2 - 1)) / 2 :
                            values.get(values.size() / 2);

                    // Set the output pixel
                    outputRA.get().setReal(median);
                }

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }, numThreads);

        return output;
    }

    /**
     * Apply a Gaussian blur filter to an ImgPlus with multiple time points and channels.
     *
     * @param <T>        The pixel type
     * @param input      The input image
     * @param sigma      Sigma value for the Gaussian kernel
     * @param numThreads Number of threads to use (1 for single-threaded)
     * @return A new ImgPlus with the filtered data
     */
    public static <T extends RealType<T>> ImgPlus<T> applyGaussianFilter(
            ImgPlus<T> input, double sigma, int numThreads) {

        // Create output image
        final Img<T> outputImg = createEmptyImgLike(input);
        final ImgPlus<T> output = wrapWithMetadata(outputImg, input, "Gaussian Filtered");

        // Apply filter to each frame and channel
        processTimeChannelFrames(input, output, (inputFrame, outputFrame) -> {
            try {
                // Use Gauss3 for in-place Gaussian filtering
                double[] sigmas = new double[] { sigma, sigma };
                Gauss3.gauss(sigmas, Views.extendMirrorSingle(inputFrame), outputFrame);
                return true;
            } catch (IncompatibleTypeException e) {
                e.printStackTrace();
                return false;
            }
        }, numThreads);

        return output;
    }

    /**
     * Apply a top-hat filter to an ImgPlus with multiple time points and channels.
     * The top-hat filter enhances bright features smaller than the specified radius.
     *
     * @param <T>        The pixel type
     * @param input      The input image
     * @param radius     The radius of the filter
     * @param numThreads Number of threads to use (1 for single-threaded)
     * @return A new ImgPlus with the filtered data
     */
    public static <T extends RealType<T>> ImgPlus<T> applyTopHatFilter(
            ImgPlus<T> input, int radius, int numThreads) {

        // Create output image
        final Img<T> outputImg = createEmptyImgLike(input);
        final ImgPlus<T> output = wrapWithMetadata(outputImg, input, "TopHat Filtered");

        // Apply filter to each frame and channel
        processTimeChannelFrames(input, output, (inputFrame, outputFrame) -> {
            try {
                // Manual implementation of top-hat filter
                // Top-hat = original - opening
                // Opening = dilation(erosion(image))

                // Create a temporary image for erosion
                final Img<T> erodedImg = createEmptyImgLike(inputFrame);
                // Create a temporary image for opening (erosion followed by dilation)
                final Img<T> openedImg = createEmptyImgLike(inputFrame);

                // Define the shape for morphological operations
                final RectangleShape shape = new RectangleShape(radius, false);

                // 1. Apply erosion manually
                applyErosion(inputFrame, erodedImg, shape);

                // 2. Apply dilation to the eroded image to get the opening
                applyDilation(erodedImg, openedImg, shape);

                // 3. Subtract opening from original (top-hat)
                subtractImages(inputFrame, openedImg, outputFrame);

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }, numThreads);

        return output;
    }

    /**
     * Convenience methods that use specify one thread when not provided.
     */
    public static <T extends RealType<T>> ImgPlus<T> applyMedianFilter(
            ImgPlus<T> input, int radius) {
        return applyMedianFilter(input, radius, 1);
    }

    public static <T extends RealType<T>> ImgPlus<T> applyGaussianFilter(
            ImgPlus<T> input, double sigma) {
        return applyGaussianFilter(input, sigma, 1);
    }

    public static <T extends RealType<T>> ImgPlus<T> applyTopHatFilter(
            ImgPlus<T> input, int radius) {
        return applyTopHatFilter(input, radius, 1);
    }

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

        // Determine if we have channel and time dimensions
        int timeDim = input.dimensionIndex(Axes.TIME);
        int channelDim = input.dimensionIndex(Axes.CHANNEL);

        // Define the number of time points and channels
        long timePoints = (timeDim >= 0) ? dims[timeDim] : 1;
        long channels = (channelDim >= 0) ? dims[channelDim] : 1;

        // Process each time point and channel
        for (long t = 0; t < timePoints; t++) {
            for (long c = 0; c < channels; c++) {
                try {
                    // Extract the current 2D slice
                    RandomAccessibleInterval<T> slice = input;
                    RandomAccessibleInterval<T> outputSlice = output;

                    // If we have time dimension, get the appropriate hyperslice
                    if (timeDim >= 0) {
                        slice = Views.hyperSlice(slice, timeDim, t);
                        outputSlice = Views.hyperSlice(outputSlice, timeDim, t);
                    }

                    // If we have channel dimension, get the appropriate hyperslice
                    if (channelDim >= 0) {
                        // We need to check if the channel dimension index has changed due to previous hyperslice
                        int adjustedChannelDim = channelDim;
                        if (timeDim >= 0 && channelDim > timeDim) {
                            adjustedChannelDim--;
                        }
                        slice = Views.hyperSlice(slice, adjustedChannelDim, c);
                        outputSlice = Views.hyperSlice(outputSlice, adjustedChannelDim, c);
                    }

                    // Interpolate the 2D slice
                    interpolateSlice(slice, outputSlice, xDim, yDim, scaleFactor, interpolatorFactory);
                } catch (Exception e) {
                    System.err.println("Error processing time=" + t + ", channel=" + c + ": " + e.getMessage());
                    throw e; // re-throw to maintain original behavior
                }
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

        // Create accessor for the interpolant
        RealRandomAccess<T> interpolatedAccess = interpolant.realRandomAccess();

        // After hyperslices, the dimensions are reduced, so we need to adjust accordingly
        // For a 2D image after hyperslices, the dimensions should be 0 and 1
        int adjustedXDim = 0;
        int adjustedYDim = 1;

        // For images with more than 2 dimensions, we need to adjust
        if (input.numDimensions() > 2) {
            // Try to determine correct dimension mapping based on original dimension order
            if (xDim > yDim) {
                adjustedXDim = 1;
                adjustedYDim = 0;
            }
        }

        // For each position in the output image
        double[] inputPosition = new double[input.numDimensions()];
        long[] outputPos = new long[output.numDimensions()];

        while (outputCursor.hasNext()) {
            outputCursor.fwd();

            // Get cursor position
            outputCursor.localize(outputPos);

            // Calculate input position (divide by scale factor)
            for (int d = 0; d < inputPosition.length; d++) {
                if (d == adjustedXDim || d == adjustedYDim) {
                    inputPosition[d] = outputPos[d] / scaleFactor;
                } else {
                    inputPosition[d] = outputPos[d];
                }
            }

            // Set the interpolator to the calculated position
            for (int d = 0; d < inputPosition.length; d++) {
                interpolatedAccess.setPosition(inputPosition[d], d);
            }

            // Set output value
            outputCursor.get().set(interpolatedAccess.get());
        }
    }

    /**
     * Skips frames in a time series, keeping only every Nth frame.
     *
     * @param <T> The pixel type
     * @param input The input image
     * @param skipFactor Include every Nth frame (e.g., 5 means include frames 0, 5, 10, etc.)
     * @return A new ImgPlus with reduced number of frames
     */
    public static <T extends RealType<T>> ImgPlus<T> skipFrames(ImgPlus<T> input, int skipFactor) {
        // Find time axis
        final int timeAxisIndex = input.dimensionIndex(Axes.TIME);

        // If no time dimension or invalid skip factor, return the original
        if (timeAxisIndex < 0 || skipFactor <= 1) {
            return input;
        }

        // Get dimensions
        long[] dims = new long[input.numDimensions()];
        input.dimensions(dims);

        // Calculate new time dimension size
        long timePoints = dims[timeAxisIndex];
        long newTimePoints = (timePoints + skipFactor - 1) / skipFactor; // Ceiling division

        // Create new dimensions array
        long[] newDims = dims.clone();
        newDims[timeAxisIndex] = newTimePoints;

        // Create output image
        @SuppressWarnings("unchecked")
        ImgFactory<T> factory = (ImgFactory<T>) input.factory();
        Img<T> outputImg = factory.create(newDims, input.firstElement().copy());
        ImgPlus<T> output = wrapWithMetadata(outputImg, input, "Frames Skipped");

        // Get channel axis if it exists
        final int channelAxisIndex = input.dimensionIndex(Axes.CHANNEL);
        final long channels = (channelAxisIndex >= 0) ? dims[channelAxisIndex] : 1;

        // Copy selected frames
        RandomAccess<T> inputRA = input.randomAccess();
        RandomAccess<T> outputRA = output.randomAccess();

        // Process each output frame
        for (int newT = 0; newT < newTimePoints; newT++) {
            int originalT = newT * skipFactor;

            // Copy this frame to output
            copyFrame(input, output, inputRA, outputRA, originalT, newT, timeAxisIndex, channelAxisIndex, dims);
        }

        return output;
    }

    /**
     * Averages groups of frames in a time series.
     *
     * @param <T> The pixel type
     * @param input The input image
     * @param groupSize Number of frames to average together
     * @return A new ImgPlus with reduced number of frames
     */
    public static <T extends RealType<T>> ImgPlus<T> averageFrames(ImgPlus<T> input, int groupSize) {
        return reduceFrames(input, groupSize, true); // Use average
    }

    /**
     * Sums groups of frames in a time series.
     *
     * @param <T> The pixel type
     * @param input The input image
     * @param groupSize Number of frames to sum together
     * @return A new ImgPlus with reduced number of frames
     */
    public static <T extends RealType<T>> ImgPlus<T> sumFrames(ImgPlus<T> input, int groupSize) {
        return reduceFrames(input, groupSize, false); // Use sum
    }

    /**
     * Helper method to reduce frames by averaging or summing.
     *
     * @param <T> The pixel type
     * @param input The input image
     * @param groupSize Number of frames to reduce together
     * @param average If true, average the frames; if false, sum them
     * @return A new ImgPlus with reduced number of frames
     */
    private static <T extends RealType<T>> ImgPlus<T> reduceFrames(ImgPlus<T> input, int groupSize, boolean average) {
        // Find time axis
        final int timeAxisIndex = input.dimensionIndex(Axes.TIME);

        // If no time dimension or invalid group size, return the original
        if (timeAxisIndex < 0 || groupSize <= 1) {
            return input;
        }

        // Get dimensions
        long[] dims = new long[input.numDimensions()];
        input.dimensions(dims);

        // Calculate new time dimension size
        long timePoints = dims[timeAxisIndex];
        long newTimePoints = (timePoints + groupSize - 1) / groupSize; // Ceiling division

        // Create new dimensions array
        long[] newDims = dims.clone();
        newDims[timeAxisIndex] = newTimePoints;

        // Create output image
        @SuppressWarnings("unchecked")
        ImgFactory<T> factory = (ImgFactory<T>) input.factory();
        Img<T> outputImg = factory.create(newDims, input.firstElement().copy());
        ImgPlus<T> output = wrapWithMetadata(outputImg, input, average ? "Frames Averaged" : "Frames Summed");

        // Get channel axis if it exists
        final int channelAxisIndex = input.dimensionIndex(Axes.CHANNEL);
        final long channels = (channelAxisIndex >= 0) ? dims[channelAxisIndex] : 1;

        // Get X and Y dimensions
        final int xAxisIndex = input.dimensionIndex(Axes.X);
        final int yAxisIndex = input.dimensionIndex(Axes.Y);

        if (xAxisIndex < 0 || yAxisIndex < 0) {
            // If X or Y axis is not explicitly set, return the original
            return input;
        }

        // Process each output frame
        for (int newT = 0; newT < newTimePoints; newT++) {
            // Determine the range of frames to process
            int startFrame = newT * groupSize;
            int endFrame = Math.min(startFrame + groupSize, (int)timePoints);
            int actualGroupSize = endFrame - startFrame;

            // For each channel
            for (int c = 0; c < channels; c++) {
                // For each pixel position in the X-Y plane
                for (int y = 0; y < dims[yAxisIndex]; y++) {
                    for (int x = 0; x < dims[xAxisIndex]; x++) {
                        // Initialize accumulator
                        double sum = 0.0;

                        // For each frame in the group
                        for (int t = startFrame; t < endFrame; t++) {
                            // Set position for input
                            long[] pos = new long[input.numDimensions()];
                            pos[xAxisIndex] = x;
                            pos[yAxisIndex] = y;
                            pos[timeAxisIndex] = t;
                            if (channelAxisIndex >= 0) {
                                pos[channelAxisIndex] = c;
                            }

                            // Get value
                            RandomAccess<T> ra = input.randomAccess();
                            ra.setPosition(pos);
                            sum += ra.get().getRealDouble();
                        }

                        // Calculate final value
                        double finalValue = average ? sum / actualGroupSize : sum;

                        // Set output position
                        long[] outPos = new long[output.numDimensions()];
                        outPos[xAxisIndex] = x;
                        outPos[yAxisIndex] = y;
                        outPos[timeAxisIndex] = newT;
                        if (channelAxisIndex >= 0) {
                            outPos[channelAxisIndex] = c;
                        }

                        // Set value
                        RandomAccess<T> outRA = output.randomAccess();
                        outRA.setPosition(outPos);
                        outRA.get().setReal(finalValue);
                    }
                }
            }
        }

        return output;
    }

    /**
     * Applies vertical reflection (flipping) to an image.
     *
     * @param <T> The pixel type
     * @param input The input image
     * @return A new ImgPlus with vertically flipped frames
     */
    public static <T extends RealType<T>> ImgPlus<T> applyVerticalReflection(ImgPlus<T> input) {
        // Find Y axis
        final int yAxisIndex = input.dimensionIndex(Axes.Y);

        // If no Y dimension, return the original
        if (yAxisIndex < 0) {
            return input;
        }

        // Get dimensions
        long[] dims = new long[input.numDimensions()];
        input.dimensions(dims);

        // Create output image
        @SuppressWarnings("unchecked")
        ImgFactory<T> factory = (ImgFactory<T>) input.factory();
        Img<T> outputImg = factory.create(dims, input.firstElement().copy());
        ImgPlus<T> output = wrapWithMetadata(outputImg, input, "Vertically Reflected");

        // Get time and channel axes if they exist
        final int timeAxisIndex = input.dimensionIndex(Axes.TIME);
        final int channelAxisIndex = input.dimensionIndex(Axes.CHANNEL);
        final long timePoints = (timeAxisIndex >= 0) ? dims[timeAxisIndex] : 1;
        final long channels = (channelAxisIndex >= 0) ? dims[channelAxisIndex] : 1;

        // Get X dimension
        final int xAxisIndex = input.dimensionIndex(Axes.X);

        if (xAxisIndex < 0) {
            // If X axis is not explicitly set, return the original
            return input;
        }

        // Process each frame
        for (int t = 0; t < timePoints; t++) {
            for (int c = 0; c < channels; c++) {
                // For each row in the Y dimension
                for (int y = 0; y < dims[yAxisIndex]; y++) {
                    // Calculate the flipped y position
                    int flippedY = (int)dims[yAxisIndex] - 1 - y;

                    // For each column in the X dimension
                    for (int x = 0; x < dims[xAxisIndex]; x++) {
                        // Set position for input
                        long[] inPos = new long[input.numDimensions()];
                        inPos[xAxisIndex] = x;
                        inPos[yAxisIndex] = y;
                        if (timeAxisIndex >= 0) {
                            inPos[timeAxisIndex] = t;
                        }
                        if (channelAxisIndex >= 0) {
                            inPos[channelAxisIndex] = c;
                        }

                        // Set position for output with flipped Y
                        long[] outPos = inPos.clone();
                        outPos[yAxisIndex] = flippedY;

                        // Copy value
                        RandomAccess<T> inRA = input.randomAccess();
                        RandomAccess<T> outRA = output.randomAccess();
                        inRA.setPosition(inPos);
                        outRA.setPosition(outPos);
                        outRA.get().set(inRA.get());
                    }
                }
            }
        }

        return output;
    }

    /**
     * Helper method to copy a single frame from input to output.
     */
    private static <T extends RealType<T>> void copyFrame(
            ImgPlus<T> input, ImgPlus<T> output,
            RandomAccess<T> inputRA, RandomAccess<T> outputRA,
            int srcT, int destT, int timeAxisIndex, int channelAxisIndex, long[] dims) {

        // Get X and Y axes
        final int xAxisIndex = input.dimensionIndex(Axes.X);
        final int yAxisIndex = input.dimensionIndex(Axes.Y);

        if (xAxisIndex < 0 || yAxisIndex < 0) {
            return; // Cannot copy if X or Y axes not found
        }

        // Get number of channels
        final long channels = (channelAxisIndex >= 0) ? dims[channelAxisIndex] : 1;

        // For each channel
        for (int c = 0; c < channels; c++) {
            // For each pixel position in the X-Y plane
            for (int y = 0; y < dims[yAxisIndex]; y++) {
                for (int x = 0; x < dims[xAxisIndex]; x++) {
                    // Set position for input
                    long[] pos = new long[input.numDimensions()];
                    pos[xAxisIndex] = x;
                    pos[yAxisIndex] = y;
                    pos[timeAxisIndex] = srcT;
                    if (channelAxisIndex >= 0) {
                        pos[channelAxisIndex] = c;
                    }
                    inputRA.setPosition(pos);

                    // Set position for output
                    pos[timeAxisIndex] = destT;
                    outputRA.setPosition(pos);

                    // Copy value
                    outputRA.get().set(inputRA.get());
                }
            }
        }
    }

    /**
     * Apply erosion operation manually.
     */
    private static <T extends RealType<T>> void applyErosion(
            RandomAccessibleInterval<T> input, RandomAccessibleInterval<T> output,
            RectangleShape shape) {

        // Create neighborhood
        final RandomAccessible<Neighborhood<T>> neighborhoods =
                shape.neighborhoodsRandomAccessible(Views.extendMirrorSingle(input));
        final RandomAccessibleInterval<Neighborhood<T>> neighborhoodsInterval =
                Views.interval(neighborhoods, input);

        // Make it iterable
        final IterableInterval<Neighborhood<T>> iterableNeighborhoods =
                Views.iterable(neighborhoodsInterval);

        // Create cursor for neighborhoods
        final Cursor<Neighborhood<T>> cursor = iterableNeighborhoods.cursor();

        // Create random access for output
        final RandomAccess<T> outputRA = output.randomAccess();

        // For each pixel
        while (cursor.hasNext()) {
            final Neighborhood<T> neighborhood = cursor.next();
            outputRA.setPosition(cursor);

            // Find minimum value in the neighborhood (erosion)
            double minValue = Double.MAX_VALUE;
            for (final T value : neighborhood) {
                double val = value.getRealDouble();
                if (val < minValue) {
                    minValue = val;
                }
            }

            // Set the output pixel
            outputRA.get().setReal(minValue);
        }
    }

    /**
     * Apply dilation operation manually.
     */
    private static <T extends RealType<T>> void applyDilation(
            RandomAccessibleInterval<T> input, RandomAccessibleInterval<T> output,
            RectangleShape shape) {

        // Create neighborhood
        final RandomAccessible<Neighborhood<T>> neighborhoods =
                shape.neighborhoodsRandomAccessible(Views.extendMirrorSingle(input));
        final RandomAccessibleInterval<Neighborhood<T>> neighborhoodsInterval =
                Views.interval(neighborhoods, input);

        // Make it iterable
        final IterableInterval<Neighborhood<T>> iterableNeighborhoods =
                Views.iterable(neighborhoodsInterval);

        // Create cursor for neighborhoods
        final Cursor<Neighborhood<T>> cursor = iterableNeighborhoods.cursor();

        // Create random access for output
        final RandomAccess<T> outputRA = output.randomAccess();

        // For each pixel
        while (cursor.hasNext()) {
            final Neighborhood<T> neighborhood = cursor.next();
            outputRA.setPosition(cursor);

            // Find maximum value in the neighborhood (dilation)
            double maxValue = -Double.MAX_VALUE;
            for (final T value : neighborhood) {
                double val = value.getRealDouble();
                if (val > maxValue) {
                    maxValue = val;
                }
            }

            // Set the output pixel
            outputRA.get().setReal(maxValue);
        }
    }

    /**
     * Create an empty image with the same dimensions and type as the input.
     *
     * @param <T>   The pixel type
     * @param input The input image
     * @return A new empty image with the same dimensions and type
     */
    @SuppressWarnings("unchecked")
    private static <T extends RealType<T>> Img<T> createEmptyImgLike(RandomAccessibleInterval<T> input) {
        final T type = Util.getTypeFromInterval(input);
        final ImgFactory<T> factory = Util.getSuitableImgFactory(input, type);
        return factory.create(input);
    }

    /**
     * Wrap an Img with metadata from another ImgPlus.
     *
     * @param <T>        The pixel type
     * @param img        The image to wrap
     * @param original   The original ImgPlus to copy metadata from
     * @param nameSuffix Suffix to add to the name
     * @return A new ImgPlus with the same metadata as the original
     */
    private static <T extends RealType<T>> ImgPlus<T> wrapWithMetadata(
            Img<T> img, ImgPlus<?> original, String nameSuffix) {

        // Collect all axes from the original ImgPlus
        final CalibratedAxis[] axes = new CalibratedAxis[original.numDimensions()];
        for (int d = 0; d < original.numDimensions(); d++) {
            axes[d] = original.axis(d).copy();
        }

        // Create a new ImgPlus with the original metadata
        return new ImgPlus<>(img, original.getName() + " (" + nameSuffix + ")", axes);
    }

    /**
     * Process each XY frame in a time series with multiple channels using multiple threads.
     *
     * @param <T>        The pixel type
     * @param input      The input ImgPlus
     * @param output     The output ImgPlus
     * @param processor  The function to apply to each frame
     * @param numThreads Number of threads to use (1 for single-threaded)
     */
    private static <T extends RealType<T>> void processTimeChannelFrames(
            ImgPlus<T> input, ImgPlus<T> output,
            FrameProcessor<T> processor, int numThreads) {

        // Find time and channel axes
        final int timeAxisIndex = input.dimensionIndex(Axes.TIME);
        final int channelAxisIndex = input.dimensionIndex(Axes.CHANNEL);

        // Get the number of time points and channels
        final long numTimePoints = (timeAxisIndex >= 0) ? input.dimension(timeAxisIndex) : 1;
        final long numChannels = (channelAxisIndex >= 0) ? input.dimension(channelAxisIndex) : 1;

        // Handle single-threaded case separately for simplicity
        if (numThreads <= 1) {
            processSingleThreaded(input, output, processor, timeAxisIndex, channelAxisIndex,
                    numTimePoints, numChannels);
            return;
        }

        // Multi-threaded processing
        processMultiThreaded(input, output, processor, timeAxisIndex, channelAxisIndex,
                numTimePoints, numChannels, numThreads);
    }

    /**
     * Process frames in a single thread.
     */
    private static <T extends RealType<T>> void processSingleThreaded(
            ImgPlus<T> input, ImgPlus<T> output, FrameProcessor<T> processor,
            int timeAxisIndex, int channelAxisIndex, long numTimePoints, long numChannels) {

        // Process each time point and channel
        for (int t = 0; t < numTimePoints; t++) {
            for (int c = 0; c < numChannels; c++) {
                processFrame(input, output, processor, t, c, timeAxisIndex, channelAxisIndex);
            }
        }
    }

    /**
     * Process frames using multiple threads.
     */
    private static <T extends RealType<T>> void processMultiThreaded(
            ImgPlus<T> input, ImgPlus<T> output, FrameProcessor<T> processor,
            int timeAxisIndex, int channelAxisIndex, long numTimePoints, long numChannels,
            int numThreads) {

        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            // Create tasks for all frames
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int t = 0; t < numTimePoints; t++) {
                for (int c = 0; c < numChannels; c++) {
                    final int finalT = t;
                    final int finalC = c;

                    tasks.add(() -> {
                        processFrame(input, output, processor, finalT, finalC,
                                timeAxisIndex, channelAxisIndex);
                        return null;
                    });
                }
            }

            // Execute all tasks
            List<Future<Void>> futures = executor.invokeAll(tasks);

            // Wait for all tasks to complete
            for (Future<Void> future : futures) {
                try {
                    future.get(); // Wait for task completion
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } finally {
            // Shutdown executor
            executor.shutdown();
        }
    }

    /**
     * Process a single frame at the specified time point and channel.
     */
    private static <T extends RealType<T>> void processFrame(
            ImgPlus<T> input, ImgPlus<T> output, FrameProcessor<T> processor,
            int t, int c, int timeAxisIndex, int channelAxisIndex) {

        // Extract the current frame
        RandomAccessibleInterval<T> inputFrame;
        RandomAccessibleInterval<T> outputFrame;

        if (timeAxisIndex >= 0 && channelAxisIndex >= 0) {
            // Both time and channel dimensions exist
            inputFrame = extractFrame(input, t, c, timeAxisIndex, channelAxisIndex);
            outputFrame = extractFrame(output, t, c, timeAxisIndex, channelAxisIndex);
        } else if (timeAxisIndex >= 0) {
            // Only time dimension exists
            inputFrame = Views.hyperSlice(input, timeAxisIndex, t);
            outputFrame = Views.hyperSlice(output, timeAxisIndex, t);
        } else if (channelAxisIndex >= 0) {
            // Only channel dimension exists
            inputFrame = Views.hyperSlice(input, channelAxisIndex, c);
            outputFrame = Views.hyperSlice(output, channelAxisIndex, c);
        } else {
            // Neither time nor channel dimension exists (just a 2D image)
            inputFrame = input;
            outputFrame = output;
        }

        // Apply the frame processor
        processor.process(inputFrame, outputFrame);
    }

    /**
     * Extract a 2D frame from a 4D image (x, y, time, channel).
     *
     * @param <T>              The pixel type
     * @param img              The 4D image
     * @param t                Time index
     * @param c                Channel index
     * @param timeAxisIndex    Index of the time dimension
     * @param channelAxisIndex Index of the channel dimension
     * @return A 2D view of the specified frame
     */
    private static <T extends RealType<T>> RandomAccessibleInterval<T> extractFrame(
            RandomAccessibleInterval<T> img, int t, int c,
            int timeAxisIndex, int channelAxisIndex) {

        // Handle the different possible dimension orderings
        if (timeAxisIndex < channelAxisIndex) {
            // Order is: (x, y, time, channel) or similar
            return Views.hyperSlice(
                    Views.hyperSlice(img, timeAxisIndex, t),
                    channelAxisIndex - 1, c);  // -1 because we removed one dimension
        } else {
            // Order is: (x, y, channel, time) or similar
            return Views.hyperSlice(
                    Views.hyperSlice(img, channelAxisIndex, c),
                    timeAxisIndex - 1, t);  // -1 because we removed one dimension
        }
    }

    /**
     * Subtract one image from another.
     *
     * @param <T>      The pixel type
     * @param minuend  The image to subtract from
     * @param subtrahend The image to subtract
     * @param result   The image to store the result in
     */
    private static <T extends RealType<T>> void subtractImages(
            RandomAccessibleInterval<T> minuend,
            RandomAccessibleInterval<T> subtrahend,
            RandomAccessibleInterval<T> result) {

        // Make the input iterable
        final IterableInterval<T> iterableMinuend = Views.iterable(minuend);

        // Create cursor for input
        final Cursor<T> minuendCursor = iterableMinuend.cursor();

        // Create random access for other images
        final RandomAccess<T> subtrahendRA = subtrahend.randomAccess();
        final RandomAccess<T> resultRA = result.randomAccess();

        // Iterate and subtract
        while (minuendCursor.hasNext()) {
            minuendCursor.fwd();
            subtrahendRA.setPosition(minuendCursor);
            resultRA.setPosition(minuendCursor);

            // Get values
            double minuendValue = minuendCursor.get().getRealDouble();
            double subtrahendValue = subtrahendRA.get().getRealDouble();

            // Subtract and ensure non-negative result
            double difference = Math.max(0, minuendValue - subtrahendValue);

            // Set result
            resultRA.get().setReal(difference);
        }
    }

    /**
     * Functional interface for processing a single frame.
     *
     * @param <T> The pixel type
     */
    @FunctionalInterface
    private interface FrameProcessor<T extends RealType<T>> {
        /**
         * Process a single frame.
         *
         * @param input  The input frame
         * @param output The output frame
         * @return True if processing was successful, false otherwise
         */
        boolean process(RandomAccessibleInterval<T> input, RandomAccessibleInterval<T> output);
    }
}
