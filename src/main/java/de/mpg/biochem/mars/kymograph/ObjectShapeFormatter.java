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

import de.mpg.biochem.mars.image.PeakShape;
import de.mpg.biochem.mars.object.MartianObject;
import de.mpg.biochem.mars.object.ObjectArchive;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

/**
 * Creates a montage of shapes from a MartianObject across time points.
 * 
 * @author Karl Duderstadt
 */
public class ObjectShapeFormatter {

    @Parameter
    private Context context;

    @Parameter
    private LogService logService;

    @Parameter
    private DatasetService datasetService;

    private MartianObject martianObject;
    private ObjectArchive objectArchive;
    
    private String title = "";
    private String xAxisLabel = "Time";
    private int spacing = 10;
    private int canvasWidth = 150;
    private int canvasHeight = 150;
    private int horizontalMargin = 50;
    private int verticalMargin = 50;
    private int minT = -1;
    private int maxT = -1;
    private int frameStep = 1;
    private boolean showTimeLabels = true;
    private boolean normalizeShapes = false;
    private boolean showAxes = true;
    private int xaxis_precision = 0;
    private int timeUnitDivider = 1; // For converting to appropriate time units
    
    private Font font = new Font("Arial", Font.PLAIN, 12);
    private Font label_font = new Font("Arial", Font.PLAIN, 14);
    private Font title_font = new Font("Arial", Font.PLAIN, 16);
    
    private Color shapeColor = Color.BLUE;
    private float shapeLineWidth = 2.0f;
    private Color axisColor = Color.BLACK;
    private float axisLineWidth = 1.0f;
    private boolean fillShape = false;
    private Color fillColor = new Color(0, 0, 255, 50); // Semi-transparent blue
    
    private boolean darkTheme = false;
    private final Color DARK_THEME_COLOR = new Color(0xe0e0e0);
    private final Color DARK_THEME_FILL_COLOR = new Color(224, 224, 224, 50);
    
    private String htmlEncodedImage;
    private BufferedImage image;

    public ObjectShapeFormatter(Context context) {
        context.inject(this);
    }

    public ObjectShapeFormatter(Context context, ObjectArchive objectArchive) {
        context.inject(this);
        this.objectArchive = objectArchive;
    }
    
