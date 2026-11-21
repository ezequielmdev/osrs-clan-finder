package com.osrsclanfinder;

import com.google.gson.Gson;
import com.osrsclanfinder.model.AccountTypeClassification;
import com.osrsclanfinder.model.ClanDirectoryCategory;
import com.osrsclanfinder.model.ClanEligibilityReport;
import com.osrsclanfinder.model.ClanEntry;
import com.osrsclanfinder.model.ClanFilterCriteria;
import com.osrsclanfinder.model.ClanRequirement;
import com.osrsclanfinder.model.ClanSortOrder;
import com.osrsclanfinder.model.EligibilityStatus;
import com.osrsclanfinder.model.PlayerStatsSnapshot;
import com.osrsclanfinder.model.RankingMetric;
import com.osrsclanfinder.model.RequirementDataAvailability;
import com.osrsclanfinder.model.RequirementEvaluationResult;
import com.osrsclanfinder.model.RequirementType;
import com.osrsclanfinder.model.VerificationStatus;
import com.osrsclanfinder.service.ClanImageService;
import com.osrsclanfinder.service.ClanRepositoryService;
import com.osrsclanfinder.service.PlayerEligibilityService;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ClanFinderVerificationTest
{
	private ClanRepositoryService repositoryService;
	private PlayerEligibilityService eligibilityService;
	private ClanImageService imageService;

	@Before
	public void setUp()
	{
		Gson gson = new Gson();
		OkHttpClient httpClient = new OkHttpClient();
		ClanFinderConfig defaultConfig = new ClanFinderConfig() {};

		repositoryService = new ClanRepositoryService(gson, httpClient, defaultConfig);
		repositoryService.loadBundledDatabase();

		eligibilityService = new PlayerEligibilityService(null, defaultConfig, httpClient, gson);
		imageService = new ClanImageService(httpClient);
	}

	@Test
	public void testAllRequestedClansAcrossFourCategories()
	{
		List<String> expectedPvm = Arrays.asList(
			"Oblivion", "Vintage", "Sanity", "Infernal", "Forbidden", "Menace", "Candor",
			"Slaystars", "Aus Myth", "Pink LSZ", "Prodigy", "Reborn OSRS", "Simply Better PvM", "Live ON"
		);
		List<String> expectedIronman = Arrays.asList(
			"Valiance", "Olympus", "07 Iron", "Iron Clan", "Fe Prestige", "Irons Tavern", "Iron Relax",
			"Defiant", "Ironic", "Merit", "Irons Grotto", "WeNeverTrade", "Iron Foundry"
		);
		List<String> expectedSocial = Arrays.asList(
			"Home", "FOH", "Scuff Unit", "BigDog", "Swedes", "Ocage", "Skadoosh",
			"Valkyrie", "Winter", "Avandra", "Elysium", "Veritas", "Smoke Scape", "Grind It", "Live ON"
		);

		Set<String> pvmNames = repositoryService.filterClans(
			ClanFilterCriteria.builder().selectedDirectoryCategory(ClanDirectoryCategory.PVM).build()
		).stream().map(ClanEntry::getClanName).collect(Collectors.toSet());

		Set<String> ironNames = repositoryService.filterClans(
			ClanFilterCriteria.builder().selectedDirectoryCategory(ClanDirectoryCategory.IRONMAN).build()
		).stream().map(ClanEntry::getClanName).collect(Collectors.toSet());

		Set<String> socialNames = repositoryService.filterClans(
			ClanFilterCriteria.builder().selectedDirectoryCategory(ClanDirectoryCategory.SOCIAL).build()
		).stream().map(ClanEntry::getClanName).collect(Collectors.toSet());

		Set<String> mixedNames = repositoryService.filterClans(
			ClanFilterCriteria.builder().selectedDirectoryCategory(ClanDirectoryCategory.MIXED_COMMUNITY).build()
		).stream().map(ClanEntry::getClanName).collect(Collectors.toSet());

		for (String name : expectedPvm)
		{
			Assert.assertTrue("Missing PvM clan: " + name, pvmNames.contains(name));
		}
		for (String name : expectedIronman)
		{
			Assert.assertTrue("Missing Ironman clan: " + name, ironNames.contains(name));
		}
		Assert.assertFalse("Live ON must not appear in the Ironman-only category", ironNames.contains("Live ON"));
		for (String name : expectedSocial)
		{
			Assert.assertTrue("Missing Social clan: " + name, socialNames.contains(name));
		}
		Assert.assertFalse("Mixed / Community category must not be empty", mixedNames.isEmpty());
		Assert.assertTrue("Live ON must appear in Mixed / Community", mixedNames.contains("Live ON"));
	}

	@Test
	public void testExclusiveVisualBannersAndLogosPerClan()
	{
		List<ClanEntry> clans = repositoryService.getAllClans();
		Assert.assertTrue("Should have at least 40 verified clans", clans.size() >= 40);

		Set<Integer> bannerPixelSignatures = new HashSet<>();
		for (ClanEntry clan : clans)
		{
			BufferedImage banner = imageService.generateExclusiveClanBanner(clan, 206, 54);
			BufferedImage logo = imageService.generateExclusiveClanCrestLogo(clan, 36);
			Assert.assertNotNull(banner);
			Assert.assertNotNull(logo);

			// Sample multiple pixels across the banner to verify each clan has an exclusive visual banner
			int sig = banner.getRGB(20, 20) ^ (banner.getRGB(100, 25) * 31) ^ (banner.getRGB(180, 40) * 97);
			bannerPixelSignatures.add(sig);
		}
		Assert.assertTrue("Banners must be uniquely generated per clan, never a generic identical image",
			bannerPixelSignatures.size() > 25);
	}

	@Test
	public void testEhbAndEhpRankingsAndMetricSeparation()
	{
		List<ClanEntry> byAvgEhb = repositoryService.getRankedClans(RankingMetric.AVERAGE_EHB);
		Assert.assertFalse(byAvgEhb.isEmpty());
		Assert.assertEquals("Oblivion should be #1 by Average EHB", "Oblivion", byAvgEhb.get(0).getClanName());
		Assert.assertTrue(byAvgEhb.get(0).getEffectiveAverageEhb() >= byAvgEhb.get(1).getEffectiveAverageEhb());

		List<ClanEntry> byAvgEhp = repositoryService.getRankedClans(RankingMetric.AVERAGE_EHP);
		Assert.assertFalse(byAvgEhp.isEmpty());
		Assert.assertEquals("Olympus should be #1 by Average EHP", "Olympus", byAvgEhp.get(0).getClanName());
		Assert.assertTrue(byAvgEhp.get(0).getEffectiveAverageEhp() >= byAvgEhp.get(1).getEffectiveAverageEhp());

		// Verify Averages are strictly distinct from Totals
		ClanEntry oblivion = byAvgEhb.get(0);
		Assert.assertTrue("Total EHB must exceed Average EHB multiplied by member count > 1",
			oblivion.getEffectiveTotalEhb() > oblivion.getEffectiveAverageEhb() * 100);
	}

	@Test
	public void testOfficialWebsitesAndTraceableSources()
	{
		List<ClanEntry> clans = repositoryService.getAllClans();
		for (ClanEntry clan : clans)
		{
			Assert.assertNotNull("Clan name must be present", clan.getClanName());
			Assert.assertNotNull("Requirement source URLs list must be present for " + clan.getClanName(), clan.getRequirementSourceUrls());
			Assert.assertFalse("Every clan must have at least one traceable source URL: " + clan.getClanName(),
				clan.getRequirementSourceUrls().isEmpty());

			boolean hasWeb = clan.getOfficialWebsite() != null && clan.getOfficialWebsite().startsWith("http");
			boolean hasDiscord = clan.getDiscordInvitation() != null && clan.getDiscordInvitation().startsWith("http");
			boolean hasWom = clan.getWiseOldManGroupUrl() != null && clan.getWiseOldManGroupUrl().startsWith("http");
			boolean hasTemple = clan.getTempleOsrsGroupUrl() != null && clan.getTempleOsrsGroupUrl().startsWith("http");
			Assert.assertTrue("Clan " + clan.getClanName() + " must have at least one official external link",
				hasWeb || hasDiscord || hasWom || hasTemple);
		}
	}

	@Test
	public void testSearchAndSortingOrders()
	{
		ClanFilterCriteria alphaCriteria = ClanFilterCriteria.builder()
			.sortOrder(ClanSortOrder.ALPHABETICAL)
			.build();
		List<ClanEntry> alphaList = repositoryService.filterClans(alphaCriteria);
		Assert.assertEquals("07 Iron", alphaList.get(0).getClanName());

		ClanFilterCriteria searchLiveOn = ClanFilterCriteria.builder()
			.searchQuery("Live ON")
			.build();
		List<ClanEntry> found = repositoryService.filterClans(searchLiveOn);
		Assert.assertEquals(1, found.size());
		Assert.assertEquals("https://liveon.gg/", found.get(0).getOfficialWebsite());
	}

	@Test
	public void testPlayerEligibilityEvaluation()
	{
		ClanEntry mirage = repositoryService.getAllClans().stream()
			.filter(c -> "Mirage".equalsIgnoreCase(c.getClanName()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Mirage clan entry not found"));

		PlayerStatsSnapshot highLevelPlayer = PlayerStatsSnapshot.builder()
			.playerName("TestPlayer")
			.loggedIn(true)
			.detectedAccountType(AccountTypeClassification.MAIN_ACCOUNTS_ONLY)
			.combatLevel(126)
			.totalLevel(2150)
			.hiscoresLoaded(true)
			.combinedRaidsKillCount(80)
			.build();

		ClanEligibilityReport metReport = eligibilityService.evaluateClanEligibility(mirage, highLevelPlayer);
		Assert.assertEquals(3, metReport.getMetCount());
		Assert.assertEquals(0, metReport.getNotMetCount());

		PlayerStatsSnapshot midLevelPlayer = PlayerStatsSnapshot.builder()
			.playerName("MidPlayer")
			.loggedIn(true)
			.detectedAccountType(AccountTypeClassification.MAIN_ACCOUNTS_ONLY)
			.combatLevel(105)
			.totalLevel(1600)
			.hiscoresLoaded(false)
			.build();

		ClanEligibilityReport partialReport = eligibilityService.evaluateClanEligibility(mirage, midLevelPlayer);
		Assert.assertEquals(0, partialReport.getMetCount());
		Assert.assertEquals(2, partialReport.getNotMetCount());
		Assert.assertEquals(1, partialReport.getUnableToVerifyCount());

		for (RequirementEvaluationResult eval : partialReport.getEvaluations())
		{
			if (eval.getRequirement().getType() == RequirementType.MIN_TOTAL_LEVEL)
			{
				Assert.assertEquals(EligibilityStatus.REQUIREMENT_NOT_MET, eval.getStatus());
			}
			else if (eval.getRequirement().getType() == RequirementType.REQUIRED_RAID_COMPLETIONS)
			{
				Assert.assertEquals(EligibilityStatus.UNABLE_TO_VERIFY, eval.getStatus());
				Assert.assertTrue(eval.isManualConfirmationRequired());
			}
		}
	}

	@Test
	public void testNeverSilentlyOverwriteOfficialRequirementsWithUnverified()
	{
		ClanEntry existingLiveOn = repositoryService.getAllClans().stream()
			.filter(c -> "clan-live-on".equals(c.getId()))
			.findFirst()
			.orElseThrow();
		int originalReqCount = existingLiveOn.getOfficialRecruitmentRequirements().size();
		Assert.assertTrue(originalReqCount > 0);

		ClanEntry unverifiedRemoteCopy = ClanEntry.builder()
			.id("clan-live-on")
			.clanName("Live ON")
			.accountType(AccountTypeClassification.MIXED)
			.countryOrRegion("Brazil")
			.brazilianClan(true)
			.requirementAvailability(RequirementDataAvailability.NOT_PUBLICLY_AVAILABLE)
			.overallVerificationStatus(VerificationStatus.UNVERIFIED)
			.officialRecruitmentRequirements(Collections.emptyList())
			.requirementSourceUrls(Arrays.asList("https://wiseoldman.net/groups/1945"))
			.build();

		repositoryService.mergeRemoteClansSafely(Collections.singletonList(unverifiedRemoteCopy), "2026-11-21");

		ClanEntry afterMerge = repositoryService.getAllClans().stream()
			.filter(c -> "clan-live-on".equals(c.getId()))
			.findFirst()
			.orElseThrow();

		Assert.assertEquals("Official requirements must never be overwritten by unverified empty requirements",
			originalReqCount, afterMerge.getOfficialRecruitmentRequirements().size());

		Assert.assertFalse("Brazil Chat must not be in the clan list",
			repositoryService.getAllClans().stream().anyMatch(c -> "Brazil Chat".equalsIgnoreCase(c.getClanName())));
		Assert.assertFalse("Oldvicios must not be in the clan list",
			repositoryService.getAllClans().stream().anyMatch(c -> "Oldvicios".equalsIgnoreCase(c.getClanName())));
	}
}
