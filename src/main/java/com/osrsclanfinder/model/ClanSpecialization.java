package com.osrsclanfinder.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClanSpecialization
{
	PVM("PvM"),
	RAIDS("Raids"),
	ENDGAME_PVM("Endgame PvM"),
	SKILLING("Skilling"),
	SOCIAL("Social");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
