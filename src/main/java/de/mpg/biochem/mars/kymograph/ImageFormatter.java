package de.mpg.biochem.mars.kymograph;

import net.imagej.Dataset;

import ij.ImagePlus;
import ij.IJ;

import org.scijava.Context;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import java.awt.*;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

import java.util.*;

import java.awt.geom.Point2D;
import java.awt.geom.AffineTransform;
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
    private int horizontalMargin = 50;
    private int verticalMargin = 50;
    private boolean showXAxis = true;
    private String xAxisLabel = "";

    private Rectangle2D.Double bounds;

    private AffineTransform transform;
    private boolean showYAxis = true;
    private double xMinValue = 0;
    private double xMaxValue = 0;

    private double xStepSize = Double.NaN;

    private double yStepSize = Double.NaN;

    private String yAxisLabel = "";

    private double yMinValue = 0;

    private double yMaxValue = 0;
    
    private int channelStackspacing = 10;
    private boolean channelStack = false;
    private String title = "";

    private int xaxis_precision = 0;

    private int yaxis_precision = 0;

    private int rescaleFactor = 1;

    private Font font = new Font("Arial", Font.PLAIN, 16);
    private Font label_font = new Font("Arial", Font.PLAIN, 16);
    private Font title_font = new Font("Arial", Font.PLAIN, 16);

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

    public ImageFormatter setXAxisRange(double min, double max) {
        this.xMinValue = min;
        this.xMaxValue = max;
        return this;
    }

    public ImageFormatter setYAxisRange(double min, double max) {
        this.yMinValue = min;
        this.yMaxValue = max;
        return this;
    }

    public ImageFormatter setLUT(int c, String lut) {
        cToLUTName.put(c, lut);
        return this;
    }

    public ImageFormatter setRescale(int rescaleFactor) {
        this.rescaleFactor = rescaleFactor;
        return this;
    }

    public ImageFormatter setXAxisLabel(String xAxisLabel) {
        this.xAxisLabel = xAxisLabel;
        return this;
    }

    public ImageFormatter setYAxisLabel(String yAxisLabel) {
        this.yAxisLabel = yAxisLabel;
        return this;
    }

    public ImageFormatter setTitle(String title) {
        this.title = title;
        return this;
    }

    public ImageFormatter setXAxisPrecision(int xAxisPrecision) {
        this.xaxis_precision = xAxisPrecision;
        return this;
    }

    public ImageFormatter setYAxisPrecision(int yAxisPrecision) {
        this.yaxis_precision = yAxisPrecision;
        return this;
    }

    public ImageFormatter setHorizontalMargin(int horizontalMargin) {
        this.horizontalMargin = horizontalMargin;
        return this;
    }

    public ImageFormatter setVerticalMargin(int verticalMargin) {
        this.verticalMargin = verticalMargin;
        return this;
    }

    public ImageFormatter showChannelStack(int spacing) {
        this.channelStackspacing = spacing;
        this.channelStack = true;
        return this;
    }

    public ImageFormatter setXStepSize(double xStepSize) {
        this.xStepSize = xStepSize;
        return this;
    }

    public ImageFormatter setYStepSize(double yStepSize) {
        this.yStepSize = yStepSize;
        return this;
    }

    public ImageFormatter hideXAxis() {
        this.showXAxis = false;
        return this;
    }

    public ImageFormatter hideYAxis() {
        this.showYAxis = false;
        return this;
    }

    public void build() {
        for (int c=1; c<=imp.getNChannels(); c++) {
            imp.setC(c);
            updateChannelColor(imp, c);
        }
        imp = imp.resize(imp.getWidth()*rescaleFactor, imp.getHeight()*rescaleFactor, 1, "none");

        int fullWidth = imp.getWidth() + horizontalMargin*2;
        int fullHeight = (channelStack) ? (1 + imp.getNChannels())*imp.getHeight() + imp.getNChannels()*channelStackspacing + verticalMargin*2 : imp.getHeight() + verticalMargin*2;

        BufferedImage image = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.createGraphics();

        bounds = new Rectangle2D.Double(xMinValue, yMinValue, xMaxValue - xMinValue, yMaxValue - yMinValue);

        transform = new AffineTransform();
        transform.translate(horizontalMargin, verticalMargin + imp.getHeight());
        transform.scale(imp.getWidth() / bounds.width, -imp.getHeight() / bounds.height);
        transform.translate(-bounds.x, -bounds.y);

        g.drawImage(imp.getBufferedImage(), horizontalMargin, verticalMargin, null);
        if (showYAxis) paintYAxis(g, verticalMargin, verticalMargin + imp.getHeight());
        if (showXAxis) paintXAxis(g, imp.getWidth(), fullHeight - verticalMargin*2);

        if (channelStack)
            for (int c=1; c<=imp.getNChannels(); c++) {
                transform = new AffineTransform();
                transform.translate(horizontalMargin, verticalMargin + (c + 1)*imp.getHeight() + c*channelStackspacing);
                transform.scale(imp.getWidth() / bounds.width, -imp.getHeight() / bounds.height);
                transform.translate(-bounds.x, -bounds.y);

                imp.setC(c);
                ImagePlus temp = new ImagePlus("temp image", imp.getChannelProcessor());
                updateChannelColor(temp, c);
                g.drawImage(temp.getBufferedImage(), horizontalMargin, verticalMargin + c*(imp.getHeight() + channelStackspacing), null);
                if (showYAxis) paintYAxis(g, verticalMargin + c*imp.getHeight() + c*channelStackspacing, verticalMargin + (c + 1)*imp.getHeight() + c*channelStackspacing);
            }

        //add title
        g.setFont(title_font);
        ((Graphics2D) g).drawString(title, horizontalMargin + imp.getWidth() / 2 - g.getFontMetrics().stringWidth(title) / 2, g.getFontMetrics(title_font).getHeight());
        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();

            htmlEncodedImage = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            logService.error(e);
        }
    }

    private void updateChannelColor(ImagePlus img, int c) {
        if (displayRangeCtoMin.containsKey(c) || displayRangeCtoMax.containsKey(c))
            img.setDisplayRange(displayRangeCtoMin.containsKey(c) ? displayRangeCtoMin.get(c) : img.getDisplayRangeMin(),
                    displayRangeCtoMax.containsKey(c) ? displayRangeCtoMax.get(c) : img.getDisplayRangeMax());
        else IJ.run(img, "Enhance Contrast", "saturated=0.35");
        if (cToLUTName.containsKey(c))
            IJ.run(img, cToLUTName.get(c), "");
        else if (c == 1)
            IJ.run(img, "Blue", "");
        else if (c == 2)
            IJ.run(img, "Magenta", "");
        else if (c == 3)
            IJ.run(img, "Green", "");
    }

    private void paintXAxis(Graphics g, int imgPixelWidth, int imgPixelHeight) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(2.0f));

        if (Double.isNaN(xStepSize)) xStepSize = getStepSize(bounds.width, imgPixelWidth / 50);	// draw a number every 50 pixels
        g.setFont(font);

        for (double x = bounds.x - (bounds.x % xStepSize); x < bounds.x + bounds.width; x += xStepSize) {
            Point2D.Double p = new Point2D.Double(x, 0);
            transform.transform(p, p);

            if (p.x >= horizontalMargin) {
                g2d.setColor(Color.BLACK);
                g2d.drawLine((int)p.x, verticalMargin + imgPixelHeight, (int)p.x, verticalMargin + imgPixelHeight + 5);
                g2d.drawString(String.format("%." + xaxis_precision + "f", x), (int)p.x - g.getFontMetrics(font).stringWidth(String.format("%." + xaxis_precision + "f", x))/2, verticalMargin + imgPixelHeight + g.getFontMetrics(font).getHeight() + 2);
            }
        }

        g.setFont(label_font);
        g2d.drawString(xAxisLabel, horizontalMargin + imgPixelWidth / 2 - g.getFontMetrics().stringWidth(xAxisLabel) / 2, verticalMargin + imgPixelHeight + g.getFontMetrics(label_font).getHeight() + g.getFontMetrics(font).getHeight() + 7);
    }

    private void paintYAxis(Graphics g, int imgTop, int imgBottom) {
        int imgPixelHeight = imgBottom - imgTop;
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(2.0f));

        if (Double.isNaN(yStepSize)) yStepSize = getStepSize(bounds.height, imgPixelHeight / 50); // draw a number every 50 pixels
        g.setFont(font);

        for (double y = bounds.y - (bounds.y % yStepSize); y < bounds.y + bounds.height; y += yStepSize) {
            Point2D.Double p = new Point2D.Double(0, y);
            transform.transform(p, p);

            if (p.y <= imgBottom) {
                g2d.setColor(Color.BLACK);
                g2d.drawLine(horizontalMargin - 5, (int)p.y, horizontalMargin, (int)p.y);
                g2d.drawString(String.format("%." + yaxis_precision + "f", y), (int)horizontalMargin - g.getFontMetrics(font).stringWidth(String.format("%." + yaxis_precision + "f", y)) - 7, (int)p.y + (int)(g.getFontMetrics(font).getHeight()/3.1));
            }
        }

        g.setFont(label_font);

        AffineTransform at = g2d.getTransform();
        g2d.translate(g.getFontMetrics(label_font).getHeight(), ((imgTop + imgBottom) / 2) + g.getFontMetrics().stringWidth(yAxisLabel) / 2);
        g2d.rotate(-Math.PI / 2);
        g2d.drawString(yAxisLabel, 0, 0);
        g2d.setTransform(at);
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
