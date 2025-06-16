package com.taxes.rucker.overlays;

import com.taxes.rucker.RucktaxesPlugin;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

public class BankHighlightOverlay extends WidgetItemOverlay {
    private final RucktaxesPlugin plugin;

    private static final Color HIGHLIGHT_COLOR = new Color(0, 255, 100, 50);
    private static final Color BORDER_COLOR = Color.GREEN;
    private static final Stroke BORDER_STROKE = new BasicStroke(1);

    @Inject
    private BankHighlightOverlay(RucktaxesPlugin plugin) {
        this.plugin = plugin;
        showOnBank();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
        if (plugin.getListedItemIds().contains(itemId)) {
            final Rectangle bounds = widgetItem.getCanvasBounds();
            if (bounds == null) {
                return;
            }

            graphics.setColor(HIGHLIGHT_COLOR);
            graphics.fill(bounds);

            graphics.setColor(BORDER_COLOR);
            graphics.setStroke(BORDER_STROKE);
            graphics.draw(bounds);
        }
    }
}