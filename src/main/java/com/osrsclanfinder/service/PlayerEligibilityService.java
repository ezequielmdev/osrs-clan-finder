package com.osrsclanfinder.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osrsclanfinder.ClanFinderConfig;
import com.osrsclanfinder.model.AccountTypeClassification;
import com.osrsclanfinder.model.ClanEligibilityReport;
import com.osrsclanfinder.model.ClanEntry;
import com.osrsclanfinder.model.ClanRequirement;
import com.osrsclanfinder.model.EligibilityStatus;
import com.osrsclanfinder.model.PlayerStatsSnapshot;
import com.osrsclanfinder.model.RequirementEvaluationResult;
import com.osrsclanfinder.model.RequirementType;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class PlayerEligibilityService
{
	public static final String ELIGIBILITY_DISCLAIMER =
		"Meeting a clan's numerical requirements does not guarantee or constitute official acceptance into the clan. "
		+ "All applicants must still follow the clan's official application, trial, and leadership verification process.";

	private static final String WOM_PLAYER_ENDPOINT = "https://api.wiseoldman.net/v2/players/";
	private static final String USER_AGENT = "OSRS-Clan-Finder-RuneLite-Plugin/1.0";

	private static final String[] RAID_KEYS = {
		"chambers_of_xeric",
		"chambers_of_xeric_challenge_mode",
		"theatre_of_blood",
		"theatre_of_blood_hard_mode",
		"tombs_of_amascut",
		"tombs_of_amascut_expert"
	};

	private final Client client;
	private final ClanFinderConfig config;
	private final OkHttpClient okHttpClient;
	private final Gson gson;

	private volatile PlayerStatsSnapshot cachedSnapshot;

	@Inject
	public PlayerEligibilityService(Client client, ClanFinderConfig config, OkHttpClient okHttpClient, Gson gson)
	{
		this.client = client;
		this.config = config;
		this.okHttpClient = okHttpClient;
		this.gson = gson;
	}

	/**
	 * Reads current in-game stats when logged into OSRS, or falls back to cached/configured player stats.
	 */
	public PlayerStatsSnapshot captureInGamePlayerStats()
	{
		if (client != null && client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null)
		{
			Player local = client.getLocalPlayer();
			String name = local.getName() != null ? local.getName() : "Logged-In Player";
			int attack = client.getRealSkillLevel(Skill.ATTACK);
			int strength = client.getRealSkillLevel(Skill.STRENGTH);
			int defence = client.getRealSkillLevel(Skill.DEFENCE);
			int hitpoints = client.getRealSkillLevel(Skill.HITPOINTS);
			int prayer = client.getRealSkillLevel(Skill.PRAYER);
			int ranged = client.getRealSkillLevel(Skill.RANGED);
			int magic = client.getRealSkillLevel(Skill.MAGIC);
			int slayer = client.getRealSkillLevel(Skill.SLAYER);
			int totalLevel = client.getTotalLevel();
			long totalXp = client.getOverallExperience();
			int combatLevel = local.getCombatLevel() > 0
				? local.getCombatLevel()
				: calculateCombatLevel(attack, strength, defence, hitpoints, prayer, ranged, magic);

			AccountTypeClassification detectedType = detectAccountTypeFromVarbit();
			Integer inGameQp = null;
			try
			{
				int qp = client.getVarpValue(VarPlayer.QUEST_POINTS);
				if (qp >= 0)
				{
					inGameQp = qp;
				}
			}
			catch (Exception e)
			{
				log.debug("Could not read QUEST_POINTS varp", e);
			}

			PlayerStatsSnapshot.PlayerStatsSnapshotBuilder builder = PlayerStatsSnapshot.builder()
				.playerName(name)
				.loggedIn(true)
				.detectedAccountType(detectedType)
				.combatLevel(combatLevel)
				.totalLevel(totalLevel)
				.totalExperience(totalXp)
				.hitpointsLevel(hitpoints)
				.strengthLevel(strength)
				.prayerLevel(prayer)
				.rangedLevel(ranged)
				.magicLevel(magic)
				.defenceLevel(defence)
				.slayerLevel(slayer)
				.attackLevel(attack)
				.questPoints(inGameQp);

			if (cachedSnapshot != null && name.equalsIgnoreCase(cachedSnapshot.getPlayerName()) && cachedSnapshot.isHiscoresLoaded())
			{
				builder.hiscoresLoaded(true)
					.combinedRaidsKillCount(cachedSnapshot.getCombinedRaidsKillCount())
					.estimatedEhb(cachedSnapshot.getEstimatedEhb())
					.bossKillCounts(cachedSnapshot.getBossKillCounts());
			}

			cachedSnapshot = builder.build();
			return cachedSnapshot;
		}

		if (cachedSnapshot != null)
		{
			return cachedSnapshot;
		}

		String fallbackName = config.manualPlayerName() != null ? config.manualPlayerName().trim() : "";
		return PlayerStatsSnapshot.builder()
			.playerName(fallbackName.isEmpty() ? "Not Logged In" : fallbackName)
			.loggedIn(false)
			.detectedAccountType(config.overrideAccountType())
			.hiscoresLoaded(false)
			.build();
	}

	private AccountTypeClassification detectAccountTypeFromVarbit()
	{
		try
		{
			int accountTypeVarbit = client.getVarbitValue(Varbits.ACCOUNT_TYPE);
			// OSRS Varbit 1777: 0 = Main/Normal, 1 = Ironman, 2 = Ultimate Ironman, 3 = Hardcore Ironman, 4+ = Group Ironman
			if (accountTypeVarbit == 0)
			{
				return AccountTypeClassification.MAIN_ACCOUNTS_ONLY;
			}
			else if (accountTypeVarbit >= 1)
			{
				return AccountTypeClassification.IRONMAN_ONLY;
			}
		}
		catch (Exception e)
		{
			log.debug("Could not read ACCOUNT_TYPE varbit", e);
		}
		return config.overrideAccountType();
	}

	public static int calculateCombatLevel(int attack, int strength, int defence, int hitpoints, int prayer, int ranged, int magic)
	{
		double base = 0.25 * (defence + hitpoints + Math.floor(prayer / 2.0));
		double melee = 0.325 * (attack + strength);
		double range = 0.325 * (Math.floor(ranged / 2.0) + ranged);
		double mage = 0.325 * (Math.floor(magic / 2.0) + magic);
		return (int) Math.floor(base + Math.max(melee, Math.max(range, mage)));
	}

	/**
	 * Fetches player stats, Raid KC, Boss KC, and EHB from Wise Old Man / Hiscores asynchronously
	 * when the user clicks "Check My Eligibility" or searches an RSN.
	 */
	public void fetchPlayerHiscoresProfile(String rsn, Consumer<PlayerStatsSnapshot> callback)
	{
		String targetRsn = (rsn != null && !rsn.trim().isEmpty())
			? rsn.trim()
			: (config.manualPlayerName() != null ? config.manualPlayerName().trim() : "");

		if (targetRsn.isEmpty() || "Not Logged In".equalsIgnoreCase(targetRsn))
		{
			if (callback != null)
			{
				callback.accept(captureInGamePlayerStats());
			}
			return;
		}

		String encoded = URLEncoder.encode(targetRsn, StandardCharsets.UTF_8).replace("+", "%20");
		String url = WOM_PLAYER_ENDPOINT + encoded;

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.get()
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Could not fetch Wise Old Man player profile for {}", targetRsn, e);
				if (callback != null)
				{
					callback.accept(captureInGamePlayerStats());
				}
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						if (callback != null)
						{
							callback.accept(captureInGamePlayerStats());
						}
						return;
					}

					JsonObject playerJson = gson.fromJson(body.string(), JsonObject.class);
					PlayerStatsSnapshot parsed = parseWiseOldManPlayer(targetRsn, playerJson);
					if (parsed != null)
					{
						cachedSnapshot = parsed;
						if (callback != null)
						{
							callback.accept(parsed);
						}
						return;
					}
				}
				catch (Exception ex)
				{
					log.debug("Failed to parse Wise Old Man player response for {}", targetRsn, ex);
				}

				if (callback != null)
				{
					callback.accept(captureInGamePlayerStats());
				}
			}
		});
	}

	private PlayerStatsSnapshot parseWiseOldManPlayer(String rsn, JsonObject json)
	{
		if (json == null)
		{
			return null;
		}

		String displayName = json.has("displayName") && !json.get("displayName").isJsonNull()
			? json.get("displayName").getAsString()
			: rsn;

		int combatLevel = json.has("combatLevel") && !json.get("combatLevel").isJsonNull()
			? json.get("combatLevel").getAsInt()
			: 0;

		Double ehb = json.has("ehb") && !json.get("ehb").isJsonNull()
			? json.get("ehb").getAsDouble()
			: null;

		AccountTypeClassification accountType = config.overrideAccountType();
		if (json.has("type") && !json.get("type").isJsonNull())
		{
			String womType = json.get("type").getAsString().toLowerCase(Locale.ROOT);
			if (womType.contains("iron") || womType.contains("hardcore") || womType.contains("ultimate"))
			{
				accountType = AccountTypeClassification.IRONMAN_ONLY;
			}
			else if ("regular".equals(womType))
			{
				accountType = AccountTypeClassification.MAIN_ACCOUNTS_ONLY;
			}
		}

		int totalLevel = 0;
		long totalXp = 0L;
		int attack = 1;
		int strength = 1;
		int defence = 1;
		int hitpoints = 10;
		int prayer = 1;
		int ranged = 1;
		int magic = 1;
		int slayer = 1;
		int combinedRaids = 0;
		Map<String, Integer> bosses = new HashMap<>();

		if (json.has("latestSnapshot") && json.get("latestSnapshot").isJsonObject())
		{
			JsonObject snapshot = json.getAsJsonObject("latestSnapshot");
			if (snapshot.has("data") && snapshot.get("data").isJsonObject())
			{
				JsonObject data = snapshot.getAsJsonObject("data");
				if (data.has("skills") && data.get("skills").isJsonObject())
				{
					JsonObject skills = data.getAsJsonObject("skills");
					totalLevel = getSkillLevel(skills, "overall", 0);
					totalXp = getSkillExperience(skills, "overall", 0L);
					attack = getSkillLevel(skills, "attack", 1);
					strength = getSkillLevel(skills, "strength", 1);
					defence = getSkillLevel(skills, "defence", 1);
					hitpoints = getSkillLevel(skills, "hitpoints", 10);
					prayer = getSkillLevel(skills, "prayer", 1);
					ranged = getSkillLevel(skills, "ranged", 1);
					magic = getSkillLevel(skills, "magic", 1);
					slayer = getSkillLevel(skills, "slayer", 1);
				}

				if (data.has("bosses") && data.get("bosses").isJsonObject())
				{
					JsonObject bossesObj = data.getAsJsonObject("bosses");
					for (Map.Entry<String, JsonElement> entry : bossesObj.entrySet())
					{
						if (entry.getValue().isJsonObject())
						{
							JsonObject bObj = entry.getValue().getAsJsonObject();
							if (bObj.has("kills") && !bObj.get("kills").isJsonNull())
							{
								int kills = Math.max(0, bObj.get("kills").getAsInt());
								bosses.put(entry.getKey(), kills);
							}
						}
					}
					for (String raidKey : RAID_KEYS)
					{
						combinedRaids += bosses.getOrDefault(raidKey, 0);
					}
				}
			}
		}

		if (combatLevel <= 0 && totalLevel > 0)
		{
			combatLevel = calculateCombatLevel(attack, strength, defence, hitpoints, prayer, ranged, magic);
		}

		return PlayerStatsSnapshot.builder()
			.playerName(displayName)
			.loggedIn(true)
			.detectedAccountType(accountType)
			.combatLevel(combatLevel)
			.totalLevel(totalLevel)
			.totalExperience(totalXp)
			.hitpointsLevel(hitpoints)
			.strengthLevel(strength)
			.prayerLevel(prayer)
			.rangedLevel(ranged)
			.magicLevel(magic)
			.defenceLevel(defence)
			.slayerLevel(slayer)
			.attackLevel(attack)
			.hiscoresLoaded(true)
			.combinedRaidsKillCount(combinedRaids)
			.estimatedEhb(ehb)
			.bossKillCounts(bosses)
			.build();
	}

	private int getSkillLevel(JsonObject skills, String key, int defaultVal)
	{
		if (skills.has(key) && skills.get(key).isJsonObject())
		{
			JsonObject s = skills.getAsJsonObject(key);
			if (s.has("level") && !s.get("level").isJsonNull())
			{
				return Math.max(defaultVal, s.get("level").getAsInt());
			}
		}
		return defaultVal;
	}

	private long getSkillExperience(JsonObject skills, String key, long defaultVal)
	{
		if (skills.has(key) && skills.get(key).isJsonObject())
		{
			JsonObject s = skills.getAsJsonObject(key);
			if (s.has("experience") && !s.get("experience").isJsonNull())
			{
				return Math.max(defaultVal, s.get("experience").getAsLong());
			}
		}
		return defaultVal;
	}

	/**
	 * Evaluates a player's statistics against every published recruitment requirement of a clan.
	 */
	public ClanEligibilityReport evaluateClanEligibility(ClanEntry clan, PlayerStatsSnapshot stats)
	{
		PlayerStatsSnapshot effectiveStats = stats != null ? stats : captureInGamePlayerStats();
		List<RequirementEvaluationResult> results = new ArrayList<>();

		int met = 0;
		int notMet = 0;
		int notApplicable = 0;
		int unableToVerify = 0;

		List<ClanRequirement> reqs = clan.getOfficialRecruitmentRequirements();
		if (reqs == null || reqs.isEmpty())
		{
			RequirementEvaluationResult unavailableEval = RequirementEvaluationResult.builder()
				.requirement(ClanRequirement.builder()
					.type(RequirementType.ADDITIONAL_APPLICATION_REQUIREMENT)
					.exactRequirement(clan.getRequirementAvailability() != null
						? clan.getRequirementAvailability().getDisplayMessage()
						: "Recruitment requirements not publicly available.")
					.sourceUrl(clan.getWiseOldManGroupUrl())
					.verificationStatus(clan.getOverallVerificationStatus())
					.lastVerificationDate(clan.getLastVerificationDate())
					.build())
				.requirementValueDisplay("Not Publicly Listed")
				.playerCurrentValueDisplay(effectiveStats.isLoggedIn() ? effectiveStats.getPlayerName() : "No Player Loaded")
				.status(EligibilityStatus.UNABLE_TO_VERIFY)
				.remainingProgressDisplay("Manual confirmation with clan leadership is required.")
				.manualConfirmationRequired(true)
				.build();
			results.add(unavailableEval);
			unableToVerify++;
		}
		else
		{
			for (ClanRequirement req : reqs)
			{
				RequirementEvaluationResult eval = evaluateSingleRequirement(req, effectiveStats);
				results.add(eval);
				switch (eval.getStatus())
				{
					case REQUIREMENT_MET:
						met++;
						break;
					case REQUIREMENT_NOT_MET:
						notMet++;
						break;
					case NOT_APPLICABLE:
						notApplicable++;
						break;
					case UNABLE_TO_VERIFY:
					default:
						unableToVerify++;
						break;
				}
			}
		}

		return ClanEligibilityReport.builder()
			.clan(clan)
			.playerStats(effectiveStats)
			.evaluations(results)
			.metCount(met)
			.notMetCount(notMet)
			.notApplicableCount(notApplicable)
			.unableToVerifyCount(unableToVerify)
			.summaryDisclaimer(ELIGIBILITY_DISCLAIMER)
			.build();
	}

	private RequirementEvaluationResult evaluateSingleRequirement(ClanRequirement req, PlayerStatsSnapshot stats)
	{
		RequirementType type = req.getType();
		if (type == null)
		{
			type = RequirementType.ADDITIONAL_APPLICATION_REQUIREMENT;
		}

		if (type == RequirementType.NO_NUMERIC_REQUIREMENT)
		{
			return RequirementEvaluationResult.builder()
				.requirement(req)
				.requirementValueDisplay("None (Open to all levels)")
				.playerCurrentValueDisplay(stats.isLoggedIn()
					? "Combat " + stats.getCombatLevel() + " / Total " + stats.getTotalLevel()
					: "Not Loaded")
				.status(EligibilityStatus.NOT_APPLICABLE)
				.remainingProgressDisplay("No minimum stat requirement to meet.")
				.manualConfirmationRequired(false)
				.build();
		}

		if (type == RequirementType.ACCOUNT_RESTRICTION)
		{
			return evaluateAccountRestriction(req, stats);
		}

		if (type == RequirementType.REQUIRED_ACHIEVEMENTS
			&& "QUEST_POINTS".equalsIgnoreCase(req.getBossOrRaidMetricKey())
			&& req.getTargetValue() != null)
		{
			if (stats.getQuestPoints() != null)
			{
				return evaluateMinimumNumeric(req, stats.getQuestPoints(), "Quest Points");
			}
			return RequirementEvaluationResult.builder()
				.requirement(req)
				.requirementValueDisplay(req.getTargetValue() + "+ Quest Points")
				.playerCurrentValueDisplay("Log into OSRS to auto-read Quest Points")
				.status(EligibilityStatus.UNABLE_TO_VERIFY)
				.remainingProgressDisplay("Quest Points cannot be read from public Hiscores alone. Log into OSRS or confirm manually on the clan application page.")
				.manualConfirmationRequired(true)
				.build();
		}

		if (type == RequirementType.ADDITIONAL_APPLICATION_REQUIREMENT
			|| type == RequirementType.REQUIRED_ACHIEVEMENTS)
		{
			return RequirementEvaluationResult.builder()
				.requirement(req)
				.requirementValueDisplay(req.getExactRequirement())
				.playerCurrentValueDisplay("Requires Manual Review")
				.status(EligibilityStatus.UNABLE_TO_VERIFY)
				.remainingProgressDisplay("Manual confirmation required via clan application or Discord.")
				.manualConfirmationRequired(true)
				.build();
		}

		if (!stats.isLoggedIn() || stats.getTotalLevel() <= 0)
		{
			return RequirementEvaluationResult.builder()
				.requirement(req)
				.requirementValueDisplay(formatTargetValue(req))
				.playerCurrentValueDisplay("Player stats not loaded")
				.status(EligibilityStatus.UNABLE_TO_VERIFY)
				.remainingProgressDisplay("Log into OSRS or enter your RSN to verify this requirement. Manual confirmation required.")
				.manualConfirmationRequired(true)
				.build();
		}

		switch (type)
		{
			case MIN_COMBAT_LEVEL:
				return evaluateMinimumNumeric(req, stats.getCombatLevel(), "Combat Level");
			case MAX_COMBAT_LEVEL:
				return evaluateMaximumNumeric(req, stats.getCombatLevel(), "Combat Level");
			case MIN_TOTAL_LEVEL:
				return evaluateMinimumNumeric(req, stats.getTotalLevel(), "Total Level");
			case MIN_HITPOINTS_LEVEL:
				return evaluateMinimumNumeric(req, stats.getHitpointsLevel(), "Hitpoints Level");
			case MAX_HITPOINTS_LEVEL:
				return evaluateMaximumNumeric(req, stats.getHitpointsLevel(), "Hitpoints Level");
			case MIN_STRENGTH_LEVEL:
				return evaluateMinimumNumeric(req, stats.getStrengthLevel(), "Strength Level");
			case MIN_PRAYER_LEVEL:
				return evaluateMinimumNumeric(req, stats.getPrayerLevel(), "Prayer Level");
			case MIN_RANGED_LEVEL:
				return evaluateMinimumNumeric(req, stats.getRangedLevel(), "Ranged Level");
			case MIN_MAGIC_LEVEL:
				return evaluateMinimumNumeric(req, stats.getMagicLevel(), "Magic Level");
			case MIN_DEFENCE_LEVEL:
				return evaluateMinimumNumeric(req, stats.getDefenceLevel(), "Defence Level");
			case MAX_DEFENCE_LEVEL:
				return evaluateMaximumNumeric(req, stats.getDefenceLevel(), "Defence Level");
			case MIN_SLAYER_LEVEL:
				return evaluateMinimumNumeric(req, stats.getSlayerLevel(), "Slayer Level");
			case MIN_EXPERIENCE:
				return evaluateMinimumNumeric(req, stats.getTotalExperience(), "XP");
			case REQUIRED_RAID_COMPLETIONS:
				return evaluateRaidRequirement(req, stats);
			case REQUIRED_BOSS_KILLS:
				return evaluateBossRequirement(req, stats);
			case REQUIRED_PVM_EXPERIENCE:
				return evaluateEhbRequirement(req, stats);
			default:
				return RequirementEvaluationResult.builder()
					.requirement(req)
					.requirementValueDisplay(req.getExactRequirement())
					.playerCurrentValueDisplay("Unavailable")
					.status(EligibilityStatus.UNABLE_TO_VERIFY)
					.remainingProgressDisplay("Manual confirmation required.")
					.manualConfirmationRequired(true)
					.build();
		}
	}

	private RequirementEvaluationResult evaluateAccountRestriction(ClanRequirement req, PlayerStatsSnapshot stats)
	{
		AccountTypeClassification required = req.getRequiredAccountType();
		AccountTypeClassification playerType = stats.getDetectedAccountType();

		if (required == AccountTypeClassification.MIXED)
		{
			String currentDisplay = (playerType != null && playerType != AccountTypeClassification.UNCONFIRMED)
				? playerType.getDisplayName()
				: "Any Account Type";
			return RequirementEvaluationResult.builder()
				.requirement(req)
				.requirementValueDisplay(AccountTypeClassification.MIXED.getDisplayName())
				.playerCurrentValueDisplay(currentDisplay)
				.status(EligibilityStatus.REQUIREMENT_MET)
				.remainingProgressDisplay("Requirement Met (Clan accepts both Ironman and Main accounts).")
				.manualConfirmationRequired(false)
				.build();
		}

		if (playerType == null || playerType == AccountTypeClassification.UNCONFIRMED)
		{
			return RequirementEvaluationResult.builder()
				.requirement(req)
				.requirementValueDisplay(required != null ? required.getDisplayName() : req.getExactRequirement())
				.playerCurrentValueDisplay("Unconfirmed Account Type")
				.status(EligibilityStatus.UNABLE_TO_VERIFY)
				.remainingProgressDisplay("Log in or set your Account Type in plugin settings. Manual confirmation required.")
				.manualConfirmationRequired(true)
				.build();
		}

		boolean matches = (required == playerType);
		return RequirementEvaluationResult.builder()
			.requirement(req)
			.requirementValueDisplay(required != null ? required.getDisplayName() : req.getExactRequirement())
			.playerCurrentValueDisplay(playerType.getDisplayName())
			.status(matches ? EligibilityStatus.REQUIREMENT_MET : EligibilityStatus.REQUIREMENT_NOT_MET)
			.remainingProgressDisplay(matches
				? "0 remaining (Your account type matches the clan's restriction)."
				: "Account type mismatch: Clan requires " + (required != null ? required.getDisplayName() : "specific account mode") + ".")
			.manualConfirmationRequired(false)
			.build();
	}

	private RequirementEvaluationResult evaluateMinimumNumeric(ClanRequirement req, long currentVal, String unit)
	{
		long target = req.getTargetValue() != null ? req.getTargetValue() : 0L;
		NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
		boolean met = currentVal >= target;
		long remaining = Math.max(0L, target - currentVal);

		return RequirementEvaluationResult.builder()
			.requirement(req)
			.requirementValueDisplay(nf.format(target) + "+ " + unit)
			.playerCurrentValueDisplay(nf.format(currentVal) + " " + unit)
			.status(met ? EligibilityStatus.REQUIREMENT_MET : EligibilityStatus.REQUIREMENT_NOT_MET)
			.remainingProgressDisplay(met
				? "0 remaining (Exceeds requirement by " + nf.format(currentVal - target) + ")"
				: nf.format(remaining) + " " + unit + " remaining")
			.manualConfirmationRequired(false)
			.build();
	}

	private RequirementEvaluationResult evaluateMaximumNumeric(ClanRequirement req, long currentVal, String unit)
	{
		long maxAllowed = req.getTargetValue() != null ? req.getTargetValue() : 99L;
		NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
		boolean met = currentVal <= maxAllowed;

		return RequirementEvaluationResult.builder()
			.requirement(req)
			.requirementValueDisplay("Max " + nf.format(maxAllowed) + " " + unit)
			.playerCurrentValueDisplay(nf.format(currentVal) + " " + unit)
			.status(met ? EligibilityStatus.REQUIREMENT_MET : EligibilityStatus.REQUIREMENT_NOT_MET)
			.remainingProgressDisplay(met
				? "Within allowed build restriction (" + nf.format(currentVal) + " / " + nf.format(maxAllowed) + ")"
				: "Exceeds maximum allowed " + unit + " by " + nf.format(currentVal - maxAllowed))
			.manualConfirmationRequired(false)
			.build();
	}

	private RequirementEvaluationResult evaluateRaidRequirement(ClanRequirement req, PlayerStatsSnapshot stats)
	{
		long target = req.getTargetValue() != null ? req.getTargetValue() : 0L;
		if (!stats.isHiscoresLoaded())
		{
			return RequirementEvaluationResult.builder()
				.requirement(req)
				.requirementValueDisplay(target + "+ Combined Raid KC")
				.playerCurrentValueDisplay("Hiscores KC Not Loaded")
				.status(EligibilityStatus.UNABLE_TO_VERIFY)
				.remainingProgressDisplay("Click 'Lookup RSN Hiscores / WOM' to check Raid KC, or confirm manually with clan leadership.")
				.manualConfirmationRequired(true)
				.build();
		}

		int currentKc = stats.getCombinedRaidsKillCount();
		boolean met = currentKc >= target;
		long remaining = Math.max(0L, target - currentKc);

		return RequirementEvaluationResult.builder()
			.requirement(req)
			.requirementValueDisplay(target + "+ Combined Raid KC")
			.playerCurrentValueDisplay(currentKc + " Combined Raid KC")
			.status(met ? EligibilityStatus.REQUIREMENT_MET : EligibilityStatus.REQUIREMENT_NOT_MET)
			.remainingProgressDisplay(met
				? "0 remaining (Exceeds by " + (currentKc - target) + " Raid KC)"
				: remaining + " Raid KC remaining")
			.manualConfirmationRequired(false)
			.build();
	}

	private RequirementEvaluationResult evaluateBossRequirement(ClanRequirement req, PlayerStatsSnapshot stats)
	{
		long target = req.getTargetValue() != null ? req.getTargetValue() : 0L;
		String key = req.getBossOrRaidMetricKey();
		if (!stats.isHiscoresLoaded() || key == null || !stats.getBossKillCounts().containsKey(key))
		{
			return RequirementEvaluationResult.builder()
				.requirement(req)
				.requirementValueDisplay(target + "+ Boss KC")
				.playerCurrentValueDisplay("Unable to verify from local data")
				.status(EligibilityStatus.UNABLE_TO_VERIFY)
				.remainingProgressDisplay("Manual confirmation required via Hiscores or clan leadership.")
				.manualConfirmationRequired(true)
				.build();
		}

		int currentKc = stats.getBossKillCounts().getOrDefault(key, 0);
		boolean met = currentKc >= target;
		long remaining = Math.max(0L, target - currentKc);

		return RequirementEvaluationResult.builder()
			.requirement(req)
			.requirementValueDisplay(target + "+ KC (" + key + ")")
			.playerCurrentValueDisplay(currentKc + " KC")
			.status(met ? EligibilityStatus.REQUIREMENT_MET : EligibilityStatus.REQUIREMENT_NOT_MET)
			.remainingProgressDisplay(met ? "0 remaining" : remaining + " KC remaining")
			.manualConfirmationRequired(false)
			.build();
	}

	private RequirementEvaluationResult evaluateEhbRequirement(ClanRequirement req, PlayerStatsSnapshot stats)
	{
		long target = req.getTargetValue() != null ? req.getTargetValue() : 0L;
		if (!stats.isHiscoresLoaded() || stats.getEstimatedEhb() == null)
		{
			return RequirementEvaluationResult.builder()
				.requirement(req)
				.requirementValueDisplay(target + "+ EHB")
				.playerCurrentValueDisplay("EHB Not Loaded")
				.status(EligibilityStatus.UNABLE_TO_VERIFY)
				.remainingProgressDisplay("Click 'Lookup RSN Hiscores / WOM' to fetch Wise Old Man EHB, or confirm manually with clan leadership.")
				.manualConfirmationRequired(true)
				.build();
		}

		double currentEhb = stats.getEstimatedEhb();
		boolean met = currentEhb >= target;
		double remaining = Math.max(0.0, target - currentEhb);

		return RequirementEvaluationResult.builder()
			.requirement(req)
			.requirementValueDisplay(target + "+ EHB")
			.playerCurrentValueDisplay(String.format(Locale.US, "%.1f EHB", currentEhb))
			.status(met ? EligibilityStatus.REQUIREMENT_MET : EligibilityStatus.REQUIREMENT_NOT_MET)
			.remainingProgressDisplay(met
				? String.format(Locale.US, "0 remaining (+%.1f EHB above target)", currentEhb - target)
				: String.format(Locale.US, "%.1f EHB remaining", remaining))
			.manualConfirmationRequired(false)
			.build();
	}

	private String formatTargetValue(ClanRequirement req)
	{
		if (req.getTargetValue() != null && req.getTargetValue() > 0)
		{
			return NumberFormat.getIntegerInstance(Locale.US).format(req.getTargetValue()) + "+";
		}
		return req.getExactRequirement();
	}
}
