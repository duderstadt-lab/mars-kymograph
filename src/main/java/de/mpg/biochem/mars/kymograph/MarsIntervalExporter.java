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

import de.mpg.biochem.mars.metadata.MarsBdvSource;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.*;
import ij.IJ;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bdv.viewer.Source;
import bdv.viewer.Interpolation;

public class MarsIntervalExporter<T extends NumericType<T> & NativeType<T>> {

    @Parameter
    private Context context;

    @Parameter
    private LogService logService;

    private MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive;

    private Molecule molecule;
    private static Map<String, List<Source>> sourcesForExport;
    private static Map<String, Map<String, long[]>> sourceDimensions;
    private static Map<String, N5Reader> n5Readers;
    private int minT, maxT;
    private Interval interval;

    private MarsMetadata marsMetadata;
    public MarsIntervalExporter(Context context, MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive) {
        context.inject(this);
        this.archive = archive;

        if (sourcesForExport == null) sourcesForExport = new HashMap<String, List<Source>>();
        if (sourceDimensions == null) sourceDimensions = new HashMap<String, Map<String, long[]>>();
        if (n5Readers == null) n5Readers = new HashMap<String, N5Reader>();
    }

    public MarsIntervalExporter setMolecule(Molecule molecule) {
        this.molecule = molecule;
        marsMetadata = archive.getMetadata(molecule.getMetadataUID());
        addSources(marsMetadata);
        return this;
    }

    public MarsIntervalExporter setMolecule(String UID) {
        this.molecule = archive.get(UID);
        marsMetadata = archive.getMetadata(molecule.getMetadataUID());
        addSources(marsMetadata);
        return this;
    }

    public MarsIntervalExporter setMinT(int minT) {
        this.minT = minT;
        return this;
    }

    public MarsIntervalExporter setMaxT(int maxT) {
        this.maxT = maxT;
        return this;
    }

    public MarsIntervalExporter setInterval(Interval interval) {
        this.interval = interval;
        return this;
    }

