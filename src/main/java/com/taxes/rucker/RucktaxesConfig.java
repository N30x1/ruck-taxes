package com.taxes.rucker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("rucktaxes")
public interface RucktaxesConfig extends Config {

	@ConfigItem(
			keyName = "useShift",
			name = "Shift right-click to show",
			description = "Require Shift to be held to show the 'Place Order' menu option."
	)
	default boolean useShift() {
		return true;
	}

	@ConfigItem(
			keyName = "showGuide",
			name = "Show trade guide in overlay",
			description = "Whether to show the guide under the order creation overlay or not."
	)
	default boolean showGuide() {
		return true;
	}
}