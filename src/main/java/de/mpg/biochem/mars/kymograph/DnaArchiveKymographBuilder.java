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

        Line line = new Line(dnaMolecule.getParameter("Dna_Top_X1") - minX,
                dnaMolecule.getParameter("Dna_Top_Y1") - minY,
                dnaMolecule.getParameter("Dna_Bottom_X2") - minX,
                dnaMolecule.getParameter("Dna_Bottom_Y2") - minY);

        // Build lines from the ROI
        LinesBuilder linesBuilder = new LinesBuilder(line);
        linesBuilder.setLineWidth(width);
        linesBuilder.build();

        Dataset dataset = convertService.convert(imgPlus, net.imagej.Dataset.class);

        KymographCreator creator = new KymographCreator(context, dataset, linesBuilder);
        creator.build();

        this.kymograph = creator.getProjectedKymograph();
        return kymograph;
    }

    public Dataset getKymograph() {
        return kymograph;
    }
}