    /**
     * Set the object to use for shape visualization
     *
     * @param martianObject The object to use
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setObject(MartianObject martianObject) {
        this.martianObject = martianObject;
        return this;
    }

    /**
     * Set the object to use by UID
     *
     * @param UID The UID of the object to use
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setObject(String UID) {
        if (objectArchive != null) {
            this.martianObject = objectArchive.get(UID);
        } else {
            logService.error("Object archive is not set. Cannot retrieve object by UID.");
        }
        return this;
    }
    
    /**
     * Set the title for the montage
     *
     * @param title The title string
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setTitle(String title) {
        this.title = title;
        return this;
    }
    
    /**
     * Set the x-axis label
     *
     * @param label The x-axis label
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setXAxisLabel(String label) {
        this.xAxisLabel = label;
        return this;
    }
    
    /**
     * Set spacing between frames in the montage
     *
     * @param spacing The spacing in pixels
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setSpacing(int spacing) {
        this.spacing = spacing;
        return this;
    }
    
    /**
     * Set the width of each canvas for individual shapes
     *
     * @param width Width in pixels
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setCanvasWidth(int width) {
        this.canvasWidth = width;
        return this;
    }
    
    /**
     * Set the height of each canvas for individual shapes
     *
     * @param height Height in pixels
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setCanvasHeight(int height) {
        this.canvasHeight = height;
        return this;
    }
    
    /**
     * Set horizontal margin
     *
     * @param margin Margin in pixels
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setHorizontalMargin(int margin) {
        this.horizontalMargin = margin;
        return this;
    }
    
    /**
     * Set vertical margin
     *
     * @param margin Margin in pixels
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setVerticalMargin(int margin) {
        this.verticalMargin = margin;
        return this;
    }
    
    /**
     * Set the minimum time point to include
     *
     * @param minT The minimum time point
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setMinT(int minT) {
        this.minT = minT;
        return this;
    }
    
    /**
     * Set the maximum time point to include
     *
     * @param maxT The maximum time point
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setMaxT(int maxT) {
        this.maxT = maxT;
        return this;
    }
    
    /**
     * Set the step size between frames to include
     *
     * @param step Include every Nth frame
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setFrameStep(int step) {
        if (step < 1) step = 1;
        this.frameStep = step;
        return this;
    }
    
    /**
     * Enable or disable time labels
     *
     * @param show True to show time labels, false to hide
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter showTimeLabels(boolean show) {
        this.showTimeLabels = show;
        return this;
    }
    
    /**
     * Enable or disable shape normalization
     * When enabled, shapes will be scaled to fit the canvas
     *
     * @param normalize True to normalize shapes, false to use original scale
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter normalizeShapes(boolean normalize) {
        this.normalizeShapes = normalize;
        return this;
    }
    
    /**
     * Enable or disable axes
     *
     * @param show True to show axes, false to hide
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter showAxes(boolean show) {
        this.showAxes = show;
        return this;
    }
    
    /**
     * Set the precision for the time display
     *
     * @param precision Number of decimal places to show
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setXAxisPrecision(int precision) {
        this.xaxis_precision = precision;
        return this;
    }
    
    /**
     * Set the axis font
     *
     * @param font Font for the axis text
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setAxisFont(Font font) {
        this.font = font;
        return this;
    }
    
    /**
     * Set the axis font size
     *
     * @param size Font size in points
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setAxisFontSize(int size) {
        this.font = new Font(font.getName(), font.getStyle(), size);
        return this;
    }
    
    /**
     * Set the label font
     *
     * @param font Font for labels
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setLabelFont(Font font) {
        this.label_font = font;
        return this;
    }
    
    /**
     * Set the label font size
     *
     * @param size Font size in points
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setLabelFontSize(int size) {
        this.label_font = new Font(label_font.getName(), label_font.getStyle(), size);
        return this;
    }
    
    /**
     * Set the title font
     *
     * @param font Font for the title
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setTitleFont(Font font) {
        this.title_font = font;
        return this;
    }
    
    /**
     * Set the title font size
     *
     * @param size Font size in points
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setTitleFontSize(int size) {
        this.title_font = new Font(title_font.getName(), title_font.getStyle(), size);
        return this;
    }
    
    /**
     * Set the shape color
     *
     * @param color Color for shape outlines
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setShapeColor(Color color) {
        this.shapeColor = color;
        return this;
    }
    
    /**
     * Set the shape line width
     *
     * @param width Line width in pixels
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setShapeLineWidth(float width) {
        this.shapeLineWidth = width;
        return this;
    }
    
    /**
     * Set the axis color
     *
     * @param color Color for axes
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setAxisColor(Color color) {
        this.axisColor = color;
        return this;
    }
    
    /**
     * Set the axis line width
     *
     * @param width Line width in pixels
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setAxisLineWidth(float width) {
        this.axisLineWidth = width;
        return this;
    }
    
    /**
     * Enable or disable shape filling
     *
     * @param fill True to fill shapes, false for outlines only
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setFillShape(boolean fill) {
        this.fillShape = fill;
        return this;
    }
    
    /**
     * Set the fill color for shapes
     *
     * @param color Fill color (can include alpha transparency)
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setFillColor(Color color) {
        this.fillColor = color;
        return this;
    }
    
    /**
     * Set the time unit divider
     * This can be used to convert time units (e.g., frames to seconds)
     *
     * @param divider Division factor for time values
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setTimeUnitDivider(int divider) {
        if (divider <= 0) divider = 1;
        this.timeUnitDivider = divider;
        return this;
    }
    
    /**
     * Enable or disable dark theme
     *
     * @param darkTheme True for dark theme, false for light theme
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter setDarkTheme(boolean darkTheme) {
        this.darkTheme = darkTheme;
        return this;
    }

    /**
     * Build the montage of shapes
     *
     * @return This builder after creating the montage
     */
    public ObjectShapeFormatter build() {
        if (martianObject == null) {
            logService.error("No object set. Use setObject() before building.");
            return this;
        }
        
        // Get all available time points with shapes
        Set<Integer> allTimePoints = martianObject.getShapeKeys();
        
        if (allTimePoints.isEmpty()) {
            logService.error("Object has no shapes.");
            return this;
        }
        
        // Sort time points
        List<Integer> sortedTimePoints = new ArrayList<>(allTimePoints);
        Collections.sort(sortedTimePoints);
        
        // Apply time point constraints
        int firstT = (minT != -1) ? minT : sortedTimePoints.get(0);
        int lastT = (maxT != -1) ? maxT : sortedTimePoints.get(sortedTimePoints.size() - 1);
        
        // Filter time points based on constraints and step
        List<Integer> selectedTimePoints = sortedTimePoints.stream()
                .filter(t -> t >= firstT && t <= lastT && (t - firstT) % frameStep == 0)
                .collect(Collectors.toList());
        
        if (selectedTimePoints.isEmpty()) {
            logService.error("No shapes found in the specified time range.");
            return this;
        }
        
        int frameCount = selectedTimePoints.size();
        
        // Calculate overall montage dimensions
        int totalWidth = horizontalMargin * 2 + frameCount * canvasWidth + (frameCount - 1) * spacing;
        int totalHeight = verticalMargin * 2 + canvasHeight;
        
        // Create the montage image
        image = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        // Enable anti-aliasing for smoother rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // Set background color based on theme
        if (darkTheme) {
            g2d.setColor(Color.BLACK);
        } else {
            g2d.setColor(Color.WHITE);
        }
        g2d.fillRect(0, 0, totalWidth, totalHeight);
        
        // Draw title if provided
        if (title != null && !title.isEmpty()) {
            g2d.setFont(title_font);
            g2d.setColor(darkTheme ? DARK_THEME_COLOR : Color.BLACK);
            int titleWidth = g2d.getFontMetrics().stringWidth(title);
            g2d.drawString(title, (totalWidth - titleWidth) / 2, g2d.getFontMetrics().getHeight());
        }
        
        // Find global bounds for shape normalization (if enabled)
        double globalMinX = Double.POSITIVE_INFINITY;
        double globalMaxX = Double.NEGATIVE_INFINITY;
        double globalMinY = Double.POSITIVE_INFINITY;
        double globalMaxY = Double.NEGATIVE_INFINITY;
        
        if (normalizeShapes) {
            for (int t : selectedTimePoints) {
                if (martianObject.hasShape(t)) {
                    PeakShape shape = martianObject.getShape(t);
                    for (double x : shape.x) {
                        if (x < globalMinX) globalMinX = x;
                        if (x > globalMaxX) globalMaxX = x;
                    }
                    for (double y : shape.y) {
                        if (y < globalMinY) globalMinY = y;
                        if (y > globalMaxY) globalMaxY = y;
                    }
                }
            }
        }
        
        // Draw each shape in the sequence
        for (int i = 0; i < selectedTimePoints.size(); i++) {
            int t = selectedTimePoints.get(i);
            int xPos = horizontalMargin + i * (canvasWidth + spacing);
            int yPos = verticalMargin;
            
            // Draw a shape for this time point
            drawShape(g2d, t, xPos, yPos, globalMinX, globalMaxX, globalMinY, globalMaxY);
            
            // Draw time label
            if (showTimeLabels) {
                g2d.setFont(font);
                g2d.setColor(darkTheme ? DARK_THEME_COLOR : Color.BLACK);
                String timeLabel = String.format("%." + xaxis_precision + "f", (double)t / timeUnitDivider);
                int labelWidth = g2d.getFontMetrics().stringWidth(timeLabel);
                g2d.drawString(timeLabel, xPos + (canvasWidth - labelWidth) / 2, 
                        yPos + canvasHeight + g2d.getFontMetrics().getHeight() + 5);
            }
        }
        
        // Draw x-axis label if provided
        if (xAxisLabel != null && !xAxisLabel.isEmpty()) {
            g2d.setFont(label_font);
            g2d.setColor(darkTheme ? DARK_THEME_COLOR : Color.BLACK);
            int labelWidth = g2d.getFontMetrics().stringWidth(xAxisLabel);
            g2d.drawString(xAxisLabel, (totalWidth - labelWidth) / 2, 
                    totalHeight - verticalMargin / 3);
        }
        
        g2d.dispose();
        
        // Encode the image for HTML display
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();
            
            htmlEncodedImage = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            logService.error("Failed to encode image: " + e.getMessage());
        }
        
