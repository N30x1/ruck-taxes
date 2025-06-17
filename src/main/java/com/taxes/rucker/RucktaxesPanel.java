package com.taxes.rucker;

import com.taxes.rucker.panels.CollapsiblePanel;
import com.taxes.rucker.panels.FixedWidthPanel;
import com.taxes.rucker.panels.NotificationPanel;
import com.taxes.rucker.panels.OrderListItemPanel;
import com.taxes.rucker.websocket.dto.Notification;
import com.taxes.rucker.websocket.dto.TradeOrder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ImageUtil;

public class RucktaxesPanel extends PluginPanel {
    private static final ImageIcon SEARCH_ICON;
    private static final ImageIcon LIST_ICON;
    private static final ImageIcon NOTIFICATION_ICON;
    private static final ImageIcon IGNORE_ICON;

    static {
        SEARCH_ICON = createTabIcon("/search_icon.png");
        LIST_ICON = createTabIcon("/list_icon.png");
        NOTIFICATION_ICON = createTabIcon("/notification_icon.png");
        IGNORE_ICON = createTabIcon("/block_icon.png");
    }

    private final RucktaxesPlugin plugin;
    private final ItemManager itemManager;

    private final IconTextField searchBar = new IconTextField();
    private final JCheckBox buyFilter = new JCheckBox("Buy", true);
    private final JCheckBox sellFilter = new JCheckBox("Sell", true);
    private final JCheckBox onlineOnlyFilter = new JCheckBox("Online Only", true);
    private final JTextField minPriceField = createFilterTextField();
    private final JTextField maxPriceField = createFilterTextField();
    private final JTextField minQuantityField = createFilterTextField();
    private final JTextField maxQuantityField = createFilterTextField();
    private final JLabel errorLabel = new JLabel();

    private final JPanel searchResultsContainer = createListContainer();
    private final JPanel myOrdersContainer = createListContainer();
    private final JPanel notificationsContainer = createListContainer();
    private final JPanel ignoreListContainer = createListContainer();

    private final JRadioButton sentNotifsRadio = new JRadioButton("Sent");
    private final JRadioButton receivedNotifsRadio = new JRadioButton("Received");

    private final Border defaultTextFieldBorder = minPriceField.getBorder();

    public RucktaxesPanel(RucktaxesPlugin plugin, ItemManager itemManager) {
        super(false);
        this.plugin = plugin;
        this.itemManager = itemManager;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab(null, SEARCH_ICON, createSearchTab(), "Search for trade orders");
        tabbedPane.addTab(null, LIST_ICON, createMyOrdersTab(), "View your active orders");
        tabbedPane.addTab(null, NOTIFICATION_ICON, createNotificationsTab(), "View your trade notifications");
        tabbedPane.addTab(null, IGNORE_ICON, createIgnoreListTab(), "Manage your ignore list");

        add(tabbedPane, BorderLayout.CENTER);

        setMyOrders(new ArrayList<>());
        setNotifications(new ArrayList<>());
        updateIgnoreList(new ArrayList<>());
        showInitialSearchMessage();
    }

    private JPanel createSearchTab() {
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(createSearchControlsPanel(), BorderLayout.NORTH);
        searchPanel.add(createScrollPane(searchResultsContainer), BorderLayout.CENTER);
        return searchPanel;
    }

