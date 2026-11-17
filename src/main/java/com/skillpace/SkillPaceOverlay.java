package com.skillpace;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

public class SkillPaceOverlay extends OverlayPanel
{
    private final SkillPacePlugin plugin;
    private final SkillPaceConfig config;

    @Inject
    public SkillPaceOverlay(SkillPacePlugin plugin, SkillPaceConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        Duration elapsed = plugin.getElapsed();
        long totalSeconds = elapsed.getSeconds();
        String timeFormatted = SkillPacePlugin.formatDuration(totalSeconds);
        if (plugin.isAutoPaused())
        {
            timeFormatted += " (AFK)";
        }

        Color timerColor = !plugin.isRunning()
                ? Color.WHITE
                : (plugin.isPaused() ? Color.YELLOW : Color.GREEN);

        panelComponent.setPreferredSize(new Dimension(190, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(timeFormatted)
                .color(timerColor)
                .build());

        String activityText = "Swing / catch to start...";
        if (plugin.getActiveSkill() != null)
        {
            activityText = plugin.getDetectedItemName().isEmpty()
                    ? plugin.getActiveSkill().getName()
                    : plugin.getActiveSkill().getName() + " - " + plugin.getDetectedItemName();
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left(activityText)
                .leftColor(Color.CYAN)
                .build());

        int items = plugin.getItemsGained();
        String noun = plugin.getSmartUnitNoun();
        String verb = plugin.getSmartPastVerb();
        long itemsPerHour = totalSeconds > 0 ? Math.round((items * 3600.0) / totalSeconds) : 0;

        panelComponent.getChildren().add(LineComponent.builder()
                .left(String.format("%,d %s %s", items, noun, verb))
                .right(String.format("%,d %s/hr", itemsPerHour, noun))
                .rightColor(Color.GREEN)
                .build());

        int xp = plugin.getXpGained();
        long xpPerHour = totalSeconds > 0 ? Math.round((xp * 3600.0) / totalSeconds) : 0;

        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP: " + QuantityFormatter.quantityToStackSize(xp))
                .right(QuantityFormatter.quantityToStackSize(xpPerHour) + "/hr")
                .build());

        double avgXp = items > 0 ? (double) xp / items : 0;
        double secPerItem = items > 0 ? (double) totalSeconds / items : 0;

        panelComponent.getChildren().add(LineComponent.builder()
                .left(String.format("Avg: %.1f XP/%s", avgXp, noun))
                .right(String.format("%.1fs/%s", secPerItem, noun))
                .build());

        long gp = plugin.getGpGained();
        long gpPerHour = totalSeconds > 0 ? Math.round((gp * 3600.0) / totalSeconds) : 0;

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Loot: " + QuantityFormatter.quantityToStackSize(gp) + " gp")
                .right(QuantityFormatter.quantityToStackSize(gpPerHour) + "/hr")
                .rightColor(new Color(220, 170, 50))
                .build());

        int goal = config.targetItems();
        if (goal > 0 && items < goal && itemsPerHour > 0)
        {
            long remaining = goal - items;
            long etaSeconds = Math.round((remaining * 3600.0) / itemsPerHour);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Goal (" + items + "/" + goal + "):")
                    .right("ETA " + SkillPacePlugin.formatDuration(etaSeconds))
                    .rightColor(Color.LIGHT_GRAY)
                    .build());
        }

        return super.render(graphics);
    }
}
