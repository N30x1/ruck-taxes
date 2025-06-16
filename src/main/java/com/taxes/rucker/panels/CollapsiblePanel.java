package com.taxes.rucker.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

public class CollapsiblePanel extends JPanel {

    private static final String EXPAND_ICON = "▼ ";
    private static final String COLLAPSE_ICON = "▶ ";

    private final JButton titleButton;
    private final JPanel contentPanel;
    private final String title;

    public CollapsiblePanel(String title, JPanel contentPanel, boolean startExpanded) {
        this.title = title;
        this.contentPanel = contentPanel;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        this.titleButton = new JButton();
        titleButton.setText((startExpanded ? EXPAND_ICON : COLLAPSE_ICON) + title);
        titleButton.setForeground(Color.WHITE);
        titleButton.setHorizontalAlignment(SwingConstants.LEFT);
        titleButton.setBorder(new EmptyBorder(2, 2, 2, 2));
        titleButton.setFocusPainted(false);
        titleButton.setBorderPainted(false);
        titleButton.setContentAreaFilled(false);
        titleButton.setOpaque(false);
        titleButton.addActionListener(e -> toggleVisibility());

        this.contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        this.contentPanel.setVisible(startExpanded);

        add(titleButton, BorderLayout.NORTH);
        add(this.contentPanel, BorderLayout.CENTER);
    }

    public void setExpanded(boolean expanded) {
        if (contentPanel.isVisible() == expanded) {
            return;
        }

        contentPanel.setVisible(expanded);
        titleButton.setText((expanded ? EXPAND_ICON : COLLAPSE_ICON) + title);

        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }

    public void toggleVisibility() {
        setExpanded(!contentPanel.isVisible());
    }
}