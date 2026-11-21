package com.osrsclanfinder.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanEligibilityReport
{
	private ClanEntry clan;
	private PlayerStatsSnapshot playerStats;
	private List<RequirementEvaluationResult> evaluations;
	private int metCount;
	private int notMetCount;
	private int notApplicableCount;
	private int unableToVerifyCount;
	private String summaryDisclaimer;
}
