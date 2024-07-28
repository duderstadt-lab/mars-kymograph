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

import de.mpg.biochem.mars.image.DNASegment;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.ops.OpService;
import net.imagej.ops.special.computer.UnaryComputerOp;
import net.imglib2.RandomAccess;
import net.imglib2.type.Type;

import org.scijava.Context;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import ij.gui.Line;

/**
 * The main class that actually build the kymograph for one channel.
 * From KymographBuilder: Yet Another Kymograph Fiji plugin. Small
 * modifications including kymograph rotation to horizontal.
 *
 * @author Hadrien Mary
 * @author Karl Duderstadt
 */
public class KymographCreator {

    @Parameter
    private ConvertService convert;

    @Parameter
    private LogService log;

    @Parameter
    private DatasetService dsService;

    @Parameter
    private OpService opService;

    private final Dataset dataset;
    private Dataset kymograph;
    private Dataset projectedKymograph;

    private final LinesBuilder linesBuilder;

    private RandomAccess<?> datasetCursor;
    private RandomAccess<?> kymographCursor;

    public KymographCreator(Context context, Dataset dataset, LinesBuilder linesBuilder)
    {

        context.inject(this);
        this.linesBuilder = linesBuilder;
        this.dataset = dataset;
    }

    public Dataset getKymograph() {
        return this.kymograph;
    }

    public Dataset getProjectedKymograph() {
        return this.projectedKymograph;
    }

    public void build() {
        this.buildKymograph();
        this.projectKymograph();
    }

    private void buildKymograph() {

        // Create kymograph dataset
        // A 3D dataset because it contains one kymograph by "width" unit
        long[] dimensions = new long[4];

        dimensions[0] = this.dataset.dimension(this.dataset.dimensionIndex(Axes.TIME));

        if (this.linesBuilder.getLines().size() == 1) {
            dimensions[1] = this.linesBuilder.getTotalLength() - this.linesBuilder.getLines().size();
        }
        else {
            dimensions[1] = this.linesBuilder.getTotalLength() - this.linesBuilder.getLines().size() + 1;
        }

        dimensions[2] = this.linesBuilder.getlineWidth();
        dimensions[3] = this.dataset.dimension(this.dataset.dimensionIndex(Axes.CHANNEL));

        AxisType[] axisTypes = { Axes.X, Axes.Y, Axes.Z, Axes.CHANNEL };

        String title = dataset.getName() + " (Kymograph)";

        this.kymograph = dsService.create(dimensions, title, axisTypes, dataset.getValidBits(), dataset
                .isSigned(), !dataset.isInteger());

        // Get cursors to access and set pixels.
        this.datasetCursor = this.dataset.getImgPlus().randomAccess();
        this.kymographCursor = this.kymograph.getImgPlus().randomAccess();

        int offset = 0;
        DNASegment line;
        double[] vectorScaled;
        int length;

        // Iterate over each lines (for polyline) and fill the kymograph dataset.
        for (int i = 0; i < this.linesBuilder.getLines().size(); i++) {

            line = this.linesBuilder.getLines().get(i);
            vectorScaled = this.linesBuilder.getLinesVectorScaled().get(i);
            length = this.linesBuilder.getLinesLength().get(i);

            this.fillKymograph(line, vectorScaled, offset);

            offset += length - 1;
        }

    }

