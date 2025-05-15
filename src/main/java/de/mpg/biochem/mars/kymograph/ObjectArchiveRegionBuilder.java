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

import de.mpg.biochem.mars.image.PeakShape;
import de.mpg.biochem.mars.molecule.DnaMolecule;
import de.mpg.biochem.mars.molecule.DnaMoleculeArchive;
import de.mpg.biochem.mars.object.MartianObject;
import de.mpg.biochem.mars.object.ObjectArchive;
import net.imagej.ImgPlus;
import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

/**
 * Creates montages from objects from an ObjectArchive.
 * This class extracts data from the archive. The MontageBuilder is then be used for montage creation.
 *
 * @author Karl Duderstadt
 */
public class ObjectArchiveRegionBuilder {

    @Parameter
    private Context context;

    @Parameter
    private LogService logService;

    private final ObjectArchive objectArchive;
    private MartianObject martianObject;
    private ImgPlus<?> imgPlus;
    private int borderWidth = 10;
    private int borderHeight = 10;

    /**
     * Constructor for the DnaArchiveMontageBuilder
     *
     * @param context The SciJava context
     * @param objectArchive The archive containing the molecules
     */
    public ObjectArchiveRegionBuilder(Context context, ObjectArchive objectArchive) {
        context.inject(this);
        this.objectArchive = objectArchive;
    }

    /**
     * Set the molecule to use for building the montage
     *
     * @param martianObject The molecule to use
     * @return This builder for method chaining
     */
    public ObjectArchiveRegionBuilder setObject(MartianObject martianObject) {
        this.martianObject = martianObject;
        return this;
    }

    /**
     * Set the molecule to use for building the montage by UID
     *
     * @param UID The UID of the molecule to use
     * @return This builder for method chaining
     */
    public ObjectArchiveRegionBuilder setObject(String UID) {
        this.martianObject = objectArchive.get(UID);
        return this;
    }

    /**
     * Set border width around the DNA
     *
     * @param width The border width in pixels
     * @return This builder for method chaining
     */
    public ObjectArchiveRegionBuilder setBorderWidth(int width) {
        this.borderWidth = width;
        return this;
    }

    /**
     * Set border height around the DNA
     *
     * @param height The border height in pixels
     * @return This builder for method chaining
     */
    public ObjectArchiveRegionBuilder setBorderHeight(int height) {
        this.borderHeight = height;
        return this;
    }


    /**
     * Build the montage
     *
     * @return The montage dataset
     */
    public ImgPlus<?> build() {
        if (martianObject == null) {
            logService.error("No molecule set. Use setMolecule() before building.");
            return null;
        }

        // Extract source data from archive
        MarsIntervalExporter exporter = new MarsIntervalExporter(context, objectArchive);

        //Find bounding box considering the shape at all time points.
        double xmin = Double.POSITIVE_INFINITY;
        double xmax = Double.NEGATIVE_INFINITY;
        double ymin = Double.POSITIVE_INFINITY;
        double ymax = Double.NEGATIVE_INFINITY;
        for (int t : martianObject.getShapeKeys()) {
            PeakShape shape = martianObject.getShape(t);
            for (double x : shape.x) {
                if (x < xmin) xmin = x;
                if (x > xmax) xmax = x;
            }
            for (double y : shape.y) {
                if (y < ymin) ymin = y;
                if (y > ymax) ymax = y;
            }
        }

        Interval interval = Intervals.createMinMax((long) xmin - borderWidth, (long) ymin - borderHeight, (long) xmax + borderWidth, (long) ymax + borderHeight);
        imgPlus = exporter.setMolecule(martianObject).setInterval(interval).build();

        return imgPlus;
    }

    /**
     * Get the region.
     *
     * @return The extracted region dataset
     */
    public ImgPlus<?> getImage() {
        return imgPlus;
    }
}