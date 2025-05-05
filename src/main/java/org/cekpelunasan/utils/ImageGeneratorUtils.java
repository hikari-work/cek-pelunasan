package org.cekpelunasan.utils;

import org.cekpelunasan.entity.Repayment;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

@Component
public class ImageGeneratorUtils {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 600;
    private static final int LINE_HEIGHT = 40;
    private static final int MARGIN = 60;
    private static final int COLON_X = 400;

    private final RupiahFormatUtils rupiahFormatUtils;
    private final PenaltyUtils penaltyUtils;

    public ImageGeneratorUtils(RupiahFormatUtils rupiahFormatUtils, PenaltyUtils penaltyUtils) {
        this.rupiahFormatUtils = rupiahFormatUtils;
        this.penaltyUtils = penaltyUtils;
    }

    public InputFile generateImages(Repayment repayment) {
        try {
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();

            setRenderingHints(g);
            drawBackground(g);
            drawTitle(g, "Rincian Pelunasan");

            g.setFont(new Font("Arial", Font.PLAIN, 22));
            int y = 160;

            drawDetail(g, "Nama", repayment.getName(), y);
            drawDetail(g, "No. SPK", repayment.getCustomerId(), y += LINE_HEIGHT);
            drawDetail(g, "Produk", repayment.getProduct(), y += LINE_HEIGHT);
            drawDetail(g, "Plafond", rupiahFormatUtils.formatRupiah(repayment.getPlafond()), y += LINE_HEIGHT);
            drawDetail(g, "Bakidebet", rupiahFormatUtils.formatRupiah(repayment.getAmount()), y += LINE_HEIGHT);
            drawDetail(g, "Tunggakan bunga", rupiahFormatUtils.formatRupiah(repayment.getInterest()), y += LINE_HEIGHT);

            Map<String, Long> penalty = getPenalty(repayment);
            drawDetail(g, "Penalty + " + penalty.get("multiplier") + "x", rupiahFormatUtils.formatRupiah(penalty.get("penalty")), y += LINE_HEIGHT);

            drawDetail(g, "Denda", rupiahFormatUtils.formatRupiah(repayment.getPenaltyRepayment()), y += LINE_HEIGHT);
            drawDetail(g, "Total Pelunasan", rupiahFormatUtils.formatRupiah(calculateTotalRepayment(repayment)), y += LINE_HEIGHT);

            drawFooterLine(g, y + 30);
            g.dispose();

            return createInputFile(image);
        } catch (Exception e) {
            throw new RuntimeException("Error generating image: " + e.getMessage(), e);
        }
    }

    private void setRenderingHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private void drawBackground(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private void drawTitle(Graphics2D g, String title) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 36));
        FontMetrics fm = g.getFontMetrics();
        int x = (WIDTH - fm.stringWidth(title)) / 2;
        g.drawString(title, x, 100);
    }

    private void drawDetail(Graphics2D g, String label, String value, int y) {
        FontMetrics metrics = g.getFontMetrics();
        int labelX = MARGIN;
        int valueX = WIDTH - MARGIN - metrics.stringWidth(value);

        g.setColor(Color.BLACK);
        g.drawString(label, labelX, y);
        g.drawString(":", COLON_X, y);
        g.drawString(value, valueX, y);
    }

    private void drawFooterLine(Graphics2D g, int y) {
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(MARGIN, y, WIDTH - MARGIN, y);
    }

    private long calculateTotalRepayment(Repayment repayment) {
        Map<String, Long> penalty = getPenalty(repayment);
        return repayment.getAmount() + repayment.getInterest() + repayment.getPenaltyRepayment() + penalty.get("penalty");
    }

    private InputFile createInputFile(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new InputFile(new ByteArrayInputStream(out.toByteArray()), "rincian-pelunasan.png");
    }

    private Map<String, Long> getPenalty(Repayment repayment) {
        return penaltyUtils.penalty(repayment.getStartDate(), repayment.getPenaltyLoan(), repayment.getProduct(), repayment);
    }
}
