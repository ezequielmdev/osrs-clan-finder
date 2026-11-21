package com.osrsclanfinder.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osrsclanfinder.ClanFinderConfig;
import com.osrsclanfinder.model.AccountTypeClassification;
import com.osrsclanfinder.model.ClanDatabasePayload;
import com.osrsclanfinder.model.ClanDirectoryCategory;
import com.osrsclanfinder.model.ClanEntry;
import com.osrsclanfinder.model.ClanFilterCriteria;
import com.osrsclanfinder.model.ClanRequirement;
import com.osrsclanfinder.model.ClanSortOrder;
import com.osrsclanfinder.model.ClanSpecialization;
import com.osrsclanfinder.model.RankingMetric;
import com.osrsclanfinder.model.RecruitmentAvailabilityStatus;
import com.osrsclanfinder.model.RequirementDataAvailability;
import com.osrsclanfinder.model.VerificationStatus;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class ClanRepositoryService
{
	private static final String BUNDLED_RESOURCE_PATH = "/com/osrsclanfinder/verified_clans.json";
	private static final String WOM_GROUP_ENDPOINT = "https://api.wiseoldman.net/v2/groups/";
	private static final String TEMPLE_GROUPS_URL = "https://templeosrs.com/groups/view_groups.php";
	private static final String OSRSCLANS_API_URL = "https://osrsclans.cc/api/v1/clans";
	private static final String USER_AGENT = "OSRS-Clan-Finder-RuneLite-Plugin/2.0";

	private static final Pattern TEMPLE_ROW_PATTERN = Pattern.compile(
		"(?s)<tr[^>]*>.*?overview\\.php\\?id=(\\d+)[^>]*>([^<]+)</a>\\s*</td>\\s*<td[^>]*>([0-9,]+)</td>\\s*<td[^>]*>([0-9,]+)</td>\\s*<td[^>]*>([0-9,]+)</td>\\s*<td[^>]*>([0-9,]+)</td>.*?</tr>"
	);

	private final AtomicInteger womRotationIndex = new AtomicInteger(0);

	private final Gson gson;
	private final OkHttpClient okHttpClient;
	private final ClanFinderConfig config;

	private final List<ClanEntry> clans = new CopyOnWriteArrayList<>();

	@Getter
	private volatile String lastDatabaseSyncTimestamp = "2026-11-21";

	@Getter
	private volatile String activeDatabaseSource = "Verified Clan Directory";

	@Inject
	public ClanRepositoryService(Gson gson, OkHttpClient okHttpClient, ClanFinderConfig config)
	{
		this.gson = gson;
		this.okHttpClient = okHttpClient;
		this.config = config;
	}

	public synchronized void loadBundledDatabase()
	{
		try (InputStream is = ClanRepositoryService.class.getResourceAsStream(BUNDLED_RESOURCE_PATH))
		{
			if (is == null)
			{
				log.error("Bundled verified_clans.json could not be found at {}", BUNDLED_RESOURCE_PATH);
				return;
			}
			try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
			{
				ClanDatabasePayload payload = gson.fromJson(reader, ClanDatabasePayload.class);
				if (payload != null && payload.getClans() != null)
				{
					List<ClanEntry> validated = validateAndSanitizeClans(payload.getClans());
					clans.clear();
					clans.addAll(validated);
					lastDatabaseSyncTimestamp = payload.getDatabaseUpdatedDate() != null
						? payload.getDatabaseUpdatedDate()
						: LocalDate.now().toString();
					activeDatabaseSource = "Verified Clan Directory (" + validated.size() + " clans)";
				}
			}
		}
		catch (Exception e)
		{
			log.error("Failed to load bundled verified clan database", e);
		}
	}

	public void refreshFromRemoteIfConfigured(Runnable onUpdateCallback)
	{
		// Always sync live member counts, EHP, EHB, and Total XP from TempleOSRS, OSRSClans API, and Wise Old Man
		syncAllLiveTrackerStats(() -> {
			String remoteUrl = config.remoteDatabaseUrl();
			if (remoteUrl == null || remoteUrl.trim().isEmpty())
			{
				if (onUpdateCallback != null)
				{
					onUpdateCallback.run();
				}
				return;
			}
			fetchCustomAdminRemoteJson(remoteUrl.trim(), onUpdateCallback);
		});
	}

	private void fetchCustomAdminRemoteJson(String trimmedUrl, Runnable onUpdateCallback)
	{
		if (!trimmedUrl.startsWith("https://") && !trimmedUrl.startsWith("http://"))
		{
			log.warn("Invalid remote database URL configured: {}", trimmedUrl);
			if (onUpdateCallback != null)
			{
				onUpdateCallback.run();
			}
			return;
		}

		Request request = new Request.Builder()
			.url(trimmedUrl)
			.header("User-Agent", USER_AGENT)
			.get()
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Failed to fetch remote clan database from {}", trimmedUrl, e);
				if (onUpdateCallback != null)
				{
					onUpdateCallback.run();
				}
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.warn("Unsuccessful response ({}) from remote clan database {}", response.code(), trimmedUrl);
						return;
					}

					String jsonText = body.string();
					ClanDatabasePayload payload = gson.fromJson(jsonText, ClanDatabasePayload.class);
					if (payload != null && payload.getClans() != null && !payload.getClans().isEmpty())
					{
						mergeRemoteClansSafely(payload.getClans(), payload.getDatabaseUpdatedDate());
						activeDatabaseSource = "Remote Verified URL + Bundled (" + clans.size() + " clans)";
					}
				}
				catch (Exception ex)
				{
					log.warn("Failed to parse remote clan database JSON", ex);
				}
				finally
				{
					if (onUpdateCallback != null)
					{
						onUpdateCallback.run();
					}
				}
			}
		});
	}

	/**
	 * Merges administrator updates without ever replacing official requirements with unverified or guessed values.
	 */
	public synchronized void mergeRemoteClansSafely(List<ClanEntry> remoteClans, String remoteDate)
	{
		List<ClanEntry> sanitizedRemote = validateAndSanitizeClans(remoteClans);
		Map<String, ClanEntry> mergedMap = new LinkedHashMap<>();
		for (ClanEntry existing : clans)
		{
			mergedMap.put(existing.getId(), existing);
		}

		for (ClanEntry incoming : sanitizedRemote)
		{
			ClanEntry existing = mergedMap.get(incoming.getId());
			if (existing == null)
			{
				mergedMap.put(incoming.getId(), incoming);
				continue;
			}

			boolean existingHasOfficialReqs = existing.getOfficialRecruitmentRequirements() != null
				&& existing.getOfficialRecruitmentRequirements().stream()
					.anyMatch(r -> r.getVerificationStatus() == VerificationStatus.OFFICIALLY_VERIFIED);

			boolean incomingHasVerifiedReqs = incoming.getOfficialRecruitmentRequirements() != null
				&& incoming.getOfficialRecruitmentRequirements().stream()
					.anyMatch(r -> r.getVerificationStatus() == VerificationStatus.OFFICIALLY_VERIFIED
						|| r.getVerificationStatus() == VerificationStatus.COMMUNITY_CONFIRMED);

			if (existingHasOfficialReqs && !incomingHasVerifiedReqs)
			{
				log.info("Preserving existing officially verified requirements for clan {}", existing.getClanName());
				incoming.setOfficialRecruitmentRequirements(existing.getOfficialRecruitmentRequirements());
				incoming.setRequirementAvailability(existing.getRequirementAvailability());
			}

			mergedMap.put(incoming.getId(), incoming);
		}

		clans.clear();
		clans.addAll(mergedMap.values());
		lastDatabaseSyncTimestamp = remoteDate != null && !remoteDate.trim().isEmpty()
			? remoteDate
			: LocalDate.now().toString();
	}

	/**
	 * Synchronizes live clan statistics (Member Count, Average EHP, Average EHB, Total EHP, Total EHB, Total XP)
	 * directly from TempleOSRS Verified Groups, OSRSClans API, and a rotating batch of Wise Old Man API groups.
	 */
	public void syncAllLiveTrackerStats(Runnable onComplete)
	{
		syncFromTempleOsrs(() ->
			syncFromOsrsClansApi(() ->
				syncBatchFromWiseOldMan(5, onComplete)
			)
		);
	}

	private void syncFromTempleOsrs(Runnable nextStep)
	{
		Request request = new Request.Builder()
			.url(TEMPLE_GROUPS_URL)
			.header("User-Agent", USER_AGENT)
			.get()
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Could not sync live stats from TempleOSRS: {}", e.getMessage());
				if (nextStep != null)
				{
					nextStep.run();
				}
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (response.isSuccessful() && body != null)
					{
						String html = body.string();
						Matcher m = TEMPLE_ROW_PATTERN.matcher(html);
						String today = LocalDate.now().toString();
						int updatedCount = 0;

						while (m.find())
						{
							int templeId = Integer.parseInt(m.group(1));
							long avgXp = parseCommaLong(m.group(3));
							double avgEhp = parseCommaDouble(m.group(4));
							double avgEhb = parseCommaDouble(m.group(5));
							int members = (int) parseCommaLong(m.group(6));

							for (ClanEntry clan : clans)
							{
								if (clan.getTempleOsrsGroupId() != null && clan.getTempleOsrsGroupId() == templeId)
								{
									if (members > 0)
									{
										clan.setVerifiedMemberCount(members);
									}
									if (avgXp > 0)
									{
										clan.setAverageXp(avgXp);
										if (members > 0)
										{
											clan.setTotalXp(avgXp * members);
										}
									}
									if (avgEhp > 0)
									{
										clan.setAverageEhp(avgEhp);
										if (members > 0)
										{
											clan.setTotalEhp(avgEhp * members);
										}
									}
									if (avgEhb > 0)
									{
										clan.setAverageEhb(avgEhb);
										if (members > 0)
										{
											clan.setTotalEhb(avgEhb * members);
										}
									}
									clan.setLastVerificationDate(today);
									updatedCount++;
								}
							}
						}
						if (updatedCount > 0)
						{
							lastDatabaseSyncTimestamp = today;
							log.debug("Updated live TempleOSRS stats for {} clans", updatedCount);
						}
					}
				}
				catch (Exception ex)
				{
					log.debug("Error parsing TempleOSRS live group table", ex);
				}
				finally
				{
					if (nextStep != null)
					{
						nextStep.run();
					}
				}
			}
		});
	}

	private void syncFromOsrsClansApi(Runnable nextStep)
	{
		Request request = new Request.Builder()
			.url(OSRSCLANS_API_URL)
			.header("User-Agent", USER_AGENT)
			.get()
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Could not sync live stats from OSRSClans API: {}", e.getMessage());
				if (nextStep != null)
				{
					nextStep.run();
				}
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (response.isSuccessful() && body != null)
					{
						JsonObject root = gson.fromJson(body.string(), JsonObject.class);
						if (root != null && root.has("clans") && root.get("clans").isJsonArray())
						{
							String today = LocalDate.now().toString();
							for (JsonElement el : root.getAsJsonArray("clans"))
							{
								if (!el.isJsonObject())
								{
									continue;
								}
								JsonObject cObj = el.getAsJsonObject();
								String apiName = cObj.has("name") && !cObj.get("name").isJsonNull()
									? cObj.get("name").getAsString().trim()
									: "";
								if (apiName.isEmpty() || !cObj.has("stats") || !cObj.get("stats").isJsonObject())
								{
									continue;
								}
								JsonObject stats = cObj.getAsJsonObject("stats");
								for (ClanEntry clan : clans)
								{
									if (clan.getClanName().equalsIgnoreCase(apiName))
									{
										int members = stats.has("members") && !stats.get("members").isJsonNull()
											? stats.get("members").getAsInt()
											: 0;
										long totalExp = stats.has("totalExp") && !stats.get("totalExp").isJsonNull()
											? stats.get("totalExp").getAsLong()
											: 0L;
										double totEhp = stats.has("ehp") && !stats.get("ehp").isJsonNull()
											? stats.get("ehp").getAsDouble()
											: 0.0;
										double totEhb = stats.has("ehb") && !stats.get("ehb").isJsonNull()
											? stats.get("ehb").getAsDouble()
											: 0.0;

										if (members > 0)
										{
											clan.setVerifiedMemberCount(members);
										}
										if (totalExp > 0)
										{
											clan.setTotalXp(totalExp);
											if (members > 0)
											{
												clan.setAverageXp(totalExp / members);
											}
										}
										if (totEhp > 0)
										{
											clan.setTotalEhp(totEhp);
											if (members > 0 && (clan.getTempleOsrsGroupId() == null || clan.getTempleOsrsGroupId() <= 0))
											{
												clan.setAverageEhp(totEhp / members);
											}
										}
										if (totEhb > 0)
										{
											clan.setTotalEhb(totEhb);
											if (members > 0 && (clan.getTempleOsrsGroupId() == null || clan.getTempleOsrsGroupId() <= 0))
											{
												clan.setAverageEhb(totEhb / members);
											}
										}
										clan.setLastVerificationDate(today);
									}
								}
							}
						}
					}
				}
				catch (Exception ex)
				{
					log.debug("Failed to parse OSRSClans API live response", ex);
				}
				finally
				{
					if (nextStep != null)
					{
						nextStep.run();
					}
				}
			}
		});
	}

	private void syncBatchFromWiseOldMan(int batchSize, Runnable onComplete)
	{
		List<ClanEntry> womClans = clans.stream()
			.filter(c -> c.getWiseOldManGroupId() != null && c.getWiseOldManGroupId() > 0)
			.collect(Collectors.toList());

		if (womClans.isEmpty())
		{
			if (onComplete != null)
			{
				onComplete.run();
			}
			return;
		}

		int count = Math.min(batchSize, womClans.size());
		AtomicInteger remaining = new AtomicInteger(count);

		for (int i = 0; i < count; i++)
		{
			int idx = Math.floorMod(womRotationIndex.getAndIncrement(), womClans.size());
			ClanEntry target = womClans.get(idx);
			verifyClanWithWiseOldMan(target, () -> {
				if (remaining.decrementAndGet() == 0 && onComplete != null)
				{
					onComplete.run();
				}
			});
		}
	}

	private static long parseCommaLong(String raw)
	{
		if (raw == null)
		{
			return 0L;
		}
		try
		{
			return Long.parseLong(raw.replace(",", "").trim());
		}
		catch (NumberFormatException e)
		{
			return 0L;
		}
	}

	private static double parseCommaDouble(String raw)
	{
		if (raw == null)
		{
			return 0.0;
		}
		try
		{
			return Double.parseDouble(raw.replace(",", "").trim());
		}
		catch (NumberFormatException e)
		{
			return 0.0;
		}
	}

	/**
	 * Periodically verifies clan group metadata against the live Wise Old Man API where a group ID exists.
	 */
	public void verifyClanWithWiseOldMan(ClanEntry clan, Runnable onCompleteCallback)
	{
		if (clan == null || clan.getWiseOldManGroupId() == null || clan.getWiseOldManGroupId() <= 0)
		{
			if (onCompleteCallback != null)
			{
				onCompleteCallback.run();
			}
			return;
		}

		String url = WOM_GROUP_ENDPOINT + clan.getWiseOldManGroupId();
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
				log.debug("Could not reach Wise Old Man API for group {}", clan.getWiseOldManGroupId(), e);
				if (onCompleteCallback != null)
				{
					onCompleteCallback.run();
				}
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						return;
					}
					JsonObject json = gson.fromJson(body.string(), JsonObject.class);
					if (json == null)
					{
						return;
					}

					if (json.has("memberCount") && !json.get("memberCount").isJsonNull())
					{
						clan.setVerifiedMemberCount(json.get("memberCount").getAsInt());
					}
					if (json.has("homeworld") && !json.get("homeworld").isJsonNull())
					{
						clan.setHomeWorld(json.get("homeworld").getAsInt());
					}
					if (json.has("clanChat") && !json.get("clanChat").isJsonNull())
					{
						String cc = json.get("clanChat").getAsString().trim();
						if (!cc.isEmpty())
						{
							clan.setInGameClanChat(cc);
						}
					}
					if (json.has("socialLinks") && json.get("socialLinks").isJsonObject())
					{
						JsonObject links = json.getAsJsonObject("socialLinks");
						updateSocialLinkIfPresent(links, "website", clan, true);
						updateSocialLinkIfPresent(links, "discord", clan, false);
					}

					String today = LocalDate.now().toString();
					clan.setLastVerificationDate(today);
					lastDatabaseSyncTimestamp = today;
				}
				catch (Exception ex)
				{
					log.debug("Failed to parse Wise Old Man verification response for {}", clan.getClanName(), ex);
				}
				finally
				{
					if (onCompleteCallback != null)
					{
						onCompleteCallback.run();
					}
				}
			}
		});
	}

	private void updateSocialLinkIfPresent(JsonObject links, String key, ClanEntry clan, boolean isWebsite)
	{
		if (links.has(key) && !links.get(key).isJsonNull())
		{
			JsonElement el = links.get(key);
			String val = el.getAsString().trim();
			if (!val.isEmpty())
			{
				if (isWebsite && (clan.getOfficialWebsite() == null || clan.getOfficialWebsite().startsWith("Unavailable")))
				{
					clan.setOfficialWebsite(val);
				}
				else if (!isWebsite && (clan.getDiscordInvitation() == null || clan.getDiscordInvitation().startsWith("Unavailable")))
				{
					clan.setDiscordInvitation(val);
				}
			}
		}
	}

	private List<ClanEntry> validateAndSanitizeClans(List<ClanEntry> rawClans)
	{
		if (rawClans == null)
		{
			return Collections.emptyList();
		}
		List<ClanEntry> valid = new ArrayList<>();
		for (ClanEntry c : rawClans)
		{
			if (c == null || c.getClanName() == null || c.getClanName().trim().isEmpty())
			{
				continue;
			}
			if (c.getAccountType() == null)
			{
				c.setAccountType(AccountTypeClassification.UNCONFIRMED);
			}
			if (c.getOverallVerificationStatus() == null)
			{
				c.setOverallVerificationStatus(VerificationStatus.UNVERIFIED);
			}
			if (c.getRecruitmentAvailability() == null)
			{
				c.setRecruitmentAvailability(RecruitmentAvailabilityStatus.UNKNOWN);
			}
			if (c.getDirectoryCategories() == null || c.getDirectoryCategories().isEmpty())
			{
				c.setDirectoryCategories(Collections.singletonList(ClanDirectoryCategory.MIXED_COMMUNITY));
			}
			if (c.getRequirementAvailability() == null)
			{
				c.setRequirementAvailability(
					(c.getOfficialRecruitmentRequirements() == null || c.getOfficialRecruitmentRequirements().isEmpty())
						? RequirementDataAvailability.NOT_PUBLICLY_AVAILABLE
						: RequirementDataAvailability.AVAILABLE
				);
			}
			if (c.getShortDescription() == null || c.getShortDescription().trim().isEmpty())
			{
				c.setShortDescription(c.getDescription() != null ? c.getDescription() : "OSRS Clan Community.");
			}
			if (c.getOfficialWebsite() == null || c.getOfficialWebsite().trim().isEmpty())
			{
				c.setOfficialWebsite("Unavailable");
			}
			if (c.getRecruitmentPageUrl() == null || c.getRecruitmentPageUrl().trim().isEmpty())
			{
				c.setRecruitmentPageUrl(!c.getOfficialWebsite().equals("Unavailable") ? c.getOfficialWebsite() : "Unavailable");
			}
			if (c.getDiscordInvitation() == null || c.getDiscordInvitation().trim().isEmpty())
			{
				c.setDiscordInvitation("Unavailable");
			}
			if (c.getWiseOldManGroupUrl() == null || c.getWiseOldManGroupUrl().trim().isEmpty())
			{
				c.setWiseOldManGroupUrl(c.getWiseOldManGroupId() != null && c.getWiseOldManGroupId() > 0
					? "https://wiseoldman.net/groups/" + c.getWiseOldManGroupId()
					: "Unavailable");
			}
			if (c.getTempleOsrsGroupUrl() == null || c.getTempleOsrsGroupUrl().trim().isEmpty())
			{
				c.setTempleOsrsGroupUrl(c.getTempleOsrsGroupId() != null && c.getTempleOsrsGroupId() > 0
					? "https://templeosrs.com/groups/overview.php?id=" + c.getTempleOsrsGroupId()
					: "Unavailable");
			}
			if (c.getLastVerificationDate() == null || c.getLastVerificationDate().trim().isEmpty())
			{
				c.setLastVerificationDate("2026-11-21");
			}
			if (c.getOfficialRecruitmentRequirements() != null)
			{
				for (ClanRequirement req : c.getOfficialRecruitmentRequirements())
				{
					if (req.getVerificationStatus() == null)
					{
						req.setVerificationStatus(VerificationStatus.UNVERIFIED);
					}
					if (req.getSourceUrl() == null || req.getSourceUrl().trim().isEmpty())
					{
						req.setSourceUrl("Unavailable");
					}
					if (req.getLastVerificationDate() == null || req.getLastVerificationDate().trim().isEmpty())
					{
						req.setLastVerificationDate(c.getLastVerificationDate());
					}
				}
			}
			else
			{
				c.setOfficialRecruitmentRequirements(Collections.emptyList());
			}
			valid.add(c);
		}
		return valid;
	}

	public List<ClanEntry> getAllClans()
	{
		return Collections.unmodifiableList(new ArrayList<>(clans));
	}

	public List<ClanEntry> filterClans(ClanFilterCriteria criteria)
	{
		if (criteria == null)
		{
			return getAllClans();
		}

		String query = criteria.getSearchQuery() == null
			? ""
			: criteria.getSearchQuery().trim().toLowerCase(Locale.ROOT);
		Set<ClanSpecialization> selectedSpecs = criteria.getSelectedSpecializations();

		List<ClanEntry> filtered = clans.stream()
			.filter(clan -> matchesSearchQuery(clan, query))
			.filter(clan -> matchesDirectoryCategory(clan, criteria.getSelectedDirectoryCategory()))
			.filter(clan -> matchesAccountTypeFilters(clan, criteria))
			.filter(clan -> matchesRegionFilters(clan, criteria))
			.filter(clan -> matchesSpecializations(clan, selectedSpecs))
			.filter(clan -> !criteria.isOpenRecruitmentOnly()
				|| clan.getRecruitmentAvailability() == RecruitmentAvailabilityStatus.OPEN)
			.filter(clan -> !criteria.isOnlyOfficiallyVerified()
				|| clan.getOverallVerificationStatus() == VerificationStatus.OFFICIALLY_VERIFIED)
			.collect(Collectors.toList());

		sortClanList(filtered, criteria.getSortOrder());
		return filtered;
	}

	private void sortClanList(List<ClanEntry> list, ClanSortOrder sortOrder)
	{
		if (sortOrder == null)
		{
			return;
		}
		switch (sortOrder)
		{
			case RANK_BY_AVERAGE_EHB:
				list.sort(Comparator.comparingDouble(ClanEntry::getEffectiveAverageEhb).reversed()
					.thenComparing(ClanEntry::getClanName, String.CASE_INSENSITIVE_ORDER));
				break;
			case RANK_BY_AVERAGE_EHP:
				list.sort(Comparator.comparingDouble(ClanEntry::getEffectiveAverageEhp).reversed()
					.thenComparing(ClanEntry::getClanName, String.CASE_INSENSITIVE_ORDER));
				break;
			case RANK_BY_MEMBERS:
				list.sort(Comparator.comparingInt((ClanEntry c) -> c.getVerifiedMemberCount() != null ? c.getVerifiedMemberCount() : 0).reversed()
					.thenComparing(ClanEntry::getClanName, String.CASE_INSENSITIVE_ORDER));
				break;
			case ALPHABETICAL:
				list.sort(Comparator.comparing(ClanEntry::getClanName, String.CASE_INSENSITIVE_ORDER));
				break;
		}
	}

	/**
	 * Returns clans ranked strictly by the selected RankingMetric (never mixing averages with totals).
	 */
	public List<ClanEntry> getRankedClans(RankingMetric metric)
	{
		List<ClanEntry> copy = new ArrayList<>(clans);
		Comparator<ClanEntry> comparator;
		switch (metric)
		{
			case AVERAGE_EHB:
				comparator = Comparator.comparingDouble(ClanEntry::getEffectiveAverageEhb).reversed();
				break;
			case AVERAGE_EHP:
				comparator = Comparator.comparingDouble(ClanEntry::getEffectiveAverageEhp).reversed();
				break;
			case TOTAL_EHB:
				comparator = Comparator.comparingDouble(ClanEntry::getEffectiveTotalEhb).reversed();
				break;
			case TOTAL_EHP:
				comparator = Comparator.comparingDouble(ClanEntry::getEffectiveTotalEhp).reversed();
				break;
			case MEMBER_COUNT:
				comparator = Comparator.comparingInt((ClanEntry c) -> c.getVerifiedMemberCount() != null ? c.getVerifiedMemberCount() : 0).reversed();
				break;
			case TOTAL_XP:
			default:
				comparator = Comparator.comparingLong(ClanEntry::getEffectiveTotalXp).reversed();
				break;
		}
		copy.sort(comparator.thenComparing(ClanEntry::getClanName, String.CASE_INSENSITIVE_ORDER));
		return copy;
	}

	private boolean matchesDirectoryCategory(ClanEntry clan, ClanDirectoryCategory category)
	{
		if (category == null)
		{
			return true;
		}
		return clan.getDirectoryCategories() != null && clan.getDirectoryCategories().contains(category);
	}

	private boolean matchesSearchQuery(ClanEntry clan, String query)
	{
		if (query.isEmpty())
		{
			return true;
		}
		boolean nameMatch = clan.getClanName() != null && clan.getClanName().toLowerCase(Locale.ROOT).contains(query);
		boolean descMatch = clan.getDescription() != null && clan.getDescription().toLowerCase(Locale.ROOT).contains(query);
		boolean shortMatch = clan.getShortDescription() != null && clan.getShortDescription().toLowerCase(Locale.ROOT).contains(query);
		boolean regionMatch = clan.getCountryOrRegion() != null && clan.getCountryOrRegion().toLowerCase(Locale.ROOT).contains(query);
		boolean ccMatch = clan.getInGameClanChat() != null && clan.getInGameClanChat().toLowerCase(Locale.ROOT).contains(query);
		return nameMatch || descMatch || shortMatch || regionMatch || ccMatch;
	}

	private boolean matchesAccountTypeFilters(ClanEntry clan, ClanFilterCriteria criteria)
	{
		if (!criteria.hasAccountTypeFilters())
		{
			return true;
		}
		AccountTypeClassification type = clan.getAccountType();
		if (criteria.isIronmanOnly() && type == AccountTypeClassification.IRONMAN_ONLY)
		{
			return true;
		}
		if (criteria.isMainAccountsOnly() && type == AccountTypeClassification.MAIN_ACCOUNTS_ONLY)
		{
			return true;
		}
		if (criteria.isMixedClans() && type == AccountTypeClassification.MIXED)
		{
			return true;
		}
		return false;
	}

	private boolean matchesRegionFilters(ClanEntry clan, ClanFilterCriteria criteria)
	{
		if (!criteria.hasRegionFilters())
		{
			return true;
		}
		if (criteria.isBrazilianClans() && clan.isBrazilianClan())
		{
			return true;
		}
		if (criteria.isInternationalClans() && clan.isInternationalClan())
		{
			return true;
		}
		return false;
	}

	private boolean matchesSpecializations(ClanEntry clan, Set<ClanSpecialization> selectedSpecs)
	{
		if (selectedSpecs.isEmpty())
		{
			return true;
		}
		List<ClanSpecialization> clanSpecs = clan.getSpecializations();
		if (clanSpecs == null || clanSpecs.isEmpty())
		{
			return clan.getMainSpecialization() != null && selectedSpecs.contains(clan.getMainSpecialization());
		}
		for (ClanSpecialization selected : selectedSpecs)
		{
			if (!clanSpecs.contains(selected) && clan.getMainSpecialization() != selected)
			{
				return false;
			}
		}
		return true;
	}
}
