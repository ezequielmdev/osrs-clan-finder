package com.osrsclanfinder.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RankingMetric
{
	AVERAGE_EHB("Average EHB", true),
	AVERAGE_EHP("Average EHP", true),
	TOTAL_EHB("Total EHB", false),
	TOTAL_EHP("Total EHP", false),
	MEMBER_COUNT("Members Count", false),
	TOTAL_XP("Total XP", false);

	private final String displayName;
	private final boolean averageMetric;

	@Override
	public String toString()
	{
		return displayName;
	}
}
