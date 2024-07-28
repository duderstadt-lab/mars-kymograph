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

import java.util.ArrayList;
import java.util.List;

import de.mpg.biochem.mars.image.DNASegment;
import ij.gui.Line;
import ij.gui.PolygonRoi;
import ij.gui.Roi;

/**
 * From KymographBuilder: Yet Another Kymograph Fiji plugin.
 *
 * @author Hadrien Mary
 */
public class LinesBuilder {

    private Roi roi;

    List<DNASegment> lines;
    private int lineWidth = 1;
    private List<Integer> linesLength;
    private List<double[]> linesVectorScaled;
    private int totalLength;

    public LinesBuilder(Roi roi) {
        this.roi = roi;
    }

    public List<DNASegment> getLines() {
        return this.lines;
    }

    public List<Integer> getLinesLength() {
        return this.linesLength;
    }

    public List<double[]> getLinesVectorScaled() {
        return this.linesVectorScaled;
    }

    public int getTotalLength() {
        return this.totalLength;
    }

    public void setLineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
    }

    public int getlineWidth() {
        return this.lineWidth;
    }

    public void build() {
        this.buildLines();
        this.buildVector();

        //this.lineWidth = Math.round(roi.getStrokeWidth());
        //this.lineWidth = this.lineWidth < 1 ? 1 : this.lineWidth;
    }

    private void buildLines() {

        lines = new ArrayList<>();

        if (this.roi.getTypeAsString().equals("Straight Line")) {
            Line lineRoi = (Line) this.roi;
            DNASegment line = new DNASegment(lineRoi.x1, lineRoi.y1, lineRoi.x2, lineRoi.y2);
            lines.add(line);

        }
        else {
            PolygonRoi roiPoly = (PolygonRoi) this.roi;

            int xStart;
            int yStart;
            int xEnd;
            int yEnd;

            for (int i = 0; i < roiPoly.getPolygon().npoints - 1; i++) {
                xStart = roiPoly.getPolygon().xpoints[i];
                yStart = roiPoly.getPolygon().ypoints[i];
                xEnd = roiPoly.getPolygon().xpoints[i + 1];
                yEnd = roiPoly.getPolygon().ypoints[i + 1];
                DNASegment line = new DNASegment(xStart, yStart, xEnd, yEnd);
                lines.add(line);
            }
        }
    }

    private void buildVector() {

        this.linesLength = new ArrayList<>();
        this.linesVectorScaled = new ArrayList<>();

        totalLength = 0;
        int length;

        for (DNASegment line : this.lines) {
            length = (int) Math.round(line.getLength());
            this.linesLength.add(length);
            double[] v = new double[2];
            v[0] = (line.getX1() - line.getX2()) / line.getLength();
            v[1] = (line.getY1() - line.getY2()) / line.getLength();
            this.linesVectorScaled.add(v);
            totalLength += length;
        }
    }
}
