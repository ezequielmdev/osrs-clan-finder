package com.osrsclanfinder.ui;

import com.osrsclanfinder.model.AccountTypeClassification;
import com.osrsclanfinder.model.ClanDirectoryCategory;
import com.osrsclanfinder.model.ClanEligibilityReport;
import com.osrsclanfinder.model.ClanEntry;
import com.osrsclanfinder.model.ClanFilterCriteria;
import com.osrsclanfinder.model.ClanRequirement;
import com.osrsclanfinder.model.ClanSortOrder;
import com.osrsclanfinder.model.ClanSpecialization;
import com.osrsclanfinder.model.PlayerStatsSnapshot;
import com.osrsclanfinder.model.RankingMetric;
import com.osrsclanfinder.model.RecruitmentAvailabilityStatus;
import com.osrsclanfinder.model.RequirementDataAvailability;
import com.osrsclanfinder.model.RequirementEvaluationResult;
import com.osrsclanfinder.model.VerificationStatus;
import com.osrsclanfinder.service.ClanImageService;
import com.osrsclanfinder.service.ClanRepositoryService;
import com.osrsclanfinder.service.PlayerEligibilityService;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

public class ClanFinderPanel extends PluginPanel
{
	private static final String CARD_DIRECTORY = "DIRECTORY";
	private static final String CARD_RANKINGS = "RANKINGS";
	private static final String CARD_DETAILS = "DETAILS";

	private static final String UNCONFIRMED_REQS_MESSAGE = "Unconfirmed requirements — check official Discord.";

	private static final String URL_WISE_OLD_MAN_GROUPS = "https://wiseoldman.net/groups";
	private static final String URL_TEMPLE_OSRS_GROUPS = "https://templeosrs.com/groups/view_groups.php";
	private static final String URL_OSRS_CLANS_DIRECTORY = "https://osrsclans.cc/";

	private static final Color PANEL_BG = new Color(20, 22, 26);
	private static final Color CARD_BG = new Color(30, 33, 39);
	private static final Color CARD_INNER_BG = new Color(22, 24, 29);
	private static final Color ACCENT_GOLD = new Color(255, 190, 55);
	private static final Color ACCENT_EMERALD = new Color(46, 175, 95);
	private static final Color ACCENT_BLUE = new Color(65, 145, 235);
	private static final Color MUTED_TEXT = new Color(175, 180, 190);

	private final ClanRepositoryService repositoryService;
	private final PlayerEligibilityService eligibilityService;
	private final ClanImageService imageService;

	private final CardLayout cardLayout = new CardLayout();
	private final JPanel mainContainer = new JPanel(cardLayout);

	// Top mode buttons (Directory vs Rankings)
	private JButton directoryModeBtn;
	private JButton rankingsModeBtn;

	// Directory view state & components
	private ClanDirectoryCategory selectedCategoryTab = null; // null = All Categories
	private final List<JButton> categoryTabButtons = new ArrayList<>();
	private final JTextField searchField = new JTextField();
	private final JComboBox<ClanSortOrder> sortComboBox = new JComboBox<>(ClanSortOrder.values());
	private final JTextArea databaseStatusArea = createWrappedLabelArea("", MUTED_TEXT);
	private final JTextArea resultCountArea = createWrappedLabelArea("", Color.LIGHT_GRAY);
	private final ScrollableVerticalPanel clanCardsListPanel = new ScrollableVerticalPanel();
	private final JPanel filterContentPanel = new JPanel();
	private boolean filtersExpanded = false;

	// Filter checkboxes
	private final JCheckBox cbOpenRecruitmentOnly = createFilterCheckBox("Open Recruitment Only");
	private final JCheckBox cbIronmanOnly = createFilterCheckBox("Ironman Only");
	private final JCheckBox cbMainAccountsOnly = createFilterCheckBox("Main Accounts Only");
	private final JCheckBox cbMixedClans = createFilterCheckBox("Mixed Clans");

	private final JCheckBox cbBrazilianClans = createFilterCheckBox("Brazilian Clans");
	private final JCheckBox cbInternationalClans = createFilterCheckBox("International Clans");

	private final JCheckBox cbPvm = createFilterCheckBox("PvM");
	private final JCheckBox cbRaids = createFilterCheckBox("Raids");
	private final JCheckBox cbEndgamePvm = createFilterCheckBox("Endgame PvM");
	private final JCheckBox cbSkilling = createFilterCheckBox("Skilling");
	private final JCheckBox cbSocial = createFilterCheckBox("Social");
	private final JCheckBox cbOfficiallyVerifiedOnly = createFilterCheckBox("Officially Verified Only");

	// Rankings view state & components
	private RankingMetric selectedRankingMetric = RankingMetric.AVERAGE_EHB;
	private final JComboBox<RankingMetric> rankingMetricCombo = new JComboBox<>(RankingMetric.values());
	private final JTextArea rankingInfoArea = createWrappedLabelArea("", MUTED_TEXT);
	private final ScrollableVerticalPanel rankingsListPanel = new ScrollableVerticalPanel();

	// Details & Eligibility view components
	private final JPanel detailsViewPanel = new JPanel(new BorderLayout());
	private final ScrollableVerticalPanel detailsContentContainer = new ScrollableVerticalPanel();
	private final JTextField rsnLookupField = new JTextField();
	private ClanEntry currentlySelectedClan;
	private ClanEligibilityReport latestEligibilityReport;

	public ClanFinderPanel(
		ClanRepositoryService repositoryService,
		PlayerEligibilityService eligibilityService,
		ClanImageService imageService)
	{
		super(false);
		this.repositoryService = repositoryService;
		this.eligibilityService = eligibilityService;
		this.imageService = imageService;

		setLayout(new BorderLayout());
		setBackground(PANEL_BG);

		add(buildGlobalTopHeader(), BorderLayout.NORTH);

		mainContainer.setBackground(PANEL_BG);
		mainContainer.add(buildDirectoryView(), CARD_DIRECTORY);
		mainContainer.add(buildRankingsView(), CARD_RANKINGS);
		mainContainer.add(buildDetailsView(), CARD_DETAILS);

		add(mainContainer, BorderLayout.CENTER);
		refreshClanList();
		refreshRankingsView();
	}

