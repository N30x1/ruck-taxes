package com.taxes.rucker.overlays;

import com.taxes.rucker.RucktaxesPlugin;
import com.taxes.rucker.RucktaxesPlugin.TradeStatus;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class TradeVerificationOverlay extends Overlay {
    private static final int Y_OFFSET = 30;

    private final RucktaxesPlugin plugin;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    private TradeVerificationOverlay(RucktaxesPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        final TradeStatus status = plugin.getTradeStatus();
        if (plugin.getActiveTrade() == null || status == TradeStatus.PENDING) {
            return null;
        }

        final Rectangle tradeWindowBounds = plugin.getTradeWindowBounds();
        if (tradeWindowBounds == null) {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(tradeWindowBounds.width, 0));
        panelComponent.setPreferredLocation(new Point(tradeWindowBounds.x, tradeWindowBounds.y - Y_OFFSET));

        final String text;
        final Color color;

        switch (status) {
            case CORRECT:
                text = "Trade Correct";
                color = Color.GREEN;
                break;
            case INCORRECT_ITEMS:
                text = "Incorrect Items!";
                color = Color.RED;
                break;
            case INCORRECT_PRICE:
                text = "Incorrect Price!";
                color = Color.RED;
                break;
            default:
                return null;
        }

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(text)
                .color(color)
                .build());

        return panelComponent.render(graphics);
    }
}