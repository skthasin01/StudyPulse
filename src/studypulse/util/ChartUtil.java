package studypulse.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.renderer.AreaRendererEndType;
import org.jfree.chart.renderer.category.AreaRenderer;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

/**
 * JFreeChart helpers with the StudyPulse look: white background, light grid lines,
 * Segoe UI fonts and UITheme colours. An empty dataset shows {@link #NO_DATA}.
 */
public final class ChartUtil {

    public static final String NO_DATA = "Not enough data yet – add tasks and check-ins";
    public static final int EXPORT_WIDTH = 900;
    public static final int EXPORT_HEIGHT = 600;
    private static final Font TITLE_FONT = new Font(UITheme.FONT_NAME, Font.BOLD, 14);
    private static final Font TICK_FONT = new Font(UITheme.FONT_NAME, Font.PLAIN, 11);

    private ChartUtil() {
    }

    /** Vertical bar chart with one colour. */
    public static JFreeChart barChart(String title, String valueLabel, DefaultCategoryDataset dataset, Color color) {
        JFreeChart chart = ChartFactory.createBarChart(title, null, valueLabel, dataset);
        styleCategoryChart(chart, dataset);
        BarRenderer renderer = (BarRenderer) chart.getCategoryPlot().getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setSeriesPaint(0, color);
        renderer.setMaximumBarWidth(0.06);
        renderer.setItemMargin(0.0);
        chart.removeLegend();
        return chart;
    }

    /** Line chart; series i gets colors[i]. */
    public static JFreeChart lineChart(String title, String valueLabel, DefaultCategoryDataset dataset,
            Color... colors) {
        JFreeChart chart = ChartFactory.createLineChart(title, null, valueLabel, dataset);
        styleCategoryChart(chart, dataset);
        LineAndShapeRenderer renderer = (LineAndShapeRenderer) chart.getCategoryPlot().getRenderer();
        for (int i = 0; i < colors.length; i++) {
            renderer.setSeriesPaint(i, colors[i]);
            renderer.setSeriesStroke(i, new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            renderer.setSeriesShape(i, new Ellipse2D.Double(-3, -3, 6, 6));
        }
        renderer.setDefaultShapesVisible(true);
        return chart;
    }

    /** Pie chart; 'colors' maps section keys to colours. Zero sections are left out. */
    public static JFreeChart pieChart(String title, DefaultPieDataset<String> dataset, Map<String, Color> colors) {
        JFreeChart chart = ChartFactory.createPieChart(title, dataset, true, true, false);
        styleChart(chart);
        @SuppressWarnings("unchecked")
        PiePlot<String> plot = (PiePlot<String>) chart.getPlot();
        for (Map.Entry<String, Color> e : colors.entrySet()) {
            plot.setSectionPaint(e.getKey(), e.getValue());
        }
        plot.setBackgroundPaint(UITheme.SURFACE);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setIgnoreZeroValues(true);
        plot.setLabelFont(TICK_FONT);
        plot.setLabelPaint(UITheme.TEXT);
        plot.setLabelBackgroundPaint(UITheme.SURFACE);
        plot.setLabelOutlinePaint(UITheme.GRID);
        plot.setLabelShadowPaint(null);
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}: {1}"));
        plot.setDefaultSectionOutlinePaint(UITheme.SURFACE);
        plot.setDefaultSectionOutlineStroke(new BasicStroke(2f));
        noData(plot);
        return chart;
    }

