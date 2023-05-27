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
import java.io.ByteArrayOutputStream;

import java.util.*;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.geom.Point2D;
import java.awt.geom.AffineTransform;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

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

    //private int leftMargin = 100;
    private int leftRightMargin = 50;
    private int topBottomMargin = 50;
    //private int topMargin = 0;

    private int fullWidth;
    private int fullHeight;

    private String xAxisLabel = "x";

    private String yAxisLabel = "y";

    private String plotTitle = "title";

    private int xaxis_precision = 0;

    private int yaxis_precision = 0;

    public ImageFormatter(Context context, Dataset kymograph) {
        context.inject(this);
        imp = convertService.convert(kymograph, ij.ImagePlus.class);
        displayRangeCtoMin = new HashMap<>();
        displayRangeCtoMax = new HashMap<>();
        cToLUTName = new HashMap<>();
    }

    public ImageFormatter setDisplayRangeMin(int c, double min) {
        displayRangeCtoMin.put(c, min);
        return this;
    }

    public ImageFormatter setDisplayRangeMax(int c, double min) {
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

        fullWidth = imp.getWidth() + leftRightMargin*2;
        fullHeight = imp.getHeight() + topBottomMargin*2;

        BufferedImage image = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.createGraphics();
        g.drawImage(imp.getBufferedImage(), leftRightMargin, topBottomMargin, null);
        paintAxis(g, 0, 0, 100, 27);
        g.dispose();

        try {
            //String tempFilePath = System.getProperty("java.io.tmpdir") + "temp_mars_kymograph_" + MarsMath.getUUID58() + ".png";
            //ImageIO.write(image, "PNG", new File(tempFilePath));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();

            //byte[] fileContent = Files.readAllBytes(new File(tempFilePath).toPath());
            htmlEncodedImage = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            logService.error(e);
        }
    }

    private void paintAxis(Graphics g, int imgLowerLeftX, int imgLowerLeftY, int imgWidth, int imgHeight) {
        Graphics2D g2d = (Graphics2D) g;

        g2d.setStroke(new BasicStroke(2.0f));

        int width = fullWidth - leftRightMargin * 2;
        int height = fullHeight - topBottomMargin * 2;

        Font font = new Font("Arial", Font.PLAIN, 14);
        Font label_font = new Font("Arial", Font.PLAIN, 14);
        Font title_font = new Font("Arial", Font.PLAIN, 14);

        Rectangle2D.Double bounds = new Rectangle2D.Double(imgLowerLeftX, imgLowerLeftY, imgWidth, imgHeight);

        AffineTransform transform = new AffineTransform();
        transform.translate(leftRightMargin, topBottomMargin + height);
        transform.scale(width / bounds.width, -height / bounds.height);
        transform.translate(-bounds.x, -bounds.y);

        double xStepSize = getStepSize(bounds.width, width / 50);	// draw a number every 50 pixels
        double yStepSize = getStepSize(bounds.height, height / 50);

        for (double x = bounds.x - (bounds.x % xStepSize); x < bounds.x + bounds.width; x += xStepSize) {
            Point2D.Double p = new Point2D.Double(x, 0);
            transform.transform(p, p);

            if (p.x > leftRightMargin) {
                g2d.setColor(Color.BLACK);
                g2d.drawLine((int)p.x, topBottomMargin + height, (int)p.x, topBottomMargin + height + 5);
                g2d.drawString(String.format("%." + xaxis_precision + "f", x), (int)p.x - g.getFontMetrics(font).stringWidth(String.format("%." + xaxis_precision + "f", x))/2, topBottomMargin + height + g.getFontMetrics(font).getHeight() + 2);
            }
        }

        g.setFont(label_font);

        AffineTransform at = g2d.getTransform();
        g2d.translate(g.getFontMetrics(label_font).getHeight(), topBottomMargin + height / 2 + g.getFontMetrics().stringWidth(yAxisLabel) / 2);
        g2d.rotate(-Math.PI / 2);
        g2d.drawString(yAxisLabel, 0, 0);
        g2d.setTransform(at);

        g2d.drawString(xAxisLabel, leftRightMargin + width / 2 - g.getFontMetrics().stringWidth(xAxisLabel) / 2, topBottomMargin + height + g.getFontMetrics(label_font).getHeight() + g.getFontMetrics(font).getHeight() + 7);

        g.setFont(title_font);
        g2d.drawString(plotTitle, leftRightMargin + width / 2 - g.getFontMetrics().stringWidth(plotTitle) / 2, g.getFontMetrics(title_font).getHeight());

        g.setFont(font);

        for (double y = bounds.y - (bounds.y % yStepSize); y < bounds.y + bounds.height; y += yStepSize) {
            Point2D.Double p = new Point2D.Double(0, y);
            transform.transform(p, p);

            if (p.y < height + topBottomMargin) {
                g2d.setColor(Color.BLACK);
                g2d.drawLine(leftRightMargin - 5, (int)p.y, leftRightMargin, (int)p.y);
                g2d.drawString(String.format("%." + yaxis_precision + "f", y), (int)leftRightMargin - g.getFontMetrics(font).stringWidth(String.format("%." + yaxis_precision + "f", y)) - 7, (int)p.y + (int)(g.getFontMetrics(font).getHeight()/3.1));
            }
        }
    }

    private double getStepSize(double range, int steps) {
        double step = range / steps;    // e.g. 0.00321
        double magnitude = Math.pow(10, Math.floor(Math.log10(step)));  // e.g. 0.001
        double mostSignificantDigit = Math.ceil(step / magnitude); // e.g. 3.21

        if (mostSignificantDigit > 5.0)
            return magnitude * 10.0;
        else if (mostSignificantDigit > 2.0)
            return magnitude * 5.0;
        else
            return magnitude * 2.0;
    }

    public String getHtmlEncodedImage() {
        return this.htmlEncodedImage;
    }
}
