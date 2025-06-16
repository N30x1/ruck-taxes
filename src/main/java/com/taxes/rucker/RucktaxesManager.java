package com.taxes.rucker;

import com.taxes.rucker.websocket.dto.TradeOrder;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ItemComposition;

@Singleton
@Getter
public class RucktaxesManager {
    private boolean isOverlayActive = false;
    private ItemComposition item;
    private int maxQuantity;
    private TradeOrder orderBeingModified = null;

    @Setter(AccessLevel.PACKAGE)
    private String quantityInput = "";
    @Setter(AccessLevel.PACKAGE)
    private String priceInput = "";
    @Setter(AccessLevel.PACKAGE)
    private TradeOrder.OrderType orderType = TradeOrder.OrderType.SELL;
    @Setter(AccessLevel.PACKAGE)
    private FocusedField focusedField = FocusedField.QUANTITY;

    public enum FocusedField { QUANTITY, PRICE }

    // No @Inject annotation here
    RucktaxesManager() {
    }

    public void open(ItemComposition item, int maxQuantity) {
        this.orderBeingModified = null;
        this.item = item;
        this.maxQuantity = maxQuantity;
        this.isOverlayActive = true;
        this.quantityInput = String.valueOf(maxQuantity);
        this.priceInput = "";
        this.focusedField = FocusedField.QUANTITY;
        this.orderType = TradeOrder.OrderType.SELL;
    }

    public void openForModify(TradeOrder order, int maxQuantity) {
        this.orderBeingModified = order;
        this.item = order.getItem();
        this.maxQuantity = maxQuantity;
        this.isOverlayActive = true;
        this.quantityInput = String.valueOf(order.getQuantity());
        this.priceInput = String.valueOf(order.getPricePerItem());
        this.orderType = order.getType();
        this.focusedField = FocusedField.QUANTITY;
    }

    public void close() {
        this.isOverlayActive = false;
        this.orderBeingModified = null;
        this.item = null;
    }
}