	/**
	 * Custom JPanel that tracks the viewport width of its JScrollPane so child JTextAreas
	 * and cards always wrap to the exact RuneLite sidebar width and never cut off text horizontally.
	 */
	private static class ScrollableVerticalPanel extends JPanel implements Scrollable
	{
		ScrollableVerticalPanel()
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(PANEL_BG);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 64;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private JPanel buildGlobalTopHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(CARD_BG);
		header.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, new Color(55, 60, 70)),
			new EmptyBorder(7, 6, 7, 6)
		));

		JPanel titleRow = new JPanel(new BorderLayout(6, 0));
		titleRow.setBackground(CARD_BG);
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel pluginLogoLabel = new JLabel(new ImageIcon(imageService.getPluginHeaderLogo(46)));

		JPanel titleTextCol = new JPanel();
		titleTextCol.setLayout(new BoxLayout(titleTextCol, BoxLayout.Y_AXIS));
		titleTextCol.setBackground(CARD_BG);

		JLabel titleLabel = new JLabel("OSRS Clan Finder");
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(ACCENT_GOLD);

		JLabel subTitleLabel = new JLabel("Verified Directory & Rankings");
		subTitleLabel.setFont(FontManager.getRunescapeSmallFont());
		subTitleLabel.setForeground(MUTED_TEXT);

		titleTextCol.add(titleLabel);
		titleTextCol.add(subTitleLabel);

		JButton syncButton = createActionButton("Sync", "Check for verified database updates", CARD_INNER_BG, Color.WHITE);
		syncButton.addActionListener(e -> {
			syncButton.setText("...");
			syncButton.setEnabled(false);
			repositoryService.refreshFromRemoteIfConfigured(() -> SwingUtilities.invokeLater(() -> {
				syncButton.setText("Sync");
				syncButton.setEnabled(true);
				refreshClanList();
				refreshRankingsView();
			}));
		});

		titleRow.add(pluginLogoLabel, BorderLayout.WEST);
		titleRow.add(titleTextCol, BorderLayout.CENTER);
		titleRow.add(syncButton, BorderLayout.EAST);
		header.add(titleRow);
		header.add(Box.createVerticalStrut(5));

		// Mode switcher: Clan Directory vs EHB/EHP Rankings
		JPanel modeRow = new JPanel(new GridLayout(1, 2, 4, 0));
		modeRow.setBackground(CARD_BG);
		modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		directoryModeBtn = createActionButton("Clan Directory", "Browse visual clan cards, banners, and requirements", new Color(55, 46, 20), ACCENT_GOLD);
		rankingsModeBtn = createActionButton("EHB / EHP Rankings", "View official EHB & EHP leaderboards", CARD_INNER_BG, Color.LIGHT_GRAY);

		directoryModeBtn.addActionListener(e -> switchMainView(CARD_DIRECTORY));
		rankingsModeBtn.addActionListener(e -> {
			refreshRankingsView();
			switchMainView(CARD_RANKINGS);
		});

		modeRow.add(directoryModeBtn);
		modeRow.add(rankingsModeBtn);
		header.add(modeRow);
		header.add(Box.createVerticalStrut(4));

		// Quick Global Platform Links Bar (Wise Old Man, TempleOSRS, OSRS Clans)
		JPanel platformLinksRow = new JPanel(new GridLayout(1, 3, 3, 0));
		platformLinksRow.setBackground(CARD_BG);
		platformLinksRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton womDirBtn = createActionButton("WOM", "Open Wise Old Man Groups Directory", CARD_INNER_BG, new Color(130, 195, 255));
		womDirBtn.addActionListener(e -> LinkBrowser.browse(URL_WISE_OLD_MAN_GROUPS));

		JButton templeDirBtn = createActionButton("Temple", "Open TempleOSRS Verified Groups", CARD_INNER_BG, new Color(130, 195, 255));
		templeDirBtn.addActionListener(e -> LinkBrowser.browse(URL_TEMPLE_OSRS_GROUPS));

		JButton osrsClansBtn = createActionButton("OSRSClans", "Open OSRSClans.cc Directory", CARD_INNER_BG, new Color(130, 195, 255));
		osrsClansBtn.addActionListener(e -> LinkBrowser.browse(URL_OSRS_CLANS_DIRECTORY));

		platformLinksRow.add(womDirBtn);
		platformLinksRow.add(templeDirBtn);
		platformLinksRow.add(osrsClansBtn);
		header.add(platformLinksRow);

		return header;
	}

	private void switchMainView(String cardName)
	{
		boolean isDir = CARD_DIRECTORY.equals(cardName);
		boolean isRank = CARD_RANKINGS.equals(cardName);

		if (directoryModeBtn != null && rankingsModeBtn != null)
		{
			directoryModeBtn.setBackground(isDir ? new Color(55, 46, 20) : CARD_INNER_BG);
			directoryModeBtn.setForeground(isDir ? ACCENT_GOLD : Color.LIGHT_GRAY);
			rankingsModeBtn.setBackground(isRank ? new Color(55, 46, 20) : CARD_INNER_BG);
			rankingsModeBtn.setForeground(isRank ? ACCENT_GOLD : Color.LIGHT_GRAY);
		}
		cardLayout.show(mainContainer, cardName);
	}

	private JPanel buildDirectoryView()
	{
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(PANEL_BG);

		JPanel controlsPanel = new JPanel();
		controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
		controlsPanel.setBackground(PANEL_BG);
		controlsPanel.setBorder(new EmptyBorder(6, 6, 4, 6));

		// 1. Category Pills (All, PvM, Ironman, Social, Mixed / Community)
		JLabel catTitle = new JLabel("Categories:");
		catTitle.setFont(FontManager.getRunescapeSmallFont());
		catTitle.setForeground(ACCENT_GOLD);
		catTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		controlsPanel.add(catTitle);
		controlsPanel.add(Box.createVerticalStrut(3));

		JPanel catRow1 = new JPanel(new GridLayout(1, 3, 3, 0));
		catRow1.setBackground(PANEL_BG);
		catRow1.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton btnAll = createCategoryTabButton("All Clans", null);
		JButton btnPvm = createCategoryTabButton("PvM", ClanDirectoryCategory.PVM);
		JButton btnIron = createCategoryTabButton("Ironman", ClanDirectoryCategory.IRONMAN);
		catRow1.add(btnAll);
		catRow1.add(btnPvm);
		catRow1.add(btnIron);
		controlsPanel.add(catRow1);
		controlsPanel.add(Box.createVerticalStrut(3));

		JPanel catRow2 = new JPanel(new GridLayout(1, 2, 3, 0));
		catRow2.setBackground(PANEL_BG);
		catRow2.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton btnSocial = createCategoryTabButton("Social", ClanDirectoryCategory.SOCIAL);
		JButton btnMixed = createCategoryTabButton("Mixed / Community", ClanDirectoryCategory.MIXED_COMMUNITY);
		catRow2.add(btnSocial);
		catRow2.add(btnMixed);
		controlsPanel.add(catRow2);
		updateCategoryTabStyles();
		controlsPanel.add(Box.createVerticalStrut(6));

		// 2. Search Bar + Reset
		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setBackground(PANEL_BG);
		searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchField.setBackground(CARD_BG);
		searchField.setForeground(Color.WHITE);
		searchField.setCaretColor(Color.WHITE);
		searchField.setToolTipText("Search by clan name, region, or Clan Chat...");
		searchField.setBorder(new CompoundBorder(
			new LineBorder(new Color(65, 70, 80), 1),
			new EmptyBorder(4, 6, 4, 6)
		));
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refreshClanList();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refreshClanList();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refreshClanList();
			}
		});

		JButton clearSearchBtn = createActionButton("Reset", "Clear search query and all active filters", CARD_BG, Color.WHITE);
		clearSearchBtn.addActionListener(e -> resetAllFilters());

		searchRow.add(searchField, BorderLayout.CENTER);
		searchRow.add(clearSearchBtn, BorderLayout.EAST);
		controlsPanel.add(searchRow);
		controlsPanel.add(Box.createVerticalStrut(5));

		// 3. Sort Order Dropdown (Sort by EHB, EHP, Members, Alphabetical)
		sortComboBox.setFont(FontManager.getRunescapeSmallFont());
		sortComboBox.setBackground(CARD_BG);
		sortComboBox.setForeground(Color.WHITE);
		sortComboBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		sortComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		sortComboBox.addActionListener(e -> refreshClanList());
		controlsPanel.add(sortComboBox);
		controlsPanel.add(Box.createVerticalStrut(5));

		controlsPanel.add(resultCountArea);
		root.add(controlsPanel, BorderLayout.NORTH);

		clanCardsListPanel.setBorder(new EmptyBorder(2, 6, 10, 6));
		JScrollPane scrollPane = new JScrollPane(clanCardsListPanel);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBackground(PANEL_BG);
		scrollPane.setBorder(null);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);

		root.add(scrollPane, BorderLayout.CENTER);
		return root;
	}

	private JButton createCategoryTabButton(String label, ClanDirectoryCategory category)
	{
		JButton btn = createActionButton(label, "Filter by " + label, CARD_BG, Color.LIGHT_GRAY);
		btn.putClientProperty("category", category);
		btn.addActionListener(e -> {
			selectedCategoryTab = category;
			updateCategoryTabStyles();
			refreshClanList();
		});
		categoryTabButtons.add(btn);
		return btn;
	}

	private void updateCategoryTabStyles()
	{
		for (JButton btn : categoryTabButtons)
		{
			Object cat = btn.getClientProperty("category");
			boolean active = (cat == selectedCategoryTab);
			btn.setBackground(active ? new Color(55, 46, 20) : CARD_BG);
			btn.setForeground(active ? ACCENT_GOLD : Color.LIGHT_GRAY);
			btn.setBorder(new CompoundBorder(
				new LineBorder(active ? ACCENT_GOLD : new Color(60, 64, 72), 1),
				new EmptyBorder(3, 4, 3, 4)
			));
		}
	}

	private void populateFilterCheckboxes()
	{
		filterContentPanel.removeAll();

		filterContentPanel.add(createFilterCategoryLabel("Recruitment & Verification:"));
		JPanel recGrid = new JPanel(new GridLayout(0, 1, 2, 2));
		recGrid.setBackground(CARD_BG);
		recGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
		recGrid.add(cbOpenRecruitmentOnly);
		recGrid.add(cbOfficiallyVerifiedOnly);
		filterContentPanel.add(recGrid);
		filterContentPanel.add(Box.createVerticalStrut(5));

		filterContentPanel.add(createFilterCategoryLabel("Account Type Classification:"));
		JPanel accountGrid = new JPanel(new GridLayout(0, 1, 2, 2));
		accountGrid.setBackground(CARD_BG);
		accountGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
		accountGrid.add(cbIronmanOnly);
		accountGrid.add(cbMainAccountsOnly);
		accountGrid.add(cbMixedClans);
		filterContentPanel.add(accountGrid);
		filterContentPanel.add(Box.createVerticalStrut(5));

		filterContentPanel.add(createFilterCategoryLabel("Region / Community:"));
		JPanel regionGrid = new JPanel(new GridLayout(0, 1, 2, 2));
		regionGrid.setBackground(CARD_BG);
		regionGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
		regionGrid.add(cbBrazilianClans);
		regionGrid.add(cbInternationalClans);
		filterContentPanel.add(regionGrid);
		filterContentPanel.add(Box.createVerticalStrut(5));

		filterContentPanel.add(createFilterCategoryLabel("Specialization (Combinable):"));
		JPanel specGrid = new JPanel(new GridLayout(0, 2, 2, 2));
		specGrid.setBackground(CARD_BG);
		specGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
		specGrid.add(cbPvm);
		specGrid.add(cbRaids);
		specGrid.add(cbEndgamePvm);
		specGrid.add(cbSkilling);
		specGrid.add(cbSocial);
		filterContentPanel.add(specGrid);
	}

	private JLabel createFilterCategoryLabel(String text)
	{
		JLabel lbl = new JLabel(text);
		lbl.setFont(FontManager.getRunescapeSmallFont());
		lbl.setForeground(ACCENT_GOLD);
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		return lbl;
	}

	private JCheckBox createFilterCheckBox(String label)
	{
		JCheckBox box = new JCheckBox(label);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setBackground(CARD_BG);
		box.setForeground(Color.WHITE);
		box.setFocusPainted(false);
		box.addActionListener(e -> refreshClanList());
		return box;
	}

	private void resetAllFilters()
	{
		searchField.setText("");
		selectedCategoryTab = null;
		updateCategoryTabStyles();
		cbOpenRecruitmentOnly.setSelected(false);
		cbIronmanOnly.setSelected(false);
		cbMainAccountsOnly.setSelected(false);
		cbMixedClans.setSelected(false);
		cbInternationalClans.setSelected(false);
		cbBrazilianClans.setSelected(false);
		cbPvm.setSelected(false);
		cbRaids.setSelected(false);
		cbEndgamePvm.setSelected(false);
		cbSkilling.setSelected(false);
		cbSocial.setSelected(false);
		cbOfficiallyVerifiedOnly.setSelected(false);
		sortComboBox.setSelectedItem(ClanSortOrder.RANK_BY_AVERAGE_EHB);
		refreshClanList();
	}

	public ClanFilterCriteria buildCurrentFilterCriteria()
	{
		ClanSortOrder selectedSort = (ClanSortOrder) sortComboBox.getSelectedItem();
		return ClanFilterCriteria.builder()
			.searchQuery(searchField.getText())
			.selectedDirectoryCategory(selectedCategoryTab)
			.openRecruitmentOnly(cbOpenRecruitmentOnly.isSelected())
			.ironmanOnly(cbIronmanOnly.isSelected())
			.mainAccountsOnly(cbMainAccountsOnly.isSelected())
			.mixedClans(cbMixedClans.isSelected())
			.internationalClans(cbInternationalClans.isSelected())
			.brazilianClans(cbBrazilianClans.isSelected())
			.pvm(cbPvm.isSelected())
			.raids(cbRaids.isSelected())
			.endgamePvm(cbEndgamePvm.isSelected())
			.skilling(cbSkilling.isSelected())
			.social(cbSocial.isSelected())
			.onlyOfficiallyVerified(cbOfficiallyVerifiedOnly.isSelected())
			.sortOrder(selectedSort != null ? selectedSort : ClanSortOrder.RANK_BY_AVERAGE_EHB)
			.build();
	}

	public void refreshClanList()
	{
		ClanFilterCriteria criteria = buildCurrentFilterCriteria();
		List<ClanEntry> matchingClans = repositoryService.filterClans(criteria);
		int totalClans = repositoryService.getAllClans().size();

		String catLabel = selectedCategoryTab != null ? selectedCategoryTab.getDisplayName() : "All Categories";
		resultCountArea.setText("Showing " + matchingClans.size() + " of " + totalClans + " clans (" + catLabel + ")");

		clanCardsListPanel.removeAll();

		if (matchingClans.isEmpty())
		{
			JPanel emptyCard = createStyledCard(new Color(70, 75, 85));
			emptyCard.add(createWrappedLabelArea(
				"No clans match the selected filter combination.\n\n"
				+ "Click 'Reset' to view all verified clans across PvM, Ironman, Social, and Mixed / Community.",
				MUTED_TEXT
			));
			clanCardsListPanel.add(emptyCard);
		}
		else
		{
			for (ClanEntry clan : matchingClans)
			{
				clanCardsListPanel.add(buildPremiumClanCard(clan));
				clanCardsListPanel.add(Box.createVerticalStrut(10));
			}
		}

		clanCardsListPanel.revalidate();
		clanCardsListPanel.repaint();
	}

	/**
	 * Builds an esports/gaming-dashboard style Clan Card featuring:
	 * - Unique Clan Visual Banner + Crest Logo
	 * - Prominent Clan Name, Category Pills & Recruitment Status Indicator
	 * - 2x2 Metrics Dashboard (Members, Avg EHP, Avg EHB, Total XP)
	 * - Account Type Badge & Recruitment Requirements Summary
	 * - Direct Quick Links (Website, Discord, TempleOSRS, Wise Old Man)
	 */
	private JPanel buildPremiumClanCard(ClanEntry clan)
	{
		Color brandColor = ClanImageService.parseColor(
			clan.getPrimaryColorHex(),
			clan.isBrazilianClan() ? ACCENT_EMERALD : ACCENT_GOLD
		);

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD_BG);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setBorder(new CompoundBorder(
			new CompoundBorder(
				new MatteBorder(0, 3, 0, 0, brandColor),
				new MatteBorder(1, 0, 1, 1, new Color(58, 63, 74))
			),
			new EmptyBorder(6, 7, 8, 7)
		));

		// 1. Visual Banner Header (Unique per clan — never generic repeated image)
		JLabel bannerLabel = new JLabel();
		bannerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		bannerLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		BufferedImage bannerImg = imageService.getOrGenerateCardBanner(clan, 206, 54, updatedBanner ->
			SwingUtilities.invokeLater(() -> {
				bannerLabel.setIcon(new ImageIcon(updatedBanner));
				bannerLabel.repaint();
			})
		);
		bannerLabel.setIcon(new ImageIcon(bannerImg));
		bannerLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				openClanDetails(clan, false);
			}
		});
		card.add(bannerLabel);
		card.add(Box.createVerticalStrut(6));

		// 2. Logo Crest + Clan Name + Category Row
		JPanel identityRow = new JPanel(new BorderLayout(6, 0));
		identityRow.setBackground(CARD_BG);
		identityRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel logoLabel = new JLabel();
		BufferedImage logoImg = imageService.getOrGenerateLogo(clan, 36, updatedLogo ->
			SwingUtilities.invokeLater(() -> {
				logoLabel.setIcon(new ImageIcon(updatedLogo));
				logoLabel.repaint();
			})
		);
		logoLabel.setIcon(new ImageIcon(logoImg));
		identityRow.add(logoLabel, BorderLayout.WEST);

		JPanel titleAndCatCol = new JPanel();
		titleAndCatCol.setLayout(new BoxLayout(titleAndCatCol, BoxLayout.Y_AXIS));
		titleAndCatCol.setBackground(CARD_BG);

		JTextArea nameArea = createWrappedLabelArea(clan.getClanName(), ACCENT_GOLD);
		nameArea.setFont(FontManager.getRunescapeBoldFont());
		titleAndCatCol.add(nameArea);

		String catText = formatDirectoryCategories(clan) + "  •  " + (clan.getCountryOrRegion() != null ? clan.getCountryOrRegion() : "Global");
		titleAndCatCol.add(createWrappedLabelArea(catText, new Color(150, 205, 255)));

		identityRow.add(titleAndCatCol, BorderLayout.CENTER);
		card.add(identityRow);
		card.add(Box.createVerticalStrut(5));

		// 3. Recruitment Status Badge + Account Type Classification Badge
		RecruitmentAvailabilityStatus recAvail = clan.getRecruitmentAvailability() != null
			? clan.getRecruitmentAvailability()
			: RecruitmentAvailabilityStatus.UNKNOWN;
		card.add(createWrappedBadgePanel("Recruitment: " + recAvail.getDisplayName(), recAvail.getBadgeColor()));
		card.add(Box.createVerticalStrut(3));

		AccountTypeClassification accType = clan.getAccountType() != null
			? clan.getAccountType()
			: AccountTypeClassification.UNCONFIRMED;
		card.add(createWrappedBadgePanel("Account Type: " + accType.getDisplayName(), accType.getBadgeColor()));
		card.add(Box.createVerticalStrut(5));

		// 4. Short Description
		String shortDesc = clan.getShortDescription() != null ? clan.getShortDescription() : clan.getDescription();
		if (shortDesc != null && !shortDesc.isEmpty())
		{
			card.add(createWrappedLabelArea(shortDesc, Color.LIGHT_GRAY));
			card.add(Box.createVerticalStrut(6));
		}

		// 5. Statistics Dashboard Grid (Members, Avg EHP, Avg EHB, Total XP)
		card.add(buildStatsDashboardGrid(clan, false));
		card.add(Box.createVerticalStrut(6));

		// 6. Recruitment Requirements Summary
		JLabel reqTitle = new JLabel("Recruitment Requirements:");
		reqTitle.setFont(FontManager.getRunescapeSmallFont());
		reqTitle.setForeground(ACCENT_GOLD);
		reqTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(reqTitle);
		card.add(Box.createVerticalStrut(2));

		card.add(createWrappedLabelArea(buildCardRequirementSummary(clan), Color.WHITE));
		card.add(Box.createVerticalStrut(6));

		// 7. Quick External Links Bar (Website, Discord, TempleOSRS, Wise Old Man)
		card.add(buildCardQuickLinksPanel(clan));
		card.add(Box.createVerticalStrut(6));

		// 8. Action Buttons (View Profile & Check My Eligibility)
		JButton detailsBtn = createFullWidthActionButton(
			"Open Clan Profile & Full Stats",
			"View larger banner, full description, activities, historical stats & links",
			new Color(38, 84, 145),
			Color.WHITE
		);
		detailsBtn.addActionListener(e -> openClanDetails(clan, false));
		card.add(detailsBtn);
		card.add(Box.createVerticalStrut(4));

		JButton checkEligibilityBtn = createFullWidthActionButton(
			"Check My Eligibility",
			"Compare your OSRS stats against this clan's published requirements",
			new Color(58, 48, 20),
			ACCENT_GOLD
		);
		checkEligibilityBtn.addActionListener(e -> openClanDetails(clan, true));
		card.add(checkEligibilityBtn);

		return card;
	}

	private JPanel buildStatsDashboardGrid(ClanEntry clan, boolean includeTotals)
	{
		NumberFormat intFmt = NumberFormat.getIntegerInstance(Locale.US);

		int rows = includeTotals ? 3 : 2;
		JPanel grid = new JPanel(new GridLayout(rows, 2, 4, 4));
		grid.setBackground(CARD_BG);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);

		String membersStr = (clan.getVerifiedMemberCount() != null && clan.getVerifiedMemberCount() > 0)
			? intFmt.format(clan.getVerifiedMemberCount())
			: "N/A";
		String avgEhpStr = clan.getEffectiveAverageEhp() > 0
			? intFmt.format(Math.round(clan.getEffectiveAverageEhp()))
			: "N/A";
		String avgEhbStr = clan.getEffectiveAverageEhb() > 0
			? intFmt.format(Math.round(clan.getEffectiveAverageEhb()))
			: "N/A";
		String totalXpStr = clan.getEffectiveTotalXp() > 0
			? formatCompactXp(clan.getEffectiveTotalXp())
			: "N/A";

		grid.add(createMetricTile("Members", membersStr, Color.WHITE));
		grid.add(createMetricTile("Total XP", totalXpStr, new Color(130, 225, 160)));
		grid.add(createMetricTile("Avg EHP", avgEhpStr, new Color(120, 195, 255)));
		grid.add(createMetricTile("Avg EHB", avgEhbStr, ACCENT_GOLD));

		if (includeTotals)
		{
			String totEhpStr = clan.getEffectiveTotalEhp() > 0
				? intFmt.format(Math.round(clan.getEffectiveTotalEhp()))
				: "N/A";
			String totEhbStr = clan.getEffectiveTotalEhb() > 0
				? intFmt.format(Math.round(clan.getEffectiveTotalEhb()))
				: "N/A";
			grid.add(createMetricTile("Total EHP", totEhpStr, new Color(120, 195, 255)));
			grid.add(createMetricTile("Total EHB", totEhbStr, ACCENT_GOLD));
		}

		return grid;
	}

	private JPanel createMetricTile(String label, String value, Color valueColor)
	{
		JPanel tile = new JPanel();
		tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
		tile.setBackground(CARD_INNER_BG);
		tile.setBorder(new CompoundBorder(
			new LineBorder(new Color(50, 54, 64), 1),
			new EmptyBorder(4, 6, 4, 6)
		));

		JLabel lbl = new JLabel(label);
		lbl.setFont(FontManager.getRunescapeSmallFont());
		lbl.setForeground(MUTED_TEXT);

		JLabel val = new JLabel(value);
		val.setFont(FontManager.getRunescapeBoldFont());
		val.setForeground(valueColor);

		tile.add(lbl);
		tile.add(val);
		return tile;
	}

	private JPanel buildCardQuickLinksPanel(ClanEntry clan)
	{
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setBackground(CARD_INNER_BG);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.setBorder(new CompoundBorder(
			new LineBorder(new Color(52, 58, 70), 1),
			new EmptyBorder(4, 5, 5, 5)
		));

		String primaryUrl = resolvePrimaryRecruitmentUrl(clan);
		String primaryLabel = resolvePrimaryRecruitmentLinkLabel(clan);
		wrapper.add(createWrappedLabelArea(primaryLabel + ": " + primaryUrl, new Color(145, 200, 255)));
		wrapper.add(Box.createVerticalStrut(4));

		List<JButton> linkButtons = new ArrayList<>();

		if (isValidHttpUrl(clan.getOfficialWebsite()))
		{
			JButton webBtn = createActionButton("Website", clan.getOfficialWebsite(), new Color(36, 76, 128), Color.WHITE);
			webBtn.addActionListener(e -> LinkBrowser.browse(clan.getOfficialWebsite().trim()));
			linkButtons.add(webBtn);
		}
		if (isValidHttpUrl(clan.getDiscordInvitation()))
		{
			JButton discBtn = createActionButton("Discord", clan.getDiscordInvitation(), new Color(68, 78, 145), Color.WHITE);
			discBtn.addActionListener(e -> LinkBrowser.browse(clan.getDiscordInvitation().trim()));
			linkButtons.add(discBtn);
		}
		if (isValidHttpUrl(clan.getTempleOsrsGroupUrl()))
		{
			JButton templeBtn = createActionButton("Temple", clan.getTempleOsrsGroupUrl(), new Color(45, 52, 64), Color.LIGHT_GRAY);
			templeBtn.addActionListener(e -> LinkBrowser.browse(clan.getTempleOsrsGroupUrl().trim()));
			linkButtons.add(templeBtn);
		}
		if (isValidHttpUrl(clan.getWiseOldManGroupUrl()))
		{
			JButton womBtn = createActionButton("WOM", clan.getWiseOldManGroupUrl(), new Color(45, 52, 64), Color.LIGHT_GRAY);
			womBtn.addActionListener(e -> LinkBrowser.browse(clan.getWiseOldManGroupUrl().trim()));
			linkButtons.add(womBtn);
		}

		if (!linkButtons.isEmpty())
		{
			int cols = Math.min(2, linkButtons.size());
			int rows = (int) Math.ceil(linkButtons.size() / 2.0);
			JPanel btnGrid = new JPanel(new GridLayout(rows, cols, 3, 3));
			btnGrid.setBackground(CARD_INNER_BG);
			btnGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
			for (JButton b : linkButtons)
			{
				btnGrid.add(b);
			}
			wrapper.add(btnGrid);
		}

		return wrapper;
	}

	/**
	 * Builds the independent Rankings View (Section 5):
	 * - Independent EHB Ranking & EHP Ranking quick tabs
	 * - Metric switcher allowing switching between Average EHB, Average EHP, Total EHB, Total EHP, Members Count, and Total XP
	 *   (strictly separated so averages are never mixed with totals)
	 * - Rank position, variation indicator vs previous update, members considered, and last update timestamp
	 */
	private JPanel buildRankingsView()
	{
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(PANEL_BG);

		JPanel topControls = new JPanel();
		topControls.setLayout(new BoxLayout(topControls, BoxLayout.Y_AXIS));
		topControls.setBackground(PANEL_BG);
		topControls.setBorder(new EmptyBorder(6, 6, 6, 6));

		JTextArea rankTitle = createWrappedLabelArea("Clan Efficiency & Roster Rankings", ACCENT_GOLD);
		rankTitle.setFont(FontManager.getRunescapeBoldFont());
		topControls.add(rankTitle);
		topControls.add(Box.createVerticalStrut(4));

		// Quick preset buttons for the two main independent rankings: Ranking EHB vs Ranking EHP
		JPanel mainRankPresets = new JPanel(new GridLayout(1, 2, 4, 0));
		mainRankPresets.setBackground(PANEL_BG);
		mainRankPresets.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton ehbRankBtn = createActionButton("Ranking EHB (Avg)", "Order clans by highest Average EHB", new Color(55, 46, 20), ACCENT_GOLD);
		ehbRankBtn.addActionListener(e -> {
			rankingMetricCombo.setSelectedItem(RankingMetric.AVERAGE_EHB);
		});

		JButton ehpRankBtn = createActionButton("Ranking EHP (Avg)", "Order clans by highest Average EHP", CARD_BG, new Color(120, 195, 255));
		ehpRankBtn.addActionListener(e -> {
			rankingMetricCombo.setSelectedItem(RankingMetric.AVERAGE_EHP);
		});

		mainRankPresets.add(ehbRankBtn);
		mainRankPresets.add(ehpRankBtn);
		topControls.add(mainRankPresets);
		topControls.add(Box.createVerticalStrut(5));

		JLabel metricSelectLbl = new JLabel("Active Ranking Metric (Averages & Totals Separated):");
		metricSelectLbl.setFont(FontManager.getRunescapeSmallFont());
		metricSelectLbl.setForeground(Color.LIGHT_GRAY);
		metricSelectLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		topControls.add(metricSelectLbl);
		topControls.add(Box.createVerticalStrut(3));

		rankingMetricCombo.setFont(FontManager.getRunescapeSmallFont());
		rankingMetricCombo.setBackground(CARD_BG);
		rankingMetricCombo.setForeground(Color.WHITE);
		rankingMetricCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
		rankingMetricCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		rankingMetricCombo.addActionListener(e -> {
			RankingMetric chosen = (RankingMetric) rankingMetricCombo.getSelectedItem();
			if (chosen != null)
			{
				selectedRankingMetric = chosen;
				refreshRankingsView();
			}
		});
		topControls.add(rankingMetricCombo);
		topControls.add(Box.createVerticalStrut(5));

		topControls.add(rankingInfoArea);
		root.add(topControls, BorderLayout.NORTH);

		rankingsListPanel.setBorder(new EmptyBorder(2, 6, 10, 6));
		JScrollPane scroll = new JScrollPane(rankingsListPanel);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBackground(PANEL_BG);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		root.add(scroll, BorderLayout.CENTER);
		return root;
	}

	public void refreshRankingsView()
	{
		List<ClanEntry> ranked = repositoryService.getRankedClans(selectedRankingMetric);
		String modeNote = selectedRankingMetric.isAverageMetric()
			? "Displaying per-member AVERAGE values only."
			: "Displaying cumulative clan TOTAL values only.";

		rankingInfoArea.setText("Metric: " + selectedRankingMetric.getDisplayName() + "\n"
			+ modeNote + "\n"
			+ "Last Updated: " + repositoryService.getLastDatabaseSyncTimestamp());

		rankingsListPanel.removeAll();

		int position = 1;
		for (ClanEntry clan : ranked)
		{
			rankingsListPanel.add(buildRankingEntryRow(clan, position, selectedRankingMetric));
			rankingsListPanel.add(Box.createVerticalStrut(6));
			position++;
		}

		rankingsListPanel.revalidate();
		rankingsListPanel.repaint();
	}

	private JPanel buildRankingEntryRow(ClanEntry clan, int currentRank, RankingMetric metric)
	{
		Color medalColor;
		if (currentRank == 1)
		{
			medalColor = new Color(255, 205, 55);
		}
		else if (currentRank == 2)
		{
			medalColor = new Color(195, 200, 210);
		}
		else if (currentRank == 3)
		{
			medalColor = new Color(215, 140, 80);
		}
		else
		{
			medalColor = new Color(75, 82, 96);
		}

		JPanel rowCard = new JPanel();
		rowCard.setLayout(new BoxLayout(rowCard, BoxLayout.Y_AXIS));
		rowCard.setBackground(CARD_BG);
		rowCard.setAlignmentX(Component.LEFT_ALIGNMENT);
		rowCard.setBorder(new CompoundBorder(
			new CompoundBorder(
				new MatteBorder(0, 3, 0, 0, medalColor),
				new MatteBorder(1, 0, 1, 1, new Color(55, 60, 70))
			),
			new EmptyBorder(6, 7, 6, 7)
		));

		JPanel topLine = new JPanel(new BorderLayout(6, 0));
		topLine.setBackground(CARD_BG);
		topLine.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel logoLabel = new JLabel();
		BufferedImage logo = imageService.getOrGenerateLogo(clan, 28, updated ->
			SwingUtilities.invokeLater(() -> {
				logoLabel.setIcon(new ImageIcon(updated));
				logoLabel.repaint();
			})
		);
		logoLabel.setIcon(new ImageIcon(logo));
		topLine.add(logoLabel, BorderLayout.WEST);

		String deltaStr = formatRankDelta(clan, currentRank, metric);
		JTextArea rankAndName = createWrappedLabelArea(
			"#" + currentRank + "  " + clan.getClanName() + "  " + deltaStr,
			currentRank <= 3 ? medalColor : Color.WHITE
		);
		rankAndName.setFont(FontManager.getRunescapeBoldFont());
		topLine.add(rankAndName, BorderLayout.CENTER);

		rowCard.add(topLine);
		rowCard.add(Box.createVerticalStrut(4));

		String formattedMetricVal = formatRankingMetricValue(clan, metric);
		int membersConsidered = clan.getVerifiedMemberCount() != null ? clan.getVerifiedMemberCount() : 0;
		String verDate = clan.getLastVerificationDate() != null ? clan.getLastVerificationDate() : "2026-11-21";

		JTextArea statsLine = createWrappedLabelArea(
			metric.getDisplayName() + ": " + formattedMetricVal + "\n"
			+ "Members Considered: " + membersConsidered + "  |  Updated: " + verDate,
			new Color(145, 205, 255)
		);
		rowCard.add(statsLine);
		rowCard.add(Box.createVerticalStrut(4));

		JButton openBtn = createFullWidthActionButton(
			"View " + clan.getClanName() + " Profile",
			"Open full details for " + clan.getClanName(),
			CARD_INNER_BG,
			Color.LIGHT_GRAY
		);
		openBtn.addActionListener(e -> openClanDetails(clan, false));
		rowCard.add(openBtn);

		return rowCard;
	}

	private String formatRankDelta(ClanEntry clan, int currentRank, RankingMetric metric)
	{
		Integer prevRank = null;
		if (metric == RankingMetric.AVERAGE_EHB || metric == RankingMetric.TOTAL_EHB)
		{
			prevRank = clan.getPreviousEhbRank();
		}
		else if (metric == RankingMetric.AVERAGE_EHP || metric == RankingMetric.TOTAL_EHP)
		{
			prevRank = clan.getPreviousEhpRank();
		}
		if (prevRank == null || prevRank <= 0)
		{
			return "[=]";
		}
		int diff = prevRank - currentRank;
		if (diff > 0)
		{
			return "[+" + diff + "]";
		}
		else if (diff < 0)
		{
			return "[" + diff + "]";
		}
		return "[=]";
	}

	private String formatRankingMetricValue(ClanEntry clan, RankingMetric metric)
	{
		NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
		switch (metric)
		{
			case AVERAGE_EHB:
				return nf.format(Math.round(clan.getEffectiveAverageEhb())) + " Avg EHB / member";
			case AVERAGE_EHP:
				return nf.format(Math.round(clan.getEffectiveAverageEhp())) + " Avg EHP / member";
			case TOTAL_EHB:
				return nf.format(Math.round(clan.getEffectiveTotalEhb())) + " Total Clan EHB";
			case TOTAL_EHP:
				return nf.format(Math.round(clan.getEffectiveTotalEhp())) + " Total Clan EHP";
			case MEMBER_COUNT:
				return nf.format(clan.getVerifiedMemberCount() != null ? clan.getVerifiedMemberCount() : 0) + " Tracked Members";
			case TOTAL_XP:
			default:
				return formatCompactXp(clan.getEffectiveTotalXp()) + " (" + nf.format(clan.getEffectiveTotalXp()) + " XP)";
		}
	}

	private static String formatCompactXp(long xp)
	{
		if (xp >= 1_000_000_000L)
		{
			return String.format(Locale.US, "%.2fB XP", xp / 1_000_000_000.0);
		}
		if (xp >= 1_000_000L)
		{
			return String.format(Locale.US, "%.1fM XP", xp / 1_000_000.0);
		}
		return NumberFormat.getIntegerInstance(Locale.US).format(xp) + " XP";
	}

	private String buildCardRequirementSummary(ClanEntry clan)
	{
		if (clan.getRequirementAvailability() == RequirementDataAvailability.NOT_PUBLICLY_AVAILABLE
			|| clan.getRequirementAvailability() == RequirementDataAvailability.UNCLEAR)
		{
			List<ClanRequirement> reqs = clan.getOfficialRecruitmentRequirements();
			if (reqs == null || reqs.isEmpty())
			{
				return UNCONFIRMED_REQS_MESSAGE;
			}
		}
		List<ClanRequirement> reqs = clan.getOfficialRecruitmentRequirements();
		if (reqs == null || reqs.isEmpty())
		{
			return UNCONFIRMED_REQS_MESSAGE;
		}
		return reqs.stream()
			.map(r -> "• " + r.getExactRequirement())
			.collect(Collectors.joining("\n"));
	}

	private JPanel buildDetailsView()
	{
		detailsViewPanel.setBackground(PANEL_BG);

		JPanel navBar = new JPanel(new GridLayout(1, 2, 4, 0));
		navBar.setBackground(CARD_BG);
		navBar.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, new Color(55, 60, 70)),
			new EmptyBorder(6, 6, 6, 6)
		));

		JButton backButton = createActionButton("<- Back to List", "Return to the Clan Directory list", CARD_INNER_BG, Color.WHITE);
		backButton.addActionListener(e -> switchMainView(CARD_DIRECTORY));

		JButton liveVerifyButton = createActionButton("Sync WOM Live", "Fetch live group metadata from Wise Old Man API", CARD_INNER_BG, ACCENT_GOLD);
		liveVerifyButton.addActionListener(e -> {
			if (currentlySelectedClan != null)
			{
				liveVerifyButton.setText("Syncing...");
				liveVerifyButton.setEnabled(false);
				repositoryService.verifyClanWithWiseOldMan(currentlySelectedClan, () -> SwingUtilities.invokeLater(() -> {
					liveVerifyButton.setText("Sync WOM Live");
					liveVerifyButton.setEnabled(true);
					renderClanDetailsContent(currentlySelectedClan, latestEligibilityReport != null);
					refreshClanList();
					refreshRankingsView();
				}));
			}
		});

		navBar.add(backButton);
		navBar.add(liveVerifyButton);
		detailsViewPanel.add(navBar, BorderLayout.NORTH);

		detailsContentContainer.setBorder(new EmptyBorder(8, 6, 12, 6));

		JScrollPane detailsScroll = new JScrollPane(detailsContentContainer);
		detailsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		detailsScroll.setBackground(PANEL_BG);
		detailsScroll.setBorder(null);
		detailsScroll.getVerticalScrollBar().setUnitIncrement(16);

		detailsViewPanel.add(detailsScroll, BorderLayout.CENTER);
		return detailsViewPanel;
	}

	public void openClanDetails(ClanEntry clan, boolean runEligibilityImmediately)
	{
		this.currentlySelectedClan = clan;
		if (runEligibilityImmediately)
		{
			PlayerStatsSnapshot snapshot = eligibilityService.captureInGamePlayerStats();
			this.latestEligibilityReport = eligibilityService.evaluateClanEligibility(clan, snapshot);
		}
		else
		{
			this.latestEligibilityReport = null;
		}
		renderClanDetailsContent(clan, runEligibilityImmediately);
		cardLayout.show(mainContainer, CARD_DETAILS);
	}

	private void renderClanDetailsContent(ClanEntry clan, boolean showEligibilityReport)
	{
		detailsContentContainer.removeAll();
		if (clan == null)
		{
			return;
		}

		Color brandColor = ClanImageService.parseColor(clan.getPrimaryColorHex(), ACCENT_GOLD);

		// 1. Hero Visual Card: Larger Banner + Official Logo + Full Description
		JPanel heroCard = createStyledCard(brandColor);

		JLabel largeBannerLabel = new JLabel();
		largeBannerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		BufferedImage largeBanner = imageService.getOrGenerateCardBanner(clan, 206, 82, updated ->
			SwingUtilities.invokeLater(() -> {
				largeBannerLabel.setIcon(new ImageIcon(updated));
				largeBannerLabel.repaint();
			})
		);
		largeBannerLabel.setIcon(new ImageIcon(largeBanner));
		heroCard.add(largeBannerLabel);
		heroCard.add(Box.createVerticalStrut(7));

		JPanel logoAndTitleRow = new JPanel(new BorderLayout(8, 0));
		logoAndTitleRow.setBackground(CARD_BG);
		logoAndTitleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel largeLogoLabel = new JLabel();
		BufferedImage largeLogo = imageService.getOrGenerateLogo(clan, 48, updated ->
			SwingUtilities.invokeLater(() -> {
				largeLogoLabel.setIcon(new ImageIcon(updated));
				largeLogoLabel.repaint();
			})
		);
		largeLogoLabel.setIcon(new ImageIcon(largeLogo));
		logoAndTitleRow.add(largeLogoLabel, BorderLayout.WEST);

		JPanel titleBlock = new JPanel();
		titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
		titleBlock.setBackground(CARD_BG);

		JTextArea titleArea = createWrappedLabelArea(clan.getClanName(), ACCENT_GOLD);
		titleArea.setFont(FontManager.getRunescapeBoldFont());
		titleBlock.add(titleArea);
		titleBlock.add(createWrappedLabelArea("Categories: " + formatDirectoryCategories(clan), new Color(145, 205, 255)));

		logoAndTitleRow.add(titleBlock, BorderLayout.CENTER);
		heroCard.add(logoAndTitleRow);
		heroCard.add(Box.createVerticalStrut(6));

		RecruitmentAvailabilityStatus recAvail = clan.getRecruitmentAvailability() != null
			? clan.getRecruitmentAvailability()
			: RecruitmentAvailabilityStatus.UNKNOWN;
		heroCard.add(createWrappedBadgePanel("Recruitment Status: " + recAvail.getDisplayName(), recAvail.getBadgeColor()));
		heroCard.add(Box.createVerticalStrut(4));

		AccountTypeClassification accType = clan.getAccountType() != null
			? clan.getAccountType()
			: AccountTypeClassification.UNCONFIRMED;
		heroCard.add(createWrappedBadgePanel("Account Classification: " + accType.getDisplayName(), accType.getBadgeColor()));
		heroCard.add(Box.createVerticalStrut(4));

		VerificationStatus verStatus = clan.getOverallVerificationStatus() != null
			? clan.getOverallVerificationStatus()
			: VerificationStatus.UNVERIFIED;
		heroCard.add(createWrappedBadgePanel("Verification: " + verStatus.getDisplayName(), verStatus.getBadgeColor()));
		heroCard.add(Box.createVerticalStrut(7));

		heroCard.add(createWrappedLabelArea(clan.getDescription() != null ? clan.getDescription() : "Unavailable", Color.WHITE));
		heroCard.add(Box.createVerticalStrut(7));

		// Complete Clan Statistics Grid (Averages & Totals cleanly separated)
		heroCard.add(buildStatsDashboardGrid(clan, true));
		heroCard.add(Box.createVerticalStrut(6));

		if (clan.getHistoricalStatsSummary() != null && !clan.getHistoricalStatsSummary().isEmpty())
		{
			heroCard.add(createWrappedLabelArea("Historical & Tracker Summary:\n" + clan.getHistoricalStatsSummary(), new Color(180, 215, 245)));
			heroCard.add(Box.createVerticalStrut(6));
		}

		String cc = clan.getInGameClanChat() != null ? clan.getInGameClanChat() : "Unavailable";
		String hw = (clan.getHomeWorld() != null && clan.getHomeWorld() > 0) ? "World " + clan.getHomeWorld() : "Unavailable";

		String metaDetails = "Country / Region: " + (clan.getCountryOrRegion() != null ? clan.getCountryOrRegion() : "Unavailable") + "\n"
			+ "In-Game Clan Chat: " + cc + "  |  Home World: " + hw + "\n"
			+ "Specializations: " + formatSpecializations(clan) + "\n"
			+ "Last Verification Date: " + clan.getLastVerificationDate();
		heroCard.add(createWrappedLabelArea(metaDetails, MUTED_TEXT));

		detailsContentContainer.add(heroCard);
		detailsContentContainer.add(Box.createVerticalStrut(8));

		// 2. Clan Activities Performed (Section 4 requirement)
		if (clan.getActivities() != null && !clan.getActivities().isEmpty())
		{
			JPanel activitiesCard = createStyledCard(new Color(65, 72, 86));
			JTextArea actHeader = createWrappedLabelArea("Clan Activities & Events", ACCENT_GOLD);
			actHeader.setFont(FontManager.getRunescapeBoldFont());
			activitiesCard.add(actHeader);
			activitiesCard.add(Box.createVerticalStrut(5));

			String actList = clan.getActivities().stream()
				.map(a -> "• " + a)
				.collect(Collectors.joining("\n"));
			activitiesCard.add(createWrappedLabelArea(actList, Color.WHITE));

			detailsContentContainer.add(activitiesCard);
			detailsContentContainer.add(Box.createVerticalStrut(8));
		}

		// 3. Official Recruitment Requirements (Section 6)
		JPanel reqSectionCard = createStyledCard(new Color(65, 72, 86));
		JTextArea reqHeader = createWrappedLabelArea("Official Recruitment Requirements", ACCENT_GOLD);
		reqHeader.setFont(FontManager.getRunescapeBoldFont());
		reqSectionCard.add(reqHeader);
		reqSectionCard.add(Box.createVerticalStrut(6));

		List<ClanRequirement> requirements = clan.getOfficialRecruitmentRequirements();
		if (requirements == null || requirements.isEmpty())
		{
			reqSectionCard.add(createWrappedBadgePanel(
				UNCONFIRMED_REQS_MESSAGE,
				VerificationStatus.OUTDATED.getBadgeColor()
			));
		}
		else
		{
			for (ClanRequirement req : requirements)
			{
				reqSectionCard.add(buildRequirementDetailBox(req));
				reqSectionCard.add(Box.createVerticalStrut(6));
			}
		}

		detailsContentContainer.add(reqSectionCard);
		detailsContentContainer.add(Box.createVerticalStrut(8));

		// 4. Player Eligibility Checker ("Check My Eligibility")
		JPanel eligibilityCard = createStyledCard(ACCENT_GOLD);
		JTextArea eligTitle = createWrappedLabelArea("Player Eligibility Checker", ACCENT_GOLD);
		eligTitle.setFont(FontManager.getRunescapeBoldFont());
		eligibilityCard.add(eligTitle);
		eligibilityCard.add(Box.createVerticalStrut(4));

		PlayerStatsSnapshot currentStats = eligibilityService.captureInGamePlayerStats();
		if (rsnLookupField.getText().trim().isEmpty() && currentStats.isLoggedIn() && !"Not Logged In".equals(currentStats.getPlayerName()))
		{
			rsnLookupField.setText(currentStats.getPlayerName());
		}

		eligibilityCard.add(createWrappedLabelArea("RSN (In-Game or Hiscores / Wise Old Man lookup):", Color.LIGHT_GRAY));
		eligibilityCard.add(Box.createVerticalStrut(3));

		JPanel rsnInputRow = new JPanel(new BorderLayout(4, 0));
		rsnInputRow.setBackground(CARD_BG);
		rsnInputRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		rsnLookupField.setBackground(CARD_INNER_BG);
		rsnLookupField.setForeground(Color.WHITE);
		rsnLookupField.setCaretColor(Color.WHITE);
		rsnLookupField.setBorder(new CompoundBorder(
			new LineBorder(new Color(65, 70, 80), 1),
			new EmptyBorder(4, 6, 4, 6)
		));
		rsnInputRow.add(rsnLookupField, BorderLayout.CENTER);
		eligibilityCard.add(rsnInputRow);
		eligibilityCard.add(Box.createVerticalStrut(6));

		JButton checkNowBtn = createFullWidthActionButton(
			"Check My Eligibility (Current Stats)",
			"Compare current stats against published requirements",
			new Color(60, 50, 22),
			ACCENT_GOLD
		);
		checkNowBtn.addActionListener(e -> {
			PlayerStatsSnapshot stats = eligibilityService.captureInGamePlayerStats();
			latestEligibilityReport = eligibilityService.evaluateClanEligibility(clan, stats);
			renderClanDetailsContent(clan, true);
		});
		eligibilityCard.add(checkNowBtn);
		eligibilityCard.add(Box.createVerticalStrut(4));

		JButton fetchWomBtn = createFullWidthActionButton(
			"Lookup RSN on Hiscores / Wise Old Man",
			"Fetch stats, Raid KC, Boss KC & EHB for the entered RSN",
			new Color(38, 84, 145),
			Color.WHITE
		);
		fetchWomBtn.addActionListener(e -> {
			String rsn = rsnLookupField.getText().trim();
			fetchWomBtn.setText("Fetching RSN Profile...");
			fetchWomBtn.setEnabled(false);
			eligibilityService.fetchPlayerHiscoresProfile(rsn, fetchedStats -> SwingUtilities.invokeLater(() -> {
				fetchWomBtn.setText("Lookup RSN on Hiscores / Wise Old Man");
				fetchWomBtn.setEnabled(true);
				latestEligibilityReport = eligibilityService.evaluateClanEligibility(clan, fetchedStats);
				renderClanDetailsContent(clan, true);
			}));
		});
		eligibilityCard.add(fetchWomBtn);

		if (showEligibilityReport)
		{
			if (latestEligibilityReport == null)
			{
				latestEligibilityReport = eligibilityService.evaluateClanEligibility(clan, currentStats);
			}
			eligibilityCard.add(Box.createVerticalStrut(8));
			eligibilityCard.add(buildEligibilityReportPanel(latestEligibilityReport));
		}

		detailsContentContainer.add(eligibilityCard);
		detailsContentContainer.add(Box.createVerticalStrut(8));

		// 5. Official Clan Links & Traceable Sources (Section 7)
		detailsContentContainer.add(buildLinksAndSourcesCard(clan));

		detailsContentContainer.revalidate();
		detailsContentContainer.repaint();
	}

	private JPanel buildRequirementDetailBox(ClanRequirement req)
	{
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(CARD_INNER_BG);
		box.setBorder(new CompoundBorder(
			new LineBorder(new Color(58, 63, 74), 1),
			new EmptyBorder(6, 6, 6, 6)
		));
		box.setAlignmentX(Component.LEFT_ALIGNMENT);

		String categoryName = req.getType() != null ? req.getType().getDisplayName() : "Requirement";
		JTextArea typeArea = createWrappedLabelArea(categoryName, ACCENT_GOLD);
		typeArea.setFont(FontManager.getRunescapeBoldFont());
		box.add(typeArea);
		box.add(Box.createVerticalStrut(3));

		box.add(createWrappedLabelArea(req.getExactRequirement(), Color.WHITE));
		box.add(Box.createVerticalStrut(4));

		VerificationStatus status = req.getVerificationStatus() != null
			? req.getVerificationStatus()
			: VerificationStatus.UNVERIFIED;
		box.add(createWrappedBadgePanel(status.getDisplayName(), status.getBadgeColor()));
		box.add(Box.createVerticalStrut(4));

		String verDate = req.getLastVerificationDate() != null ? req.getLastVerificationDate() : "2026-11-21";
		String sourceUrl = req.getSourceUrl() != null ? req.getSourceUrl() : "Unavailable";

		box.add(createWrappedLabelArea("Last Verified: " + verDate + "\nSource URL: " + sourceUrl, MUTED_TEXT));

		if (isValidHttpUrl(sourceUrl))
		{
			box.add(Box.createVerticalStrut(4));
			JButton openSourceBtn = createFullWidthActionButton(
				"Open Requirement Source URL",
				sourceUrl,
				new Color(38, 84, 145),
				Color.WHITE
			);
			openSourceBtn.addActionListener(e -> LinkBrowser.browse(sourceUrl));
			box.add(openSourceBtn);
		}

		return box;
	}

	private JPanel buildEligibilityReportPanel(ClanEligibilityReport report)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(CARD_INNER_BG);
		panel.setBorder(new CompoundBorder(
			new LineBorder(ACCENT_GOLD, 1),
			new EmptyBorder(8, 8, 8, 8)
		));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		PlayerStatsSnapshot p = report.getPlayerStats();
		String playerHeader = p.isLoggedIn()
			? "Account Checked: " + p.getPlayerName() + " (Combat " + p.getCombatLevel() + " | Total " + p.getTotalLevel() + ")"
			: "Account Checked: Not Logged In (Enter RSN above or log into OSRS)";

		String summaryCounts = playerHeader + "\n"
			+ "Met: " + report.getMetCount()
			+ "  |  Not Met: " + report.getNotMetCount()
			+ "  |  Unable to Verify: " + report.getUnableToVerifyCount()
			+ "  |  N/A: " + report.getNotApplicableCount();

		panel.add(createWrappedLabelArea(summaryCounts, Color.WHITE));
		panel.add(Box.createVerticalStrut(6));

		for (RequirementEvaluationResult eval : report.getEvaluations())
		{
			JPanel evalBox = new JPanel();
			evalBox.setLayout(new BoxLayout(evalBox, BoxLayout.Y_AXIS));
			evalBox.setBackground(CARD_BG);
			evalBox.setBorder(new CompoundBorder(
				new LineBorder(eval.getStatus().getStatusColor(), 1),
				new EmptyBorder(6, 6, 6, 6)
			));
			evalBox.setAlignmentX(Component.LEFT_ALIGNMENT);

			evalBox.add(createWrappedBadgePanel(eval.getStatus().getDisplayName(), eval.getStatus().getStatusColor()));
			evalBox.add(Box.createVerticalStrut(4));

			String evalDetails = "Requirement: " + eval.getRequirement().getExactRequirement() + "\n"
				+ "Required Value: " + eval.getRequirementValueDisplay() + "\n"
				+ "Your Current Value: " + eval.getPlayerCurrentValueDisplay() + "\n"
				+ "Progress Remaining: " + eval.getRemainingProgressDisplay();

			evalBox.add(createWrappedLabelArea(evalDetails, Color.LIGHT_GRAY));

			if (eval.isManualConfirmationRequired())
			{
				evalBox.add(Box.createVerticalStrut(3));
				evalBox.add(createWrappedLabelArea(
					"Note: Manual confirmation with clan leadership is required.",
					new Color(255, 190, 80)
				));
			}

			panel.add(evalBox);
			panel.add(Box.createVerticalStrut(6));
		}

		panel.add(createWrappedLabelArea("IMPORTANT NOTICE: " + report.getSummaryDisclaimer(), new Color(255, 200, 110)));

		return panel;
	}

	private JPanel buildLinksAndSourcesCard(ClanEntry clan)
	{
		JPanel card = createStyledCard(new Color(65, 72, 86));

		JTextArea header = createWrappedLabelArea("Official Clan Links & Traceable Sources", ACCENT_GOLD);
		header.setFont(FontManager.getRunescapeBoldFont());
		card.add(header);
		card.add(Box.createVerticalStrut(6));

		card.add(buildLinkBlock("Official Website", clan.getOfficialWebsite()));
		card.add(Box.createVerticalStrut(5));
		card.add(buildLinkBlock("Recruitment / Apply Page", clan.getRecruitmentPageUrl()));
		card.add(Box.createVerticalStrut(5));
		card.add(buildLinkBlock("Official Discord", clan.getDiscordInvitation()));
		card.add(Box.createVerticalStrut(5));
		card.add(buildLinkBlock("TempleOSRS Profile", clan.getTempleOsrsGroupUrl()));
		card.add(Box.createVerticalStrut(5));
		card.add(buildLinkBlock("Wise Old Man Profile", clan.getWiseOldManGroupUrl()));

		if (isValidHttpUrl(clan.getOsrsClansUrl()))
		{
			card.add(Box.createVerticalStrut(5));
			card.add(buildLinkBlock("OSRSClans Directory", clan.getOsrsClansUrl()));
		}

		if (clan.getRequirementSourceUrls() != null && !clan.getRequirementSourceUrls().isEmpty())
		{
			card.add(Box.createVerticalStrut(8));
			card.add(createWrappedLabelArea("Traceable Requirement Source URLs:", ACCENT_GOLD));
			card.add(Box.createVerticalStrut(4));

			for (String url : clan.getRequirementSourceUrls())
			{
				card.add(buildLinkBlock("Verified Source", url));
				card.add(Box.createVerticalStrut(4));
			}
		}

		return card;
	}

	private JPanel buildLinkBlock(String label, String value)
	{
		String safeValue = (value == null || value.trim().isEmpty()) ? "Unavailable" : value.trim();
		JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBackground(CARD_INNER_BG);
		block.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.setBorder(new CompoundBorder(
			new LineBorder(new Color(55, 58, 66), 1),
			new EmptyBorder(5, 6, 5, 6)
		));

		block.add(createWrappedLabelArea(label + ":\n" + safeValue, Color.LIGHT_GRAY));

		if (isValidHttpUrl(safeValue))
		{
			block.add(Box.createVerticalStrut(4));
			JPanel btnRow = new JPanel(new GridLayout(1, 2, 4, 0));
			btnRow.setBackground(CARD_INNER_BG);
			btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

			JButton openBtn = createActionButton("Open Link", "Open " + safeValue + " in your browser", new Color(38, 84, 145), Color.WHITE);
			openBtn.addActionListener(e -> LinkBrowser.browse(safeValue));

			JButton copyBtn = createActionButton("Copy URL", "Copy URL to clipboard", CARD_BG, Color.WHITE);
			copyBtn.addActionListener(e -> {
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(safeValue), null);
				copyBtn.setText("Copied!");
			});

			btnRow.add(openBtn);
			btnRow.add(copyBtn);
			block.add(btnRow);
		}

		return block;
	}

	private String resolvePrimaryRecruitmentUrl(ClanEntry clan)
	{
		if (isValidHttpUrl(clan.getOfficialWebsite()))
		{
			return clan.getOfficialWebsite().trim();
		}
		if (isValidHttpUrl(clan.getDiscordInvitation()))
		{
			return clan.getDiscordInvitation().trim();
		}
		if (isValidHttpUrl(clan.getTempleOsrsGroupUrl()))
		{
			return clan.getTempleOsrsGroupUrl().trim();
		}
		if (isValidHttpUrl(clan.getWiseOldManGroupUrl()))
		{
			return clan.getWiseOldManGroupUrl().trim();
		}
		return "Unavailable";
	}

	private String resolvePrimaryRecruitmentLinkLabel(ClanEntry clan)
	{
		if (isValidHttpUrl(clan.getOfficialWebsite()))
		{
			String web = clan.getOfficialWebsite().toLowerCase(Locale.ROOT);
			if (web.contains("templeosrs.com"))
			{
				return "TempleOSRS";
			}
			if (web.contains("wiseoldman.net"))
			{
				return "Wise Old Man";
			}
			if (web.contains("osrsclans.cc"))
			{
				return "OSRSClans";
			}
			return "Official Website";
		}
		if (isValidHttpUrl(clan.getDiscordInvitation()))
		{
			return "Official Discord";
		}
		return "Profile Link";
	}

	private String formatDirectoryCategories(ClanEntry clan)
	{
		if (clan.getDirectoryCategories() != null && !clan.getDirectoryCategories().isEmpty())
		{
			return clan.getDirectoryCategories().stream()
				.map(ClanDirectoryCategory::getDisplayName)
				.collect(Collectors.joining(" / "));
		}
		return "Community";
	}

	private JPanel createStyledCard(Color borderColor)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD_BG);
		card.setBorder(new CompoundBorder(
			new LineBorder(borderColor, 1),
			new EmptyBorder(8, 8, 8, 8)
		));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	private JPanel createWrappedBadgePanel(String text, Color bgColor)
	{
		JPanel badgePanel = new JPanel(new BorderLayout());
		badgePanel.setBackground(bgColor);
		badgePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		badgePanel.setBorder(new EmptyBorder(3, 6, 3, 6));

		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setOpaque(false);
		area.setFont(FontManager.getRunescapeSmallFont());
		area.setForeground(Color.WHITE);
		area.setBorder(null);
		badgePanel.add(area, BorderLayout.CENTER);
		return badgePanel;
	}

	private static JTextArea createWrappedLabelArea(String content, Color textColor)
	{
		JTextArea area = new JTextArea(content != null ? content : "Unavailable");
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setOpaque(false);
		area.setFont(FontManager.getRunescapeSmallFont());
		area.setForeground(textColor);
		area.setBorder(null);
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		return area;
	}

	private JButton createActionButton(String text, String tooltip, Color bgColor, Color fgColor)
	{
		JButton btn = new JButton(text);
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setToolTipText(tooltip);
		btn.setBackground(bgColor);
		btn.setForeground(fgColor);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(new CompoundBorder(
			new LineBorder(new Color(62, 68, 78), 1),
			new EmptyBorder(4, 6, 4, 6)
		));
		return btn;
	}

	private JButton createFullWidthActionButton(String text, String tooltip, Color bgColor, Color fgColor)
	{
		JButton btn = createActionButton(text, tooltip, bgColor, fgColor);
		btn.setAlignmentX(Component.LEFT_ALIGNMENT);
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		return btn;
	}

	private String formatSpecializations(ClanEntry clan)
	{
		if (clan.getSpecializations() != null && !clan.getSpecializations().isEmpty())
		{
			return clan.getSpecializations().stream()
				.map(ClanSpecialization::getDisplayName)
				.collect(Collectors.joining(", "));
		}
		if (clan.getMainSpecialization() != null)
		{
			return clan.getMainSpecialization().getDisplayName();
		}
		return "Unavailable";
	}

	private boolean isValidHttpUrl(String val)
	{
		return val != null && (val.trim().startsWith("https://") || val.trim().startsWith("http://"));
	}
}
