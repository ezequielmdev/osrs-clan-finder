package com.osrsclanfinder.model;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RecruitmentAvailabilityStatus
{
	OPEN("Open Recruitment", new Color(46, 160, 67)),
	CLOSED("Closed / Waitlist", new Color(218, 54, 51)),
	UNKNOWN("Status Unknown (Check Discord)", new Color(210, 153, 34));

	private final String displayName;
	private final Color badgeColor;

	@Override
	public String toString()
	{
		return displayName;
	}
}
