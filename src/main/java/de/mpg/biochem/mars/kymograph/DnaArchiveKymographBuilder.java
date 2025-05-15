/*-
 * #%L
 * Mars kymograph builder.
 * %%
 * Copyright (C) 2023 - 2024 Karl Duderstadt
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
import ij.gui.Line;
import net.imagej.Dataset;


import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import org.scijava.Context;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;

import net.imagej.ImgPlus;


public class DnaArchiveKymographBuilder
{
    @Parameter
    private Context context;

    @Parameter
    private ConvertService convertService;

    private DnaMoleculeArchive dnaMoleculeArchive;

    private DnaMolecule dnaMolecule;

    private Dataset kymograph;
    private int width = 1;
    private int minT = -1;
    private int maxT = -1;

    private FrameReductionMethod reductionMethod = FrameReductionMethod.NONE;
    private int reductionFactor = 1; // Used for skipping or grouping frames
    private ImageFilterMethod filterMethod = ImageFilterMethod.NONE;
    private double filterSize = 2;
    private boolean verticalReflection = false; // Whether to vertically flip the frames
    private int numThreads = 1; // Number of threads for filtering operations
    private double interpolationFactor = 1.0; // Interpolation factor for increasing resolution

    // Add the FrameReductionMethod and ImageFilterMethod enums
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

    public DnaArchiveKymographBuilder(Context context, DnaMoleculeArchive dnaMoleculeArchive) {
        context.inject(this);
        this.dnaMoleculeArchive = dnaMoleculeArchive;
    }

    public DnaArchiveKymographBuilder setMolecule(DnaMolecule dnaMolecule) {
        this.dnaMolecule = dnaMolecule;
        return this;
    }

    public DnaArchiveKymographBuilder setMolecule(String UID) {
        this.dnaMolecule = dnaMoleculeArchive.get(UID);
        return this;
    }

    public DnaArchiveKymographBuilder setProjectionWidth(int width) {
        this.width = width;
        return this;
    }

    public DnaArchiveKymographBuilder setMinT(int minT) {
        this.minT = minT;
        return this;
    }

    public DnaArchiveKymographBuilder setMaxT(int maxT) {
        this.maxT = maxT;
        return this;
    }

    /**
     * Set the frame reduction method to skip frames
     * This will include only every Nth frame in the kymograph
     *
     * @param skipFactor Include every Nth frame (e.g., 5 means include frames 0, 5, 10, etc.)
     * @return This builder for method chaining
     */
    public DnaArchiveKymographBuilder skipFrames(int skipFactor) {
        if (skipFactor < 1) {
            skipFactor = 1; // Prevent invalid values
        }
        this.reductionMethod = FrameReductionMethod.SKIP;
        this.reductionFactor = skipFactor;
        return this;
    }

    /**
     * Set the frame reduction method to average frames
     * This will average groups of N frames to create each kymograph frame
     *
     * @param groupSize Number of frames to average together
     * @return This builder for method chaining
     */
    public DnaArchiveKymographBuilder averageFrames(int groupSize) {
        if (groupSize < 1) {
            groupSize = 1; // Prevent invalid values
        }
        this.reductionMethod = FrameReductionMethod.AVERAGE;
        this.reductionFactor = groupSize;
        return this;
    }

    /**
     * Set the frame reduction method to sum frames
     * This will sum groups of N frames to create each kymograph frame
     *
     * @param groupSize Number of frames to sum together
     * @return This builder for method chaining
     */
    public DnaArchiveKymographBuilder sumFrames(int groupSize) {
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
    public DnaArchiveKymographBuilder useAllFrames() {
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
    public DnaArchiveKymographBuilder setVerticalReflection(boolean reflect) {
        this.verticalReflection = reflect;
        return this;
    }

    /**
     * Set the filter method to median filter
     *
     * @param radius The radius of the median filter
     * @return This builder for method chaining
     */
    public DnaArchiveKymographBuilder medianFilter(int radius) {
        this.filterMethod = ImageFilterMethod.MEDIAN;
        this.filterSize = radius;
        return this;
    }

    /**
     * Set the filter method to Gaussian filter
     *
     * @param sigma The sigma value for the Gaussian filter
     * @return This builder for method chaining
     */
    public DnaArchiveKymographBuilder gaussianFilter(double sigma) {
        this.filterMethod = ImageFilterMethod.GAUSSIAN;
        this.filterSize = sigma;
        return this;
    }

    /**
     * Set the filter method to top-hat filter
     *
     * @param radius The radius of the top-hat filter
     * @return This builder for method chaining
     */
    public DnaArchiveKymographBuilder tophatFilter(int radius) {
        this.filterMethod = ImageFilterMethod.TOPHAT;
        this.filterSize = radius;
        return this;
    }

    /**
     * Set the number of threads to use for filter operations
     *
     * @param numThreads The number of threads
     * @return This builder for method chaining
     */
    public DnaArchiveKymographBuilder threads(int numThreads) {
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
    public DnaArchiveKymographBuilder interpolation(double factor) {
        if (factor <= 0) {
            factor = 1.0;
        }
        this.interpolationFactor = factor;
        return this;
    }

    public Dataset build() {
        MarsIntervalExporter exporter = new MarsIntervalExporter(context, dnaMoleculeArchive);

        if (minT != -1) exporter.setMinT(minT);
        if (maxT != -1) exporter.setMaxT(maxT);

        final int minX = (int)Math.min(dnaMolecule.getParameter("Dna_Top_X1"), dnaMolecule.getParameter("Dna_Bottom_X2")) - 10;
        final int maxX = (int)Math.max(dnaMolecule.getParameter("Dna_Top_X1"), dnaMolecule.getParameter("Dna_Bottom_X2")) + 10;
        final int minY = (int)Math.min(dnaMolecule.getParameter("Dna_Top_Y1"), dnaMolecule.getParameter("Dna_Bottom_Y2")) - 10;
        final int maxY = (int)Math.max(dnaMolecule.getParameter("Dna_Top_Y1"), dnaMolecule.getParameter("Dna_Bottom_Y2")) + 10;

        Interval interval = Intervals.createMinMax(minX, minY, maxX, maxY);
        ImgPlus imgPlus = exporter.setMolecule(dnaMolecule).setInterval(interval).build();

        // Apply frame reduction if requested
        imgPlus = applyFrameReduction(imgPlus);

        // Apply filtering if requested
        if (filterMethod != ImageFilterMethod.NONE) {
            imgPlus = applyFilterToImage(imgPlus);
        }

        // Apply interpolation if requested
        if (interpolationFactor > 1.0) {
            imgPlus = increaseImageResolution(imgPlus, interpolationFactor);
        }

        // Apply vertical reflection if requested
        if (verticalReflection) {
            imgPlus = applyVerticalReflection(imgPlus);
        }

        Line line = new Line(dnaMolecule.getParameter("Dna_Top_X1") - minX,
                dnaMolecule.getParameter("Dna_Top_Y1") - minY,
                dnaMolecule.getParameter("Dna_Bottom_X2") - minX,
                dnaMolecule.getParameter("Dna_Bottom_Y2") - minY);

        // Build lines from the ROI
        LinesBuilder linesBuilder = new LinesBuilder(line);
        linesBuilder.setLineWidth(width);
        linesBuilder.build();

        Dataset dataset = convertService.convert(imgPlus, net.imagej.Dataset.class);

        KymographBuilder creator = new KymographBuilder(context, dataset, linesBuilder);
        creator.build();

        this.kymograph = creator.getProjectedKymograph();
        return kymograph;
    }

    /**
     * Helper method to apply frame reduction based on the selected method
     *
     * @param image The image to process
     * @return The processed image with frames reduced
     */
    @SuppressWarnings("unchecked")
    private <T extends net.imglib2.type.numeric.RealType<T>> ImgPlus<?> applyFrameReduction(ImgPlus<?> image) {
        if (reductionMethod == FrameReductionMethod.NONE || reductionFactor <= 1) {
            return image; // No reduction needed
        }

        // First check if the input image contains a RealType
        Object type = image.firstElement();
        if (!(type instanceof net.imglib2.type.numeric.RealType)) {
            return image; // Cannot apply reduction
        }

        try {
            // This is a type-safe operation because we've checked the element type
            ImgPlus<T> typedImage = (ImgPlus<T>) image;

            switch (reductionMethod) {
                case SKIP:
                    return MarsKymographUtils.skipFrames(typedImage, reductionFactor);
                case AVERAGE:
                    return MarsKymographUtils.averageFrames(typedImage, reductionFactor);
                case SUM:
                    return MarsKymographUtils.sumFrames(typedImage, reductionFactor);
                default:
                    return image; // No reduction needed
            }
        } catch (Exception e) {
            // Log error if available
            return image; // Return original if reduction fails
        }
    }

    /**
     * Helper method to apply the appropriate filter to the image.
     * Uses generic methods to properly handle type parameters.
     *
     * @param image The image to filter
     * @return The filtered image
     */
    @SuppressWarnings("unchecked")
    private <T extends net.imglib2.type.numeric.RealType<T>> ImgPlus<?> applyFilterToImage(ImgPlus<?> image) {
        // First check if the input image contains a RealType
        Object type = image.firstElement();
        if (!(type instanceof net.imglib2.type.numeric.RealType)) {
            return image; // Cannot apply filter
        }

        try {
            // This is a type-safe operation because we've checked the element type
            ImgPlus<T> typedImage = (ImgPlus<T>) image;

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
                    return image; // No filtering needed
            }
        } catch (Exception e) {
            // Log error if available
            return image; // Return original if filtering fails
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
            return image; // Cannot increase resolution
        }

        try {
            // This is a type-safe operation because we've checked the element type
            ImgPlus<T> typedImage = (ImgPlus<T>) image;
            return MarsKymographUtils.increaseResolution(typedImage, factor);
        } catch (Exception e) {
            // Log error if available
            return image; // Return original if interpolation fails
        }
    }

    /**
     * Helper method to apply vertical reflection to the image.
     * Uses generic methods to properly handle type parameters.
     *
     * @param image The image to reflect
     * @return The reflected image
     */
    @SuppressWarnings("unchecked")
    private <T extends net.imglib2.type.numeric.RealType<T>> ImgPlus<?> applyVerticalReflection(ImgPlus<?> image) {
        // First check if the input image contains a RealType
        Object type = image.firstElement();
        if (!(type instanceof net.imglib2.type.numeric.RealType)) {
            return image; // Cannot apply reflection
        }

        try {
            // This is a type-safe operation because we've checked the element type
            ImgPlus<T> typedImage = (ImgPlus<T>) image;
            return MarsKymographUtils.applyVerticalReflection(typedImage);
        } catch (Exception e) {
            // Log error if available
            return image; // Return original if reflection fails
        }
    }

    public Dataset getKymograph() {
        return kymograph;
    }
}
