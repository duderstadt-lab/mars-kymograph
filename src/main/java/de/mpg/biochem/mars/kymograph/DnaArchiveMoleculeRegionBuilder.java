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

import de.mpg.biochem.mars.molecule.*;
import net.imagej.ImgPlus;
import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

/**
 * Creates montages from DNA molecules in a MoleculeArchive.
 * This class extracts data from the archive and uses MontageBuilder for the actual montage creation.
 *
 * @author Karl Duderstadt
 */
public class DnaArchiveMoleculeRegionBuilder {

    @Parameter
    private Context context;

    @Parameter
    private LogService logService;

    private final DnaMoleculeArchive dnaMoleculeArchive;
    private DnaMolecule dnaMolecule;
    private ImgPlus<?> imgPlus;
    private int borderWidth = 10;
    private int borderHeight = 10;

    /**
     * Constructor for the DnaArchiveMontageBuilder
     *
     * @param context The SciJava context
     * @param dnaMoleculeArchive The archive containing the molecules
     */
    public DnaArchiveMoleculeRegionBuilder(Context context, DnaMoleculeArchive dnaMoleculeArchive) {
        context.inject(this);
        this.dnaMoleculeArchive = dnaMoleculeArchive;
    }

    /**
     * Set the molecule to use for building the montage
     *
     * @param dnaMolecule The molecule to use
     * @return This builder for method chaining
     */
    public DnaArchiveMoleculeRegionBuilder setMolecule(DnaMolecule dnaMolecule) {
        this.dnaMolecule = dnaMolecule;
        return this;
    }

    /**
     * Set the molecule to use for building the montage by UID
     *
     * @param UID The UID of the molecule to use
     * @return This builder for method chaining
     */
    public DnaArchiveMoleculeRegionBuilder setMolecule(String UID) {
        this.dnaMolecule = dnaMoleculeArchive.get(UID);
        return this;
    }

    /**
     * Set border width around the DNA
     *
     * @param width The border width in pixels
     * @return This builder for method chaining
     */
    public DnaArchiveMoleculeRegionBuilder setBorderWidth(int width) {
        this.borderWidth = width;
        return this;
    }

    /**
     * Set border height around the DNA
     *
     * @param height The border height in pixels
     * @return This builder for method chaining
     */
    public DnaArchiveMoleculeRegionBuilder setBorderHeight(int height) {
        this.borderHeight = height;
        return this;
    }


    /**
     * Build the montage
     *
     * @return The montage dataset
     */
    public ImgPlus<?> build() {
        if (dnaMolecule == null) {
            logService.error("No molecule set. Use setMolecule() before building.");
            return null;
        }

        // Extract source data from archive
        MarsIntervalExporter exporter = new MarsIntervalExporter(context, dnaMoleculeArchive);

        // Define the interval around the DNA with borders
        final int minX = (int)Math.min(dnaMolecule.getParameter("Dna_Top_X1"), dnaMolecule.getParameter("Dna_Bottom_X2")) - borderWidth;
        final int maxX = (int)Math.max(dnaMolecule.getParameter("Dna_Top_X1"), dnaMolecule.getParameter("Dna_Bottom_X2")) + borderWidth;
        final int minY = (int)Math.min(dnaMolecule.getParameter("Dna_Top_Y1"), dnaMolecule.getParameter("Dna_Bottom_Y2")) - borderHeight;
        final int maxY = (int)Math.max(dnaMolecule.getParameter("Dna_Top_Y1"), dnaMolecule.getParameter("Dna_Bottom_Y2")) + borderHeight;

        Interval interval = Intervals.createMinMax(minX, minY, maxX, maxY);
        imgPlus = exporter.setMolecule(dnaMolecule).setInterval(interval).build();

        return imgPlus;
    }

    /**
     * Get the built montage
     *
     * @return The montage dataset
     */
    public ImgPlus<?> getMontage() {
        return imgPlus;
    }
}
