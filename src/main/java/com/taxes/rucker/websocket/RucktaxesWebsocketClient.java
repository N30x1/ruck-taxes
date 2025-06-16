package com.taxes.rucker.websocket;

import com.google.gson.Gson;
import com.taxes.rucker.RucktaxesPlugin;
import com.taxes.rucker.websocket.dto.AuthResponse;
import com.taxes.rucker.websocket.dto.TradeOrder;
import com.taxes.rucker.websocket.dto.WebsocketMessage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

@Slf4j
@Singleton
public class RucktaxesWebsocketClient extends WebSocketListener {
    private enum ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    private static final String BASE_URL = "api.ruckge.com";
    private final RucktaxesPlugin plugin;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private WebSocket webSocket;
    private String rsn;

    public RucktaxesWebsocketClient(RucktaxesPlugin plugin, OkHttpClient httpClient, Gson gson) {
        this.plugin = plugin;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public void connect(String rsn) {
        if (state != ConnectionState.DISCONNECTED) {
            log.warn("Connect called while already connecting or connected. State: {}", state);
            return;
        }

        this.rsn = rsn;
        state = ConnectionState.CONNECTING;
        log.info("Attempting to connect to Rucktaxes service for RSN: {}", rsn);
        getAuthTokenAndConnect();
    }

    public void disconnect() {
        if (state != ConnectionState.DISCONNECTED) {
            log.info("Client requested disconnect.");
            if (webSocket != null) {
                webSocket.close(1000, "Client requested disconnect");
                webSocket = null;
            }
            state = ConnectionState.DISCONNECTED;
        }
    }

    public boolean isConnected() {
        return state == ConnectionState.CONNECTED;
    }

    private void getAuthTokenAndConnect() {
        RequestBody formBody = new FormBody.Builder()
                .add("username", rsn)
                .add("password", "")
                .build();

        Request request = new Request.Builder()
                .url("https://" + BASE_URL + "/v1/auth/token")
                .post(formBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                log.error("Failed to authenticate with backend, will retry.", e);
                state = ConnectionState.DISCONNECTED;
            }

            @Override
            public void onResponse(@Nonnull Call call, @Nonnull Response response) {
                if (!response.isSuccessful()) {
                    log.error("Authentication failed with code: {}, will retry.", response.code());
                    state = ConnectionState.DISCONNECTED;
                    return;
                }
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        log.error("Authentication response body was null.");
                        state = ConnectionState.DISCONNECTED;
                        return;
                    }

                    AuthResponse authResponse = gson.fromJson(responseBody.string(), AuthResponse.class);
                    String token = authResponse.getAccess_token();
                    Request wsRequest = new Request.Builder()
                            .url("wss://" + BASE_URL + "/ws/" + token)
                            .build();
                    webSocket = httpClient.newWebSocket(wsRequest, RucktaxesWebsocketClient.this);
                } catch (Exception e) {
                    log.error("Failed to parse auth response, will retry.", e);
                    state = ConnectionState.DISCONNECTED;
                }
            }
        });
    }

    @Override
    public void onOpen(@Nonnull WebSocket webSocket, @Nonnull Response response) {
        log.info("Rucktaxes WebSocket connection opened.");
        state = ConnectionState.CONNECTED;
        plugin.onWebsocketConnected();
    }

    @Override
    public void onClosing(@Nonnull WebSocket webSocket, int code, @Nonnull String reason) {
        log.info("Rucktaxes WebSocket closing: {} - {}", code, reason);
        webSocket.close(1000, null);
    }

    @Override
    public void onClosed(@Nonnull WebSocket webSocket, int code, @Nonnull String reason) {
        log.info("Rucktaxes WebSocket connection closed.");
        state = ConnectionState.DISCONNECTED;
        plugin.onWebsocketDisconnected();
    }

    @Override
    public void onFailure(@Nonnull WebSocket webSocket, @Nonnull Throwable t, Response response) {
        log.error("Rucktaxes WebSocket failure.", t);
        state = ConnectionState.DISCONNECTED;
        plugin.onWebsocketDisconnected();
    }

    @Override
    public void onMessage(@Nonnull WebSocket webSocket, @Nonnull String text) {
        try {
            WebsocketMessage message = gson.fromJson(text, WebsocketMessage.class);
            plugin.handleWebsocketMessage(message);
        } catch (Exception e) {
            log.error("Failed to parse WebSocket message: '{}'", text, e);
        }
    }

    private void sendMessage(String type, Object payload) {
        if (!isConnected() || webSocket == null) {
            log.warn("Attempted to send message while disconnected: type {}", type);
            return;
        }
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("type", type);
        messageMap.put("payload", payload);
        webSocket.send(gson.toJson(messageMap));
    }

    public void sendUpdateLocation(int x, int y, int plane) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("x", x);
        payload.put("y", y);
        payload.put("plane", plane);
        sendMessage("UPDATE_MY_LOCATION", payload);
    }

    public void sendCreateOrder(int itemId, String itemName, int quantity, int price, TradeOrder.OrderType orderType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("item_id", itemId);
        payload.put("item_name", itemName);
        payload.put("quantity", quantity);
        payload.put("price_per_item", price);
        payload.put("order_type", orderType.toString());
        sendMessage("CREATE_ORDER", payload);
    }

    public void sendModifyOrder(String orderId, int quantity, int price) {
        if (orderId == null) {
            log.warn("Attempted to send MODIFY_ORDER with a null orderId.");
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", orderId);
        payload.put("quantity", quantity);
        payload.put("price_per_item", price);
        sendMessage("MODIFY_ORDER", payload);
    }

    public void sendCancelOrder(String orderId) {
        // FIX: Add a null check to prevent NullPointerException when creating the payload map.
        if (orderId == null) {
            log.warn("Attempted to send CANCEL_ORDER with a null orderId.");
            return;
        }
        sendMessage("CANCEL_ORDER", Map.of("order_id", orderId));
    }

    public void sendInitiateTrade(String orderId) {
        if (orderId == null) {
            log.warn("Attempted to send INITIATE_TRADE with a null orderId.");
            return;
        }
        sendMessage("INITIATE_TRADE", Map.of("order_id", orderId));
    }

    public void sendAcceptTrade(String notificationId) {
        if (notificationId == null) {
            log.warn("Attempted to send ACCEPT_TRADE with a null notificationId.");
            return;
        }
        sendMessage("ACCEPT_TRADE", Map.of("notification_id", notificationId));
    }

    public void sendCancelTrade(String notificationId) {
        if (notificationId == null) {
            log.warn("Attempted to send CANCEL_TRADE with a null notificationId.");
            return;
        }
        sendMessage("CANCEL_TRADE", Map.of("notification_id", notificationId));
    }

    public void sendCompleteOrder(String orderId) {
        if (orderId == null) {
            log.warn("Attempted to send COMPLETE_TRADE with a null orderId.");
            return;
        }
        sendMessage("COMPLETE_TRADE", Map.of("order_id", orderId));
    }

    public void sendAddToIgnoreList(String rsn) {
        sendMessage("ADD_TO_IGNORE_LIST", Map.of("rsn", rsn));
    }

    public void sendRemoveFromIgnoreList(String rsn) {
        sendMessage("REMOVE_FROM_IGNORE_LIST", Map.of("rsn", rsn));
    }
}