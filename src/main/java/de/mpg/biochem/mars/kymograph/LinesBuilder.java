/*
 * #%L
 * KymographBuilder: Yet Another Kymograph Fiji plugin.
 * %%
 * Copyright (C) 2016 - 2022 Fiji developers.
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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