        return this;
    }
    
    /**
     * Draw a shape for a specific time point
     *
     * @param g2d The graphics context
     * @param t Time point
     * @param xPos X position in the montage
     * @param yPos Y position in the montage
     * @param globalMinX Global minimum X for normalization
     * @param globalMaxX Global maximum X for normalization
     * @param globalMinY Global minimum Y for normalization
     * @param globalMaxY Global maximum Y for normalization
     */
    private void drawShape(Graphics2D g2d, int t, int xPos, int yPos, 
            double globalMinX, double globalMaxX, double globalMinY, double globalMaxY) {
        
        if (!martianObject.hasShape(t)) {
            return;
        }
        
        PeakShape shape = martianObject.getShape(t);
        
        // Calculate local bounds for this shape
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        
        for (double x : shape.x) {
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
        }
        for (double y : shape.y) {
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        
        // Use local or global bounds based on normalization setting
        if (normalizeShapes) {
            minX = globalMinX;
            maxX = globalMaxX;
            minY = globalMinY;
            maxY = globalMaxY;
        }
        
        // Calculate scaling factors
        double scaleX = (canvasWidth - 20) / (maxX - minX);
        double scaleY = (canvasHeight - 20) / (maxY - minY);
        double scale = Math.min(scaleX, scaleY);
        
        // Calculate center offsets
        double centerX = (maxX + minX) / 2;
        double centerY = (maxY + minY) / 2;
        
        // Draw frame and axes if enabled
        if (showAxes) {
            g2d.setColor(darkTheme ? DARK_THEME_COLOR : axisColor);
            g2d.setStroke(new BasicStroke(axisLineWidth));
            g2d.drawRect(xPos, yPos, canvasWidth, canvasHeight);
            
            // Draw coordinate axes through the center
            int centerXPos = xPos + canvasWidth / 2;
            int centerYPos = yPos + canvasHeight / 2;
            g2d.drawLine(xPos + 10, centerYPos, xPos + canvasWidth - 10, centerYPos);
            g2d.drawLine(centerXPos, yPos + 10, centerXPos, yPos + canvasHeight - 10);
        }
        
        // Create polygon for the shape
        int[] xPoints = new int[shape.x.length];
        int[] yPoints = new int[shape.y.length];
        
        for (int i = 0; i < shape.x.length; i++) {
            xPoints[i] = (int)(xPos + canvasWidth/2 + (shape.x[i] - centerX) * scale);
            yPoints[i] = (int)(yPos + canvasHeight/2 - (shape.y[i] - centerY) * scale);
        }
        
        // Fill shape if enabled
        if (fillShape) {
            g2d.setColor(darkTheme ? DARK_THEME_FILL_COLOR : fillColor);
            g2d.fillPolygon(xPoints, yPoints, shape.x.length);
        }
        
        // Draw shape outline
        g2d.setColor(darkTheme ? DARK_THEME_COLOR : shapeColor);
        g2d.setStroke(new BasicStroke(shapeLineWidth));
        g2d.drawPolygon(xPoints, yPoints, shape.x.length);
    }
    
    /**
     * Get the HTML-encoded image
     *
     * @return Base64-encoded PNG image with data URL prefix
     */
    public String getHtmlEncodedImage() {
        return htmlEncodedImage;
    }
    
    /**
     * Get the buffered image
     *
     * @return The montage as a BufferedImage
     */
    public BufferedImage getImage() {
        return image;
    }
    
    /**
     * Save the montage as a PNG file
     *
     * @param path The file path to save to
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter saveAsPNG(String path) {
        try {
            ImageIO.write(image, "png", new File(path));
            logService.info("Montage saved to: " + path);
        } catch (IOException e) {
            logService.error("Failed to save PNG: " + e.getMessage());
        }
        return this;
    }
    
    /**
     * Saves the montage as an SVG file
     *
     * @param path The file path to save to (should end with .svg)
     * @return This builder for method chaining
     */
    public ObjectShapeFormatter saveAsSVG(String path) {
        try {
            // Create an SVG document
            DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
            String svgNS = "http://www.w3.org/2000/svg";
            Document document = domImpl.createDocument(svgNS, "svg", null);
            
            // Create an SVG generator
            SVGGraphics2D svgGenerator = new SVGGraphics2D(document);
            
            // Set dimensions
            svgGenerator.setSVGCanvasSize(new Dimension(image.getWidth(), image.getHeight()));
            
            // Rebuild the visualization directly to SVG
            // We'll recalculate everything to ensure vector quality
            svgGenerator.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            svgGenerator.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Set background
            svgGenerator.setColor(darkTheme ? Color.BLACK : Color.WHITE);
            svgGenerator.fillRect(0, 0, image.getWidth(), image.getHeight());
            
            // Redraw everything
            // Get all available time points with shapes
            Set<Integer> allTimePoints = martianObject.getShapeKeys();
            
            // Sort time points
            List<Integer> sortedTimePoints = new ArrayList<>(allTimePoints);
            Collections.sort(sortedTimePoints);
            
            // Apply time point constraints
            int firstT = (minT != -1) ? minT : sortedTimePoints.get(0);
            int lastT = (maxT != -1) ? maxT : sortedTimePoints.get(sortedTimePoints.size() - 1);
            
            // Filter time points based on constraints and step
            List<Integer> selectedTimePoints = sortedTimePoints.stream()
                    .filter(t -> t >= firstT && t <= lastT && (t - firstT) % frameStep == 0)
                    .collect(Collectors.toList());
            
            int frameCount = selectedTimePoints.size();
            int totalWidth = horizontalMargin * 2 + frameCount * canvasWidth + (frameCount - 1) * spacing;
            
            // Draw title if provided
            if (title != null && !title.isEmpty()) {
                svgGenerator.setFont(title_font);
                svgGenerator.setColor(darkTheme ? DARK_THEME_COLOR : Color.BLACK);
                int titleWidth = svgGenerator.getFontMetrics().stringWidth(title);
                svgGenerator.drawString(title, (totalWidth - titleWidth) / 2, svgGenerator.getFontMetrics().getHeight());
            }
            
            // Find global bounds for shape normalization (if enabled)
            double globalMinX = Double.POSITIVE_INFINITY;
            double globalMaxX = Double.NEGATIVE_INFINITY;
            double globalMinY = Double.POSITIVE_INFINITY;
            double globalMaxY = Double.NEGATIVE_INFINITY;
            
            if (normalizeShapes) {
                for (int t : selectedTimePoints) {
                    if (martianObject.hasShape(t)) {
                        PeakShape shape = martianObject.getShape(t);
                        for (double x : shape.x) {
                            if (x < globalMinX) globalMinX = x;
                            if (x > globalMaxX) globalMaxX = x;
                        }
                        for (double y : shape.y) {
                            if (y < globalMinY) globalMinY = y;
                            if (y > globalMaxY) globalMaxY = y;
                        }
                    }
                }
            }
            
            // Draw each shape in the sequence
            for (int i = 0; i < selectedTimePoints.size(); i++) {
                int t = selectedTimePoints.get(i);
                int xPos = horizontalMargin + i * (canvasWidth + spacing);
                int yPos = verticalMargin;
                
                // Draw a shape for this time point
                drawShapeSVG(svgGenerator, t, xPos, yPos, globalMinX, globalMaxX, globalMinY, globalMaxY);
                
                // Draw time label
                if (showTimeLabels) {
                    svgGenerator.setFont(font);
                    svgGenerator.setColor(darkTheme ? DARK_THEME_COLOR : Color.BLACK);
                    String timeLabel = String.format("%." + xaxis_precision + "f", (double)t / timeUnitDivider);
                    int labelWidth = svgGenerator.getFontMetrics().stringWidth(timeLabel);
                    svgGenerator.drawString(timeLabel, xPos + (canvasWidth - labelWidth) / 2, 
                            yPos + canvasHeight + svgGenerator.getFontMetrics().getHeight() + 5);
                }
            }
            
            // Draw x-axis label if provided
            if (xAxisLabel != null && !xAxisLabel.isEmpty()) {
                svgGenerator.setFont(label_font);
                svgGenerator.setColor(darkTheme ? DARK_THEME_COLOR : Color.BLACK);
                int labelWidth = svgGenerator.getFontMetrics().stringWidth(xAxisLabel);
                svgGenerator.drawString(xAxisLabel, (totalWidth - labelWidth) / 2, 
                        image.getHeight() - verticalMargin / 3);
            }
            
            // Write to file
            try (Writer out = new OutputStreamWriter(new FileOutputStream(path), "UTF-8")) {
                svgGenerator.stream(out, true); // true means use CSS style attributes
            }
            
            logService.info("SVG saved to: " + path);
        } catch (Exception e) {
            logService.error("Failed to save SVG: " + e.getMessage());
            e.printStackTrace();
        }
        return this;
    }
    
    /**
     * Draw a shape for SVG output
     * 
     * @param svgGenerator The SVG graphics context
     * @param t Time point
     * @param xPos X position in the montage
     * @param yPos Y position in the montage
     * @param globalMinX Global minimum X for normalization
     * @param globalMaxX Global maximum X for normalization
     * @param globalMinY Global minimum Y for normalization
     * @param globalMaxY Global maximum Y for normalization
     */
    private void drawShapeSVG(SVGGraphics2D svgGenerator, int t, int xPos, int yPos,
            double globalMinX, double globalMaxX, double globalMinY, double globalMaxY) {
        
        if (!martianObject.hasShape(t)) {
            return;
        }
        
        PeakShape shape = martianObject.getShape(t);
        
        // Calculate local bounds for this shape
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        
        for (double x : shape.x) {
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
        }
        for (double y : shape.y) {
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        
        // Use local or global bounds based on normalization setting
        if (normalizeShapes) {
            minX = globalMinX;
            maxX = globalMaxX;
            minY = globalMinY;
            maxY = globalMaxY;
        }
        
        // Calculate scaling factors
        double scaleX = (canvasWidth - 20) / (maxX - minX);
        double scaleY = (canvasHeight - 20) / (maxY - minY);
        double scale = Math.min(scaleX, scaleY);
        
        // Calculate center offsets
        double centerX = (maxX + minX) / 2;
        double centerY = (maxY + minY) / 2;
        
        // Draw frame and axes if enabled
        if (showAxes) {
            svgGenerator.setColor(darkTheme ? DARK_THEME_COLOR : axisColor);
            svgGenerator.setStroke(new BasicStroke(axisLineWidth));
            svgGenerator.drawRect(xPos, yPos, canvasWidth, canvasHeight);
            
            // Draw coordinate axes through the center
            int centerXPos = xPos + canvasWidth / 2;
            int centerYPos = yPos + canvasHeight / 2;
            svgGenerator.drawLine(xPos + 10, centerYPos, xPos + canvasWidth - 10, centerYPos);
            svgGenerator.drawLine(centerXPos, yPos + 10, centerXPos, yPos + canvasHeight - 10);
        }
        
        // Create polygon for the shape
        int[] xPoints = new int[shape.x.length];
        int[] yPoints = new int[shape.y.length];
        
        for (int i = 0; i < shape.x.length; i++) {
            xPoints[i] = (int)(xPos + canvasWidth/2 + (shape.x[i] - centerX) * scale);
            yPoints[i] = (int)(yPos + canvasHeight/2 - (shape.y[i] - centerY) * scale);
        }
        
        // Fill shape if enabled
        if (fillShape) {
            svgGenerator.setColor(darkTheme ? DARK_THEME_FILL_COLOR : fillColor);
            svgGenerator.fillPolygon(xPoints, yPoints, shape.x.length);
        }
        
        // Draw shape outline
        svgGenerator.setColor(darkTheme ? DARK_THEME_COLOR : shapeColor);
        svgGenerator.setStroke(new BasicStroke(shapeLineWidth));
        svgGenerator.drawPolygon(xPoints, yPoints, shape.x.length);
    }
}