package com.osrsclanfinder;

import com.osrsclanfinder.model.AccountTypeClassification;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("osrsclanfinder")
public interface ClanFinderConfig extends Config
{
	@ConfigSection(
		name = "Player & Eligibility Settings",
		description = "Settings for checking your OSRS account eligibility against published clan requirements.",
		position = 0
	)
	String playerSection = "playerSection";

	@ConfigSection(
		name = "Verification Settings",
		description = "Settings for periodic Wise Old Man and TempleOSRS verification.",
		position = 1
	)
	String databaseSection = "databaseSection";

	@ConfigItem(
		keyName = "manualPlayerName",
		name = "Fallback RSN for Eligibility Check",
		description = "Optional RuneScape Name to look up on the official OSRS Hiscores if you are not currently logged in.",
		section = playerSection,
		position = 0
	)
	default String manualPlayerName()
	{
		return "";
	}

	@ConfigItem(
		keyName = "overrideAccountType",
		name = "Account Type Override",
		description = "Used when checking eligibility while logged out or if manual account type selection is preferred.",
		section = playerSection,
		position = 1
	)
	default AccountTypeClassification overrideAccountType()
	{
		return AccountTypeClassification.UNCONFIRMED;
	}

	@ConfigItem(
		keyName = "fetchHiscoresForBosses",
		name = "Fetch Hiscores for Raid & Boss KC",
		description = "Query the official OSRS Hiscores and Wise Old Man API to verify Raid and Boss kill count requirements.",
		section = playerSection,
		position = 2
	)
	default boolean fetchHiscoresForBosses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enablePeriodicVerification",
		name = "Periodic Wise Old Man Verification",
		description = "Periodically verify clan metadata and recruitment status against the official Wise Old Man API.",
		section = databaseSection,
		position = 0
	)
	default boolean enablePeriodicVerification()
	{
		return true;
	}

	@Range(min = 15, max = 1440)
	@ConfigItem(
		keyName = "verificationIntervalMinutes",
		name = "Verification Interval (Minutes)",
		description = "How often (in minutes) the plugin checks remote and Wise Old Man endpoints for updated recruitment information.",
		section = databaseSection,
		position = 2
	)
	default int verificationIntervalMinutes()
	{
		return 60;
	}
}
