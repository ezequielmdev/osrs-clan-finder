package com.osrsclanfinder.model;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VerificationStatus
{
	OFFICIALLY_VERIFIED("Officially Verified", new Color(46, 160, 67)),
	COMMUNITY_CONFIRMED("Community Confirmed", new Color(56, 139, 253)),
	UNVERIFIED("Unverified", new Color(140, 140, 140)),
	OUTDATED("Outdated", new Color(210, 153, 34));

	private final String displayName;
	private final Color badgeColor;

	@Override
	public String toString()
	{
		return displayName;
	}
}
