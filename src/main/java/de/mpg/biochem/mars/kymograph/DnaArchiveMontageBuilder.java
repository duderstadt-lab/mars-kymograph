/*-
 * #%L
 * Mars kymograph builder.
 * %%
 * Copyright (C) 2023 Karl Duderstadt
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

import de.mpg.biochem.mars.molecule.*;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.util.Intervals;
import org.scijava.Context;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import net.imagej.ImgPlus;

public class DnaArchiveMontageBuilder {
    public enum FrameReductionMethod {
        NONE,      // Use all frames
        SKIP,      // Skip frames (e.g., every Nth frame)
        AVERAGE,   // Average groups of frames
        SUM        // Sum groups of frames
    }

    @Parameter
    private Context context;

    @Parameter
    private ConvertService convertService;

    @Parameter
    private DatasetService datasetService;

    @Parameter
    private LogService logService;

    private DnaMoleculeArchive dnaMoleculeArchive;
    private DnaMolecule dnaMolecule;

    private Dataset montage;
    private int borderWidth = 10;
    private int borderHeight = 10;
    private int spacing = 5; // Spacing between frames
    private int minT = -1;
    private int maxT = -1;
    private boolean horizontalLayout = true; // Default layout is horizontal (frames side by side)
    private int columns = -1; // Number of columns in grid layout (-1 means single row or column based on layout)
    private FrameReductionMethod reductionMethod = FrameReductionMethod.NONE;
    private int reductionFactor = 1; // Used for skipping or grouping frames
    private boolean verticalReflection = false; // Whether to vertically flip the frames

    public DnaArchiveMontageBuilder(Context context, DnaMoleculeArchive dnaMoleculeArchive) {
        context.inject(this);
        this.dnaMoleculeArchive = dnaMoleculeArchive;
    }

    public DnaArchiveMontageBuilder setMolecule(DnaMolecule dnaMolecule) {
        this.dnaMolecule = dnaMolecule;
        return this;
    }

    public DnaArchiveMontageBuilder setMolecule(String UID) {
        this.dnaMolecule = dnaMoleculeArchive.get(UID);
        return this;
    }

    public DnaArchiveMontageBuilder setBorderWidth(int width) {
        this.borderWidth = width;
        return this;
    }

    public DnaArchiveMontageBuilder setBorderHeight(int height) {
        this.borderHeight = height;
        return this;
    }

    public DnaArchiveMontageBuilder setSpacing(int spacing) {
        this.spacing = spacing;
        return this;
    }

    public DnaArchiveMontageBuilder setMinT(int minT) {
        this.minT = minT;
        return this;
    }

    public DnaArchiveMontageBuilder setMaxT(int maxT) {
        this.maxT = maxT;
        return this;
    }

    public DnaArchiveMontageBuilder setHorizontalLayout(boolean horizontal) {
        this.horizontalLayout = horizontal;
        return this;
    }

    public DnaArchiveMontageBuilder setColumns(int columns) {
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
    public DnaArchiveMontageBuilder skipFrames(int skipFactor) {
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
    public DnaArchiveMontageBuilder averageFrames(int groupSize) {
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
    public DnaArchiveMontageBuilder sumFrames(int groupSize) {
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
    public DnaArchiveMontageBuilder useAllFrames() {
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
    public DnaArchiveMontageBuilder setVerticalReflection(boolean reflect) {
        this.verticalReflection = reflect;
        return this;
    }

    public Dataset build() {
        // Get the source dataset using MarsIntervalExporter
        MarsIntervalExporter exporter = new MarsIntervalExporter(context, dnaMoleculeArchive);

        if (minT != -1) exporter.setMinT(minT);
        if (maxT != -1) exporter.setMaxT(maxT);

        // Define the interval around the DNA with borders
        final int minX = (int)Math.min(dnaMolecule.getParameter("Dna_Top_X1"), dnaMolecule.getParameter("Dna_Bottom_X2")) - borderWidth;
        final int maxX = (int)Math.max(dnaMolecule.getParameter("Dna_Top_X1"), dnaMolecule.getParameter("Dna_Bottom_X2")) + borderWidth;
        final int minY = (int)Math.min(dnaMolecule.getParameter("Dna_Top_Y1"), dnaMolecule.getParameter("Dna_Bottom_Y2")) - borderHeight;
        final int maxY = (int)Math.max(dnaMolecule.getParameter("Dna_Top_Y1"), dnaMolecule.getParameter("Dna_Bottom_Y2")) + borderHeight;

        Interval interval = Intervals.createMinMax(minX, minY, maxX, maxY);
        ImgPlus imgPlus = exporter.setMolecule(dnaMolecule).setInterval(interval).build();

        // Convert ImgPlus to Dataset
        Dataset sourceDataset = convertService.convert(imgPlus, Dataset.class);

        // Get dimensions from source dataset
        int frameWidth = (int)(maxX - minX + 1);
        int frameHeight = (int)(maxY - minY + 1);
        int timePoints = (int)sourceDataset.dimension(sourceDataset.dimensionIndex(Axes.TIME));
        int startT = (minT != -1) ? minT : 0;
        int endT = (maxT != -1 && maxT < timePoints) ? maxT : timePoints - 1;

        // Apply frame reduction based on the selected method
        java.util.List<Integer> framesToInclude = new java.util.ArrayList<>();
        java.util.List<java.util.List<Integer>> frameGroups = new java.util.ArrayList<>();

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
                                    sourceRA.setPosition(x, sourceDataset.dimensionIndex(Axes.X));
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
                                sourceRA.setPosition(x, sourceDataset.dimensionIndex(Axes.X));
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

    public Dataset getMontage() {
        return montage;
    }
}