    private <T extends Type<T>> void fillKymograph(DNASegment line, double[] vectorScaled, int offset) {

        double dx = vectorScaled[0];
        double dy = vectorScaled[1];
        int n;
        int lineWidth = this.linesBuilder.getlineWidth();
        int new_xStart;
        int new_yStart;
        int new_xEnd;
        int new_yEnd;

        int timeDimension = (int) this.dataset.dimension(this.dataset.dimensionIndex(Axes.TIME));
        int xDimension = (int) this.dataset.dimension(this.dataset.dimensionIndex(Axes.X));
        int yDimension = (int) this.dataset.dimension(this.dataset.dimensionIndex(Axes.Y));
        int channelDimension = (int) this.dataset.dimension(this.dataset.dimensionIndex(Axes.CHANNEL));

        Line currentLine;

        float[] xpoints;
        float[] ypoints;
        int npoints;
        int x;
        int y;

        // Iterate over all parallel lines (defined by lineWidth)
        for (int i = 0; i < lineWidth; i++) {

            n = i - lineWidth / 2;
            new_xStart = (int) (line.getX1() + n * dy);
            new_yStart = (int) (line.getY1() - n * dx);
            new_xEnd = (int) (line.getX2() + n * dy);
            new_yEnd = (int) (line.getY2() - n * dx);

            // TODO : Remove the use of ij.gui.Line()
            currentLine = new Line(new_xStart, new_yStart, new_xEnd, new_yEnd);
            xpoints = currentLine.getInterpolatedPolygon().xpoints;
            ypoints = currentLine.getInterpolatedPolygon().ypoints;
            npoints = currentLine.getInterpolatedPolygon().npoints;

            // Iterate over every pixel defining the line
            for (int j = 0; j < npoints - 1; j++) {
                x = Math.round(xpoints[j]);
                y = Math.round(ypoints[j]);

                if (j >= this.linesBuilder.getTotalLength() - this.linesBuilder.getLines().size()) {
                    break;
                }

                // Iterate over the time axis
                for (int t = 0; t < timeDimension; t++) {

                    // Check we are inside the image
                    if ((x > 0) && (x < xDimension) && (y > 0) && (y < yDimension)) {

                        // Iterate over channels
                        for (int channel = 0; channel < channelDimension; channel++) {

                            this.datasetCursor.setPosition(x, this.dataset.dimensionIndex(Axes.X));
                            this.datasetCursor.setPosition(y, this.dataset.dimensionIndex(Axes.Y));
                            this.datasetCursor.setPosition(t, this.dataset.dimensionIndex(Axes.TIME));

                            if (this.dataset.dimensionIndex(Axes.CHANNEL) != -1) {
                                this.datasetCursor.setPosition(channel, this.dataset.dimensionIndex(Axes.CHANNEL));
                            }

                            this.kymographCursor.setPosition(new int[] { t, offset + j, i, channel });
                            final T pixel = (T) this.kymographCursor.get();
                            pixel.set((T) this.datasetCursor.get());
                        }
                    }
                }

            }

        }

    }

    private void projectKymograph() {

        long xDimension = this.kymograph.dimension(this.kymograph.dimensionIndex(Axes.X));
        long yDimension = this.kymograph.dimension(this.kymograph.dimensionIndex(Axes.Y));
        long channelDimension = this.kymograph.dimension(this.kymograph.dimensionIndex(Axes.CHANNEL));
        long[] dimensions = { xDimension, yDimension, channelDimension };

        AxisType[] axisTypes = { Axes.X, Axes.Y, Axes.CHANNEL };

        String title = dataset.getName() + " (Projected Kymograph)";

        this.projectedKymograph = dsService.create(dimensions, title, axisTypes, dataset.getValidBits(),
                dataset.isSigned(), !dataset.isInteger());

        // Set correct unit calibrations. For the spatial axis I choose to copy
        // X axis but I assume X and Y have the same calibration.
        CalibratedAxis positionAxis = this.dataset.axis(this.dataset.dimensionIndex(Axes.X)).copy();
        this.projectedKymograph.setAxis(positionAxis, 0);

        CalibratedAxis timAxis = new DefaultLinearAxis(Axes.Y, this.dataset.axis(this.dataset
                .dimensionIndex(Axes.TIME)).calibratedValue(1));
        this.projectedKymograph.setAxis(timAxis, 1);

        // I don't understand everything here (mostly the type stuff) but it
        // works...
        UnaryComputerOp maxOp = (UnaryComputerOp) opService.op(net.imagej.ops.Ops.Stats.Max.class,
                this.kymograph.getImgPlus().getImg());

        opService.transform().project(this.projectedKymograph.getImgPlus().getImg(), this.kymograph
                .getImgPlus(), maxOp, this.kymograph.dimensionIndex(Axes.Z));
    }
}
