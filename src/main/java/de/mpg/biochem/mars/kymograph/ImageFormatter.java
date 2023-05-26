package de.mpg.biochem.mars.kymograph;

import de.mpg.biochem.mars.util.MarsMath;
import net.imagej.Dataset;

import ij.ImagePlus;
import ij.IJ;

import org.scijava.Context;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ImageFormatter {

    @Parameter
    private Context context;
    @Parameter
    private ConvertService convertService;
    @Parameter
    private LogService logService;

    private ImagePlus imp;

    private final Map<Integer, Double> displayRangeCtoMin, displayRangeCtoMax;
    private final Map<Integer, String> cToLUTName;
    private String htmlEncodedImage;

    public ImageFormatter(Context context, Dataset kymograph) {
        context.inject(this);
        imp = convertService.convert(kymograph, ij.ImagePlus.class);
        displayRangeCtoMin = new HashMap<>();
        displayRangeCtoMax = new HashMap<>();
        cToLUTName = new HashMap<>();
    }

    public ImageFormatter setMin(int c, double min) {
        displayRangeCtoMin.put(c, min);
        return this;
    }

    public ImageFormatter setMax(int c, double min) {
        displayRangeCtoMax.put(c, min);
        return this;
    }

    public ImageFormatter setLUT(int c, String lut) {
        cToLUTName.put(c, lut);
        return this;
    }

    public void build() {
        IJ.run(imp, "Make Composite", "");
        for (int c=1; c<=imp.getNChannels(); c++) {
            imp.setC(c);
            if (displayRangeCtoMin.containsKey(c) || displayRangeCtoMax.containsKey(c))
                imp.setDisplayRange(displayRangeCtoMin.containsKey(c) ? displayRangeCtoMin.get(c) : imp.getDisplayRangeMin(),
                        displayRangeCtoMax.containsKey(c) ? displayRangeCtoMax.get(c) : imp.getDisplayRangeMax());
            else IJ.run(imp, "Enhance Contrast", "saturated=0.35");
            if (cToLUTName.containsKey(c))
                IJ.run(imp, cToLUTName.get(c), "");
            else if (c == 1)
                IJ.run(imp, "Blue", "");
            else if (c == 2)
                IJ.run(imp, "Magenta", "");
            else if (c == 3)
                IJ.run(imp, "Green", "");
        }
        int scaleFactor = (int)Math.ceil(1000/imp.getWidth());
        imp = imp.resize(imp.getWidth()*scaleFactor, imp.getHeight()*scaleFactor, 1, "none");
        String tempFilePath = System.getProperty("java.io.tmpdir") + "temp_mars_kymograph_" + MarsMath.getUUID58() + ".png";
        IJ.saveAs(imp, "PNG", tempFilePath);
        try {
            byte[] fileContent = Files.readAllBytes(new File(tempFilePath).toPath());
            htmlEncodedImage = "data:image/png;base64," + Base64.getEncoder().encodeToString(fileContent);
        } catch (IOException e) {
            logService.error(e);
        }
    }

    public String getHtmlEncodedImage() {
        return this.htmlEncodedImage;
    }
}
