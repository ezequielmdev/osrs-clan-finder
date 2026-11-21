package com.osrsclanfinder.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClanDirectoryCategory
{
	PVM("PvM"),
	IRONMAN("Ironman"),
	SOCIAL("Social"),
	MIXED_COMMUNITY("Mixed / Community");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
