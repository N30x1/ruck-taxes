package com.taxes.rucker.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.taxes.rucker.CryptoUtils;
import com.taxes.rucker.IdentityManager;
import com.taxes.rucker.RucktaxesConfig;
import com.taxes.rucker.RucktaxesPlugin;
import com.taxes.rucker.websocket.dto.AuthResponse;
import com.taxes.rucker.websocket.dto.TradeOrder;
import com.taxes.rucker.websocket.dto.WebsocketMessage;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
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
    private enum ConnectionState { DISCONNECTED, AUTHENTICATING, CONNECTED }
    private static final String BASE_URL = "api.ruckge.com";
    private static final String SCHEME = "https";
    private static final String WS_SCHEME = "wss";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final RucktaxesPlugin plugin;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final IdentityManager identityManager;

    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private WebSocket webSocket;
    private String rsn;
    private PrivateKey privateKey;
    private String generatedUsername;

    public RucktaxesWebsocketClient(RucktaxesPlugin plugin, OkHttpClient httpClient, Gson gson) {
        this.plugin = plugin;
        this.httpClient = httpClient;
        this.gson = gson;
        this.identityManager = new IdentityManager();
    }

    public void connect(String rsn) {
        if (state != ConnectionState.DISCONNECTED) {
            log.warn("Connect called while already connecting or connected. State: {}", state);
            return;
        }
        this.rsn = rsn;
        state = ConnectionState.AUTHENTICATING;
        log.info("Attempting to connect to Rucktaxes service for RSN: {}", rsn);
        startAuthenticationFlow();
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

    private void startAuthenticationFlow() {
        this.privateKey = identityManager.loadPrivateKey();
        this.generatedUsername = identityManager.loadGeneratedUsername();

        if (this.privateKey == null || this.generatedUsername == null || this.generatedUsername.isEmpty()) {
            log.info("No existing identity found. Starting registration process.");
            registerNewClient();
        } else {
            log.info("Existing identity found for generated username: {}. Starting challenge-response.", generatedUsername);
            requestChallenge(this.generatedUsername);
        }
    }

    private void registerNewClient() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        if (keyPair == null) {
            log.error("Failed to generate key pair. Cannot register.");
            state = ConnectionState.DISCONNECTED;
            return;
        }
        this.privateKey = keyPair.getPrivate();
        String publicKeyPemString = CryptoUtils.publicKeyToPemString(keyPair.getPublic());

        JsonObject payload = new JsonObject();
        payload.addProperty("public_key", publicKeyPemString);
        RequestBody body = RequestBody.create(JSON, gson.toJson(payload));

        Request request = new Request.Builder()
                .url(SCHEME + "://" + BASE_URL + "/v1/auth/register")
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                log.error("Failed to register with backend, will retry.", e);
                state = ConnectionState.DISCONNECTED;
            }

            @Override
            public void onResponse(@Nonnull Call call, @Nonnull Response response) {
                if (!response.isSuccessful()) {
                    log.error("Registration failed with code: {}, will retry.", response.code());
                    state = ConnectionState.DISCONNECTED;
                    response.close();
                    return;
                }
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        log.error("Registration response body was null despite a successful status code. Will retry.");
                        state = ConnectionState.DISCONNECTED;
                        return;
                    }

                    String responseString = responseBody.string();
                    log.debug("Registration response string: {}", responseString);
                    JsonObject json = gson.fromJson(responseString, JsonObject.class);

                    if (json == null || !json.has("generated_username")) {
                        log.error("Registration response JSON is malformed or missing 'generated_username' key.");
                        state = ConnectionState.DISCONNECTED;
                        return;
                    }

                    generatedUsername = json.get("generated_username").getAsString();
                    identityManager.saveIdentity(privateKey, generatedUsername);

                    log.info("Successfully registered. New username: {}", generatedUsername);
                    requestChallenge(generatedUsername);
                } catch (Exception e) {
                    log.error("Failed to parse registration response, will retry.", e);
                    state = ConnectionState.DISCONNECTED;
                }
            }
        });
    }

    private void requestChallenge(String generatedUsername) {
        JsonObject payload = new JsonObject();
        payload.addProperty("generated_username", generatedUsername);
        RequestBody body = RequestBody.create(JSON, gson.toJson(payload));

        Request request = new Request.Builder()
                .url(SCHEME + "://" + BASE_URL + "/v1/auth/challenge")
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                log.error("Failed to get challenge from backend, will retry.", e);
                state = ConnectionState.DISCONNECTED;
            }

            @Override
            public void onResponse(@Nonnull Call call, @Nonnull Response response) {
                if (!response.isSuccessful()) {
                    log.error("Challenge request failed with code: {}, will retry.", response.code());
                    state = ConnectionState.DISCONNECTED;
                    response.close();
                    return;
                }
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        log.error("Challenge response body was null. Will retry.");
                        state = ConnectionState.DISCONNECTED;
                        return;
                    }
                    String responseString = responseBody.string();
                    log.debug("Challenge response string: {}", responseString);
                    JsonObject json = gson.fromJson(responseString, JsonObject.class);

                    if (json == null || !json.has("challenge")) {
                        log.error("Challenge response JSON is malformed or missing 'challenge' key.");
                        state = ConnectionState.DISCONNECTED;
                        return;
                    }

                    String challenge = json.get("challenge").getAsString();
                    log.info("Received challenge. Signing and requesting token.");
                    requestToken(generatedUsername, challenge);
                } catch (Exception e) {
                    log.error("Failed to parse challenge response, will retry.", e);
                    state = ConnectionState.DISCONNECTED;
                }
            }
        });
    }

    private void requestToken(String generatedUsername, String challenge) {
        String signature = CryptoUtils.sign(this.privateKey, challenge);
        if (signature == null) {
            log.error("Failed to sign challenge. Cannot authenticate.");
            state = ConnectionState.DISCONNECTED;
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("generated_username", generatedUsername);
        payload.addProperty("challenge", challenge);
        payload.addProperty("signature", signature);
        RequestBody body = RequestBody.create(JSON, gson.toJson(payload));

        Request request = new Request.Builder()
                .url(SCHEME + "://" + BASE_URL + "/v1/auth/token")
                .post(body)
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
                    response.close();
                    return;
                }
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        log.error("Token response body was null. Will retry.");
                        state = ConnectionState.DISCONNECTED;
                        return;
                    }
                    String responseString = responseBody.string();
                    log.debug("Token response string: {}", responseString);
                    AuthResponse authResponse = gson.fromJson(responseString, AuthResponse.class);

                    if (authResponse == null || authResponse.getAccess_token() == null) {
                        log.error("Token response JSON is malformed or missing 'access_token' key.");
                        state = ConnectionState.DISCONNECTED;
                        return;
                    }

                    String token = authResponse.getAccess_token();
                    Request wsRequest = new Request.Builder()
                            .url(WS_SCHEME + "://" + BASE_URL + "/ws/" + token)
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
        sendSetRsn(this.rsn);
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

    public void sendSetRsn(String rsn) {
        sendMessage("SET_RSN", Map.of("rsn", rsn));
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

    public void sendAddToIgnoreList(String userId) {
        sendMessage("ADD_TO_IGNORE_LIST", Map.of("user_id", userId));
    }

    public void sendRemoveFromIgnoreList(String userId) {
        sendMessage("REMOVE_FROM_IGNORE_LIST", Map.of("user_id", userId));
    }
}