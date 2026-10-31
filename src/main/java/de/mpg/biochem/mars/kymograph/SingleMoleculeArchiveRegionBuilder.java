/*-
 * #%L
 * Mars kymograph builder.
 * %%
 * Copyright (C) 2023 - 2026 Karl Duderstadt
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
import net.imagej.ImgPlus;
import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

/**
 * Extracts the region around a spot from a SingleMoleculeArchive for use with
 * the MontageBuilder. The spot center is the mean of the x and y columns of the
 * molecule table, or the x and y parameters when parameters are used.
 *
 * @author Karl Duderstadt
 */
public class SingleMoleculeArchiveRegionBuilder {

    @Parameter
    private Context context;

    @Parameter
    private LogService logService;

    private final SingleMoleculeArchive singleMoleculeArchive;
    private SingleMolecule molecule;
    private ImgPlus<?> imgPlus;
    private int radius = 5;
    private String xSource = "x";
    private String ySource = "y";
    private boolean useParameters = false;
    private int minT = -1;
    private int maxT = -1;

    /**
     * Constructor for the SingleMoleculeArchiveRegionBuilder
     *
     * @param context The SciJava context
     * @param singleMoleculeArchive The archive containing the molecules
     */
    public SingleMoleculeArchiveRegionBuilder(Context context, SingleMoleculeArchive singleMoleculeArchive) {
        context.inject(this);
        this.singleMoleculeArchive = singleMoleculeArchive;
    }

    /**
     * Set the molecule to use for building the region
     *
     * @param molecule The molecule to use
     * @return This builder for method chaining
     */
    public SingleMoleculeArchiveRegionBuilder setMolecule(SingleMolecule molecule) {
        this.molecule = molecule;
        return this;
    }

    /**
     * Set the molecule to use for building the region by UID
     *
     * @param UID The UID of the molecule to use
     * @return This builder for method chaining
     */
    public SingleMoleculeArchiveRegionBuilder setMolecule(String UID) {
        this.molecule = singleMoleculeArchive.get(UID);
        return this;
    }

    /**
     * Set the radius of the square region around the spot center. The region
     * is 2 * radius + 1 pixels wide and high.
     *
     * @param radius The radius in pixels
     * @return This builder for method chaining
     */
    public SingleMoleculeArchiveRegionBuilder setRadius(int radius) {
        this.radius = radius;
        return this;
    }

    /**
     * Set the table columns, or parameters, used for the spot center.
     * Defaults are x and y.
     *
     * @param xSource The x column or parameter name
     * @param ySource The y column or parameter name
     * @return This builder for method chaining
     */
    public SingleMoleculeArchiveRegionBuilder setLocationSource(String xSource, String ySource) {
        this.xSource = xSource;
        this.ySource = ySource;
        return this;
    }

    /**
     * Use molecule parameters instead of the mean of table columns for the
     * spot center.
     *
     * @param useParameters Whether to use parameters
     * @return This builder for method chaining
     */
    public SingleMoleculeArchiveRegionBuilder useParameters(boolean useParameters) {
        this.useParameters = useParameters;
        return this;
    }

    /**
     * Set the first time point to extract
     *
     * @param minT The first time point
     * @return This builder for method chaining
     */
    public SingleMoleculeArchiveRegionBuilder setMinT(int minT) {
        this.minT = minT;
        return this;
    }

    /**
     * Set the last time point to extract
     *
     * @param maxT The last time point
     * @return This builder for method chaining
     */
    public SingleMoleculeArchiveRegionBuilder setMaxT(int maxT) {
        this.maxT = maxT;
        return this;
    }

    /**
     * Build the region image
     *
     * @return The region image
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ImgPlus<?> build() {
        if (molecule == null) {
            logService.error("No molecule set. Use setMolecule() before building.");
            return null;
        }

        double x, y;
        if (useParameters) {
            if (!molecule.hasParameter(xSource) || !molecule.hasParameter(ySource)) {
                logService.error("Molecule " + molecule.getUID() + " is missing the " + xSource + " or " + ySource + " parameter.");
                return null;
            }
            x = molecule.getParameter(xSource);
            y = molecule.getParameter(ySource);
        }
        else {
            if (!molecule.getTable().hasColumn(xSource) || !molecule.getTable().hasColumn(ySource)) {
                logService.error("Molecule " + molecule.getUID() + " is missing the " + xSource + " or " + ySource + " column.");
                return null;
            }
            x = molecule.getTable().mean(xSource);
            y = molecule.getTable().mean(ySource);
        }

        final int centerX = (int) Math.round(x);
        final int centerY = (int) Math.round(y);

        MarsIntervalExporter exporter = new MarsIntervalExporter(context, (MoleculeArchive) singleMoleculeArchive);
        if (minT != -1) exporter.setMinT(minT);
        if (maxT != -1) exporter.setMaxT(maxT);

        Interval interval = Intervals.createMinMax(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        imgPlus = exporter.setMolecule(molecule).setInterval(interval).build();

        return imgPlus;
    }

    public ImgPlus<?> getRegionImage() {
        return imgPlus;
    }
}
