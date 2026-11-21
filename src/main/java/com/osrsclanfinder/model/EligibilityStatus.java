package com.osrsclanfinder.model;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EligibilityStatus
{
	REQUIREMENT_MET("Requirement Met", new Color(46, 160, 67)),
	REQUIREMENT_NOT_MET("Requirement Not Met", new Color(218, 54, 51)),
	NOT_APPLICABLE("Not Applicable", new Color(130, 130, 130)),
	UNABLE_TO_VERIFY("Unable to Verify", new Color(210, 153, 34));

	private final String displayName;
	private final Color statusColor;

	@Override
	public String toString()
	{
		return displayName;
	}
}
