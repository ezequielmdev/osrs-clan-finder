package com.osrsclanfinder;

import com.google.inject.Provides;
import com.osrsclanfinder.model.ClanEntry;
import com.osrsclanfinder.service.ClanImageService;
import com.osrsclanfinder.service.ClanRepositoryService;
import com.osrsclanfinder.service.PlayerEligibilityService;
import com.osrsclanfinder.ui.ClanFinderPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "OSRS Clan Finder",
	description = "Discover verified OSRS clans, inspect real recruitment requirements with traceable sources, and check your account eligibility.",
	tags = {"clan", "finder", "recruitment", "pvm", "raids", "ironman", "skilling", "brazil", "wiseoldman"}
)
public class ClanFinderPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "osrsclanfinder";

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClanFinderConfig config;

	@Inject
	private ClanRepositoryService repositoryService;

	@Inject
	private PlayerEligibilityService eligibilityService;

	@Inject
	private ClanImageService imageService;

	@Inject
	private ScheduledExecutorService executorService;

	private ClanFinderPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> periodicVerificationTask;

	@Override
	protected void startUp()
	{
		repositoryService.loadBundledDatabase();

		panel = new ClanFinderPanel(repositoryService, eligibilityService, imageService);

		navButton = NavigationButton.builder()
			.tooltip("OSRS Clan Finder")
			.icon(createToolbarIcon())
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		repositoryService.refreshFromRemoteIfConfigured(() ->
			SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.refreshClanList();
				}
			})
		);

		schedulePeriodicVerification();
		log.info("OSRS Clan Finder plugin started with {} verified clan entries.", repositoryService.getAllClans().size());
	}

	@Override
	protected void shutDown()
	{
		cancelPeriodicVerification();
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			eligibilityService.captureInGamePlayerStats();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("remoteDatabaseUrl".equals(event.getKey()))
		{
			repositoryService.refreshFromRemoteIfConfigured(() ->
				SwingUtilities.invokeLater(() -> {
					if (panel != null)
					{
						panel.refreshClanList();
					}
				})
			);
		}
		else if ("enablePeriodicVerification".equals(event.getKey())
			|| "verificationIntervalMinutes".equals(event.getKey()))
		{
			schedulePeriodicVerification();
		}
	}

	private void schedulePeriodicVerification()
	{
		cancelPeriodicVerification();
		if (!config.enablePeriodicVerification())
		{
			return;
		}

		int intervalMinutes = Math.max(15, config.verificationIntervalMinutes());
		periodicVerificationTask = executorService.scheduleAtFixedRate(
			this::runPeriodicVerificationCycle,
			intervalMinutes,
			intervalMinutes,
			TimeUnit.MINUTES
		);
	}

	private void cancelPeriodicVerification()
	{
		if (periodicVerificationTask != null)
		{
			periodicVerificationTask.cancel(false);
			periodicVerificationTask = null;
		}
	}

	private void runPeriodicVerificationCycle()
	{
		repositoryService.refreshFromRemoteIfConfigured(() -> {
			List<ClanEntry> all = repositoryService.getAllClans();
			if (!all.isEmpty())
			{
				// Periodically verify a clan entry with Wise Old Man API without overloading rate limits
				ClanEntry sample = all.get(0);
				repositoryService.verifyClanWithWiseOldMan(sample, () ->
					SwingUtilities.invokeLater(() -> {
						if (panel != null)
						{
							panel.refreshClanList();
						}
					})
				);
			}
		});
	}

	private BufferedImage createToolbarIcon()
	{
		try
		{
			BufferedImage loaded = ImageUtil.loadImageResource(ClanFinderPlugin.class, "/com/osrsclanfinder/plugin_icon.png");
			if (loaded != null)
			{
				return loaded;
			}
		}
		catch (Exception e)
		{
			log.debug("Falling back to generated toolbar icon", e);
		}

		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Shield background
		g.setColor(new Color(45, 55, 72));
		g.fillRoundRect(1, 1, 14, 14, 4, 4);

		// Gold border
		g.setColor(new Color(255, 185, 50));
		g.drawRoundRect(1, 1, 13, 13, 4, 4);

		// "CF" emblem
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
		g.drawString("CF", 2, 11);
		g.dispose();
		return img;
	}

	@Provides
	ClanFinderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanFinderConfig.class);
	}
}