    private void addSources(MarsMetadata marsMetadata) {
        if (!sourcesForExport.containsKey(marsMetadata.getUID())) {
            try {
                List<Source> exportSources = new ArrayList<>();

                for (MarsBdvSource marsSource : marsMetadata.getBdvSources()) {
                    if (marsSource.isN5()) {
                        exportSources.add(loadN5Source(marsSource, marsMetadata));
                    }
                }

                sourcesForExport.put(marsMetadata.getUID(), exportSources);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Source<T> loadN5Source(MarsBdvSource source, MarsMetadata meta)
            throws IOException
    {
        N5Reader reader;
        if (n5Readers.containsKey(source.getPath())) {
            reader = n5Readers.get(source.getPath());
        }
        else {
            reader = new N5Importer.N5ViewerReaderFun().apply(source.getPath());
            n5Readers.put(source.getPath(), reader);
        }

        @SuppressWarnings("rawtypes")
        final RandomAccessibleInterval wholeImage = N5Utils.open(reader, source
                .getN5Dataset());

        // wholeImage should be XYT or XYCT. If XYCT, we hyperSlice to get one
        // channel.
        // XYZCT should also be supported
        int dims = wholeImage.numDimensions();
        long[] dimensions = new long[dims];
        wholeImage.dimensions(dimensions);

        if (!sourceDimensions.containsKey(meta.getUID())) {
            Map<String, long[]> dimensionsMap = new HashMap<String, long[]>();
            sourceDimensions.put(meta.getUID(), dimensionsMap);
        }
        sourceDimensions.get(meta.getUID()).put(source.getName(), dimensions);

        @SuppressWarnings("rawtypes")
        final RandomAccessibleInterval image = (dims > 3) ? Views.hyperSlice(
                wholeImage, wholeImage.numDimensions() - 2, source.getChannel())
                : wholeImage;

        int tSize = (int) image.dimension(image.numDimensions() - 1);

        @SuppressWarnings("rawtypes")
        final RandomAccessibleInterval[] images = new RandomAccessibleInterval[1];
        images[0] = image;

        if (source.getSingleTimePointMode()) {
            AffineTransform3D[] transforms = new AffineTransform3D[tSize];

            // We don't drift correct single time point overlays
            // Drift should be corrected against them
            for (int t = 0; t < tSize; t++)
                transforms[t] = source.getAffineTransform3D();

            int singleTimePoint = source.getSingleTimePoint();
            @SuppressWarnings("unchecked")
            final MarsSingleTimePointSource<T> n5Source =
                    new MarsSingleTimePointSource<>((T) Util.getTypeFromInterval(image),
                            source.getName(), images, transforms, singleTimePoint);

            return n5Source;
        }
        else {
            AffineTransform3D[] transforms = new AffineTransform3D[tSize];

            for (int t = 0; t < tSize; t++) {
                if (source.getCorrectDrift()) {
                    double dX = meta.getPlane(0, 0, 0, t).getXDrift();
                    double dY = meta.getPlane(0, 0, 0, t).getYDrift();
                    transforms[t] = source.getAffineTransform3D(dX, dY);
                }
                else transforms[t] = source.getAffineTransform3D();
            }

            @SuppressWarnings("unchecked")
            final MarsSource<T> n5Source = new MarsSource<>((T) Util
                    .getTypeFromInterval(image), source.getName(), images, transforms);

            return n5Source;
        }

    }

    public ImgPlus<T> build() {
        if (!sourcesForExport.containsKey(marsMetadata.getUID())) return null;
        if (interval == null) return null;

        int numSources = sourcesForExport.get(marsMetadata.getUID()).size();

        if (sourcesForExport.get(marsMetadata.getUID()).stream().map(source -> source.getType()
                .getClass()).distinct().count() > 1)
        {
            logService.info("Could not create composite view because the sources are not all the same type (uint16, float32, ...).");
            return null;
        }

        List<RandomAccessibleInterval<T>> finalImages =
                new ArrayList<RandomAccessibleInterval<T>>();
        for (int i = 0; i < numSources; i++) {
            ArrayList<RandomAccessibleInterval<T>> raiList =
                    new ArrayList<RandomAccessibleInterval<T>>();
            Source<T> bdvSource = sourcesForExport.get(marsMetadata.getUID()).get(i);

            if (maxT == 0) maxT = marsMetadata.getImage(0).getSizeT() - 1;
            if (minT > maxT) minT = 0;

            for (int t = minT; t <= maxT; t++) {

                // t, level, interpolation
                final RealRandomAccessible<T> raiRaw =
                        (RealRandomAccessible<T>) bdvSource.getInterpolatedSource(t, 0,
                                Interpolation.NLINEAR);

                // retrieve transform
                AffineTransform3D affine = new AffineTransform3D();
                bdvSource.getSourceTransform(t, 0, affine);
                final AffineRandomAccessible<T, AffineGet> rai = RealViews.affine(
                        raiRaw, affine);
                RandomAccessibleInterval<T> view = Views.interval(Views.raster(rai),
                        new long[] { interval.min(0), interval.min(1), 0 }, new long[] { interval.max(0), interval.max(1), 0 });

                raiList.add(Views.hyperSlice(view, 2, 0));
            }

            if (numSources > 1) finalImages.add(Views.addDimension(Views.stack(
                    raiList), 0, 0));
            else finalImages.add(Views.stack(raiList));
        }

        String title = (molecule != null) ? "molecule " + molecule.getUID()
                : "BDV export (" + interval.min(0) + ", " + interval.min(1) + ", " + interval.max(0) + ", " + interval.max(1) + ")";

        AxisType[] axInfo = (numSources > 1) ? new AxisType[] { Axes.X, Axes.Y,
                Axes.TIME, Axes.CHANNEL } : new AxisType[] { Axes.X, Axes.Y, Axes.TIME };

        final RandomAccessibleInterval<T> rai = (numSources > 1) ? Views
                .concatenate(3, finalImages) : finalImages.get(0);

        final Img<T> img = Util.getSuitableImgFactory(rai, Util.getTypeFromInterval(
                rai)).create(rai);
        LoopBuilder.setImages(img, rai).multiThreaded().forEachPixel(Type::set);

        final ImgPlus<T> imgPlus = new ImgPlus<T>(img, title, axInfo);

        return imgPlus;
    }
}
