package com.osrsclanfinder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementEvaluationResult
{
	private ClanRequirement requirement;
	private String requirementValueDisplay;
	private String playerCurrentValueDisplay;
	private EligibilityStatus status;
	private String remainingProgressDisplay;
	private boolean manualConfirmationRequired;
}
