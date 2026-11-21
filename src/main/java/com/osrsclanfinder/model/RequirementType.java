package com.osrsclanfinder.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequirementType
{
	MIN_COMBAT_LEVEL("Minimum Combat Level"),
	MAX_COMBAT_LEVEL("Maximum Combat Level"),
	MIN_TOTAL_LEVEL("Minimum Total Level"),
	MIN_HITPOINTS_LEVEL("Minimum Hitpoints Level"),
	MAX_HITPOINTS_LEVEL("Maximum Hitpoints Level"),
	MIN_STRENGTH_LEVEL("Minimum Strength Level"),
	MIN_PRAYER_LEVEL("Minimum Prayer Level"),
	MIN_RANGED_LEVEL("Minimum Ranged Level"),
	MIN_MAGIC_LEVEL("Minimum Magic Level"),
	MIN_DEFENCE_LEVEL("Minimum Defence Level"),
	MAX_DEFENCE_LEVEL("Maximum Defence Level"),
	MIN_SLAYER_LEVEL("Minimum Slayer Level"),
	MIN_EXPERIENCE("Minimum Experience Requirement"),
	REQUIRED_BOSS_KILLS("Required Boss Kills"),
	REQUIRED_RAID_COMPLETIONS("Required Raid Completions"),
	REQUIRED_ACHIEVEMENTS("Required Achievements"),
	REQUIRED_PVM_EXPERIENCE("Required PvM Experience"),
	ACCOUNT_RESTRICTION("Ironman or Main Account Restriction"),
	ADDITIONAL_APPLICATION_REQUIREMENT("Additional Application Requirement"),
	NO_NUMERIC_REQUIREMENT("Open Recruitment Policy");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
