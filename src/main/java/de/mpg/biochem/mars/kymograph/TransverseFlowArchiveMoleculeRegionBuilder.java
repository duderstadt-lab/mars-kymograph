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

import de.mpg.biochem.mars.transverseflow.ReplicationForkShape;
import de.mpg.biochem.mars.transverseflow.TransverseFlowArchive;
import de.mpg.biochem.mars.transverseflow.TransverseFlowMolecule;
import net.imagej.ImgPlus;
import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

/**
 * Creates image from transverse flow molecules in a TransverseFlowArchive.
 * This class extracts data from the archive. The MontageBuilder is used for the actual montage creation.
 *
 * @author Karl Duderstadt
 */
public class TransverseFlowArchiveMoleculeRegionBuilder {

    @Parameter
    private Context context;

    @Parameter
    private LogService logService;

    private final TransverseFlowArchive transverseFlowArchive;
    private TransverseFlowMolecule transverseFlowMolecule;
    private ImgPlus<?> imgPlus;
    private int borderWidth = 10;
    private int borderHeight = 10;

    /**
     * Constructor for the DnaArchiveMontageBuilder
     *
     * @param context The SciJava context
     * @param transverseFlowArchive The archive containing the molecules
     */
    public TransverseFlowArchiveMoleculeRegionBuilder(Context context, TransverseFlowArchive transverseFlowArchive) {
        context.inject(this);
        this.transverseFlowArchive = transverseFlowArchive;
    }

    /**
     * Set the molecule to use for building the montage
     *
     * @param transverseFlowMolecule The molecule to use
     * @return This builder for method chaining
     */
    public TransverseFlowArchiveMoleculeRegionBuilder setMolecule(TransverseFlowMolecule transverseFlowMolecule) {
        this.transverseFlowMolecule = transverseFlowMolecule;
        return this;
    }

    /**
     * Set the molecule to use for building the montage by UID
     *
     * @param UID The UID of the molecule to use
     * @return This builder for method chaining
     */
    public TransverseFlowArchiveMoleculeRegionBuilder setMolecule(String UID) {
        this.transverseFlowMolecule = transverseFlowArchive.get(UID);
        return this;
    }

    /**
     * Set border width around the DNA
     *
     * @param width The border width in pixels
     * @return This builder for method chaining
     */
    public TransverseFlowArchiveMoleculeRegionBuilder setBorderWidth(int width) {
        this.borderWidth = width;
        return this;
    }

    /**
     * Set border height around the DNA
     *
     * @param height The border height in pixels
     * @return This builder for method chaining
     */
    public TransverseFlowArchiveMoleculeRegionBuilder setBorderHeight(int height) {
        this.borderHeight = height;
        return this;
    }


    /**
     * Build the montage
     *
     * @return The montage dataset
     */
    public ImgPlus<?> build() {
        if (transverseFlowMolecule == null) {
            logService.error("No molecule set. Use setMolecule() before building.");
            return null;
        }

        //Find shape boundaries that define interval across all timepoints
        double xmin = Double.POSITIVE_INFINITY;
        double xmax = Double.NEGATIVE_INFINITY;
        double ymin = Double.POSITIVE_INFINITY;
        double ymax = Double.NEGATIVE_INFINITY;

        for (int t : transverseFlowMolecule.getShapeKeys()) {
            ReplicationForkShape shape = transverseFlowMolecule.getShape(t);
            for (double x : shape.parentalX) {
                if (x < xmin) xmin = x;
                if (x > xmax) xmax = x;
            }
            for (double x : shape.leadingX) {
                if (x < xmin) xmin = x;
                if (x > xmax) xmax = x;
            }
            for (double x : shape.laggingX) {
                if (x < xmin) xmin = x;
                if (x > xmax) xmax = x;
            }
            for (double y : shape.parentalY) {
                if (y < ymin) ymin = y;
                if (y > ymax) ymax = y;
            }
            for (double y : shape.leadingY) {
                if (y < ymin) ymin = y;
                if (y > ymax) ymax = y;
            }
            for (double y : shape.laggingY) {
                if (y < ymin) ymin = y;
                if (y > ymax) ymax = y;
            }
        }

        // Define the interval around the DNA with borders
        final int borderMinX = (int)xmin - borderWidth;
        final int borderMaxX = (int)xmax + borderWidth;
        final int borderMinY = (int)ymin - borderHeight;
        final int borderMaxY = (int)ymax + borderHeight;

        Interval interval = Intervals.createMinMax(borderMinX, borderMinY, borderMaxX, borderMaxY);

        // Extract source data from archive
        MarsIntervalExporter exporter = new MarsIntervalExporter(context, transverseFlowArchive);
        imgPlus = exporter.setMolecule(transverseFlowMolecule).setInterval(interval).build();

        return imgPlus;
    }

    /**
     * Get the built montage
     *
     * @return The montage dataset
     */
    public ImgPlus<?> getRegionImage() {
        return imgPlus;
    }
}
