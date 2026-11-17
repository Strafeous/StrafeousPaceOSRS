package com.skillpace;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
@PluginDescriptor(
        name = "Strafeous' Pace",
        internalName = "strafeous-pace",
        legacyDataDirectory = "strafeous-pace",
        description = "Starts on first swing/action, auto-saves on AFK/logout/skill switch, and exports session logs",
        tags = {"timer", "stopwatch", "skilling", "pace", "tracker", "logs", "mining", "hunter", "strafeous"}
)
public class SkillPacePlugin extends Plugin
{
    static final String PLUGIN_TITLE = "Strafeous' Pace";
    static final String SAVE_RESET_ACTION = "Save & Reset";
    static final String RESET_ACTION = "Reset";
    static final String PAUSE_ACTION = "Pause/Resume";
    private static final DateTimeFormatter FULL_DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE M/d/yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Set<Skill> TRACKABLE_SKILLS = EnumSet.of(
            Skill.HUNTER, Skill.MINING, Skill.WOODCUTTING, Skill.FISHING,
            Skill.COOKING, Skill.FIREMAKING, Skill.CRAFTING, Skill.FLETCHING,
            Skill.SMITHING, Skill.HERBLORE, Skill.AGILITY, Skill.THIEVING,
            Skill.FARMING, Skill.RUNECRAFT, Skill.CONSTRUCTION
    );

    private static final Map<Integer, Skill> SKILLING_ANIMATIONS = new HashMap<>();

    static
    {
        int[] miningAnims = {
                AnimationID.MINING_BRONZE_PICKAXE, AnimationID.MINING_IRON_PICKAXE,
                AnimationID.MINING_STEEL_PICKAXE, AnimationID.MINING_BLACK_PICKAXE,
                AnimationID.MINING_MITHRIL_PICKAXE, AnimationID.MINING_ADAMANT_PICKAXE,
                AnimationID.MINING_RUNE_PICKAXE, AnimationID.MINING_DRAGON_PICKAXE,
                AnimationID.MINING_DRAGON_PICKAXE_UPGRADED, AnimationID.MINING_DRAGON_PICKAXE_OR,
                AnimationID.MINING_DRAGON_PICKAXE_OR_TRAILBLAZER, AnimationID.MINING_CRYSTAL_PICKAXE,
                AnimationID.MINING_INFERNAL_PICKAXE, AnimationID.MINING_3A_PICKAXE,
                AnimationID.MINING_GILDED_PICKAXE, AnimationID.MINING_MOTHERLODE_BRONZE,
                AnimationID.MINING_MOTHERLODE_IRON, AnimationID.MINING_MOTHERLODE_STEEL,
                AnimationID.MINING_MOTHERLODE_BLACK, AnimationID.MINING_MOTHERLODE_MITHRIL,
                AnimationID.MINING_MOTHERLODE_ADAMANT, AnimationID.MINING_MOTHERLODE_RUNE,
                AnimationID.MINING_MOTHERLODE_DRAGON, AnimationID.MINING_MOTHERLODE_DRAGON_UPGRADED,
                AnimationID.MINING_MOTHERLODE_DRAGON_OR, AnimationID.MINING_MOTHERLODE_DRAGON_OR_TRAILBLAZER,
                AnimationID.MINING_MOTHERLODE_CRYSTAL, AnimationID.MINING_MOTHERLODE_INFERNAL,
                AnimationID.MINING_MOTHERLODE_3A, AnimationID.MINING_MOTHERLODE_GILDED
        };
        for (int anim : miningAnims)
        {
            SKILLING_ANIMATIONS.put(anim, Skill.MINING);
        }

        int[] wcAnims = {
                AnimationID.WOODCUTTING_BRONZE, AnimationID.WOODCUTTING_IRON,
                AnimationID.WOODCUTTING_STEEL, AnimationID.WOODCUTTING_BLACK,
                AnimationID.WOODCUTTING_MITHRIL, AnimationID.WOODCUTTING_ADAMANT,
                AnimationID.WOODCUTTING_RUNE, AnimationID.WOODCUTTING_DRAGON,
                AnimationID.WOODCUTTING_DRAGON_OR, AnimationID.WOODCUTTING_INFERNAL,
                AnimationID.WOODCUTTING_3A_AXE, AnimationID.WOODCUTTING_CRYSTAL,
                AnimationID.WOODCUTTING_GILDED, AnimationID.WOODCUTTING_TRAILBLAZER
        };
        for (int anim : wcAnims)
        {
            SKILLING_ANIMATIONS.put(anim, Skill.WOODCUTTING);
        }

        int[] fishAnims = {
                AnimationID.FISHING_NET, AnimationID.FISHING_BIG_NET,
                AnimationID.FISHING_POLE_CAST, 622,
                AnimationID.FISHING_HARPOON, AnimationID.FISHING_BARBTAIL_HARPOON,
                AnimationID.FISHING_DRAGON_HARPOON, AnimationID.FISHING_INFERNAL_HARPOON,
                AnimationID.FISHING_CRYSTAL_HARPOON, AnimationID.FISHING_CAGE,
                AnimationID.FISHING_BAREHAND, AnimationID.FISHING_KARAMBWAN
        };
        for (int anim : fishAnims)
        {
            SKILLING_ANIMATIONS.put(anim, Skill.FISHING);
        }

        int[] hunterAnims = {5208, 5212, 5207, 5215, 6606, 6605};
        for (int anim : hunterAnims)
        {
            SKILLING_ANIMATIONS.put(anim, Skill.HUNTER);
        }
    }

