package com.taxes.rucker.panels;

import com.taxes.rucker.RucktaxesPlugin;
import com.taxes.rucker.websocket.dto.TradeOrder;
import com.taxes.rucker.websocket.dto.TradeOrder.OrderType;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

public class OrderListItemPanel extends JPanel {

    private Timer updateTimer;
    private final TradeOrder order;
    private final RucktaxesPlugin plugin;

    public OrderListItemPanel(RucktaxesPlugin plugin, ItemManager itemManager, TradeOrder order) {
        this.plugin = plugin;
        this.order = order;

        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(5, 5, 5, 5));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        addIcon(itemManager, c);
        addTopRow(c);
        addMiddleRow(c);
        addBottomRow(c);
    }

    private void addIcon(ItemManager itemManager, GridBagConstraints c) {
        BufferedImage itemImage = itemManager.getImage(order.getItemId(), order.getQuantity(), order.getQuantity() > 1);
        JLabel iconLabel = new JLabel(new ImageIcon(itemImage));
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 3;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        add(iconLabel, c);
        c.gridheight = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
    }

    private void addTopRow(GridBagConstraints c) {
        JLabel itemNameLabel = new JLabel(order.getItemName());
        itemNameLabel.setForeground(Color.WHITE);
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1.0;
        add(itemNameLabel, c);

        JLabel typeLabel = new JLabel(order.getType().toString());
        typeLabel.setForeground(order.getType() == OrderType.BUY ? Color.GREEN : Color.RED);
        typeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        c.gridx = 2;
        c.weightx = 0;
        add(typeLabel, c);
    }

    private void addMiddleRow(GridBagConstraints c) {
        String priceDetails = String.format("%s @ %s gp ea",
                QuantityFormatter.formatNumber(order.getQuantity()),
                QuantityFormatter.formatNumber(order.getPricePerItem()));
        JLabel priceDetailsLabel = new JLabel(priceDetails);
        priceDetailsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1.0;
        add(priceDetailsLabel, c);

        if (!order.isMyOrder()) {
            JPanel playerInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            playerInfoPanel.setBackground(null);
            playerInfoPanel.add(new OnlineIndicator(plugin.isPlayerOnline(order.getPlayerName())));
            JLabel playerLabel = new JLabel(order.getPlayerName());
            playerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            playerInfoPanel.add(playerLabel);

            c.gridx = 2;
            c.weightx = 0;
            add(playerInfoPanel, c);
        }
    }

    private void addBottomRow(GridBagConstraints c) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        buttonPanel.setBackground(null);

        if (order.isMyOrder()) {
            JButton modifyButton = new JButton("Modify");
            modifyButton.addActionListener(e -> plugin.handleModifyOrder(order));
            buttonPanel.add(modifyButton);

            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> {
                int result = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to cancel this order?",
                        "Confirm Cancellation", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    plugin.handleCancelOrder(order);
                }
            });
            buttonPanel.add(cancelButton);
        } else {
            JButton tradeButton = new JButton();
            updateTradeButtonState(tradeButton);
            tradeButton.addActionListener(e -> {
                plugin.handleInitiateTrade(order);
                updateTradeButtonState(tradeButton);
            });
            buttonPanel.add(tradeButton);
        }

        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 2;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.EAST;
        add(buttonPanel, c);
    }

    private void updateTradeButtonState(JButton button) {
        Instant cooldownEnd = plugin.getTradeButtonCooldownEnd(order.getOrderId());
        boolean onCooldown = cooldownEnd != null && Instant.now().isBefore(cooldownEnd);

        if (onCooldown) {
            long remainingSeconds = Duration.between(Instant.now(), cooldownEnd).getSeconds() + 1;
            button.setText(String.format("Wait (%ds)", remainingSeconds));
            button.setEnabled(false);

            if (updateTimer == null || !updateTimer.isRunning()) {
                updateTimer = new Timer(1000, e -> updateTradeButtonState(button));
                updateTimer.setRepeats(true);
                updateTimer.start();
            }
        } else {
            if (updateTimer != null) {
                updateTimer.stop();
            }
            button.setText(order.getType() == OrderType.SELL ? "Buy" : "Sell");
            button.setEnabled(true);
        }
    }

    private static class OnlineIndicator extends JPanel {
        OnlineIndicator(boolean isOnline) {
            setPreferredSize(new Dimension(8, 8));
            setBackground(isOnline ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_INPROGRESS_COLOR);
        }
    }
}