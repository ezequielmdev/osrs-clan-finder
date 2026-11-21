package com.osrsclanfinder.service;

import com.osrsclanfinder.model.ClanDirectoryCategory;
import com.osrsclanfinder.model.ClanEntry;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Manages asynchronous downloading, in-memory caching, and exclusive clan-branded placeholder generation
 * for Clan Banners and Clan Logos. Never reuses another clan's logo or a generic identical image.
 */
@Slf4j
@Singleton
public class ClanImageService
{
	private static final String USER_AGENT = "OSRS-Clan-Finder-RuneLite-Plugin/2.0";

	private final OkHttpClient okHttpClient;
	private final Map<String, BufferedImage> bannerCache = new ConcurrentHashMap<>();
	private final Map<String, BufferedImage> logoCache = new ConcurrentHashMap<>();
	private volatile BufferedImage cachedPluginLogo;

	@Inject
	public ClanImageService(OkHttpClient okHttpClient)
	{
		this.okHttpClient = okHttpClient;
	}

	public BufferedImage getPluginHeaderLogo(int size)
	{
		if (cachedPluginLogo == null)
		{
			try
			{
				cachedPluginLogo = ImageIO.read(ClanImageService.class.getResourceAsStream("/com/osrsclanfinder/plugin_logo.png"));
			}
			catch (Exception e)
			{
				log.debug("Could not read bundled plugin_logo.png", e);
			}
		}
		if (cachedPluginLogo == null)
		{
			return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		}

		BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(cachedPluginLogo, 0, 0, size, size, null);
		g.dispose();
		return scaled;
	}

	public BufferedImage getOrGenerateCardBanner(ClanEntry clan, int width, int height, Consumer<BufferedImage> onRemoteLoaded)
	{
		String cacheKey = clan.getId() + "_banner_" + width + "x" + height;
		BufferedImage cached = bannerCache.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		BufferedImage exclusivePlaceholder = generateExclusiveClanBanner(clan, width, height);
		bannerCache.put(cacheKey, exclusivePlaceholder);

		String bannerUrl = clan.getBannerUrl();
		if (isSupportedRemoteImageUrl(bannerUrl))
		{
			fetchRemoteImageAsync(bannerUrl.trim(), img -> {
				BufferedImage composed = composeRemoteBannerOverlay(clan, img, width, height);
				bannerCache.put(cacheKey, composed);
				if (onRemoteLoaded != null)
				{
					onRemoteLoaded.accept(composed);
				}
			});
		}
		return exclusivePlaceholder;
	}

	public BufferedImage getOrGenerateLogo(ClanEntry clan, int size, Consumer<BufferedImage> onRemoteLoaded)
	{
		String cacheKey = clan.getId() + "_logo_" + size;
		BufferedImage cached = logoCache.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		BufferedImage exclusiveCrest = generateExclusiveClanCrestLogo(clan, size);
		logoCache.put(cacheKey, exclusiveCrest);

		String logoUrl = clan.getLogoUrl();
		if (isSupportedRemoteImageUrl(logoUrl))
		{
			fetchRemoteImageAsync(logoUrl.trim(), img -> {
				BufferedImage scaled = scaleLogoCleanly(clan, img, size);
				logoCache.put(cacheKey, scaled);
				if (onRemoteLoaded != null)
				{
					onRemoteLoaded.accept(scaled);
				}
			});
		}
		return exclusiveCrest;
	}

