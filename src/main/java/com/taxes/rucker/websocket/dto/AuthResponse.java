package com.taxes.rucker.websocket.dto;

import lombok.Data;

@Data
public class AuthResponse {
    String access_token;
    String token_type;
}