    /**
     * Burnout risk trend (0-100): line with a light area fill below it;
     * only the latest point is marked and labelled.
     */
    public static JFreeChart riskTrendChart(String title, final DefaultCategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createLineChart(title, null, "Risk score", dataset);
        styleCategoryChart(chart, dataset);
        chart.removeLegend();
        CategoryPlot plot = chart.getCategoryPlot();
        ((NumberAxis) plot.getRangeAxis()).setRange(0, 100);

        LineAndShapeRenderer line = new LineAndShapeRenderer(true, true) {
            @Override
            public boolean getItemShapeVisible(int row, int column) {
                return column == dataset.getColumnCount() - 1;
            }

            @Override
            public boolean isItemLabelVisible(int row, int column) {
                return column == dataset.getColumnCount() - 1;
            }
        };
        line.setSeriesPaint(0, UITheme.ACCENT);
        line.setSeriesStroke(0, new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        line.setSeriesShape(0, new Ellipse2D.Double(-6, -6, 12, 12));
        line.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        line.setDefaultItemLabelFont(UITheme.FONT_BOLD);
        line.setDefaultItemLabelPaint(UITheme.TEXT);
        plot.setRenderer(0, line);

        // no gaps between categories and flat ends, so the fill is one continuous area
        plot.getDomainAxis().setCategoryMargin(0.0);
        AreaRenderer area = new AreaRenderer();
        area.setEndType(AreaRendererEndType.LEVEL);
        area.setSeriesPaint(0, new Color(UITheme.ACCENT.getRed(), UITheme.ACCENT.getGreen(),
                UITheme.ACCENT.getBlue(), UITheme.isDark() ? 90 : 45));
        plot.setDataset(1, dataset);
        plot.setRenderer(1, area);   // index 1 is drawn first, so the line stays on top
        return chart;
    }

    /** ChartPanel without zoom / popup, for dashboards. */
    public static ChartPanel panel(JFreeChart chart) {
        ChartPanel panel = new ChartPanel(chart);
        panel.setPopupMenu(null);
        panel.setDomainZoomable(false);
        panel.setRangeZoomable(false);
        panel.setMouseWheelEnabled(false);
        panel.setBackground(UITheme.SURFACE);
        panel.setPreferredSize(new Dimension(400, 250));
        panel.setMinimumDrawWidth(200);
        panel.setMinimumDrawHeight(150);
        panel.setMaximumDrawWidth(2400);
        panel.setMaximumDrawHeight(1600);
        return panel;
    }

    /** Saves the chart as a 900x600 PNG. */
    public static void savePng(JFreeChart chart, File file) throws IOException {
        ChartUtils.saveChartAsPNG(file, chart, EXPORT_WIDTH, EXPORT_HEIGHT);
    }

    private static void styleCategoryChart(JFreeChart chart, DefaultCategoryDataset dataset) {
        styleChart(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(UITheme.SURFACE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(UITheme.GRID);
        plot.setRangeGridlineStroke(new BasicStroke(1f));
        plot.setDomainGridlinesVisible(false);
        plot.setAxisOffset(RectangleInsets.ZERO_INSETS);
        CategoryAxis domain = plot.getDomainAxis();
        domain.setTickLabelFont(TICK_FONT);
        domain.setTickLabelPaint(UITheme.TEXT_MUTED);
        domain.setAxisLinePaint(UITheme.GRID);
        domain.setTickMarksVisible(false);
        domain.setLowerMargin(0.02);
        domain.setUpperMargin(0.02);
        if (dataset.getColumnCount() > 10) {
            domain.setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        }
        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setTickLabelFont(TICK_FONT);
        range.setTickLabelPaint(UITheme.TEXT_MUTED);
        range.setLabelFont(TICK_FONT);
        range.setLabelPaint(UITheme.TEXT_MUTED);
        range.setAxisLineVisible(false);
        range.setTickMarksVisible(false);
        range.setAutoRangeIncludesZero(true);
        range.setAutoRangeMinimumSize(1.0);   // all-zero data gives 0..1, not "4E-9" ticks
        noData(plot);
    }

    private static void styleChart(JFreeChart chart) {
        chart.setBackgroundPaint(UITheme.SURFACE);
        chart.setBorderVisible(false);
        chart.setPadding(new RectangleInsets(8, 8, 8, 8));
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(TITLE_FONT);
            chart.getTitle().setPaint(UITheme.TEXT);
        }
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemFont(TICK_FONT);
            legend.setFrame(org.jfree.chart.block.BlockBorder.NONE);
            legend.setBackgroundPaint(UITheme.SURFACE);
            legend.setItemPaint(UITheme.TEXT);
        }
    }

    private static void noData(org.jfree.chart.plot.Plot plot) {
        plot.setNoDataMessage(NO_DATA);
        plot.setNoDataMessageFont(UITheme.FONT_NORMAL);
        plot.setNoDataMessagePaint(UITheme.TEXT_MUTED);
    }
}
