package com.osrsclanfinder.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClanSortOrder
{
	RANK_BY_AVERAGE_EHB("Sort: Average EHB (High to Low)"),
	RANK_BY_AVERAGE_EHP("Sort: Average EHP (High to Low)"),
	RANK_BY_MEMBERS("Sort: Members Count (High to Low)"),
	ALPHABETICAL("Sort: Alphabetical (A - Z)");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
