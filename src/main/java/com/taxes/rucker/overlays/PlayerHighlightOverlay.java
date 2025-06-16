package com.taxes.rucker.overlays;

import com.taxes.rucker.RucktaxesPlugin;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class PlayerHighlightOverlay extends Overlay {
    private static final Color HIGHLIGHT_COLOR = new Color(0, 255, 255, 180); // Cyan

    private final Client client;
    private final RucktaxesPlugin plugin;

    @Inject
    private PlayerHighlightOverlay(Client client, RucktaxesPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        final WorldPoint otherPlayerLocation = plugin.getOtherPlayerLocation();
        if (otherPlayerLocation == null) {
            return null;
        }

        if (client.getWorldView(-1).getPlane() != otherPlayerLocation.getPlane()) {
            return null;
        }

        LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), otherPlayerLocation);
        if (localPoint == null) {
            return null;
        }

        Polygon tilePolygon = Perspective.getCanvasTilePoly(client, localPoint);
        if (tilePolygon == null) {
            return null;
        }

        OverlayUtil.renderPolygon(graphics, tilePolygon, HIGHLIGHT_COLOR);
        return null;
    }
}