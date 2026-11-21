package com.osrsclanfinder.model;

import java.util.EnumSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClanFilterCriteria
{
	@Builder.Default
	private String searchQuery = "";

	// Directory Category Tab / Filter (null = All Categories)
	private ClanDirectoryCategory selectedDirectoryCategory;

	private boolean ironmanOnly;
	private boolean mainAccountsOnly;
	private boolean mixedClans;

	private boolean internationalClans;
	private boolean brazilianClans;

	private boolean pvm;
	private boolean raids;
	private boolean endgamePvm;
	private boolean skilling;
	private boolean social;

	private boolean openRecruitmentOnly;
	private boolean onlyOfficiallyVerified;

	@Builder.Default
	private ClanSortOrder sortOrder = ClanSortOrder.RANK_BY_AVERAGE_EHB;

	public boolean hasAccountTypeFilters()
	{
		return ironmanOnly || mainAccountsOnly || mixedClans;
	}

	public boolean hasRegionFilters()
	{
		return internationalClans || brazilianClans;
	}

	public Set<ClanSpecialization> getSelectedSpecializations()
	{
		Set<ClanSpecialization> selected = EnumSet.noneOf(ClanSpecialization.class);
		if (pvm)
		{
			selected.add(ClanSpecialization.PVM);
		}
		if (raids)
		{
			selected.add(ClanSpecialization.RAIDS);
		}
		if (endgamePvm)
		{
			selected.add(ClanSpecialization.ENDGAME_PVM);
		}
		if (skilling)
		{
			selected.add(ClanSpecialization.SKILLING);
		}
		if (social)
		{
			selected.add(ClanSpecialization.SOCIAL);
		}
		return selected;
	}
}
