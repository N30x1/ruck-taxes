package com.taxes.rucker;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import com.taxes.rucker.overlays.BankHighlightOverlay;
import com.taxes.rucker.overlays.OrderCreationOverlay;
import com.taxes.rucker.overlays.PlayerHighlightOverlay;
import com.taxes.rucker.overlays.TradeVerificationOverlay;
import com.taxes.rucker.websocket.RucktaxesWebsocketClient;
import com.taxes.rucker.websocket.dto.Notification;
import com.taxes.rucker.websocket.dto.TradeOrder;
import com.taxes.rucker.websocket.dto.WebsocketMessage;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(name = "rucktaxes")
public class RucktaxesPlugin extends Plugin implements KeyListener {

	public enum TradeStatus { PENDING, CORRECT, INCORRECT_ITEMS, INCORRECT_PRICE }

	private static final String MENU_ACTION_TEXT = "Create Order";
	private static final int TRADEMAIN_GROUP_ID = 335;
	private static final int TRADEMAIN_MY_OFFER_LIST_CHILD_ID = 25;
	private static final int TRADEMAIN_THEIR_OFFER_LIST_CHILD_ID = 28;
	private static final int TRADECONFIRM_GROUP_ID = 334;
	private static final long NOTIFICATION_COOLDOWN_SECONDS = 60;
	private static final int TRADE_BUTTON_COOLDOWN_SECONDS = 5;

	@Inject @Getter private Client client;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ItemManager itemManager;
	@Inject private ClientThread clientThread;
	@Inject private KeyManager keyManager;
	@Inject private OverlayManager overlayManager;
	@Inject private Gson gson;
	@Inject private RucktaxesConfig config;
	@Inject private RucktaxesWebsocketClient websocketClient;
	@Inject private RucktaxesManager orderCreationManager;
	@Inject private OrderCreationOverlay orderCreationOverlay;
	@Inject private BankHighlightOverlay bankHighlightOverlay;
	@Inject private TradeVerificationOverlay tradeVerificationOverlay;
	@Inject private PlayerHighlightOverlay playerHighlightOverlay;
	@Inject private WorldMapPointManager worldMapPointManager;
	@Inject private ScheduledExecutorService executor;

	@Getter private Notification activeTrade;
	@Getter private TradeStatus tradeStatus = TradeStatus.PENDING;
	@Getter private final List<TradeOrder> myOrdersList = new ArrayList<>();
	@Getter private final Set<Integer> listedItemIds = new HashSet<>();
	@Getter private WorldPoint otherPlayerLocation;

	private final List<TradeOrder> allOrdersList = new ArrayList<>();
	private final List<Notification> notificationList = new ArrayList<>();
	private final Set<String> serverIgnoredList = new HashSet<>();
	private final Map<String, Instant> notificationCooldowns = new ConcurrentHashMap<>();
	private final Map<String, Instant> tradeButtonCooldowns = new ConcurrentHashMap<>();
	private final Set<String> onlineUsers = new HashSet<>();
	private String otherPlayerRsn;
	private WorldMapPoint worldMapPoint;

	private RucktaxesPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> reconnectFuture;
	private ScheduledFuture<?> locationUpdateFuture;
	private boolean isSubscribedToWebsocket = false;
	private boolean hasTradeWindowBeenSeen = false;

