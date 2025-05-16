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

import net.imagej.Dataset;

import ij.ImagePlus;
import ij.IJ;
import ij.ImageStack;
import ij.process.ImageProcessor;

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
import java.io.File;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

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
    private BufferedImage image;
    private int horizontalMargin = 50;
    private int verticalMargin = 50;

    private boolean rightVerticalOrientation = false;
    private boolean leftVerticalOrientation = false;
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

    private float axisLineWidth = 2.0f;
    private int tickLength = 5;
    private float tickLineWidth = 2.0f;
    private Color axisColor = Color.BLACK;

    private boolean darkTheme = false;

    private final Color DARK_THEME_AXIS_COLOR = new Color(0xe0e0e0);

    private final Color LIGHT_THEME_AXIS_COLOR = Color.BLACK;

    private Font font = new Font("Arial", Font.PLAIN, 16);
    private Font label_font = new Font("Arial", Font.PLAIN, 16);
    private Font title_font = new Font("Arial", Font.PLAIN, 16);

    private int singleChannelToShow = -1; // -1 means show all channels

    public ImageFormatter(Context context, Dataset kymograph) {
        context.inject(this);
        imp = convertService.convert(kymograph, ij.ImagePlus.class);
        displayRangeCtoMin = new HashMap<>();
        displayRangeCtoMax = new HashMap<>();
        cToLUTName = new HashMap<>();
    }

    public ImageFormatter setAxisFontSize(int fontSize) {
        font = new Font("Arial", Font.PLAIN, fontSize);
        return this;
    }

    public ImageFormatter setAxisFont(Font axisFont) {
        font = axisFont;
        return this;
    }

    public ImageFormatter setLabelFontSize(int fontSize) {
        label_font = new Font("Arial", Font.PLAIN, fontSize);
        return this;
    }

    public ImageFormatter setLabelFont(Font labelFont) {
        label_font = labelFont;
        return this;
    }

    public ImageFormatter setTitleFontSize(int fontSize) {
        title_font = new Font("Arial", Font.PLAIN, fontSize);
        return this;
    }

    public ImageFormatter setTitleFont(Font titleFont) {
        font = titleFont;
        return this;
    }

    /**
     * Sets the axis line width
     *
     * @param width The line width in pixels
     * @return This builder for method chaining
     */
    public ImageFormatter setAxisLineWidth(float width) {
        this.axisLineWidth = width;
        return this;
    }

    /**
     * Sets the tick mark length
     *
     * @param length The length in pixels
     * @return This builder for method chaining
     */
    public ImageFormatter setTickLength(int length) {
        this.tickLength = length;
        return this;
    }

    /**
     * Sets the tick mark line width
     *
     * @param width The line width in pixels
     * @return This builder for method chaining
     */
    public ImageFormatter setTickLineWidth(float width) {
        this.tickLineWidth = width;
        return this;
    }

    /**
     * Sets the axis color (applies to both axis lines and tick marks)
     *
     * @param color The color
     * @return This builder for method chaining
     */
    public ImageFormatter setAxisColor(Color color) {
        this.axisColor = color;
        return this;
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

    public ImageFormatter setDarkTheme(boolean darkTheme) {
        this.darkTheme = darkTheme;
        return this;
    }

    /**
     * Specifies that only a single channel should be shown in the output.
     * This will override the channelStack setting if it was enabled.
     *
     * @param channelIndex The 1-based index of the channel to show (1 for first channel)
     * @return This builder for method chaining
     */
    public ImageFormatter onlyShowChannel(int channelIndex) {
        if (channelIndex > 0 && channelIndex <= imp.getNChannels()) {
            this.singleChannelToShow = channelIndex;
            // If we're only showing one channel, we don't need stacking
            this.channelStack = false;
        } else {
            logService.warn("Channel index " + channelIndex + " is out of range (1-" + imp.getNChannels() + "). Showing all channels.");
            this.singleChannelToShow = -1;
        }
        return this;
    }

    /**
     * Specifies that only certain channels should be shown in the output.
     * If channelStack is enabled, only the specified channels will be stacked.
     *
     * @param channelIndices An array of 1-based indices of the channels to show (1 for first channel)
     * @return This builder for method chaining
     */
    public ImageFormatter onlyShowChannels(int... channelIndices) {
        if (channelIndices == null || channelIndices.length == 0) {
            logService.warn("No channel indices provided. Showing all channels.");
            this.singleChannelToShow = -1;
            return this;
        }

        // Validate channel indices
        for (int index : channelIndices) {
            if (index <= 0 || index > imp.getNChannels()) {
                logService.warn("Channel index " + index + " is out of range (1-" + imp.getNChannels() + "). Ignoring.");
                return this;
            }
        }

        // If only one channel requested, use the existing onlyShowChannel method
        if (channelIndices.length == 1) {
            return onlyShowChannel(channelIndices[0]);
        }

        // Extract the specified channels
        extractMultipleChannels(channelIndices);
        return this;
    }

    /**
     * Helper method to extract multiple channels from a multi-channel image
     *
     * @param channelIndices Array of 1-based channel indices to extract
     */
    private void extractMultipleChannels(int[] channelIndices) {
        if (imp.getNChannels() == 1) {
            // Already a single channel image, nothing to do
            return;
        }

        // Save the current position
        int currentC = imp.getC();
        int currentZ = imp.getZ();
        int currentT = imp.getT();

        int width = imp.getWidth();
        int height = imp.getHeight();
        int slices = imp.getNSlices();
        int frames = imp.getNFrames();

        // Create a new stack for the selected channels
        ImageStack newStack = new ImageStack(width, height);

        // For each time point and Z slice
        for (int t = 1; t <= frames; t++) {
            for (int z = 1; z <= slices; z++) {
                // Add each selected channel to the stack
                for (int i = 0; i < channelIndices.length; i++) {
                    int c = channelIndices[i];
                    imp.setPosition(c, z, t);
                    ImageProcessor processor = imp.getProcessor().duplicate();
                    newStack.addSlice(processor);
                }
            }
        }

        // Create new ImagePlus with the extracted channels
        ImagePlus newImp = new ImagePlus(imp.getTitle() + " - Selected Channels", newStack);

        // Set dimensions (now with only the selected channels)
        newImp.setDimensions(channelIndices.length, slices, frames);

        // Copy display ranges and LUTs for the selected channels
        for (int i = 0; i < channelIndices.length; i++) {
            int oldC = channelIndices[i];
            int newC = i + 1; // 1-based indexing for new channels

            // Set display range if specified
            if (displayRangeCtoMin.containsKey(oldC)) {
                double min = displayRangeCtoMin.get(oldC);
                double max = displayRangeCtoMax.containsKey(oldC) ?
                        displayRangeCtoMax.get(oldC) :
                        imp.getDisplayRangeMax();

                // Store for the new channel index
                displayRangeCtoMin.put(newC, min);
                displayRangeCtoMax.put(newC, max);
            }

            // Transfer LUT settings
            if (cToLUTName.containsKey(oldC)) {
                cToLUTName.put(newC, cToLUTName.get(oldC));
            }
        }

        // Replace imp with this new multi-channel image
        imp = newImp;

        // Restore the position as close as possible
        imp.setPosition(
                Math.min(currentC, channelIndices.length),
                Math.min(currentZ, slices),
                Math.min(currentT, frames)
        );
    }

    /**
     * Sets the orientation to vertical with the image rotated 90 degrees to the right
     * Kymographs/montages will be displayed vertically with time increasing downward
     * This rotates both the image and axes to match
     *
     * @return This builder for method chaining
     */
    public ImageFormatter rightVerticalOrientation() {
        this.rightVerticalOrientation = true;
        this.leftVerticalOrientation = false;
        return this;
    }

    /**
     * Sets the orientation to vertical with the image rotated 90 degrees to the left
     * Kymographs/montages will be displayed vertically with time increasing downward
     * This rotates both the image and axes to match
     *
     * @return This builder for method chaining
     */
    public ImageFormatter leftVerticalOrientation() {
        this.leftVerticalOrientation = true;
        this.rightVerticalOrientation = false;
        return this;
    }

    /**
     * Resets the orientation to the default horizontal layout
     *
     * @return This builder for method chaining
     */
    public ImageFormatter horizontalOrientation() {
        this.rightVerticalOrientation = false;
        this.leftVerticalOrientation = false;
        return this;
    }

    public void build() {
        // If only showing a single channel, extract it before proceeding
        if (singleChannelToShow > 0) {
            extractSingleChannel();
        }

        for (int c=1; c<=imp.getNChannels(); c++) {
            imp.setC(c);
            updateChannelColor(imp, c);
        }
        imp = imp.resize(imp.getWidth()*rescaleFactor, imp.getHeight()*rescaleFactor, 1, "none");

        // Calculate dimensions based on orientation
        int fullWidth, fullHeight;
        if (rightVerticalOrientation || leftVerticalOrientation) {
            // Swap width and height for vertical orientation
            fullWidth = imp.getHeight() + horizontalMargin*2;
            fullHeight = (channelStack) ?
                    (1 + imp.getNChannels())*imp.getWidth() + imp.getNChannels()*channelStackspacing + verticalMargin*2 :
                    imp.getWidth() + verticalMargin*2;
        } else {
            // Standard horizontal orientation
            fullWidth = imp.getWidth() + horizontalMargin*2;
            fullHeight = (channelStack) ?
                    (1 + imp.getNChannels())*imp.getHeight() + imp.getNChannels()*channelStackspacing + verticalMargin*2 :
                    imp.getHeight() + verticalMargin*2;
        }

        image = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Draw everything to the bitmap
        drawToGraphics(g2d);
        g2d.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();

            htmlEncodedImage = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            logService.error(e);
        }
    }

    /**
     * Helper method to extract a single channel from a multi-channel image
     */
    private void extractSingleChannel() {
        if (imp.getNChannels() == 1) {
            // Already a single channel image, nothing to do
            return;
        }

        // Save the current position
        int currentC = imp.getC();
        int currentZ = imp.getZ();
        int currentT = imp.getT();

        // Set to the channel we want to extract
        imp.setC(singleChannelToShow);

        // Create a new image containing only the selected channel
        int width = imp.getWidth();
        int height = imp.getHeight();
        int slices = imp.getNSlices();
        int frames = imp.getNFrames();

        ImageStack newStack = new ImageStack(width, height);

        for (int t = 1; t <= frames; t++) {
            for (int z = 1; z <= slices; z++) {
                imp.setPosition(singleChannelToShow, z, t);
                ImageProcessor processor = imp.getProcessor().duplicate();
                newStack.addSlice(processor);
            }
        }

        // Create new ImagePlus with the extracted channel
        ImagePlus newImp = new ImagePlus(imp.getTitle() + " - Channel " + singleChannelToShow, newStack);

        // Set dimensions (now only one channel)
        newImp.setDimensions(1, slices, frames);

        // Copy display ranges and other important settings
        if (displayRangeCtoMin.containsKey(singleChannelToShow)) {
            newImp.setDisplayRange(
                    displayRangeCtoMin.get(singleChannelToShow),
                    displayRangeCtoMax.containsKey(singleChannelToShow) ?
                            displayRangeCtoMax.get(singleChannelToShow) :
                            imp.getDisplayRangeMax()
            );
        }

        // Apply the LUT if specified
        if (cToLUTName.containsKey(singleChannelToShow)) {
            IJ.run(newImp, cToLUTName.get(singleChannelToShow), "");
        }

        // Replace imp with this new single-channel image
        imp = newImp;

        // Update the maps to refer to channel 1 (the only channel now)
        if (displayRangeCtoMin.containsKey(singleChannelToShow)) {
            displayRangeCtoMin.put(1, displayRangeCtoMin.get(singleChannelToShow));
        }
        if (displayRangeCtoMax.containsKey(singleChannelToShow)) {
            displayRangeCtoMax.put(1, displayRangeCtoMax.get(singleChannelToShow));
        }
        if (cToLUTName.containsKey(singleChannelToShow)) {
            cToLUTName.put(1, cToLUTName.get(singleChannelToShow));
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

    /**
     * Common drawing method used by both bitmap and SVG output
     *
     * @param g The graphics context to draw to
     */
    private void drawToGraphics(Graphics2D g) {
        // Setup transformation and bounds
        bounds = new Rectangle2D.Double(xMinValue, yMinValue, xMaxValue - xMinValue, yMaxValue - yMinValue);

        // Set up the background
        if (darkTheme) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
        }

        // Enable anti-aliasing for smoother lines and text
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (rightVerticalOrientation || leftVerticalOrientation) {
            // Draw rotated content
            drawRotatedContent(g);
        } else {
            // Draw standard horizontal content
            drawHorizontalContent(g);
        }

        // Draw title
        g.setFont(title_font);
        g.setColor(darkTheme ? DARK_THEME_AXIS_COLOR : Color.BLACK);
        g.drawString(title,
                image.getWidth() / 2 - g.getFontMetrics().stringWidth(title) / 2,
                g.getFontMetrics(title_font).getHeight());
    }

    /**
     * Draws content in standard horizontal orientation
     *
     * @param g The graphics context to draw to
     */
    private void drawHorizontalContent(Graphics2D g) {
        transform = new AffineTransform();
        transform.translate(horizontalMargin, verticalMargin + imp.getHeight());
        transform.scale(imp.getWidth() / bounds.width, -imp.getHeight() / bounds.height);
        transform.translate(-bounds.x, -bounds.y);

        // Draw main image
        g.drawImage(imp.getBufferedImage(), horizontalMargin, verticalMargin, null);

        // Draw axes if enabled
        if (showYAxis) paintYAxis(g, verticalMargin, verticalMargin + imp.getHeight());
        if (showXAxis) paintXAxis(g, imp.getWidth(), image.getHeight() - verticalMargin*2);

        // Draw channel stack if enabled
        if (channelStack) {
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
        }
    }

    /**
     * Draws content in rotated vertical orientation (either left or right)
     *
     * @param g The graphics context to draw to
     */
    private void drawRotatedContent(Graphics2D g) {
        // For vertical orientation, we swap width and height
        // and rotate the coordinate system accordingly
        boolean rightRotation = rightVerticalOrientation;

        // Draw main image (rotated)
        BufferedImage originalImage = imp.getBufferedImage();
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        AffineTransform imgTransform = new AffineTransform();
        if (rightRotation) {
            // Rotation 90 degrees clockwise (right)
            imgTransform.translate(horizontalMargin + height, verticalMargin);
            imgTransform.rotate(Math.PI / 2);
        } else {
            // Rotation 90 degrees counter-clockwise (left)
            imgTransform.translate(horizontalMargin, verticalMargin + width);
            imgTransform.rotate(-Math.PI / 2);
        }

        g.drawImage(originalImage, imgTransform, null);

        // Set up transform for axes
        transform = new AffineTransform();
        if (rightRotation) {
            // Right vertical: y-axis becomes horizontal, x-axis becomes vertical
            transform.translate(horizontalMargin, verticalMargin + imp.getHeight());
            transform.scale(imp.getHeight() / bounds.width, -imp.getWidth() / bounds.height);
        } else {
            // Left vertical: y-axis becomes horizontal, x-axis becomes vertical (flipped)
            transform.translate(horizontalMargin + imp.getHeight(), verticalMargin);
            transform.scale(-imp.getHeight() / bounds.width, -imp.getWidth() / bounds.height);
        }
        transform.translate(-bounds.x, -bounds.y);

        // Always draw axes on the bottom and left sides
        if (showYAxis) {
            // For both orientations, draw Y-axis as a horizontal axis at the bottom
            paintHorizontalAxis(g, imp.getHeight(),
                    verticalMargin + imp.getWidth(), // Position at the bottom of the rotated image
                    horizontalMargin, horizontalMargin + imp.getHeight(),
                    yAxisLabel, yStepSize, bounds.y, bounds.height, yaxis_precision);
        }

        if (showXAxis) {
            // For both orientations, draw X-axis as a vertical axis on the left
            paintVerticalAxis(g, imp.getWidth(), horizontalMargin,
                    verticalMargin, verticalMargin + imp.getWidth(),
                    xAxisLabel, xStepSize, bounds.x, bounds.width, xaxis_precision, false);
        }

        // Draw channel stack if enabled
        if (channelStack) {
            for (int c=1; c<=imp.getNChannels(); c++) {
                imp.setC(c);
                ImagePlus temp = new ImagePlus("temp image", imp.getChannelProcessor());
                updateChannelColor(temp, c);

                BufferedImage channelImage = temp.getBufferedImage();

                AffineTransform channelTransform = new AffineTransform();
                if (rightRotation) {
                    // Rotation 90 degrees clockwise (right)
                    channelTransform.translate(horizontalMargin + height + c*(width + channelStackspacing), verticalMargin);
                    channelTransform.rotate(Math.PI / 2);
                } else {
                    // Rotation 90 degrees counter-clockwise (left)
                    channelTransform.translate(horizontalMargin, verticalMargin + width + c*(width + channelStackspacing));
                    channelTransform.rotate(-Math.PI / 2);
                }

                g.drawImage(channelImage, channelTransform, null);

                // Draw Y-axis for each channel
                if (showYAxis) {
                    // Draw Y-axis as a horizontal axis at the bottom of each channel
                    paintHorizontalAxis(g, imp.getHeight(),
                            verticalMargin + imp.getWidth() + c*imp.getWidth() + c*channelStackspacing,
                            horizontalMargin, horizontalMargin + imp.getHeight(),
                            "", yStepSize, bounds.y, bounds.height, yaxis_precision);
                }
            }
        }
    }

    /**
     * Paints a horizontal axis (used for rotated layouts)
     */
    private void paintHorizontalAxis(Graphics g, int axisLength, int yPosition,
                                     int xStart, int xEnd, String axisLabel, double stepSize,
                                     double minValue, double valueRange, int precision) {

        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(axisLineWidth));

        // Use dark theme color or custom axis color
        Color currentAxisColor = darkTheme ? DARK_THEME_AXIS_COLOR : axisColor;
        g2d.setColor(currentAxisColor);

        if (Double.isNaN(stepSize)) stepSize = getStepSize(valueRange, axisLength / 50);
        g.setFont(font);

        // Draw main axis line
        g2d.drawLine(xStart, yPosition, xEnd, yPosition);

        // Draw tick marks and labels
        for (double value = minValue - (minValue % stepSize); value < minValue + valueRange; value += stepSize) {
            int xPos = (int)(xStart + ((value - minValue) / valueRange) * axisLength);

            if (xPos >= xStart && xPos <= xEnd) {
                g2d.setStroke(new BasicStroke(tickLineWidth));
                g2d.drawLine(xPos, yPosition, xPos, yPosition + tickLength);

                String label = String.format("%." + precision + "f", value);
                g2d.drawString(label,
                        xPos - g.getFontMetrics(font).stringWidth(label)/2,
                        yPosition + tickLength + g.getFontMetrics(font).getHeight());
            }
        }

        // Draw axis label
        if (axisLabel != null && !axisLabel.isEmpty()) {
            g.setFont(label_font);
            g2d.drawString(axisLabel,
                    xStart + axisLength / 2 - g.getFontMetrics().stringWidth(axisLabel) / 2,
                    yPosition + tickLength + g.getFontMetrics(font).getHeight() + g.getFontMetrics(label_font).getHeight());
        }
    }

    /**
     * Paints a vertical axis (used for rotated layouts)
     */
    private void paintVerticalAxis(Graphics g, int axisLength, int xPosition,
                                   int yStart, int yEnd, String axisLabel, double stepSize,
                                   double minValue, double valueRange, int precision, boolean rightSide) {

        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(axisLineWidth));

        // Use dark theme color or custom axis color
        Color currentAxisColor = darkTheme ? DARK_THEME_AXIS_COLOR : axisColor;
        g2d.setColor(currentAxisColor);

        if (Double.isNaN(stepSize)) stepSize = getStepSize(valueRange, axisLength / 50);
        g.setFont(font);

        // Draw main axis line
        g2d.drawLine(xPosition, yStart, xPosition, yEnd);

        // Draw tick marks and labels
        for (double value = minValue - (minValue % stepSize); value < minValue + valueRange; value += stepSize) {
            int yPos = (int)(yStart + ((value - minValue) / valueRange) * axisLength);

            if (yPos >= yStart && yPos <= yEnd) {
                g2d.setStroke(new BasicStroke(tickLineWidth));

                if (rightSide) {
                    g2d.drawLine(xPosition, yPos, xPosition + tickLength, yPos);

                    String label = String.format("%." + precision + "f", value);
                    g2d.drawString(label,
                            xPosition + tickLength + 2,
                            yPos + g.getFontMetrics(font).getAscent()/2);
                } else {
                    g2d.drawLine(xPosition - tickLength, yPos, xPosition, yPos);

                    String label = String.format("%." + precision + "f", value);
                    g2d.drawString(label,
                            xPosition - tickLength - g.getFontMetrics(font).stringWidth(label) - 2,
                            yPos + g.getFontMetrics(font).getAscent()/2);
                }
            }
        }

        // Draw axis label with rotation
        if (axisLabel != null && !axisLabel.isEmpty()) {
            g.setFont(label_font);
            AffineTransform at = g2d.getTransform();

            if (rightSide) {
                g2d.translate(xPosition + tickLength + g.getFontMetrics(font).getHeight() + 10,
                        yStart + axisLength / 2);
                g2d.rotate(Math.PI / 2);
            } else {
                g2d.translate(xPosition - tickLength - g.getFontMetrics(font).getHeight() - 10,
                        yStart + axisLength / 2);
                g2d.rotate(-Math.PI / 2);
            }

            g2d.drawString(axisLabel, -g.getFontMetrics().stringWidth(axisLabel) / 2, 0);
            g2d.setTransform(at);
        }
    }

    /**
     * Updated X axis painting method with customizable tick marks and colors
     */
    private void paintXAxis(Graphics g, int imgPixelWidth, int imgPixelHeight) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(axisLineWidth));

        // Use dark theme color or custom axis color
        Color currentAxisColor = darkTheme ? DARK_THEME_AXIS_COLOR : axisColor;
        g2d.setColor(currentAxisColor);

        if (Double.isNaN(xStepSize)) xStepSize = getStepSize(bounds.width, imgPixelWidth / 50);
        g.setFont(font);

        // Draw main axis line
        g2d.drawLine(horizontalMargin, verticalMargin + imgPixelHeight,
                horizontalMargin + imgPixelWidth, verticalMargin + imgPixelHeight);

        // Draw tick marks and labels
        for (double x = bounds.x - (bounds.x % xStepSize); x < bounds.x + bounds.width; x += xStepSize) {
            Point2D.Double p = new Point2D.Double(x, 0);
            transform.transform(p, p);

            if (p.x >= horizontalMargin && p.x <= horizontalMargin + imgPixelWidth) {
                g2d.setStroke(new BasicStroke(tickLineWidth));
                g2d.drawLine((int)p.x, verticalMargin + imgPixelHeight,
                        (int)p.x, verticalMargin + imgPixelHeight + tickLength);

                String label = String.format("%." + xaxis_precision + "f", x);
                g2d.drawString(label,
                        (int)p.x - g.getFontMetrics(font).stringWidth(label)/2,
                        verticalMargin + imgPixelHeight + tickLength + g.getFontMetrics(font).getHeight());
            }
        }

        // Draw axis label
        g.setFont(label_font);
        g2d.drawString(xAxisLabel,
                horizontalMargin + imgPixelWidth / 2 - g.getFontMetrics().stringWidth(xAxisLabel) / 2,
                verticalMargin + imgPixelHeight + tickLength + g.getFontMetrics(font).getHeight() + g.getFontMetrics(label_font).getHeight());
    }

    /**
     * Updated Y axis painting method with customizable tick marks and colors
     */
    private void paintYAxis(Graphics g, int imgTop, int imgBottom) {
        int imgPixelHeight = imgBottom - imgTop;
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(axisLineWidth));

        // Use dark theme color or custom axis color
        Color currentAxisColor = darkTheme ? DARK_THEME_AXIS_COLOR : axisColor;
        g2d.setColor(currentAxisColor);

        if (Double.isNaN(yStepSize)) yStepSize = getStepSize(bounds.height, imgPixelHeight / 50);
        g.setFont(font);

        // Draw main axis line
        g2d.drawLine(horizontalMargin, imgTop, horizontalMargin, imgBottom);

        // Draw tick marks and labels
        for (double y = bounds.y - (bounds.y % yStepSize); y < bounds.y + bounds.height; y += yStepSize) {
            Point2D.Double p = new Point2D.Double(0, y);
            transform.transform(p, p);

            if (p.y >= imgTop && p.y <= imgBottom) {
                g2d.setStroke(new BasicStroke(tickLineWidth));
                g2d.drawLine(horizontalMargin - tickLength, (int)p.y, horizontalMargin, (int)p.y);

                String label = String.format("%." + yaxis_precision + "f", y);
                g2d.drawString(label,
                        horizontalMargin - tickLength - g.getFontMetrics(font).stringWidth(label) - 2,
                        (int)p.y + g.getFontMetrics(font).getAscent()/2);
            }
        }

        // Draw Y axis label with rotation
        g.setFont(label_font);
        AffineTransform at = g2d.getTransform();
        g2d.translate(g.getFontMetrics(label_font).getHeight(),
                (imgTop + imgBottom) / 2 + g.getFontMetrics().stringWidth(yAxisLabel) / 2);
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

    /**
     * Saves the image as an SVG file using Apache Batik.
     *
     * @param path The file path to save to (should end with .svg)
     */
    public void saveAsSVG(String path) {
        try {
            // Create an SVG document
            DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
            String svgNS = "http://www.w3.org/2000/svg";
            Document document = domImpl.createDocument(svgNS, "svg", null);

            // Create an SVG generator
            SVGGraphics2D svgGenerator = new SVGGraphics2D(document);

            // Set dimensions
            svgGenerator.setSVGCanvasSize(new Dimension(image.getWidth(), image.getHeight()));

            // Draw content
            drawToGraphics(svgGenerator);

            // Write to file
            try (Writer out = new OutputStreamWriter(new FileOutputStream(path), "UTF-8")) {
                svgGenerator.stream(out, true); // true means use CSS style attributes
            }

            logService.info("SVG saved to: " + path);
        } catch (Exception e) {
            logService.error("Failed to save SVG: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Saves the image as a PNG file.
     *
     * @param path The file path to save to (should end with .png)
     * @return This builder for method chaining
     */
    public ImageFormatter saveAsPNG(String path) {
        if (image == null) {
            logService.error("No image to save. Call build() first.");
            return this;
        }

        try {
            File outputFile = new File(path);
            // Ensure directory exists
            outputFile.getParentFile().mkdirs();

            // Save the image as PNG
            ImageIO.write(image, "png", outputFile);

            logService.info("PNG saved to: " + path);
        } catch (Exception e) {
            logService.error("Failed to save PNG: " + e.getMessage());
            e.printStackTrace();
        }

        return this;
    }
}