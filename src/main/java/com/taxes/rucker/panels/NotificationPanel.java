package com.taxes.rucker.panels;

import com.taxes.rucker.RucktaxesPlugin;
import com.taxes.rucker.websocket.dto.Notification;
import com.taxes.rucker.websocket.dto.Notification.NotificationStatus;
import com.taxes.rucker.websocket.dto.Notification.NotificationType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

public class NotificationPanel extends JPanel {
    private final Notification notification;
    private final RucktaxesPlugin plugin;
    private JLabel nameLabel;

    public NotificationPanel(Notification notification, RucktaxesPlugin plugin) {
        this.notification = notification;
        this.plugin = plugin;

        setLayout(new BorderLayout(0, 3));
        setBorder(new EmptyBorder(5, 5, 5, 5));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        add(createTopPanel(), BorderLayout.NORTH);
        add(createMessageArea(), BorderLayout.CENTER);
        add(createSouthPanel(), BorderLayout.SOUTH);
    }

    public String getNotificationId() {
        return notification.getNotificationId();
    }

    public void updatePlayerName(String realName) {
        if (nameLabel != null) {
            nameLabel.setText(realName);
            nameLabel.setToolTipText("Original ID: " + getOtherPlayerId());
        }
    }

    private String getOtherPlayerId() {
        return notification.getType() == NotificationType.SENT
                ? notification.getToPlayerId()
                : notification.getFromPlayerId();
    }

    private String getOtherPlayerInitialName() {
        return notification.getType() == NotificationType.SENT
                ? notification.getToPlayerId()
                : notification.getFromPlayerRsn();
    }

    private JPanel createTopPanel() {
        JPanel topLine = new JPanel(new BorderLayout());
        topLine.setBackground(null);

        nameLabel = new JLabel(getOtherPlayerInitialName());
        nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JLabel timeLabel = new JLabel(formatTimeAgo(notification.getTimestamp()));
        timeLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        topLine.add(nameLabel, BorderLayout.WEST);
        topLine.add(timeLabel, BorderLayout.EAST);
        return topLine;
    }

    private JTextArea createMessageArea() {
        JTextArea messageArea = new JTextArea(notification.getMessage());
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setOpaque(false);
        messageArea.setEditable(false);
        messageArea.setForeground(Color.WHITE);
        return messageArea;
    }

    private JPanel createSouthPanel() {
        JPanel southContainer = new JPanel(new BorderLayout());
        southContainer.setBackground(null);

        JPanel buttonPanel = createButtonPanel();
        if (buttonPanel.getComponentCount() > 0) {
            buttonPanel.setBorder(new EmptyBorder(0, 0, 4, 0));
            southContainer.add(buttonPanel, BorderLayout.NORTH);
        }

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusPanel.setBackground(null);
        JLabel statusLabel = new JLabel("Status: " + notification.getStatus().toString());
        statusLabel.setForeground(getStatusColor(notification.getStatus()));
        statusPanel.add(statusLabel);

        southContainer.add(statusPanel, BorderLayout.SOUTH);
        return southContainer;
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setBackground(null);

        if (notification.getType() == NotificationType.RECEIVED && notification.getStatus() == NotificationStatus.PENDING) {
            buttonPanel.add(createButton("Accept", plugin::handleAcceptTrade));
            buttonPanel.add(createButton("Ignore", n -> plugin.handleAddToIgnoreList(n.getFromPlayerId())));
        } else if (notification.getStatus() == NotificationStatus.ACCEPTED) {
            buttonPanel.add(createButton("Cancel", plugin::handleCancelAcceptedTrade));
            JButton forceButton = createButton("Force", plugin::handleForceCompleteTrade);
            forceButton.setToolTipText("Manually mark this trade as completed if the automatic detection failed.");
            buttonPanel.add(forceButton);
        }
        return buttonPanel;
    }

    private JButton createButton(String text, Consumer<Notification> action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.accept(notification));
        return button;
    }

    private static Color getStatusColor(NotificationStatus status) {
        switch (status) {
            case ACCEPTED:
                return ColorScheme.PROGRESS_INPROGRESS_COLOR;
            case COMPLETED:
                return ColorScheme.PROGRESS_COMPLETE_COLOR;
            case CANCELLED:
                return ColorScheme.PROGRESS_ERROR_COLOR;
            case PENDING:
            default:
                return Color.WHITE;
        }
    }

    private static String formatTimeAgo(Instant timestamp) {
        if (timestamp == null) {
            return "just now";
        }
        long seconds = Duration.between(timestamp, Instant.now()).getSeconds();
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        return hours + "h ago";
    }
}