package com.osrsclanfinder.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStatsSnapshot
{
	private String playerName;
	private boolean loggedIn;
	private AccountTypeClassification detectedAccountType;
	private int combatLevel;
	private int totalLevel;
	private long totalExperience;
	private int hitpointsLevel;
	private int strengthLevel;
	private int prayerLevel;
	private int rangedLevel;
	private int magicLevel;
	private int defenceLevel;
	private int slayerLevel;
	private int attackLevel;
	private Integer questPoints;

	private boolean hiscoresLoaded;
	private int combinedRaidsKillCount;
	private Double estimatedEhb;
	@Builder.Default
	private Map<String, Integer> bossKillCounts = new HashMap<>();

	public Map<String, Integer> getBossKillCounts()
	{
		return bossKillCounts == null ? Collections.emptyMap() : bossKillCounts;
	}
}