	@Provides
	RucktaxesConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(RucktaxesConfig.class);
	}

	@Provides
	@Singleton
	RucktaxesWebsocketClient provideWebsocketClient(OkHttpClient okHttpClient, Gson gson) {
		return new RucktaxesWebsocketClient(this, okHttpClient, gson);
	}

	@Provides
	@Singleton
	RucktaxesManager provideOrderCreationManager() {
		return new RucktaxesManager();
	}

	public List<TradeOrder> getAllOrders() {
		return allOrdersList;
	}

	public List<Notification> getNotifications() {
		return notificationList;
	}

	@Override
	protected void startUp() {
		panel = new RucktaxesPanel(this, itemManager);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/panel_icon.png");
		navButton = NavigationButton.builder().tooltip("Rucktaxes Trades").icon(icon).priority(6).panel(panel).build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(orderCreationOverlay);
		overlayManager.add(bankHighlightOverlay);
		overlayManager.add(tradeVerificationOverlay);
		overlayManager.add(playerHighlightOverlay);
		keyManager.registerKeyListener(this);

		startReconnectTask();
	}

	@Override
	protected void shutDown() {
		stopReconnectTask();
		stopLocationUpdates();
		websocketClient.disconnect();
		resetAllPluginData();
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(orderCreationOverlay);
		overlayManager.remove(bankHighlightOverlay);
		overlayManager.remove(tradeVerificationOverlay);
		overlayManager.remove(playerHighlightOverlay);
		keyManager.unregisterKeyListener(this);
	}

	private void startReconnectTask() {
		if (reconnectFuture == null || reconnectFuture.isDone()) {
			reconnectFuture = executor.scheduleAtFixedRate(this::attemptReconnect, 15, 15, TimeUnit.SECONDS);
		}
	}

	private void stopReconnectTask() {
		if (reconnectFuture != null) {
			reconnectFuture.cancel(true);
			reconnectFuture = null;
		}
	}

	private void startLocationUpdates() {
		if (locationUpdateFuture == null || locationUpdateFuture.isDone()) {
			locationUpdateFuture = executor.scheduleAtFixedRate(this::sendLocationUpdate, 0, 2, TimeUnit.SECONDS);
		}
	}

	private void stopLocationUpdates() {
		if (locationUpdateFuture != null) {
			locationUpdateFuture.cancel(true);
			locationUpdateFuture = null;
		}
	}

	private void sendLocationUpdate() {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}
		clientThread.invokeLater(() -> {
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null) {
				WorldPoint location = localPlayer.getWorldLocation();
				websocketClient.sendUpdateLocation(location.getX(), location.getY(), location.getPlane());
			}
		});
	}

	private void attemptReconnect() {
		// FIX: Corrected logic to use !isConnected() to ensure reconnection attempts happen when disconnected.
		if (client.getGameState() == GameState.LOGGED_IN && !websocketClient.isConnected()) {
			clientThread.invokeLater(() -> {
				Player localPlayer = client.getLocalPlayer();
				if (localPlayer != null && localPlayer.getName() != null) {
					websocketClient.connect(localPlayer.getName());
				}
			});
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
			websocketClient.disconnect();
			resetAllPluginData();
			isSubscribedToWebsocket = false;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage) {
		if (!isSubscribedToWebsocket && chatMessage.getType() == ChatMessageType.GAMEMESSAGE &&
				chatMessage.getMessage().contains("Welcome to Old School RuneScape")) {
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null && localPlayer.getName() != null) {
				websocketClient.connect(localPlayer.getName());
				isSubscribedToWebsocket = true;
			}
		}

		if (chatMessage.getType() == ChatMessageType.TRADE && chatMessage.getMessage().contains("Accepted trade.")) {
			if (activeTrade != null) {
				handleTradeCompletion(activeTrade.getOrderId());
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		int groupId = event.getGroupId();
		if (groupId == TRADEMAIN_GROUP_ID || groupId == TRADECONFIRM_GROUP_ID) {
			clientThread.invokeLater(this::verifyTradeContents);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		int containerId = event.getContainerId();
		if (containerId == InventoryID.TRADE.getId() || containerId == InventoryID.TRADEOTHER.getId()) {
			clientThread.invokeLater(this::verifyTradeContents);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (activeTrade != null && hasTradeWindowBeenSeen && getTradeWindowBounds() == null) {
			clearActiveTrade("Trade window closed");
		}
	}

	public void onWebsocketConnected() {
		log.info("WebSocket connected successfully.");
	}

	public void onWebsocketDisconnected() {
		log.info("WebSocket connection lost. Awaiting automatic reconnection.");
	}

	private void resetAllPluginData() {
		allOrdersList.clear();
		myOrdersList.clear();
		notificationList.clear();
		onlineUsers.clear();
		serverIgnoredList.clear();
		listedItemIds.clear();
		clearActiveTrade("Plugin data reset");
		SwingUtilities.invokeLater(() -> {
			if (panel != null) {
				panel.setMyOrders(myOrdersList);
				panel.onOrderListUpdated();
				panel.setNotifications(notificationList);
				panel.updateIgnoreList(new ArrayList<>(serverIgnoredList));
			}
		});
	}

	public void handleWebsocketMessage(WebsocketMessage message) {
		if (message.getError() != null) {
			log.warn("Received error from server: {}", message.getError());
			clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Rucktaxes Error: " + message.getError(), null));
			return;
		}

		if (message.getType() == null) {
			log.warn("Received websocket message with no type: {}", message.getPayload());
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || localPlayer.getName() == null) return;
		String rsn = localPlayer.getName();

		switch (message.getType()) {
			case "INITIAL_STATE":
				handleInitialState(message.getPayload(), rsn);
				break;
			case "USER_STATUS_UPDATE":
				handleUserStatusUpdate(message.getPayload());
				break;
			case "TRADE_NOTIFICATION":
				handleTradeNotification(message.getPayload());
				break;
			case "TRADE_STATUS_UPDATE":
				handleTradeStatusUpdate(message.getPayload());
				break;
			case "PLAYER_LOCATION_UPDATE":
				handlePlayerLocationUpdate(message.getPayload());
				break;
			case "ORDER_CREATED":
				handleOrderCreated(message.getPayload(), rsn);
				break;
			case "ORDER_UPDATED":
				handleOrderUpdated(message.getPayload(), rsn);
				break;
			case "ORDER_DELETED":
				handleOrderDeleted(message.getPayload(), rsn);
				break;
			case "IGNORE_LIST_STATE":
				handleIgnoreListState(message.getPayload());
				break;
		}
	}

	private void handleInitialState(JsonElement payload, String rsn) {
		allOrdersList.clear();
		allOrdersList.addAll(gson.fromJson(payload.getAsJsonObject().get("all_orders"), new TypeToken<ArrayList<TradeOrder>>() {}.getType()));

		onlineUsers.clear();
		onlineUsers.addAll(gson.fromJson(payload.getAsJsonObject().get("online_users"), new TypeToken<HashSet<String>>() {}.getType()));

		updateAndRefreshLists(rsn);
	}

	private void handleUserStatusUpdate(JsonElement payload) {
		String updatedRsn = payload.getAsJsonObject().get("rsn").getAsString();
		boolean isOnline = payload.getAsJsonObject().get("is_online").getAsBoolean();
		if (isOnline) {
			onlineUsers.add(updatedRsn);
		} else {
			onlineUsers.remove(updatedRsn);
		}
		SwingUtilities.invokeLater(() -> panel.onOrderListUpdated());
	}

	private void handleTradeNotification(JsonElement payload) {
		Notification receivedNotification = gson.fromJson(payload, Notification.class);
		if (serverIgnoredList.contains(receivedNotification.getFromPlayer().toLowerCase())) return;
		receivedNotification.setType(Notification.NotificationType.RECEIVED);
		receivedNotification.setStatus(Notification.NotificationStatus.PENDING);
		notificationList.add(receivedNotification);
		SwingUtilities.invokeLater(() -> panel.setNotifications(notificationList));

		allOrdersList.stream()
				.filter(o -> o.getOrderId().equals(receivedNotification.getOrderId()))
				.findFirst()
				.ifPresent(order -> {
					String action = order.getType() == TradeOrder.OrderType.BUY ? "sell you" : "buy your" ;
					String message = String.format("%s wants to %s %s x %s @ %s ea.",
							receivedNotification.getFromPlayer(),
							action,
							QuantityFormatter.formatNumber(order.getQuantity()),
							order.getItemName(),
							QuantityFormatter.formatNumber(order.getPricePerItem()));
					sendGameMessage(message);
				});
	}

	private void handleTradeStatusUpdate(JsonElement payload) {
		String orderId = payload.getAsJsonObject().get("order_id").getAsString();
		Notification.NotificationStatus newStatus = Notification.NotificationStatus.valueOf(payload.getAsJsonObject().get("status").getAsString());

		if (newStatus == Notification.NotificationStatus.ACCEPTED) {
			startLocationUpdates();
			notificationList.stream()
					.filter(n -> n.getOrderId().equals(orderId) && n.getType() == Notification.NotificationType.SENT)
					.findFirst()
					.ifPresent(sentNotification -> {
						// FIX: Set the activeTrade for the initiator so they can receive location updates
						this.activeTrade = sentNotification;
						this.otherPlayerRsn = sentNotification.getToPlayer();
						allOrdersList.stream()
								.filter(o -> o.getOrderId().equals(orderId))
								.findFirst()
								.ifPresent(order -> {
									String action = order.getType() == TradeOrder.OrderType.SELL ? "buy" : "sell";
									String message = String.format("%s accepted your %s request for %s x %s @ %s ea.",
											sentNotification.getToPlayer(),
											action,
											QuantityFormatter.formatNumber(order.getQuantity()),
											order.getItemName(),
											QuantityFormatter.formatNumber(order.getPricePerItem()));
									sendGameMessage(message);
								});
					});
		}

		notificationList.stream()
				.filter(n -> n.getOrderId().equals(orderId))
				.forEach(n -> {
					n.setStatus(newStatus);
					if (newStatus == Notification.NotificationStatus.CANCELLED && activeTrade != null && activeTrade.getOrderId().equals(orderId)) {
						clearActiveTrade("Trade status updated to CANCELLED");
					}
				});
		SwingUtilities.invokeLater(() -> panel.setNotifications(notificationList));
	}

	private void handlePlayerLocationUpdate(JsonElement payload) {
		if (activeTrade == null || otherPlayerRsn == null) {
			return;
		}
		String rsn = payload.getAsJsonObject().get("rsn").getAsString();
		if (!rsn.equalsIgnoreCase(otherPlayerRsn)) {
			return;
		}

		int x = payload.getAsJsonObject().get("x").getAsInt();
		int y = payload.getAsJsonObject().get("y").getAsInt();
		int plane = payload.getAsJsonObject().get("plane").getAsInt();
		this.otherPlayerLocation = new WorldPoint(x, y, plane);

		client.setHintArrow(otherPlayerLocation);
		updateWorldMapPoint();
	}

	private void handleOrderCreated(JsonElement payload, String rsn) {
		allOrdersList.add(gson.fromJson(payload, TradeOrder.class));
		updateAndRefreshLists(rsn);
	}

	private void handleOrderUpdated(JsonElement payload, String rsn) {
		TradeOrder updatedOrder = gson.fromJson(payload, TradeOrder.class);
		allOrdersList.removeIf(o -> o.getOrderId().equals(updatedOrder.getOrderId()));
		allOrdersList.add(updatedOrder);
		updateAndRefreshLists(rsn);
	}

	private void handleOrderDeleted(JsonElement payload, String rsn) {
		String deletedId = payload.getAsJsonObject().get("order_id").getAsString();
		allOrdersList.removeIf(o -> o.getOrderId().equals(deletedId));

		notificationList.stream()
				.filter(n -> n.getOrderId().equals(deletedId) && n.getStatus() != Notification.NotificationStatus.COMPLETED)
				.forEach(n -> n.setStatus(Notification.NotificationStatus.COMPLETED));

		if (activeTrade != null && activeTrade.getOrderId().equals(deletedId)) {
			clearActiveTrade("Order was deleted");
		}

		updateAndRefreshLists(rsn);
		SwingUtilities.invokeLater(() -> panel.setNotifications(notificationList));
	}

	private void handleIgnoreListState(JsonElement payload) {
		serverIgnoredList.clear();
		serverIgnoredList.addAll(gson.fromJson(payload.getAsJsonObject().get("ignored_rsns"), new TypeToken<HashSet<String>>() {}.getType()));
		SwingUtilities.invokeLater(() -> {
			if (panel != null) {
				panel.updateIgnoreList(new ArrayList<>(serverIgnoredList));
			}
		});
	}

	private void updateAndRefreshLists(String rsn) {
		clientThread.invokeLater(() -> {
			for (TradeOrder order : allOrdersList) {
				order.setItem(itemManager.getItemComposition(order.getItemId()));
				order.setMyOrder(order.getPlayerName().equalsIgnoreCase(rsn));
			}
			myOrdersList.clear();
			myOrdersList.addAll(allOrdersList.stream().filter(TradeOrder::isMyOrder).collect(Collectors.toList()));

			listedItemIds.clear();
			myOrdersList.forEach(order -> listedItemIds.add(itemManager.canonicalize(order.getItemId())));

			SwingUtilities.invokeLater(() -> {
				if (panel != null) {
					panel.setMyOrders(myOrdersList);
					panel.onOrderListUpdated();
				}
			});
		});
	}

	public void handleInitiateTrade(TradeOrder order) {
		Instant now = Instant.now();
		if (tradeButtonCooldowns.getOrDefault(order.getOrderId(), Instant.MIN).isAfter(now)) {
			return;
		}

		String selfRsn = client.getLocalPlayer().getName();
		String targetPlayer = order.getPlayerName();
		Instant lastNotification = notificationCooldowns.get(targetPlayer);
		if (lastNotification != null && Duration.between(lastNotification, now).getSeconds() < NOTIFICATION_COOLDOWN_SECONDS) {
			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "You must wait before contacting this player again.", "Cooldown Active", JOptionPane.WARNING_MESSAGE));
			return;
		}

		tradeButtonCooldowns.put(order.getOrderId(), now.plusSeconds(TRADE_BUTTON_COOLDOWN_SECONDS));
		SwingUtilities.invokeLater(() -> panel.onOrderListUpdated());

		String myAction = order.getType() == TradeOrder.OrderType.SELL ? "buy" : "sell";
		String preposition = myAction.equals("buy") ? "from" : "to";
		String message = String.format("You requested to %s %s x %s %s %s @ %s ea.",
				myAction, QuantityFormatter.formatNumber(order.getQuantity()), order.getItemName(),
				preposition, targetPlayer, order.getPricePerItem());

		Notification sentNotification = new Notification(UUID.randomUUID().toString(), order.getOrderId(), message, selfRsn, targetPlayer, Instant.now(), Notification.NotificationType.SENT, Notification.NotificationStatus.PENDING);
		notificationList.add(sentNotification);
		SwingUtilities.invokeLater(() -> panel.setNotifications(notificationList));

		websocketClient.sendInitiateTrade(order.getOrderId());
		notificationCooldowns.put(targetPlayer, Instant.now());
		SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "A notification has been sent to " + targetPlayer + ".\nOpen the trade window to verify the transaction.", "Notification Sent", JOptionPane.INFORMATION_MESSAGE));
	}

	public void handleAcceptTrade(Notification notification) {
		if (notification == null) return;
		this.activeTrade = notification;
		this.tradeStatus = TradeStatus.PENDING;
		this.hasTradeWindowBeenSeen = false;
		this.otherPlayerRsn = notification.getFromPlayer();
		notification.setStatus(Notification.NotificationStatus.ACCEPTED);
		log.info("User accepted trade. Setting activeTrade for notification ID: {}", notification.getNotificationId());
		websocketClient.sendAcceptTrade(notification.getNotificationId());
		startLocationUpdates();
		SwingUtilities.invokeLater(() -> panel.setNotifications(notificationList));
	}

	public void handleCancelAcceptedTrade(Notification notification) {
		if (notification == null || notification.getStatus() != Notification.NotificationStatus.ACCEPTED) return;
		notification.setStatus(Notification.NotificationStatus.CANCELLED);
		websocketClient.sendCancelTrade(notification.getNotificationId());
		clearActiveTrade("User cancelled accepted trade");
		SwingUtilities.invokeLater(() -> panel.setNotifications(notificationList));
	}

	public void handleForceCompleteTrade(Notification notification) {
		if (notification == null || (notification.getStatus() != Notification.NotificationStatus.ACCEPTED && notification.getStatus() != Notification.NotificationStatus.PENDING)) {
			return;
		}
		handleTradeCompletion(notification.getOrderId());
	}

	private void handleTradeCompletion(String orderId) {
		log.info("Trade completed for order ID: {}", orderId);
		notificationList.stream()
				.filter(n -> n.getOrderId().equals(orderId))
				.forEach(n -> n.setStatus(Notification.NotificationStatus.COMPLETED));

		websocketClient.sendCompleteOrder(orderId);

		if (activeTrade != null && activeTrade.getOrderId().equals(orderId)) {
			clearActiveTrade("Trade completed");
		}
		SwingUtilities.invokeLater(() -> panel.setNotifications(notificationList));
	}

	public void handleClearNotifications(Notification.NotificationType typeToClear) {
		if (typeToClear == null) {
			notificationList.clear();
		} else {
			notificationList.removeIf(n -> n.getType() == typeToClear);
		}
		SwingUtilities.invokeLater(() -> panel.setNotifications(notificationList));
	}

	public void handleAddToIgnoreList(String rsn) {
		if (rsn == null || rsn.trim().isEmpty()) return;
		if (client.getLocalPlayer() != null && rsn.equalsIgnoreCase(client.getLocalPlayer().getName())) {
			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "You cannot ignore yourself.", "Error", JOptionPane.ERROR_MESSAGE));
			return;
		}
		websocketClient.sendAddToIgnoreList(rsn.trim());
	}

	public void handleRemoveFromIgnoreList(String rsn) {
		if (rsn == null || rsn.trim().isEmpty()) return;
		websocketClient.sendRemoveFromIgnoreList(rsn.trim());
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (config.useShift() && !client.isKeyPressed(KeyCode.KC_SHIFT)) return;

		if (event.getOption().equalsIgnoreCase("examine")) {
			final MenuEntry originalEntry = event.getMenuEntry();
			final int itemId = originalEntry.getItemId();
			if (itemId == -1) return;

			ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			if (!itemComposition.isTradeable()) return;

			client.getMenu().createMenuEntry(-1)
					.setOption(MENU_ACTION_TEXT)
					.setTarget(originalEntry.getTarget())
					.setType(MenuAction.RUNELITE)
					.setParam0(originalEntry.getParam0())
					.setParam1(originalEntry.getParam1())
					.setItemId(itemId)
					.onClick(this::handleMenuAction);
		}
	}

	private void handleMenuAction(MenuEntry menuEntry) {
		clientThread.invokeLater(() -> {
			final int itemId = menuEntry.getItemId();
			if (itemId == -1) {
				return;
			}

			ItemComposition itemComposition = itemManager.getItemComposition(itemId);

			final int widgetId = menuEntry.getParam1();
			final int groupId = widgetId >>> 16;
			final int itemIndex = menuEntry.getParam0();

			ItemContainer container = null;
			if (groupId == WidgetID.BANK_GROUP_ID) {
				container = client.getItemContainer(InventoryID.BANK);
			} else if (groupId == WidgetID.INVENTORY_GROUP_ID) {
				container = client.getItemContainer(InventoryID.INVENTORY);
			}

			if (container == null) {
				log.warn("Could not determine item container for menu action on widget group: {}", groupId);
				return;
			}

			Item item = container.getItem(itemIndex);
			if (item == null) {
				log.warn("Could not find item at index {} in container for widget group ID: {}", itemIndex, groupId);
				return;
			}

			if (item.getId() != itemId) {
				log.warn("Item ID mismatch! MenuEntry had {}, container had {} at index {}", itemId, item.getId(), itemIndex);
				return;
			}

			orderCreationManager.open(itemComposition, item.getQuantity());
		});
	}

	public void handleModifyOrder(TradeOrder order) {
		clientThread.invokeLater(() -> {
			long maxQty = getPlayerItemCount(order.getItemId());
			orderCreationManager.openForModify(order, (int) maxQty);
		});
	}

	public void handleCancelOrder(TradeOrder orderToCancel) {
		websocketClient.sendCancelOrder(orderToCancel.getOrderId());
	}

	private Map<Integer, Integer> getItemsFromWidget(Widget itemWidget) {
		if (itemWidget == null || itemWidget.getDynamicChildren() == null) {
			return Collections.emptyMap();
		}
		Map<Integer, Integer> items = new HashMap<>();
		for (Widget item : itemWidget.getDynamicChildren()) {
			if (item.getItemId() > -1) {
				int realId = itemManager.canonicalize(item.getItemId());
				items.put(realId, items.getOrDefault(realId, 0) + item.getItemQuantity());
			}
		}
		return items;
	}

	private void verifyTradeContents() {
		if (activeTrade == null) return;
		this.hasTradeWindowBeenSeen = true;

		if (client.getWidget(TRADECONFIRM_GROUP_ID, 1) != null) {
			tradeStatus = TradeStatus.CORRECT;
			return;
		}

		if (client.getWidget(TRADEMAIN_GROUP_ID, 0) == null) return;

		TradeOrder originalOrder = allOrdersList.stream()
				.filter(o -> o.getOrderId().equals(activeTrade.getOrderId()))
				.findFirst().orElse(null);

		if (originalOrder == null) {
			clearActiveTrade("Original order not found");
			return;
		}

		Map<Integer, Integer> myOffer = getItemsFromWidget(client.getWidget(TRADEMAIN_GROUP_ID, TRADEMAIN_MY_OFFER_LIST_CHILD_ID));
		Map<Integer, Integer> theirOffer = getItemsFromWidget(client.getWidget(TRADEMAIN_GROUP_ID, TRADEMAIN_THEIR_OFFER_LIST_CHILD_ID));

		boolean amISeller = originalOrder.getPlayerName().equalsIgnoreCase(client.getLocalPlayer().getName());
		Map<Integer, Integer> sellerItems = amISeller ? myOffer : theirOffer;
		Map<Integer, Integer> buyerItems = amISeller ? theirOffer : myOffer;

		if (!verifyItems(sellerItems, originalOrder)) {
			tradeStatus = TradeStatus.INCORRECT_ITEMS;
		} else if (!verifyPrice(buyerItems, originalOrder)) {
			tradeStatus = TradeStatus.INCORRECT_PRICE;
		} else {
			tradeStatus = TradeStatus.CORRECT;
		}
	}

	private boolean verifyItems(Map<Integer, Integer> offeredItems, TradeOrder order) {
		int expectedItemId = itemManager.canonicalize(order.getItemId());
		int expectedQuantity = order.getQuantity();
		int offeredQuantity = offeredItems.getOrDefault(expectedItemId, 0);
		return offeredQuantity == expectedQuantity && (offeredItems.size() == 1 || expectedQuantity == 0);
	}

	private boolean verifyPrice(Map<Integer, Integer> offeredItems, TradeOrder order) {
		long expectedPrice = order.getPrice();
		long offeredCoins = offeredItems.getOrDefault(ItemID.COINS_995, 0);
		return offeredCoins == expectedPrice && (offeredItems.size() == 1 || expectedPrice == 0);
	}

	public void clearActiveTrade(String reason) {
		if (this.activeTrade != null) {
			log.info("Clearing active trade {} due to: {}", this.activeTrade.getNotificationId(), reason);
			this.activeTrade = null;
			this.tradeStatus = TradeStatus.PENDING;
			this.hasTradeWindowBeenSeen = false;
			stopLocationUpdates();
			clearOtherPlayerData();
		}
	}

	private void clearOtherPlayerData() {
		client.clearHintArrow();
		if (worldMapPoint != null) {
			worldMapPointManager.remove(worldMapPoint);
			worldMapPoint = null;
		}
		otherPlayerLocation = null;
		otherPlayerRsn = null;
	}

	private void updateWorldMapPoint() {
		if (worldMapPoint != null) {
			worldMapPointManager.remove(worldMapPoint);
		}

		if (otherPlayerLocation == null || otherPlayerRsn == null) {
			return;
		}

		worldMapPoint = new WorldMapPoint(otherPlayerLocation, getMapPointImage());
		worldMapPoint.setName(otherPlayerRsn);
		worldMapPoint.setSnapToEdge(true);
		worldMapPoint.setJumpOnClick(true);
		worldMapPointManager.add(worldMapPoint);
	}



	private BufferedImage getMapPointImage() {
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/panel_icon.png");
		return ImageUtil.resizeImage(icon, 16, 16);
	}

	public Rectangle getTradeWindowBounds() {
		Widget tradeMainAnchor = client.getWidget(TRADEMAIN_GROUP_ID, 0);
		if (tradeMainAnchor != null) return tradeMainAnchor.getBounds();
		Widget tradeConfirmAnchor = client.getWidget(TRADECONFIRM_GROUP_ID, 0);
		if (tradeConfirmAnchor != null) return tradeConfirmAnchor.getBounds();
		return null;
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (!orderCreationManager.isOverlayActive()) return;

		switch (e.getKeyCode()) {
			case KeyEvent.VK_ENTER:
				submitOrder();
				e.consume();
				break;
			case KeyEvent.VK_ESCAPE:
				orderCreationManager.close();
				e.consume();
				break;
			case KeyEvent.VK_TAB:
				orderCreationManager.setFocusedField(
						orderCreationManager.getFocusedField() == RucktaxesManager.FocusedField.QUANTITY
								? RucktaxesManager.FocusedField.PRICE
								: RucktaxesManager.FocusedField.QUANTITY
				);
				e.consume();
				break;
			case KeyEvent.VK_UP:
			case KeyEvent.VK_DOWN:
				orderCreationManager.setOrderType(
						orderCreationManager.getOrderType() == TradeOrder.OrderType.BUY
								? TradeOrder.OrderType.SELL
								: TradeOrder.OrderType.BUY
				);
				e.consume();
				break;
			case KeyEvent.VK_BACK_SPACE:
				handleBackspace();
				e.consume();
				break;
		}
	}

	private void submitOrder() {
		try {
			int quantity = Integer.parseInt(orderCreationManager.getQuantityInput());
			long price = Long.parseLong(orderCreationManager.getPriceInput());
			if (quantity <= 0 || price <= 0) {
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "Quantity and price must be positive.", "Invalid Input", JOptionPane.ERROR_MESSAGE));
				return;
			}
			if (price > Integer.MAX_VALUE) {
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "Price per item exceeds the maximum value.", "Invalid Input", JOptionPane.ERROR_MESSAGE));
				return;
			}

			TradeOrder.OrderType type = orderCreationManager.getOrderType();
			TradeOrder orderToModify = orderCreationManager.getOrderBeingModified();

			if (orderToModify != null) {
				websocketClient.sendModifyOrder(orderToModify.getOrderId(), quantity, (int) price);
			} else {
				ItemComposition item = orderCreationManager.getItem();
				websocketClient.sendCreateOrder(item.getId(), item.getName(), quantity, (int) price, type);
			}
			orderCreationManager.close();
		} catch (NumberFormatException ex) {
			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "Please enter valid numbers for quantity and price.", "Invalid Input", JOptionPane.ERROR_MESSAGE));
		}
	}

	private void handleBackspace() {
		String currentText = getFocusedInput();
		if (currentText != null && !currentText.isEmpty()) {
			setFocusedInput(currentText.substring(0, currentText.length() - 1));
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
		if (!orderCreationManager.isOverlayActive()) return;
		if (Character.isDigit(e.getKeyChar())) {
			String currentText = getFocusedInput();
			if (currentText != null) {
				String newText = currentText + e.getKeyChar();
				try {
					Long.parseLong(newText);
					setFocusedInput(newText);
					e.consume();
				} catch (NumberFormatException ignored) {}
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {}

	private String getFocusedInput() {
		return orderCreationManager.getFocusedField() == RucktaxesManager.FocusedField.QUANTITY
				? orderCreationManager.getQuantityInput()
				: orderCreationManager.getPriceInput();
	}

	private void setFocusedInput(String newText) {
		if (orderCreationManager.getFocusedField() == RucktaxesManager.FocusedField.QUANTITY) {
			orderCreationManager.setQuantityInput(newText);
		} else {
			orderCreationManager.setPriceInput(newText);
		}
	}

	private long getPlayerItemCount(int itemId) {
		if (client.getGameState() != GameState.LOGGED_IN) return 0;
		long count = 0;
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory != null) count += inventory.count(itemId);
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null) count += bank.count(itemId);
		return count;
	}

	private void sendGameMessage(String message) {
		clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.BROADCAST, "", message, null));
	}

	public boolean isPlayerOnline(String rsn) {
		return onlineUsers.contains(rsn.toLowerCase());
	}

	public Instant getTradeButtonCooldownEnd(String orderId) {
		return tradeButtonCooldowns.get(orderId);
	}
}