    @Inject
    private Client client;

    @Inject
    private SkillPaceConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SkillPaceOverlay overlay;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    @Getter
    private ItemManager itemManager;

    private SkillPacePanel panel;
    private NavigationButton navButton;
    private ScheduledExecutorService ioExecutor;

    private final Map<Skill, Integer> previousXp = new HashMap<>();
    private final Map<Integer, Integer> previousInventory = new HashMap<>();
    private final Map<Integer, Integer> sessionLoot = new LinkedHashMap<>();

    @Getter
    private Skill activeSkill = null;

    @Getter
    private String detectedItemName = "";

    @Getter
    private String detectedLocation = "";

    @Getter
    private boolean running = false;

    @Getter
    private boolean paused = false;

    @Getter
    private boolean autoPaused = false;

    @Getter
    private int itemsGained = 0;

    @Getter
    private int xpGained = 0;

    @Getter
    private long gpGained = 0;

    private Instant startTime;
    private Instant lastActivityTime;
    private Duration accumulatedTime = Duration.ZERO;
    private int lastXpTick = -1;
    private boolean itemMatchedThisTick = false;

    @Override
    protected void startUp()
    {
        ioExecutor = Executors.newSingleThreadScheduledExecutor();

        overlay.getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, SAVE_RESET_ACTION, PLUGIN_TITLE));
        overlay.getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, PAUSE_ACTION, PLUGIN_TITLE));
        overlay.getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, RESET_ACTION, PLUGIN_TITLE));
        overlayManager.add(overlay);

        panel = new SkillPacePanel(this);
        BufferedImage clockIcon = createClockIcon();

        navButton = NavigationButton.builder()
                .tooltip(PLUGIN_TITLE)
                .icon(clockIcon)
                .priority(6)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        resetTimer();
    }

    @Override
    protected void shutDown()
    {
        if (running)
        {
            saveCurrentSession();
            resetTimer();
        }
        overlayManager.remove(overlay);
        overlay.getMenuEntries().clear();
        clientToolbar.removeNavigation(navButton);
        previousXp.clear();
        previousInventory.clear();
        sessionLoot.clear();

        if (ioExecutor != null)
        {
            ioExecutor.shutdownNow();
            ioExecutor = null;
        }
    }

    private BufferedImage createClockIcon()
    {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(220, 170, 50));
        g.setStroke(new BasicStroke(1.6f));
        g.drawOval(2, 2, 12, 12);

        g.drawLine(8, 0, 8, 2);
        g.drawLine(6, 0, 10, 0);

        g.setColor(Color.WHITE);
        g.drawLine(8, 8, 8, 4);
        g.drawLine(8, 8, 11, 8);

        g.dispose();
        return img;
    }
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.CONNECTION_LOST)
        {
            if (running)
            {
                saveCurrentSession();
                resetTimer();
            }
            previousXp.clear();
            previousInventory.clear();
        }
        else if (state == GameState.LOGGING_IN)
        {
            previousXp.clear();
            previousInventory.clear();
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        Player local = client.getLocalPlayer();
        if (local == null || event.getActor() != local)
        {
            return;
        }

        int animId = local.getAnimation();
        Skill animSkill = SKILLING_ANIMATIONS.get(animId);
        if (animSkill == null)
        {
            return;
        }

        if (paused && !autoPaused)
        {
            return;
        }

        if (running && activeSkill != null && animSkill != activeSkill)
        {
            if (config.autoSwitch())
            {
                saveCurrentSession();
                resetTimer();
            }
            else
            {
                return;
            }
        }

        recordActivity(animSkill);
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        Skill skill = event.getSkill();
        int currentXp = event.getXp();
        Integer lastXp = previousXp.put(skill, currentXp);

        if (lastXp == null || currentXp <= lastXp || !TRACKABLE_SKILLS.contains(skill))
        {
            return;
        }

        if (paused && !autoPaused)
        {
            return;
        }

        if (running && activeSkill != null && skill != activeSkill)
        {
            if (config.autoSwitch())
            {
                saveCurrentSession();
                resetTimer();
            }
            else
            {
                return;
            }
        }

        recordActivity(skill);

        xpGained += (currentXp - lastXp);
        lastXpTick = client.getTickCount();
        itemMatchedThisTick = false;
        itemsGained++;
    }

    private void recordActivity(Skill skill)
    {
        Instant now = Instant.now();
        lastActivityTime = now;

        if (!running)
        {
            running = true;
            paused = false;
            autoPaused = false;
            activeSkill = skill;
            startTime = now;
            detectedLocation = detectCurrentLocation();
        }
        else if (paused && autoPaused)
        {
            startTime = now;
            paused = false;
            autoPaused = false;
        }

        if (detectedLocation.isEmpty())
        {
            detectedLocation = detectCurrentLocation();
        }
    }

    private String detectCurrentLocation()
    {
        Player local = client.getLocalPlayer();
        if (local == null)
        {
            return "";
        }

        WorldPoint wp = local.getWorldLocation();
        if (wp == null)
        {
            return "";
        }

        int x = wp.getX();
        int y = wp.getY();
        int regionId = wp.getRegionID();

        if (regionId == 14916 || regionId == 14660 || (x >= 3710 && x <= 3775 && y >= 5630 && y <= 5695))
        {
            return "Motherlode Mine";
        }
        if ((x >= 3010 && x <= 3060 && y >= 9700 && y <= 9760) || regionId == 12183 || regionId == 12184)
        {
            return "Mining Guild";
        }
        if (x >= 3000 && x <= 3055 && y >= 9695 && y <= 9730)
        {
            return "Amethyst Mine";
        }
        if (x >= 2810 && x <= 2865 && y >= 2970 && y <= 3015)
        {
            return "Shilo Village Gem Mine";
        }
        if (x >= 3280 && x <= 3315 && y >= 3280 && y <= 3320)
        {
            return "Al Kharid Mine";
        }
        if (x >= 3170 && x <= 3190 && y >= 3360 && y <= 3380)
        {
            return "Varrock West Mine";
        }
        if (x >= 3275 && x <= 3295 && y >= 3355 && y <= 3375)
        {
            return "Varrock East Mine";
        }
        if (x >= 2490 && x <= 2610 && y >= 2875 && y <= 2945)
        {
            return "Feldip Hills";
        }
        if (x >= 2300 && x <= 2370 && y >= 3570 && y <= 3630)
        {
            return "Piscatoris Hunter Area";
        }
        if (regionId == 10307 || (x >= 2560 && x <= 2623 && y >= 4288 && y <= 4351))
        {
            return "Puro-Puro";
        }
        if (x >= 2690 && x <= 2750 && y >= 3745 && y <= 3805)
        {
            return "Rellekka Hunter Area";
        }
        if (x >= 1560 && x <= 1660 && y >= 3470 && y <= 3520)
        {
            return "Woodcutting Guild";
        }
        if (x >= 2690 && x <= 2740 && y >= 3460 && y <= 3515)
        {
            return "Seers' Village";
        }
        if (x >= 2490 && x <= 2520 && y >= 3550 && y <= 3580)
        {
            return "Barbarian Outpost";
        }
        if (x >= 2830 && x <= 2865 && y >= 3420 && y <= 3440)
        {
            return "Catherby";
        }
        if (x >= 2580 && x <= 2620 && y >= 3405 && y <= 3430)
        {
            return "Fishing Guild";
        }
        if (x >= 3210 && x <= 3255 && y >= 3140 && y <= 3175)
        {
            return "Lumbridge Swamp";
        }
        if (x >= 1200 && x <= 1850 && y >= 3400 && y <= 4000)
        {
            return "Great Kourend";
        }
        if (x >= 1350 && x <= 1800 && y >= 2850 && y <= 3350)
        {
            return "Varlamore";
        }
        if (x >= 2940 && x <= 3390 && y >= 3525 && y <= 3970)
        {
            return "Wilderness (Lvl " + Math.max(1, ((y - 3520) / 8) + 1) + ")";
        }

        return "Region " + regionId;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!running || paused || lastActivityTime == null)
        {
            return;
        }

        int timeout = config.afkTimeoutSeconds();
        if (timeout > 0)
        {
            long idleSeconds = Duration.between(lastActivityTime, Instant.now()).getSeconds();
            if (idleSeconds >= timeout)
            {
                accumulatedTime = accumulatedTime.plus(Duration.between(startTime, lastActivityTime));
                paused = true;
                saveCurrentSession();
                resetTimer();
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() != InventoryID.INVENTORY.getId())
        {
            return;
        }

        Map<Integer, Integer> currentInventory = new HashMap<>();
        ItemContainer container = event.getItemContainer();
        if (container != null)
        {
            for (Item item : container.getItems())
            {
                if (item.getId() > 0 && item.getQuantity() > 0)
                {
                    currentInventory.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
        }

        if (running && !paused && client.getTickCount() == lastXpTick)
        {
            int gainedThisTick = 0;
            int latestItemId = -1;

            for (Map.Entry<Integer, Integer> entry : currentInventory.entrySet())
            {
                int itemId = entry.getKey();
                int prevQty = previousInventory.getOrDefault(itemId, 0);
                if (entry.getValue() > prevQty)
                {
                    int diff = entry.getValue() - prevQty;
                    gainedThisTick += diff;
                    latestItemId = itemId;
                    sessionLoot.merge(itemId, diff, Integer::sum);

                    long unitPrice = itemManager.getItemPrice(itemId);
                    if (unitPrice > 0)
                    {
                        gpGained += (unitPrice * diff);
                    }
                }
            }

            if (gainedThisTick > 0)
            {
                if (!itemMatchedThisTick)
                {
                    itemsGained = (itemsGained - 1) + gainedThisTick;
                    itemMatchedThisTick = true;
                }
                else
                {
                    itemsGained += gainedThisTick;
                }

                if (latestItemId > 0)
                {
                    detectedItemName = itemManager.getItemComposition(latestItemId).getName();
                }
            }
        }

        previousInventory.clear();
        previousInventory.putAll(currentInventory);
    }

    @Subscribe
    public void onOverlayMenuClicked(OverlayMenuClicked event)
    {
        if (event.getOverlay() != overlay)
        {
            return;
        }

        String option = event.getEntry().getOption();
        if (SAVE_RESET_ACTION.equals(option))
        {
            saveCurrentSession();
            resetTimer();
        }
        else if (RESET_ACTION.equals(option))
        {
            resetTimer();
        }
        else if (PAUSE_ACTION.equals(option))
        {
            togglePause();
        }
    }

    public String getSmartUnitNoun()
    {
        if (activeSkill == null)
        {
            return "items";
        }
        switch (activeSkill)
        {
            case MINING:
                return "ore";
            case WOODCUTTING:
                return "logs";
            case FISHING:
                return "fish";
            case HUNTER:
                return "catches";
            case COOKING:
                return "cooked";
            case SMITHING:
                return "bars";
            case AGILITY:
                return "laps";
            default:
                return "items";
        }
    }

    public String getSmartPastVerb()
    {
        if (activeSkill == null)
        {
            return "gathered";
        }
        switch (activeSkill)
        {
            case MINING:
                return "mined";
            case WOODCUTTING:
                return "cut";
            case FISHING:
            case HUNTER:
                return "caught";
            case COOKING:
                return "cooked";
            case SMITHING:
                return "smithed";
            case FLETCHING:
                return "fletched";
            case CRAFTING:
                return "crafted";
            case FARMING:
                return "harvested";
            case AGILITY:
                return "completed";
            default:
                return "gained";
        }
    }
    public String getPastTenseTitleVerb()
    {
        if (activeSkill == null)
        {
            return "Trained";
        }
        switch (activeSkill)
        {
            case HUNTER:
                return "Hunted";
            case MINING:
                return "Mined";
            case WOODCUTTING:
                return "Woodcut";
            case FISHING:
                return "Fished";
            case COOKING:
                return "Cooked";
            case SMITHING:
                return "Smithed";
            case FLETCHING:
                return "Fletched";
            case CRAFTING:
                return "Crafted";
            case FARMING:
                return "Farmed";
            default:
                return "Trained " + activeSkill.getName();
        }
    }
    public void saveCurrentSession()
    {
        Duration elapsed = getElapsed();
        long totalSeconds = elapsed.getSeconds();
        if (totalSeconds <= 0 && xpGained <= 0 && itemsGained <= 0)
        {
            return;
        }

        String skillName = activeSkill != null ? activeSkill.getName() : "Skilling";
        String fullDateStr = LocalDate.now().format(FULL_DATE_FORMAT);
        String locSuffix = detectedLocation.isEmpty() ? "" : " - " + detectedLocation;
        String humanTime = getPastTenseTitleVerb() + " for " + formatHumanDuration(totalSeconds) + locSuffix;

        long itemsPerHr = totalSeconds > 0 ? Math.round((itemsGained * 3600.0) / totalSeconds) : 0;
        long xpPerHr = totalSeconds > 0 ? Math.round((xpGained * 3600.0) / totalSeconds) : 0;
        long gpPerHr = totalSeconds > 0 ? Math.round((gpGained * 3600.0) / totalSeconds) : 0;
        double avgXpPerItem = itemsGained > 0 ? (double) xpGained / itemsGained : 0;
        double secPerItem = itemsGained > 0 ? (double) totalSeconds / itemsGained : 0;

        String noun = getSmartUnitNoun();
        String verb = getSmartPastVerb();

        String itemSummary = String.format("%,d %s %s @ %,d %s/hr", itemsGained, noun, verb, itemsPerHr, noun);
        String xpSummary = QuantityFormatter.quantityToStackSize(xpGained) + " XP";
        String xpRateSummary = QuantityFormatter.quantityToStackSize(xpPerHr) + " XP/hr";
        String extraStats = String.format("Avg %.1f XP/%s | %.1fs/%s | %s GP (%s/hr)",
                avgXpPerItem, noun, secPerItem, noun,
                QuantityFormatter.quantityToStackSize(gpGained),
                QuantityFormatter.quantityToStackSize(gpPerHr));

        StringBuilder lootBuilder = new StringBuilder();
        StringBuilder humanLoot = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : sessionLoot.entrySet())
        {
            if (lootBuilder.length() > 0)
            {
                lootBuilder.append(",");
                humanLoot.append(", ");
            }
            lootBuilder.append(entry.getKey()).append(":").append(entry.getValue());
            String itemName = itemManager.getItemComposition(entry.getKey()).getName();
            humanLoot.append(itemName).append(" x").append(String.format("%,d", entry.getValue()));
        }

        String entry = String.join("|",
                skillName,
                fullDateStr,
                humanTime,
                itemSummary,
                xpSummary,
                xpRateSummary,
                extraStats,
                String.valueOf(totalSeconds),
                lootBuilder.toString()
        );

        String existing = config.savedLogs();
        String updated = existing.isEmpty() ? entry : entry + "\n" + existing;
        config.setSavedLogs(updated);

        appendToPermanentLogFile(skillName, fullDateStr, humanTime, itemSummary, xpSummary, xpRateSummary, extraStats, humanLoot.toString());

        if (panel != null)
        {
            SwingUtilities.invokeLater(panel::refreshLogs);
        }
    }

    private void appendToPermanentLogFile(String skill, String date, String timeLoc, String items, String xp, String xpHr, String extra, String loot)
    {
        StringBuilder block = new StringBuilder();
        block.append("========================================\n");
        block.append("[").append(LocalDateTime.now().format(STAMP_FORMAT)).append("] Trained ").append(skill).append("\n");
        block.append("Date: ").append(date).append("\n");
        block.append("Session: ").append(timeLoc).append("\n");
        block.append("Output: ").append(items).append("\n");
        block.append("XP: ").append(xp).append(" (").append(xpHr).append(")\n");
        block.append("Details: ").append(extra).append("\n");
        if (!loot.isEmpty())
        {
            block.append("Items: ").append(loot).append("\n");
        }

        String payload = block.toString();
        Runnable writeTask = () ->
        {
            try
            {
                Filepath dir = getPluginDirectory();
                Filepath logFile = dir.joinSegment("strafeous-pace-log.txt");
                logFile.write(payload.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            catch (IOException ex)
            {
                log.debug("Failed to append Strafeous' Pace session log", ex);
            }
        };

        if (ioExecutor != null && !ioExecutor.isShutdown())
        {
            ioExecutor.execute(writeTask);
        }
        else
        {
            writeTask.run();
        }
    }

    public void exportLogsToDesktop()
    {
        List<String> entries = getLogEntries();
        if (entries.isEmpty())
        {
            JOptionPane.showMessageDialog(null, "No saved runs to export yet!", PLUGIN_TITLE, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder report = new StringBuilder();
        report.append("STRAFEOUS' PACE - SKILLING SESSION LOG\n");
        report.append("Exported: ").append(LocalDateTime.now().format(STAMP_FORMAT)).append("\n");
        report.append("Total Saved Runs: ").append(entries.size()).append("\n");
        report.append("====================================================\n\n");

        for (String raw : entries)
        {
            String[] parts = raw.split("\\|", -1);
            if (parts.length < 8)
            {
                continue;
            }
            report.append("Trained ").append(parts[0]).append(" (").append(parts[1]).append(")\n");
            report.append("  ").append(parts[2]).append("\n");
            report.append("  ").append(parts[3]).append("\n");
            report.append("  XP: ").append(parts[4]).append(" | Avg: ").append(parts[5]).append("\n");
            report.append("  ").append(parts[6]).append("\n");

            if (parts.length >= 9 && !parts[8].isEmpty())
            {
                StringBuilder itemLine = new StringBuilder("  Items: ");
                String[] tokens = parts[8].split(",");
                for (int i = 0; i < tokens.length; i++)
                {
                    String[] kv = tokens[i].split(":");
                    if (kv.length == 2)
                    {
                        try
                        {
                            int id = Integer.parseInt(kv[0]);
                            int qty = Integer.parseInt(kv[1]);
                            if (i > 0)
                            {
                                itemLine.append(", ");
                            }
                            itemLine.append(itemManager.getItemComposition(id).getName()).append(" x").append(String.format("%,d", qty));
                        }
                        catch (NumberFormatException ignored)
                        {
                        }
                    }
                }
                report.append(itemLine).append("\n");
            }
            report.append("----------------------------------------------------\n");
        }

        String payload = report.toString();
        String defaultName = "Strafeous-Pace-Export-" + LocalDate.now().format(FILE_DATE_FORMAT) + ".txt";

        Runnable exportTask = () ->
        {
            try
            {
                Filepath dir = getPluginDirectory();
                Filepath exportFile = dir.joinSegment(defaultName);
                exportFile.write(payload.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(
                                null,
                                "Exported " + entries.size() + " run(s) to:\n~/.runelite/plugin-data/strafeous-pace/" + defaultName,
                                PLUGIN_TITLE,
                                JOptionPane.INFORMATION_MESSAGE
                        )
                );
            }
            catch (IOException ex)
            {
                log.debug("Failed to export Strafeous' Pace log", ex);
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null, "Failed to export log: " + ex.getMessage(), PLUGIN_TITLE, JOptionPane.ERROR_MESSAGE)
                );
            }
        };

        if (ioExecutor != null && !ioExecutor.isShutdown())
        {
            ioExecutor.execute(exportTask);
        }
    }

    public List<String> getLogEntries()
    {
        String raw = config.savedLogs();
        if (raw == null || raw.trim().isEmpty())
        {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(raw.split("\n")));
    }

    public void deleteLogEntry(int index)
    {
        List<String> entries = getLogEntries();
        if (index >= 0 && index < entries.size())
        {
            entries.remove(index);
            config.setSavedLogs(String.join("\n", entries));
            if (panel != null)
            {
                SwingUtilities.invokeLater(panel::refreshLogs);
            }
        }
    }

    public void clearAllLogs()
    {
        config.setSavedLogs("");
        if (panel != null)
        {
            SwingUtilities.invokeLater(panel::refreshLogs);
        }
    }

    public void resetTimer()
    {
        running = false;
        paused = false;
        autoPaused = false;
        activeSkill = null;
        detectedItemName = "";
        detectedLocation = "";
        itemsGained = 0;
        xpGained = 0;
        gpGained = 0;
        startTime = null;
        lastActivityTime = null;
        accumulatedTime = Duration.ZERO;
        lastXpTick = -1;
        itemMatchedThisTick = false;
        sessionLoot.clear();
    }

    public void togglePause()
    {
        if (!running)
        {
            return;
        }

        if (!paused)
        {

            accumulatedTime = accumulatedTime.plus(Duration.between(startTime, Instant.now()));
            paused = true;
            autoPaused = false;
        }
        else
        {
            Instant now = Instant.now();
            startTime = now;
            lastActivityTime = now;
            paused = false;
            autoPaused = false;
        }
    }
    public Duration getElapsed()
    {
        if (!running)
        {
            return Duration.ZERO;
        }
        if (paused)
        {
            return accumulatedTime;
        }
        return accumulatedTime.plus(Duration.between(startTime, Instant.now()));
    }
    public static String formatDuration(long totalSeconds)
    {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }
    public static String formatHumanDuration(long totalSeconds)
    {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0)
        {
            return hours + (hours == 1 ? " hour " : " hours ") + minutes + (minutes == 1 ? " min" : " mins");
        }
        if (minutes > 0)
        {
            return minutes + (minutes == 1 ? " min " : " mins ") + seconds + (seconds == 1 ? " sec" : " secs");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }
    @Provides
    SkillPaceConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SkillPaceConfig.class);
    }
}