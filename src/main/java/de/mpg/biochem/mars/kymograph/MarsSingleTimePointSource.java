/*-
 * #%L
 * JavaFX GUI for processing single-molecule TIRF and FMT data in the Structure and Dynamics of Molecular Machines research group.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
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

import java.util.function.Supplier;

import bdv.util.AbstractSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;

public class MarsSingleTimePointSource<T extends NumericType<T>> extends
        AbstractSource<T>
{

    protected final RandomAccessibleInterval<T>[] images;

    protected final AffineTransform3D[] transforms;

    protected final int singleTimePoint;

    public MarsSingleTimePointSource(final T type, final String name,
                                       final RandomAccessibleInterval<T>[] images,
                                       final AffineTransform3D[] transforms, final int singleTimePoint)
    {
        super(type, name);
        this.images = images;
        this.transforms = transforms;
        this.singleTimePoint = singleTimePoint;
    }

    @Override
    public RandomAccessibleInterval<T> getSource(final int t, final int level) {
        if (images[level].numDimensions() == 2) return Views.addDimension(
                images[level], 0, 0);

        RandomAccessibleInterval<T> img = Views.hyperSlice(images[level],
                images[level].numDimensions() - 1, singleTimePoint);
        // For now we assume time is the last axis and reslice accordingly
        if (img.numDimensions() > 2) return img;
        else return Views.addDimension(img, 0, 0);
    }

    @Override
    public synchronized void getSourceTransform(final int t, final int level,
                                                final AffineTransform3D transform)
    {
        if (singleTimePoint >= transforms.length) transform.set(transforms[0]);
        else transform.set(transforms[singleTimePoint]);
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return null;
    }

    @Override
    public int getNumMipmapLevels() {
        return images.length;
    }
}
