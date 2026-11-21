package com.osrsclanfinder.model;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountTypeClassification
{
	IRONMAN_ONLY("Ironman Only", new Color(180, 70, 70)),
	MAIN_ACCOUNTS_ONLY("Main Accounts Only", new Color(65, 130, 195)),
	MIXED("Mixed (Ironman and Main Accounts)", new Color(75, 160, 95)),
	UNCONFIRMED("Unconfirmed", new Color(130, 130, 130));

	private final String displayName;
	private final Color badgeColor;

	@Override
	public String toString()
	{
		return displayName;
	}
}
