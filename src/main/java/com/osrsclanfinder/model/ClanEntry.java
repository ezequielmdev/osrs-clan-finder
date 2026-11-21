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
public class ClanEntry
{
	private String id;
	private String clanName;
	private String inGameClanChat;
	private Integer homeWorld;
	private Integer verifiedMemberCount;
	private AccountTypeClassification accountType;
	private String countryOrRegion;
	private boolean brazilianClan;
	private boolean internationalClan;

	// Directory categories (PvM, Ironman, Social, Mixed / Community)
	private List<ClanDirectoryCategory> directoryCategories;
	private ClanSpecialization mainSpecialization;
	private List<ClanSpecialization> specializations;
	private List<String> activities;

	private String shortDescription;
	private String description;

	// Visual Identity Assets (Banner, Logo, Unique Brand Colors)
	private String bannerUrl;
	private String logoUrl;
	private String primaryColorHex;
	private String secondaryColorHex;
	private String emblemTag;

	// Verified Statistics (Never mix averages with totals)
	private Double averageEhp;
	private Double averageEhb;
	private Double totalEhp;
	private Double totalEhb;
	private Long averageXp;
	private Long totalXp;
	private Integer previousEhbRank;
	private Integer previousEhpRank;
	private String historicalStatsSummary;

	// Recruitment Requirements & Status
	private RecruitmentAvailabilityStatus recruitmentAvailability;
	private RequirementDataAvailability requirementAvailability;
	private List<ClanRequirement> officialRecruitmentRequirements;
	private String recruitmentStatus;

	// Official Links
	private String officialWebsite;
	private String recruitmentPageUrl;
	private String discordInvitation;
	private Integer wiseOldManGroupId;
	private String wiseOldManGroupUrl;
	private Integer templeOsrsGroupId;
	private String templeOsrsGroupUrl;
	private String osrsClansUrl;
	private List<String> requirementSourceUrls;

	private VerificationStatus overallVerificationStatus;
	private String lastVerificationDate;

	public double getEffectiveAverageEhb()
	{
		if (averageEhb != null && averageEhb > 0)
		{
			return averageEhb;
		}
		if (totalEhb != null && verifiedMemberCount != null && verifiedMemberCount > 0)
		{
			return totalEhb / verifiedMemberCount;
		}
		return 0.0;
	}

	public double getEffectiveAverageEhp()
	{
		if (averageEhp != null && averageEhp > 0)
		{
			return averageEhp;
		}
		if (totalEhp != null && verifiedMemberCount != null && verifiedMemberCount > 0)
		{
			return totalEhp / verifiedMemberCount;
		}
		return 0.0;
	}

	public double getEffectiveTotalEhb()
	{
		if (totalEhb != null && totalEhb > 0)
		{
			return totalEhb;
		}
		if (averageEhb != null && verifiedMemberCount != null && verifiedMemberCount > 0)
		{
			return averageEhb * verifiedMemberCount;
		}
		return 0.0;
	}

	public double getEffectiveTotalEhp()
	{
		if (totalEhp != null && totalEhp > 0)
		{
			return totalEhp;
		}
		if (averageEhp != null && verifiedMemberCount != null && verifiedMemberCount > 0)
		{
			return averageEhp * verifiedMemberCount;
		}
		return 0.0;
	}

	public long getEffectiveTotalXp()
	{
		if (totalXp != null && totalXp > 0)
		{
			return totalXp;
		}
		if (averageXp != null && verifiedMemberCount != null && verifiedMemberCount > 0)
		{
			return averageXp * verifiedMemberCount;
		}
		return 0L;
	}

	public long getEffectiveAverageXp()
	{
		if (averageXp != null && averageXp > 0)
		{
			return averageXp;
		}
		if (totalXp != null && verifiedMemberCount != null && verifiedMemberCount > 0)
		{
			return totalXp / verifiedMemberCount;
		}
		return 0L;
	}
}
