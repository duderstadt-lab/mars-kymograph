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

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.RandomAccess;
import org.scijava.Context;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import net.imagej.ImgPlus;
import java.util.HashMap;
import java.util.Map;
import net.imglib2.img.Img;
import net.imglib2.view.Views;
import net.imglib2.RandomAccessibleInterval;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MontageBuilder {
    public enum FrameReductionMethod {
        NONE,      // Use all frames
        SKIP,      // Skip frames (e.g., every Nth frame)
        AVERAGE,   // Average groups of frames
        SUM        // Sum groups of frames
    }

    public enum ImageFilterMethod {
        NONE,      // No filter
        MEDIAN,    // Median filter
        GAUSSIAN,  // Gaussian filter
        TOPHAT     // Top-hat filter
    }

    @Parameter
    private ConvertService convertService;

    @Parameter
    private DatasetService datasetService;

    @Parameter
    private LogService logService;

    private ImgPlus<?> sourceImgPlus;
    private Dataset montage;
    private int spacing = 0; // Spacing between frames
    private int minT = -1;
    private int maxT = -1;
    private boolean horizontalLayout = true; // Default layout is horizontal (frames side by side)
    private int columns = -1; // Number of columns in grid layout (-1 means single row or column based on layout)
    private FrameReductionMethod reductionMethod = FrameReductionMethod.NONE;
    private int reductionFactor = 1; // Used for skipping or grouping frames
    private ImageFilterMethod filterMethod = ImageFilterMethod.NONE;
    private double filterSize = 2;
    private Map<Integer, ChannelFilterConfig> channelFilters = new HashMap<>(); // Channel-specific filters
    private boolean useGlobalFilter = true; // Whether to apply global filter to all channels

    private boolean verticalReflection = false; // Whether to vertically flip the frames
    private boolean horizontalReflection = false; // Whether to horizontally flip the frames
    private int numThreads = 1; // Number of threads for filtering operations
    private double interpolationFactor = 1.0; // Interpolation factor for increasing resolution

    public MontageBuilder(Context context) {
        context.inject(this);
    }

    /**
     * Set the source image plus to use for building the montage
     *
     * @param imgPlus The source image to use
     * @return This builder for method chaining
     */
    public MontageBuilder setSource(ImgPlus<?> imgPlus) {
        this.sourceImgPlus = imgPlus;
        return this;
    }

    /**
     * Set spacing between frames in the montage
     *
     * @param spacing The spacing in pixels
     * @return This builder for method chaining
     */
    public MontageBuilder setSpacing(int spacing) {
        this.spacing = spacing;
        return this;
    }

    /**
     * Set the minimum time point to include
     *
     * @param minT The minimum time point
     * @return This builder for method chaining
     */
    public MontageBuilder setMinT(int minT) {
        this.minT = minT;
        return this;
    }

    /**
     * Set the maximum time point to include
     *
     * @param maxT The maximum time point
     * @return This builder for method chaining
     */
    public MontageBuilder setMaxT(int maxT) {
        this.maxT = maxT;
        return this;
    }

    /**
     * Set the layout orientation
     *
     * @param horizontal True for horizontal layout (left to right), false for vertical (top to bottom)
     * @return This builder for method chaining
     */
    public MontageBuilder setHorizontalLayout(boolean horizontal) {
        this.horizontalLayout = horizontal;
        return this;
    }

    /**
     * Set the number of columns for grid layout
     * If not set or set to -1, frames will be arranged in a single row or column
     * based on the layout orientation.
     *
     * @param columns Number of columns
     * @return This builder for method chaining
     */
    public MontageBuilder setColumns(int columns) {
        this.columns = columns;
        return this;
    }

    /**
     * Set the frame reduction method to skip frames
     * This will include only every Nth frame in the montage
     *
     * @param skipFactor Include every Nth frame (e.g., 5 means include frames 0, 5, 10, etc.)
     * @return This builder for method chaining
     */
    public MontageBuilder skipFrames(int skipFactor) {
        if (skipFactor < 1) {
            skipFactor = 1; // Prevent invalid values
        }
        this.reductionMethod = FrameReductionMethod.SKIP;
        this.reductionFactor = skipFactor;
        return this;
    }

    /**
     * Set the frame reduction method to average frames
     * This will average groups of N frames to create each montage frame
     *
     * @param groupSize Number of frames to average together
     * @return This builder for method chaining
     */
    public MontageBuilder averageFrames(int groupSize) {
        if (groupSize < 1) {
            groupSize = 1; // Prevent invalid values
        }
        this.reductionMethod = FrameReductionMethod.AVERAGE;
        this.reductionFactor = groupSize;
        return this;
    }

    /**
     * Set the frame reduction method to sum frames
     * This will sum groups of N frames to create each montage frame
     *
     * @param groupSize Number of frames to sum together
     * @return This builder for method chaining
     */
    public MontageBuilder sumFrames(int groupSize) {
        if (groupSize < 1) {
            groupSize = 1; // Prevent invalid values
        }
        this.reductionMethod = FrameReductionMethod.SUM;
        this.reductionFactor = groupSize;
        return this;
    }

    /**
     * Reset frame reduction to use all frames without reduction
     *
     * @return This builder for method chaining
     */
    public MontageBuilder useAllFrames() {
        this.reductionMethod = FrameReductionMethod.NONE;
        this.reductionFactor = 1;
        return this;
    }

    /**
     * Enable or disable vertical reflection (flipping) of the frames
     *
     * @param reflect True to vertically reflect frames, false for normal orientation
     * @return This builder for method chaining
     */
    public MontageBuilder setVerticalReflection(boolean reflect) {
        this.verticalReflection = reflect;
        return this;
    }

    /**
     * Enable or disable horizontal reflection (flipping) of the frames
     *
     * @param reflect True to horizontally reflect frames, false for normal orientation
     * @return This builder for method chaining
     */
    public MontageBuilder setHorizontalReflection(boolean reflect) {
        this.horizontalReflection = reflect;
        return this;
    }

    /**
     * Set the filter method to median filter
     *
     * @param radius The radius of the median filter
     * @return This builder for method chaining
     */
    public MontageBuilder medianFilter(int radius) {
        this.filterMethod = ImageFilterMethod.MEDIAN;
        this.filterSize = radius;
        return this;
    }

    /**
     * Set the filter method to median filter for a specific channel
     *
     * @param radius The radius of the median filter
     * @param channel The channel number to apply the filter to (1-based: 1, 2, 3, etc.)
     * @return This builder for method chaining
     */
    public MontageBuilder medianFilter(int radius, int channel) {
        if (channel < 1) {
            logService.warn("Channel number must be >= 1. Using channel 1 instead of " + channel);
            channel = 1;
        }
        // Convert to 0-based index for internal storage
        channelFilters.put(channel - 1, new ChannelFilterConfig(ImageFilterMethod.MEDIAN, radius));
        useGlobalFilter = false; // Switch to channel-specific filtering
        return this;
    }

    /**
     * Set the filter method to Gaussian filter
     *
     * @param sigma The sigma value for the Gaussian filter
     * @return This builder for method chaining
     */
    public MontageBuilder gaussianFilter(double sigma) {
        this.filterMethod = ImageFilterMethod.GAUSSIAN;
        this.filterSize = sigma;
        return this;
    }

    /**
     * Set the filter method to Gaussian filter for a specific channel
     *
     * @param sigma The sigma value for the Gaussian filter
     * @param channel The channel number to apply the filter to (1-based: 1, 2, 3, etc.)
     * @return This builder for method chaining
     */
    public MontageBuilder gaussianFilter(double sigma, int channel) {
        if (channel < 1) {
            logService.warn("Channel number must be >= 1. Using channel 1 instead of " + channel);
            channel = 1;
        }
        // Convert to 0-based index for internal storage
        channelFilters.put(channel - 1, new ChannelFilterConfig(ImageFilterMethod.GAUSSIAN, sigma));
        useGlobalFilter = false; // Switch to channel-specific filtering
        return this;
    }

    /**
     * Set the filter method to top-hat filter
     *
     * @param radius The radius of the top-hat filter
     * @return This builder for method chaining
     */
    public MontageBuilder tophatFilter(int radius) {
        this.filterMethod = ImageFilterMethod.TOPHAT;
        this.filterSize = radius;
        return this;
    }


    /**
     * Set the filter method to top-hat filter for a specific channel
     *
     * @param radius The radius of the top-hat filter
     * @param channel The channel number to apply the filter to (1-based: 1, 2, 3, etc.)
     * @return This builder for method chaining
     */
    public MontageBuilder tophatFilter(int radius, int channel) {
        if (channel < 1) {
            logService.warn("Channel number must be >= 1. Using channel 1 instead of " + channel);
            channel = 1;
        }
        // Convert to 0-based index for internal storage
        channelFilters.put(channel - 1, new ChannelFilterConfig(ImageFilterMethod.TOPHAT, radius));
        useGlobalFilter = false; // Switch to channel-specific filtering
        return this;
    }


    /**
     * Clear all channel-specific filters and reset to global filtering mode
     *
     * @return This builder for method chaining
     */
    public MontageBuilder clearChannelFilters() {
        channelFilters.clear();
        useGlobalFilter = true;
        return this;
    }

    /**
     * Set the number of threads to use for filter operations
     *
     * @param numThreads The number of threads
     * @return This builder for method chaining
     */
    public MontageBuilder threads(int numThreads) {
        if (numThreads < 1) {
            numThreads = 1;
        }
        this.numThreads = numThreads;
        return this;
    }

    /**
     * Enable interpolation to increase resolution
     *
     * @param factor The factor by which to increase resolution
     * @return This builder for method chaining
     */
    public MontageBuilder interpolation(double factor) {
        if (factor <= 0) {
            factor = 1.0;
        }
        this.interpolationFactor = factor;
        return this;
    }

    /**
     * Build the montage
     *
     * @return The montage dataset
     */
    public Dataset build() {
        if (sourceImgPlus == null) {
            logService.error("Source image not set. Use setSource() to set the source image.");
            return null;
        }

        // Get dataset from source
        Dataset sourceDataset = null;
        try {
            sourceDataset = convertService.convert(sourceImgPlus, Dataset.class);
        } catch (Exception e) {
            logService.error("Error creating dataset from image: " + e.getMessage());
            return null;
        }

        // Apply filtering if requested
        ImgPlus<?> processedImgPlus = sourceImgPlus;

        // Apply filter if requested
        if (shouldApplyFiltering()) {
            try {
                // Use a helper method to apply the appropriate filter
                processedImgPlus = applyFilterToImage(processedImgPlus);

                // Update source dataset after filtering
                sourceDataset = convertService.convert(processedImgPlus, Dataset.class);
            } catch (Exception e) {
                logService.error("Error applying filter: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Apply interpolation if requested
        if (interpolationFactor > 1.0) {
            try {
                // Use a helper method to apply interpolation
                processedImgPlus = increaseImageResolution(processedImgPlus, interpolationFactor);

                // Update source dataset after interpolation
                sourceDataset = convertService.convert(processedImgPlus, Dataset.class);
            } catch (Exception e) {
                logService.error("Error applying interpolation: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Get dimensions from source dataset
        int frameWidth = (int)sourceDataset.dimension(sourceDataset.dimensionIndex(Axes.X));
        int frameHeight = (int)sourceDataset.dimension(sourceDataset.dimensionIndex(Axes.Y));
        int timePoints = (int)sourceDataset.dimension(sourceDataset.dimensionIndex(Axes.TIME));
        int startT = (minT != -1) ? minT : 0;
        int endT = (maxT != -1 && maxT < timePoints) ? maxT : timePoints - 1;

        // Apply frame reduction based on the selected method
        List<Integer> framesToInclude = new ArrayList<>();
        List<List<Integer>> frameGroups = new ArrayList<>();

        switch (reductionMethod) {
            case SKIP:
                // Include only every Nth frame
                for (int t = startT; t <= endT; t += reductionFactor) {
                    framesToInclude.add(t);
                }
                break;

            case AVERAGE:
            case SUM:
                // Group frames for averaging or summing
                java.util.List<Integer> currentGroup = new java.util.ArrayList<>();
                for (int t = startT; t <= endT; t++) {
                    currentGroup.add(t);
                    if (currentGroup.size() >= reductionFactor || t == endT) {
                        frameGroups.add(new java.util.ArrayList<>(currentGroup));
                        currentGroup.clear();
                    }
                }
                // If there are remaining frames in the last group, add them
                if (!currentGroup.isEmpty()) {
                    frameGroups.add(currentGroup);
                }
                break;

            case NONE:
            default:
                // Include all frames
                for (int t = startT; t <= endT; t++) {
                    framesToInclude.add(t);
                }
                break;
        }

        int totalMontageFrames = (reductionMethod == FrameReductionMethod.AVERAGE ||
                reductionMethod == FrameReductionMethod.SUM) ?
                frameGroups.size() : framesToInclude.size();

        // Determine layout (rows and columns)
        int cols = (columns > 0) ? Math.min(columns, totalMontageFrames) :
                (horizontalLayout ? totalMontageFrames : 1);
        int rows = (int)Math.ceil((double)totalMontageFrames / cols);

        // Calculate montage dimensions
        int montageWidth = cols * frameWidth + (cols - 1) * spacing;
        int montageHeight = rows * frameHeight + (rows - 1) * spacing;

        // Create the montage dataset
        long[] dimensions;
        AxisType[] axisTypes;

        if (sourceDataset.dimensionIndex(Axes.CHANNEL) != -1) {
            // Include channel dimension
            dimensions = new long[3];
            dimensions[0] = montageWidth;
            dimensions[1] = montageHeight;
            dimensions[2] = sourceDataset.dimension(sourceDataset.dimensionIndex(Axes.CHANNEL));
            axisTypes = new AxisType[] { Axes.X, Axes.Y, Axes.CHANNEL };
        } else {
            // No channel dimension
            dimensions = new long[2];
            dimensions[0] = montageWidth;
            dimensions[1] = montageHeight;
            axisTypes = new AxisType[] { Axes.X, Axes.Y };
        }

        String title = sourceDataset.getName() + " (Montage)";
        montage = datasetService.create(dimensions, title, axisTypes,
                sourceDataset.getValidBits(), sourceDataset.isSigned(), !sourceDataset.isInteger());

        // Copy frames to the montage
        int channelDim = sourceDataset.dimensionIndex(Axes.CHANNEL);
        int channels = (channelDim != -1) ? (int)sourceDataset.dimension(channelDim) : 1;

        try {
            RandomAccess<?> sourceRA = sourceDataset.getImgPlus().randomAccess();
            RandomAccess<?> montageRA = montage.getImgPlus().randomAccess();

            if (reductionMethod == FrameReductionMethod.AVERAGE || reductionMethod == FrameReductionMethod.SUM) {
                // Process frame groups (for averaging or summing)
                for (int groupIndex = 0; groupIndex < frameGroups.size(); groupIndex++) {
                    java.util.List<Integer> group = frameGroups.get(groupIndex);

                    int row = groupIndex / cols;
                    int col = groupIndex % cols;

                    int xOffset = col * (frameWidth + spacing);
                    int yOffset = row * (frameHeight + spacing);

                    // For each pixel position, process all frames in the group
                    for (int y = 0; y < frameHeight; y++) {
                        for (int x = 0; x < frameWidth; x++) {
                            for (int c = 0; c < channels; c++) {
                                // Initialize accumulator for averaging or summing
                                double accumulator = 0.0;

                                // Process each frame in the group
                                for (Integer t : group) {
                                    // Set source position
                                    sourceRA.setPosition(horizontalReflection ? (frameWidth - 1 - x) : x, sourceDataset.dimensionIndex(Axes.X));
                                    sourceRA.setPosition(verticalReflection ? (frameHeight - 1 - y) : y, sourceDataset.dimensionIndex(Axes.Y));
                                    sourceRA.setPosition(t, sourceDataset.dimensionIndex(Axes.TIME));

                                    // Handle channel dimension if present
                                    if (channelDim != -1) {
                                        sourceRA.setPosition(c, channelDim);
                                    }

                                    // Add pixel value to accumulator
                                    if (sourceRA.get() instanceof net.imglib2.type.numeric.RealType) {
                                        accumulator += ((net.imglib2.type.numeric.RealType<?>)sourceRA.get()).getRealDouble();
                                    }
                                }

                                // Calculate final value based on reduction method
                                double finalValue;
                                if (reductionMethod == FrameReductionMethod.AVERAGE) {
                                    finalValue = accumulator / group.size();
                                } else {
                                    finalValue = accumulator; // SUM
                                }

                                // Set montage position
                                montageRA.setPosition(x + xOffset, 0); // X axis
                                montageRA.setPosition(y + yOffset, 1); // Y axis

                                // Handle channel dimension if present
                                if (channelDim != -1) {
                                    montageRA.setPosition(c, 2); // Channel is the third dimension in montage
                                }

                                // Set pixel value in montage
                                if (montageRA.get() instanceof net.imglib2.type.numeric.RealType) {
                                    ((net.imglib2.type.numeric.RealType<?>)montageRA.get()).setReal(finalValue);
                                }
                            }
                        }
                    }
                }
            } else {
                // Process individual frames (NONE or SKIP methods)
                for (int frameIndex = 0; frameIndex < framesToInclude.size(); frameIndex++) {
                    int t = framesToInclude.get(frameIndex);

                    int row = frameIndex / cols;
                    int col = frameIndex % cols;

                    int xOffset = col * (frameWidth + spacing);
                    int yOffset = row * (frameHeight + spacing);

                    // Copy each pixel from source frame to montage
                    for (int y = 0; y < frameHeight; y++) {
                        for (int x = 0; x < frameWidth; x++) {
                            for (int c = 0; c < channels; c++) {
                                // Set source position
                                sourceRA.setPosition(horizontalReflection ? (frameWidth - 1 - x) : x, sourceDataset.dimensionIndex(Axes.X));
                                sourceRA.setPosition(verticalReflection ? (frameHeight - 1 - y) : y, sourceDataset.dimensionIndex(Axes.Y));
                                sourceRA.setPosition(t, sourceDataset.dimensionIndex(Axes.TIME));

                                // Handle channel dimension if present
                                if (channelDim != -1) {
                                    sourceRA.setPosition(c, channelDim);
                                    montageRA.setPosition(c, 2); // Channel is the third dimension in montage
                                }

                                // Set montage position
                                montageRA.setPosition(x + xOffset, 0); // X axis
                                montageRA.setPosition(y + yOffset, 1); // Y axis

                                // Copy pixel value using setReal for numeric types
                                if (montageRA.get() instanceof net.imglib2.type.numeric.RealType &&
                                        sourceRA.get() instanceof net.imglib2.type.numeric.RealType) {
                                    ((net.imglib2.type.numeric.RealType<?>)montageRA.get()).setReal(
                                            ((net.imglib2.type.numeric.RealType<?>)sourceRA.get()).getRealDouble());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logService.error("Error creating montage: " + e.getMessage());
            e.printStackTrace();
        }

        return montage;
    }

    /**
     * Check if any filtering should be applied to the image.
     * This includes both global filters and channel-specific filters.
     *
     * @return true if any filtering should be applied, false otherwise
     */
    private boolean shouldApplyFiltering() {
        return (useGlobalFilter && filterMethod != ImageFilterMethod.NONE) ||
                (!useGlobalFilter && !channelFilters.isEmpty());
    }

    public Dataset getMontage() {
        return montage;
    }

    /**
     * Helper method to apply the appropriate filter to the image.
     * Uses generic methods to properly handle type parameters.
     * Supports both global filtering (all channels) and channel-specific filtering.
     *
     * @param image The image to filter
     * @return The filtered image
     */
    @SuppressWarnings("unchecked")
    private <T extends net.imglib2.type.numeric.RealType<T>> ImgPlus<?> applyFilterToImage(ImgPlus<?> image) {
        // First check if the input image contains a RealType
        Object type = image.firstElement();
        if (!(type instanceof net.imglib2.type.numeric.RealType)) {
            logService.error("Image does not contain a RealType. Cannot apply filter.");
            return image;
        }

        try {
            // This is a type-safe operation because we've checked the element type
            ImgPlus<T> typedImage = (ImgPlus<T>) image;
            if (useGlobalFilter && filterMethod != ImageFilterMethod.NONE) {
                // Apply global filter to all channels
                switch (filterMethod) {
                    case MEDIAN:
                        return MarsKymographUtils.applyMedianFilter(
                                typedImage, (int)filterSize, numThreads);
                    case GAUSSIAN:
                        return MarsKymographUtils.applyGaussianFilter(
                                typedImage, filterSize, numThreads);
                    case TOPHAT:
                        return MarsKymographUtils.applyTopHatFilter(
                                typedImage, (int)filterSize, numThreads);
                    default:
                        return image;
                }
            } else if (!useGlobalFilter && !channelFilters.isEmpty()) {
                // Apply channel-specific filters
                return applyChannelSpecificFilters(typedImage);
            }

            return image; // No filtering needed
        } catch (Exception e) {
            logService.error("Failed to apply filter: " + e.getMessage());
            return image; // Return original if filtering fails
        }
    }

    /**
     * Helper method to apply channel-specific filters to the image.
     * Creates a copy of the image and applies filters in-place to specific channels.
     *
     * @param image The image to filter
     * @return The filtered image
     */
    @SuppressWarnings("unchecked")
    private <T extends net.imglib2.type.numeric.RealType<T>> ImgPlus<T> applyChannelSpecificFilters(ImgPlus<T> image) {
        try {
            // Convert to dataset to get channel information
            Dataset dataset = convertService.convert(image, Dataset.class);
            int channelDim = dataset.dimensionIndex(Axes.CHANNEL);

            if (channelDim == -1) {
                // No channel dimension, treat as single channel (user would specify channel 1, which becomes index 0)
                ChannelFilterConfig config = channelFilters.get(0); // Still use 0-based internally
                if (config != null) {
                    return applySingleChannelFilter(image, config);
                }
                return image;
            }

            int totalChannels = (int)dataset.dimension(channelDim);

            // Create a copy of the image to work on
            ImgPlus<T> result = image.copy();

            // Apply filters to each specified channel
            for (Map.Entry<Integer, ChannelFilterConfig> entry : channelFilters.entrySet()) {
                int channelIndex = entry.getKey(); // This is already 0-based from our conversion above
                ChannelFilterConfig config = entry.getValue();

                // Validate channel index (0-based) against available channels
                if (channelIndex >= 0 && channelIndex < totalChannels) {
                    // Apply filter to this specific channel in-place
                    applyFilterToChannel(result, channelIndex, channelDim, config);
                } else {
                    // Convert back to 1-based for user-friendly error message
                    logService.warn("Channel " + (channelIndex + 1) + " is out of range. Total channels: " + totalChannels);
                }
            }

            return result;
        } catch (Exception e) {
            logService.error("Failed to apply channel-specific filters: " + e.getMessage());
            return image; // Return original if filtering fails
        }
    }

    /**
     * Apply a filter to a specific channel of a multi-channel image in-place.
     *
     * @param image The multi-channel image to modify
     * @param channelIndex The index of the channel to filter
     * @param channelDim The dimension index of the channel axis
     * @param config The filter configuration
     */
    private <T extends net.imglib2.type.numeric.RealType<T>> void applyFilterToChannel(
            ImgPlus<T> image, int channelIndex, int channelDim, ChannelFilterConfig config) {

        switch (config.method) {
            case MEDIAN:
                applyMedianFilterToChannel(image, channelIndex, channelDim, (int)config.size);
                break;
            case GAUSSIAN:
                applyGaussianFilterToChannel(image, channelIndex, channelDim, config.size);
                break;
            case TOPHAT:
                applyTopHatFilterToChannel(image, channelIndex, channelDim, (int)config.size);
                break;
            default:
                // No filter to apply
                break;
        }
    }

    /**
     * Apply a Gaussian filter to a specific channel of a multi-channel image.
     *
     * @param image The multi-channel image
     * @param channelIndex The channel to filter
     * @param channelDim The dimension index of the channel axis
     * @param sigma The sigma parameter for the Gaussian filter
     */
    private <T extends net.imglib2.type.numeric.RealType<T>> void applyGaussianFilterToChannel(
            ImgPlus<T> image, int channelIndex, int channelDim, double sigma) {

        try {
            // Extract the specific channel as a view
            RandomAccessibleInterval<T> channelView = Views.hyperSlice(image, channelDim, channelIndex);

            // Create a temporary single-channel ImgPlus for filtering
            ImgPlus<T> channelImgPlus = createSingleChannelImgPlus(channelView, image);

            // Apply the filter
            ImgPlus<T> filteredChannel = MarsKymographUtils.applyGaussianFilter(
                    channelImgPlus, sigma, numThreads);

            // Copy the filtered result back to the original channel
            copyFilteredDataBack(filteredChannel, channelView);

        } catch (Exception e) {
            logService.error("Failed to apply Gaussian filter to channel " + channelIndex + ": " + e.getMessage());
        }
    }

    /**
     * Apply a median filter to a specific channel of a multi-channel image.
     *
     * @param image The multi-channel image
     * @param channelIndex The channel to filter
     * @param channelDim The dimension index of the channel axis
     * @param radius The radius parameter for the median filter
     */
    private <T extends net.imglib2.type.numeric.RealType<T>> void applyMedianFilterToChannel(
            ImgPlus<T> image, int channelIndex, int channelDim, int radius) {

        try {
            // Extract the specific channel as a view
            RandomAccessibleInterval<T> channelView = Views.hyperSlice(image, channelDim, channelIndex);

            // Create a temporary single-channel ImgPlus for filtering
            ImgPlus<T> channelImgPlus = createSingleChannelImgPlus(channelView, image);

            // Apply the filter
            ImgPlus<T> filteredChannel = MarsKymographUtils.applyMedianFilter(
                    channelImgPlus, radius, numThreads);

            // Copy the filtered result back to the original channel
            copyFilteredDataBack(filteredChannel, channelView);

        } catch (Exception e) {
            logService.error("Failed to apply median filter to channel " + channelIndex + ": " + e.getMessage());
        }
    }

    /**
     * Apply a top-hat filter to a specific channel of a multi-channel image.
     *
     * @param image The multi-channel image
     * @param channelIndex The channel to filter
     * @param channelDim The dimension index of the channel axis
     * @param radius The radius parameter for the top-hat filter
     */
    private <T extends net.imglib2.type.numeric.RealType<T>> void applyTopHatFilterToChannel(
            ImgPlus<T> image, int channelIndex, int channelDim, int radius) {

        try {
            // Extract the specific channel as a view
            RandomAccessibleInterval<T> channelView = Views.hyperSlice(image, channelDim, channelIndex);

            // Create a temporary single-channel ImgPlus for filtering
            ImgPlus<T> channelImgPlus = createSingleChannelImgPlus(channelView, image);

            // Apply the filter
            ImgPlus<T> filteredChannel = MarsKymographUtils.applyTopHatFilter(
                    channelImgPlus, radius, numThreads);

            // Copy the filtered result back to the original channel
            copyFilteredDataBack(filteredChannel, channelView);

        } catch (Exception e) {
            logService.error("Failed to apply top-hat filter to channel " + channelIndex + ": " + e.getMessage());
        }
    }

    /**
     * Create a single-channel ImgPlus from a channel view for filter processing.
     *
     * @param channelView The channel view to wrap
     * @param originalImage The original image for metadata
     * @return A single-channel ImgPlus
     */
    private <T extends net.imglib2.type.numeric.RealType<T>> ImgPlus<T> createSingleChannelImgPlus(
            RandomAccessibleInterval<T> channelView, ImgPlus<T> originalImage) {

        // Create an Img from the view
        Img<T> channelImg = originalImage.factory().create(channelView);

        // Copy the data from the view to the new image
        net.imglib2.Cursor<T> sourceCursor = Views.iterable(channelView).cursor();
        net.imglib2.Cursor<T> targetCursor = channelImg.cursor();

        while (sourceCursor.hasNext() && targetCursor.hasNext()) {
            targetCursor.next().set(sourceCursor.next());
        }

        // Create ImgPlus with appropriate axes (excluding channel axis)
        ImgPlus<T> result = new ImgPlus<>(channelImg);

        // Copy relevant axes (excluding channel axis)
        int axisIndex = 0;
        for (int i = 0; i < originalImage.numDimensions(); i++) {
            if (originalImage.axis(i).type() != Axes.CHANNEL) {
                result.setAxis(originalImage.axis(i), axisIndex++);
            }
        }

        return result;
    }


    /**
     * Helper method to apply a filter to a single-channel image.
     *
     * @param image The image to filter
     * @param config The filter configuration
     * @return The filtered image
     */
    private <T extends net.imglib2.type.numeric.RealType<T>> ImgPlus<T> applySingleChannelFilter(
            ImgPlus<T> image, ChannelFilterConfig config) {
        switch (config.method) {
            case MEDIAN:
                return MarsKymographUtils.applyMedianFilter(image, (int)config.size, numThreads);
            case GAUSSIAN:
                return MarsKymographUtils.applyGaussianFilter(image, config.size, numThreads);
            case TOPHAT:
                return MarsKymographUtils.applyTopHatFilter(image, (int)config.size, numThreads);
            default:
                return image;
        }
    }

    /**
     * Copy filtered data back to the original channel view.
     *
     * @param filteredData The filtered single-channel image
     * @param targetView The target channel view to copy to
     */
    private <T extends net.imglib2.type.numeric.RealType<T>> void copyFilteredDataBack(
            ImgPlus<T> filteredData, RandomAccessibleInterval<T> targetView) {

        net.imglib2.Cursor<T> sourceCursor = filteredData.cursor();
        net.imglib2.Cursor<T> targetCursor = Views.iterable(targetView).cursor();

        while (sourceCursor.hasNext() && targetCursor.hasNext()) {
            targetCursor.next().set(sourceCursor.next());
        }
    }

    /**
     * Helper method to increase image resolution.
     * Uses generic methods to properly handle type parameters.
     *
     * @param image The image to interpolate
     * @param factor The resolution increase factor
     * @return The interpolated image
     */
    @SuppressWarnings("unchecked")
    private <T extends net.imglib2.type.numeric.RealType<T>> ImgPlus<?> increaseImageResolution(
            ImgPlus<?> image, double factor) {
        // First check if the input image contains a RealType
        Object type = image.firstElement();
        if (!(type instanceof net.imglib2.type.numeric.RealType)) {
            logService.error("Image does not contain a RealType. Cannot increase resolution.");
            return image;
        }

        try {
            // This is a type-safe operation because we've checked the element type
            ImgPlus<T> typedImage = (ImgPlus<T>) image;
            return MarsKymographUtils.increaseResolution(typedImage, factor);
        } catch (Exception e) {
            logService.error("Failed to increase resolution: " + e.getMessage());
            return image; // Return original if interpolation fails
        }
    }

    public static class ChannelFilterConfig {
        public final ImageFilterMethod method;
        public final double size;

        public ChannelFilterConfig(ImageFilterMethod method, double size) {
            this.method = method;
            this.size = size;
        }
    }
}
