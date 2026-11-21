package com.osrsclanfinder.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequirementDataAvailability
{
	AVAILABLE("Available"),
	NOT_PUBLICLY_AVAILABLE("Unconfirmed requirements — check official Discord."),
	UNCLEAR("Unconfirmed requirements — check official Discord.");

	private final String displayMessage;
}
