package com.osrsclanfinder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanRequirement
{
	private RequirementType type;
	private String exactRequirement;
	private Long targetValue;
	private AccountTypeClassification requiredAccountType;
	private String bossOrRaidMetricKey;
	private String sourceUrl;
	private VerificationStatus verificationStatus;
	private String lastVerificationDate;
}