	private boolean isSupportedRemoteImageUrl(String url)
	{
		if (url == null || url.trim().isEmpty())
		{
			return false;
		}
		String lower = url.trim().toLowerCase(Locale.ROOT);
		if (!lower.startsWith("https://") && !lower.startsWith("http://"))
		{
			return false;
		}
		// Standard Java ImageIO supports PNG, JPG, JPEG, GIF, BMP
		return lower.contains(".png") || lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".gif");
	}

	private void fetchRemoteImageAsync(String url, Consumer<BufferedImage> callback)
	{
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
				log.debug("Failed to download clan image from {}: {}", url, e.getMessage());
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
					byte[] bytes = body.bytes();
					BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
					if (decoded != null && decoded.getWidth() > 8 && decoded.getHeight() > 8)
					{
						callback.accept(decoded);
					}
				}
				catch (Exception ex)
				{
					log.debug("Could not decode image from {}", url, ex);
				}
			}
		});
	}

	private BufferedImage composeRemoteBannerOverlay(ClanEntry clan, BufferedImage remoteImg, int width, int height)
	{
		BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// Center-crop scale
		double scaleX = (double) width / remoteImg.getWidth();
		double scaleY = (double) height / remoteImg.getHeight();
		double scale = Math.max(scaleX, scaleY);
		int drawW = (int) Math.round(remoteImg.getWidth() * scale);
		int drawH = (int) Math.round(remoteImg.getHeight() * scale);
		int drawX = (width - drawW) / 2;
		int drawY = (height - drawH) / 2;

		g.drawImage(remoteImg, drawX, drawY, drawW, drawH, null);

		// Dark gradient scrim so overlay text & badges remain crisp
		GradientPaint scrim = new GradientPaint(
			0, 0, new Color(12, 14, 18, 125),
			0, height, new Color(12, 14, 18, 225)
		);
		g.setPaint(scrim);
		g.fillRect(0, 0, width, height);

		drawBannerWatermarkAndTag(g, clan, width, height);
		g.dispose();
		return canvas;
	}

	private BufferedImage scaleLogoCleanly(ClanEntry clan, BufferedImage remoteLogo, int size)
	{
		BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		Color primary = parseColor(clan.getPrimaryColorHex(), deriveUniqueColor(clan.getClanName(), 0));
		g.setColor(new Color(22, 24, 29));
		g.fillRoundRect(0, 0, size, size, 10, 10);

		int pad = 3;
		g.drawImage(remoteLogo, pad, pad, size - pad * 2, size - pad * 2, null);

		g.setColor(primary);
		g.setStroke(new BasicStroke(1.5f));
		g.drawRoundRect(0, 0, size - 1, size - 1, 10, 10);
		g.dispose();
		return canvas;
	}

	/**
	 * Generates an exclusive, deterministic, visually distinct banner for each clan featuring:
	 * - Unique clan palette derived from its configured hex colors or deterministic hash
	 * - Unique geometric pattern & crest watermark
	 * - Prominent clan name & category emblem
	 */
	public BufferedImage generateExclusiveClanBanner(ClanEntry clan, int width, int height)
	{
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		String name = clan.getClanName() != null ? clan.getClanName() : "OSRS Clan";
		int hash = Math.abs(name.hashCode());

		Color primary = parseColor(clan.getPrimaryColorHex(), deriveUniqueColor(name, 0));
		Color secondary = parseColor(clan.getSecondaryColorHex(), deriveUniqueColor(name, 1));

		Color darkBase = new Color(
			Math.max(12, primary.getRed() / 5),
			Math.max(14, primary.getGreen() / 5),
			Math.max(18, primary.getBlue() / 5)
		);
		Color midTone = new Color(
			Math.min(255, secondary.getRed() / 3 + 18),
			Math.min(255, secondary.getGreen() / 3 + 18),
			Math.min(255, secondary.getBlue() / 3 + 22)
		);

		GradientPaint bgGrad = new GradientPaint(0, 0, darkBase, width, height, midTone);
		g.setPaint(bgGrad);
		g.fillRoundRect(0, 0, width, height, 8, 8);

		// Unique geometric motif per clan hash (never identical across clans)
		g.setColor(new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 42));
		int motifType = hash % 4;
		if (motifType == 0)
		{
			// Diagonal speed stripes
			for (int x = -height; x < width + height; x += 22)
			{
				g.fillPolygon(
					new int[]{x, x + 10, x + height + 10, x + height},
					new int[]{0, 0, height, height},
					4
				);
			}
		}
		else if (motifType == 1)
		{
			// Concentric runic rings on right side
			int cx = width - 36;
			int cy = height / 2;
			g.setStroke(new BasicStroke(2f));
			for (int r = 14; r <= 75; r += 14)
			{
				g.drawOval(cx - r, cy - r, r * 2, r * 2);
			}
		}
		else if (motifType == 2)
		{
			// Hexagonal / Diamond lattice
			g.setStroke(new BasicStroke(1.5f));
			for (int x = 15; x < width; x += 28)
			{
				g.drawPolygon(
					new int[]{x, x + 14, x, x - 14},
					new int[]{4, height / 2, height - 4, height / 2},
					4
				);
			}
		}
		else
		{
			// Chevron tactical waves
			g.setStroke(new BasicStroke(2.5f));
			for (int x = 20; x < width + 30; x += 24)
			{
				g.drawPolyline(
					new int[]{x - 15, x, x - 15},
					new int[]{4, height / 2, height - 4},
					3
				);
			}
		}

		// Top glowing accent bar
		GradientPaint topBar = new GradientPaint(0, 0, primary, width, 0, secondary);
		g.setPaint(topBar);
		g.fillRect(0, 0, width, 3);

		drawBannerWatermarkAndTag(g, clan, width, height);

		// Outer subtle border
		g.setColor(new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 145));
		g.setStroke(new BasicStroke(1f));
		g.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, 8, 8));

		g.dispose();
		return img;
	}

	private void drawBannerWatermarkAndTag(Graphics2D g, ClanEntry clan, int width, int height)
	{
		String name = clan.getClanName() != null ? clan.getClanName() : "OSRS Clan";
		Color primary = parseColor(clan.getPrimaryColorHex(), deriveUniqueColor(name, 0));

		// Large background watermark initials on the right
		String initials = buildClanInitials(name);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(26, height - 12)));
		g.setColor(new Color(255, 255, 255, 24));
		FontMetrics wmFm = g.getFontMetrics();
		g.drawString(initials, width - wmFm.stringWidth(initials) - 8, height - 6);

		// Category / Region tag pill in top-right
		String tagText = clan.getEmblemTag() != null && !clan.getEmblemTag().isEmpty()
			? clan.getEmblemTag()
			: resolvePrimaryCategoryTag(clan);

		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		FontMetrics tagFm = g.getFontMetrics();
		int pillW = tagFm.stringWidth(tagText) + 12;
		int pillH = 16;
		int pillX = width - pillW - 6;
		int pillY = 7;

		g.setColor(new Color(14, 16, 20, 210));
		g.fillRoundRect(pillX, pillY, pillW, pillH, 8, 8);
		g.setColor(primary);
		g.drawRoundRect(pillX, pillY, pillW, pillH, 8, 8);
		g.setColor(Color.WHITE);
		g.drawString(tagText, pillX + 6, pillY + 12);

		// Clan Name prominently rendered on the banner
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, height >= 64 ? 16 : 14));
		g.setColor(new Color(0, 0, 0, 190));
		g.drawString(name, 11, height - 11);
		g.setColor(Color.WHITE);
		g.drawString(name, 10, height - 12);
	}

	/**
	 * Generates an exclusive shield/crest logo with the clan's unique color palette and initials.
	 */
	public BufferedImage generateExclusiveClanCrestLogo(ClanEntry clan, int size)
	{
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		String name = clan.getClanName() != null ? clan.getClanName() : "CL";
		Color primary = parseColor(clan.getPrimaryColorHex(), deriveUniqueColor(name, 0));
		Color secondary = parseColor(clan.getSecondaryColorHex(), deriveUniqueColor(name, 1));

		GradientPaint grad = new GradientPaint(
			0, 0,
			new Color(Math.max(20, primary.getRed() / 2), Math.max(20, primary.getGreen() / 2), Math.max(25, primary.getBlue() / 2)),
			size, size,
			new Color(Math.max(15, secondary.getRed() / 3), Math.max(15, secondary.getGreen() / 3), Math.max(20, secondary.getBlue() / 3))
		);

		g.setPaint(grad);
		g.fillRoundRect(1, 1, size - 2, size - 2, 10, 10);

		// Inner shield emblem
		int pad = 4;
		int[] sx = {size / 2, size - pad, size - pad, size / 2, pad, pad};
		int[] sy = {pad, pad + 4, size - pad - 6, size - pad, size - pad - 6, pad + 4};
		g.setColor(new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 70));
		g.fillPolygon(sx, sy, 6);
		g.setColor(primary);
		g.setStroke(new BasicStroke(1.5f));
		g.drawPolygon(sx, sy, 6);

		// Clan Initials centered
		String initials = buildClanInitials(name);
		int fontSize = initials.length() >= 3 ? Math.max(9, size / 3 - 1) : Math.max(11, size / 2 - 3);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
		FontMetrics fm = g.getFontMetrics();
		int tx = (size - fm.stringWidth(initials)) / 2;
		int ty = (size - fm.getHeight()) / 2 + fm.getAscent();

		g.setColor(new Color(0, 0, 0, 180));
		g.drawString(initials, tx + 1, ty + 1);
		g.setColor(Color.WHITE);
		g.drawString(initials, tx, ty);

		g.dispose();
		return img;
	}

	private static String buildClanInitials(String clanName)
	{
		if (clanName == null || clanName.trim().isEmpty())
		{
			return "CF";
		}
		String[] parts = clanName.trim().split("\\s+");
		if (parts.length == 1)
		{
			String word = parts[0].toUpperCase(Locale.ROOT);
			return word.length() <= 3 ? word : word.substring(0, 2);
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(3, parts.length); i++)
		{
			if (!parts[i].isEmpty())
			{
				sb.append(Character.toUpperCase(parts[i].charAt(0)));
			}
		}
		return sb.toString();
	}

	private static String resolvePrimaryCategoryTag(ClanEntry clan)
	{
		if (clan.isBrazilianClan())
		{
			return "BR • PvM";
		}
		List<ClanDirectoryCategory> cats = clan.getDirectoryCategories();
		if (cats != null && !cats.isEmpty())
		{
			return cats.get(0).getDisplayName().toUpperCase(Locale.ROOT);
		}
		return "OSRS CLAN";
	}

	public static Color parseColor(String hex, Color fallback)
	{
		if (hex == null || hex.trim().isEmpty())
		{
			return fallback;
		}
		try
		{
			String cleaned = hex.trim();
			if (cleaned.startsWith("#"))
			{
				cleaned = cleaned.substring(1);
			}
			return new Color(Integer.parseInt(cleaned, 16));
		}
		catch (Exception e)
		{
			return fallback;
		}
	}

	private static Color deriveUniqueColor(String seed, int variant)
	{
		int h = Math.abs((seed + "_" + variant).hashCode());
		float hue = (h % 360) / 360.0f;
		float sat = 0.62f + ((h >> 8) % 25) / 100.0f;
		float bri = 0.78f + ((h >> 16) % 18) / 100.0f;
		return Color.getHSBColor(hue, Math.min(0.9f, sat), Math.min(0.95f, bri));
	}
}
