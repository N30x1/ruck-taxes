package com.taxes.rucker.websocket.dto;

import com.google.gson.JsonElement;
import lombok.Value;

@Value
public class WebsocketMessage {
    String type;
    JsonElement payload;
    String error;
}