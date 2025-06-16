package com.taxes.rucker.overlays;

import com.taxes.rucker.RucktaxesConfig;
import com.taxes.rucker.RucktaxesManager;
import com.taxes.rucker.RucktaxesManager.FocusedField;
import com.taxes.rucker.websocket.dto.TradeOrder.OrderType;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.ColorUtil;

public class OrderCreationOverlay extends Overlay {
    private static final Color HIGHLIGHT_COLOR = new Color(0xFF, 0x98, 0x1F);
    private static final Color SELECTED_TYPE_COLOR = new Color(0x65, 0xE5, 0xA4);
    private static final String CURSOR = "_";

    private final RucktaxesManager manager;
    private final RucktaxesConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    private OrderCreationOverlay(RucktaxesManager manager, RucktaxesConfig config) {
        this.manager = manager;
        this.config = config;
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!manager.isOverlayActive() || manager.getItem() == null) {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(220, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Create/Modify Order")
                .color(Color.YELLOW)
                .build());

        panelComponent.getChildren().add(LineComponent.builder().build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Item:")
                .right(manager.getItem().getName())
                .build());

        addTypeLine();
        addInputLine("Quantity:", manager.getQuantityInput(), FocusedField.QUANTITY);
        addInputLine("Price (ea):", manager.getPriceInput(), FocusedField.PRICE);

        if (config.showGuide()) {
            addGuideLines();
        }

        return panelComponent.render(graphics);
    }

    private void addTypeLine() {
        final boolean isSell = manager.getOrderType() == OrderType.SELL;
        final String sellText = ColorUtil.prependColorTag("Sell", isSell ? SELECTED_TYPE_COLOR : Color.WHITE);
        final String buyText = ColorUtil.prependColorTag("Buy", !isSell ? SELECTED_TYPE_COLOR : Color.WHITE);

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Type:")
                .right(sellText + " / " + buyText)
                .build());
    }

    private void addInputLine(String label, String value, FocusedField field) {
        final boolean isFocused = manager.getFocusedField() == field;
        final String text = value + (isFocused ? CURSOR : "");
        final Color color = isFocused ? HIGHLIGHT_COLOR : Color.WHITE;

        panelComponent.getChildren().add(LineComponent.builder()
                .left(label)
                .right(text)
                .rightColor(color)
                .build());
    }

    private void addGuideLines() {
        panelComponent.getChildren().add(LineComponent.builder().build());
        panelComponent.getChildren().add(LineComponent.builder().left("Tab: Switch Field").build());
        panelComponent.getChildren().add(LineComponent.builder().left("Up/Down: Switch Type").build());
        panelComponent.getChildren().add(LineComponent.builder().left("Enter: Confirm").build());
        panelComponent.getChildren().add(LineComponent.builder().left("Esc: Cancel").build());
    }
}