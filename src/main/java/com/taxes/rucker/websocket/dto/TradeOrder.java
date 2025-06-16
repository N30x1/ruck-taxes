package com.taxes.rucker.websocket.dto;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ItemComposition;

@Getter
@Setter
@AllArgsConstructor
public class TradeOrder {
    public enum OrderType { BUY, SELL }

    // FIX: Changed annotation from "order_id" to "id" to match the backend schema.
    @SerializedName("id")
    private String orderId;

    @SerializedName("owner_rsn")
    private String playerName;

    @SerializedName("order_type")
    private OrderType type;

    @SerializedName("item_id")
    private int itemId;

    @SerializedName("item_name")
    private String itemName;

    private int quantity;

    @SerializedName("price_per_item")
    private int pricePerItem;

    private transient ItemComposition item;
    private transient boolean isMyOrder;

    public long getPrice() {
        return (long) quantity * pricePerItem;
    }
}