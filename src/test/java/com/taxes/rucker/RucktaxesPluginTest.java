package com.taxes.rucker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RucktaxesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RucktaxesPlugin.class);
		RuneLite.main(args);
	}
}