    private JPanel createSearchControlsPanel() {
        JPanel searchControls = new JPanel(new BorderLayout());
        searchControls.setBorder(new EmptyBorder(5, 5, 5, 5));
        searchControls.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PANEL_WIDTH, 25));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.addActionListener(e -> applyFilters());
        searchBar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                applyFilters();
            }
        });

        searchControls.add(searchBar, BorderLayout.NORTH);
        searchControls.add(createFilterContainer(), BorderLayout.CENTER);
        return searchControls;
    }

    private JPanel createFilterContainer() {
        JPanel filterContainer = new JPanel();
        filterContainer.setLayout(new BoxLayout(filterContainer, BoxLayout.Y_AXIS));
        filterContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        filterContainer.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel typeFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        typeFilterPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        configureCheckBox(buyFilter);
        configureCheckBox(sellFilter);
        configureCheckBox(onlineOnlyFilter);
        typeFilterPanel.add(buyFilter);
        typeFilterPanel.add(sellFilter);
        typeFilterPanel.add(onlineOnlyFilter);

        CollapsiblePanel filters = new CollapsiblePanel("Filters", createAdvancedFilterPanel(), false);
        JButton applyFiltersButton = new JButton("Apply Filters");
        applyFiltersButton.addActionListener(e -> applyFilters());

        errorLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
        errorLabel.setBorder(new EmptyBorder(4, 2, 0, 0));

        filterContainer.add(typeFilterPanel);
        filterContainer.add(Box.createRigidArea(new Dimension(0, 5)));
        filterContainer.add(filters);
        filterContainer.add(Box.createRigidArea(new Dimension(0, 8)));
        filterContainer.add(applyFiltersButton);
        filterContainer.add(errorLabel);
        return filterContainer;
    }

    private JPanel createAdvancedFilterPanel() {
        JPanel combinedFilterContent = new JPanel();
        combinedFilterContent.setLayout(new BoxLayout(combinedFilterContent, BoxLayout.Y_AXIS));
        combinedFilterContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel priceFilterContent = new JPanel(new GridLayout(2, 2, 3, 3));
        priceFilterContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        priceFilterContent.add(new JLabel("Min Price:"));
        priceFilterContent.add(minPriceField);
        priceFilterContent.add(new JLabel("Max Price:"));
        priceFilterContent.add(maxPriceField);

        JPanel quantityFilterContent = new JPanel(new GridLayout(2, 2, 3, 3));
        quantityFilterContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        quantityFilterContent.add(new JLabel("Min Quantity:"));
        quantityFilterContent.add(minQuantityField);
        quantityFilterContent.add(new JLabel("Max Quantity:"));
        quantityFilterContent.add(maxQuantityField);

        combinedFilterContent.add(priceFilterContent);
        combinedFilterContent.add(Box.createRigidArea(new Dimension(0, 5)));
        combinedFilterContent.add(quantityFilterContent);
        return combinedFilterContent;
    }

    private JPanel createMyOrdersTab() {
        JPanel myOrdersPanel = new JPanel(new BorderLayout());
        myOrdersPanel.add(createScrollPane(myOrdersContainer), BorderLayout.CENTER);
        return myOrdersPanel;
    }

    private JPanel createNotificationsTab() {
        JPanel notificationsPanel = new JPanel(new BorderLayout());
        notificationsPanel.add(createNotificationControls(), BorderLayout.NORTH);
        notificationsPanel.add(createScrollPane(notificationsContainer), BorderLayout.CENTER);
        return notificationsPanel;
    }

    private JPanel createNotificationControls() {
        JPanel notificationControls = new JPanel(new FlowLayout(FlowLayout.CENTER));
        notificationControls.setBorder(new EmptyBorder(5, 0, 25, 0));

        JRadioButton allNotifsRadio = new JRadioButton("All", true);
        ButtonGroup group = new ButtonGroup();
        group.add(allNotifsRadio);
        group.add(sentNotifsRadio);
        group.add(receivedNotifsRadio);
        notificationControls.add(allNotifsRadio);
        notificationControls.add(sentNotifsRadio);
        notificationControls.add(receivedNotifsRadio);

        allNotifsRadio.addActionListener(e -> setNotifications(plugin.getNotifications()));
        sentNotifsRadio.addActionListener(e -> setNotifications(plugin.getNotifications()));
        receivedNotifsRadio.addActionListener(e -> setNotifications(plugin.getNotifications()));

        JButton clearButton = getClearButton();
        notificationControls.add(Box.createRigidArea(new Dimension(10, 0)));
        notificationControls.add(clearButton);
        return notificationControls;
    }

    private JButton getClearButton() {
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear these notifications?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                Notification.NotificationType typeToClear = null;
                if (sentNotifsRadio.isSelected()) {
                    typeToClear = Notification.NotificationType.SENT;
                } else if (receivedNotifsRadio.isSelected()) {
                    typeToClear = Notification.NotificationType.RECEIVED;
                }
                plugin.handleClearNotifications(typeToClear);
            }
        });
        return clearButton;
    }

    private JPanel createIgnoreListTab() {
        JPanel ignoreListPanel = new JPanel(new BorderLayout());
        ignoreListPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        ignoreListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel ignoreControls = new JPanel(new BorderLayout(5, 0));
        ignoreControls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JTextField idInputField = new JTextField();
        idInputField.setToolTipText("Enter the anonymous ID of the user to ignore");
        JButton addButton = new JButton("Add");
        idInputField.addActionListener(e -> addIgnoredPlayer(idInputField));
        addButton.addActionListener(e -> addIgnoredPlayer(idInputField));
        ignoreControls.add(idInputField, BorderLayout.CENTER);
        ignoreControls.add(addButton, BorderLayout.EAST);

        ignoreListPanel.add(ignoreControls, BorderLayout.NORTH);
        ignoreListContainer.setBorder(new EmptyBorder(8, 0, 0, 0));
        ignoreListContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        ignoreListPanel.add(createScrollPane(ignoreListContainer), BorderLayout.CENTER);

        return ignoreListPanel;
    }

    private void addIgnoredPlayer(JTextField idInputField) {
        String id = idInputField.getText();
        if (id != null && !id.trim().isEmpty()) {
            plugin.handleAddToIgnoreList(id.trim());
            idInputField.setText("");
        }
    }

    private void applyFilters() {
        resetFilterErrors();
        String searchText = searchBar.getText().toLowerCase().trim();
        if (searchText.isEmpty()) {
            showInitialSearchMessage();
            return;
        }

        Optional<Long> minPrice = parseLong(minPriceField);
        Optional<Long> maxPrice = parseLong(maxPriceField);
        Optional<Integer> minQuantity = parseInteger(minQuantityField);
        Optional<Integer> maxQuantity = parseInteger(maxQuantityField);

        if (minPrice.isEmpty() && !minPriceField.getText().trim().isEmpty() ||
                maxPrice.isEmpty() && !maxPriceField.getText().trim().isEmpty() ||
                minQuantity.isEmpty() && !minQuantityField.getText().trim().isEmpty() ||
                maxQuantity.isEmpty() && !maxQuantityField.getText().trim().isEmpty()) {
            return;
        }

        List<TradeOrder> allOrders = plugin.getAllOrders();
        if (allOrders == null) {
            updateSearchResults(new ArrayList<>());
            return;
        }

        List<TradeOrder> filteredOrders = allOrders.stream()
                .filter(order -> !order.isMyOrder())
                .filter(order -> order.getItemName() != null && order.getItemName().toLowerCase().contains(searchText))
                .filter(order -> (buyFilter.isSelected() && order.getType() == TradeOrder.OrderType.BUY) ||
                        (sellFilter.isSelected() && order.getType() == TradeOrder.OrderType.SELL))
                .filter(order -> minPrice.map(p -> order.getPrice() >= p).orElse(true))
                .filter(order -> maxPrice.map(p -> order.getPrice() <= p).orElse(true))
                .filter(order -> minQuantity.map(q -> order.getQuantity() >= q).orElse(true))
                .filter(order -> maxQuantity.map(q -> order.getQuantity() <= q).orElse(true))
                .filter(order -> !onlineOnlyFilter.isSelected() || plugin.isPlayerOnline(order.getPlayerName()))
                .collect(Collectors.toList());

        updateSearchResults(filteredOrders);
    }

    private void resetFilterErrors() {
        errorLabel.setText("");
        minPriceField.setBorder(defaultTextFieldBorder);
        maxPriceField.setBorder(defaultTextFieldBorder);
        minQuantityField.setBorder(defaultTextFieldBorder);
        maxQuantityField.setBorder(defaultTextFieldBorder);
    }

    private Optional<Long> parseLong(JTextField field) {
        String text = field.getText().trim();
        if (text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException e) {
            field.setBorder(BorderFactory.createLineBorder(ColorScheme.PROGRESS_ERROR_COLOR));
            errorLabel.setText("Invalid number format.");
            return Optional.empty();
        }
    }

    private Optional<Integer> parseInteger(JTextField field) {
        String text = field.getText().trim();
        if (text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            field.setBorder(BorderFactory.createLineBorder(ColorScheme.PROGRESS_ERROR_COLOR));
            errorLabel.setText("Invalid number format.");
            return Optional.empty();
        }
    }

    public void setMyOrders(List<TradeOrder> orders) {
        List<Component> panels = orders.stream()
                .map(order -> new OrderListItemPanel(plugin, itemManager, order))
                .collect(Collectors.toList());
        updateListPanel(myOrdersContainer, panels, "No Active Orders", "You do not have any active orders.");
    }

    public void onOrderListUpdated() {
        if (searchBar.isFocusOwner() || !searchBar.getText().trim().isEmpty()) {
            applyFilters();
        }
        myOrdersContainer.revalidate();
        myOrdersContainer.repaint();
    }

    private void updateSearchResults(List<TradeOrder> results) {
        List<Component> panels = results.stream()
                .map(order -> new OrderListItemPanel(plugin, itemManager, order))
                .collect(Collectors.toList());
        updateListPanel(searchResultsContainer, panels, "No Results Found", "Your search returned no matching orders.");
    }

    private void showInitialSearchMessage() {
        updateListPanel(searchResultsContainer, Collections.emptyList(), "Live Order Search", "Enter an item name to begin searching.");
    }

    public void setNotifications(List<Notification> notifications) {
        List<Notification> filteredNotifs = notifications.stream()
                .filter(n -> {
                    if (sentNotifsRadio.isSelected()) return n.getType() == Notification.NotificationType.SENT;
                    if (receivedNotifsRadio.isSelected()) return n.getType() == Notification.NotificationType.RECEIVED;
                    return true;
                })
                .collect(Collectors.toList());
        Collections.reverse(filteredNotifs);

        List<Component> panels = filteredNotifs.stream()
                .map(notification -> new NotificationPanel(notification, plugin))
                .collect(Collectors.toList());
        updateListPanel(notificationsContainer, panels, "No Notifications", "No notifications match the current filter.");
    }

    public void updateNotificationPlayerName(String notificationId, String realName) {
        for (Component comp : notificationsContainer.getComponents()) {
            if (comp instanceof NotificationPanel) {
                NotificationPanel panel = (NotificationPanel) comp;
                if (panel.getNotificationId().equals(notificationId)) {
                    panel.updatePlayerName(realName);
                    break;
                }
            }
        }
    }

    public void updateIgnoreList(List<String> ignoredIds) {
        List<String> sortedIds = new ArrayList<>(ignoredIds);
        sortedIds.sort(String.CASE_INSENSITIVE_ORDER);

        List<Component> panels = sortedIds.stream()
                .map(this::createIgnoreListEntry)
                .collect(Collectors.toList());
        updateListPanel(ignoreListContainer, panels, "Ignore List is Empty", "You are not currently ignoring any players.");
    }

    private JPanel createIgnoreListEntry(String id) {
        JPanel entryPanel = new JPanel(new BorderLayout());
        entryPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        entryPanel.setBorder(new EmptyBorder(2, 5, 2, 5));

        JLabel nameLabel = new JLabel(id);
        nameLabel.setForeground(Color.WHITE);

        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> plugin.handleRemoveFromIgnoreList(id));

        entryPanel.add(nameLabel, BorderLayout.CENTER);
        entryPanel.add(removeButton, BorderLayout.EAST);
        return entryPanel;
    }

    private void updateListPanel(JPanel container, List<Component> components, String emptyTitle, String emptyBody) {
        container.removeAll();
        if (components.isEmpty()) {
            PluginErrorPanel errorPanel = new PluginErrorPanel();
            errorPanel.setContent(emptyTitle, emptyBody);
            container.add(errorPanel);
        } else {
            for (int i = 0; i < components.size(); i++) {
                container.add(components.get(i));
                if (i < components.size() - 1) {
                    container.add(Box.createRigidArea(new Dimension(0, 5)));
                }
            }
        }
        container.revalidate();
        container.repaint();
    }

    private static JPanel createListContainer() {
        JPanel panel = new FixedWidthPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(0, 5, 0, 5));
        return panel;
    }

    private static JScrollPane createScrollPane(Component view) {
        JScrollPane scrollPane = new JScrollPane(view);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scrollPane;
    }

    private static JTextField createFilterTextField() {
        JTextField textField = new JTextField(10);
        textField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        textField.setForeground(Color.WHITE);
        return textField;
    }

    private void configureCheckBox(JCheckBox checkBox) {
        checkBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        checkBox.addActionListener(e -> applyFilters());
    }

    private static ImageIcon createTabIcon(String resourcePath) {
        final BufferedImage image = ImageUtil.loadImageResource(RucktaxesPanel.class, resourcePath);
        return new ImageIcon(ImageUtil.resizeImage(image, 16, 16));
    }
}