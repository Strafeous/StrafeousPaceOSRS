package com.skillpace;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("skillpacetimer")
public interface SkillPaceConfig extends Config
{
    @ConfigItem(
            keyName = "afkTimeoutSeconds",
            name = "AFK Auto-Pause (Seconds)",
            description = "Automatically pauses the timer after this many seconds with no skilling activity"
    )
    default int afkTimeoutSeconds()
    {
        return 120;
    }

    @ConfigItem(
            keyName = "autoSwitch",
            name = "Auto-Save & Switch Activities",
            description = "Automatically saves your current run and resets to 00:00 if you start a different skill"
    )
    default boolean autoSwitch()
    {
        return true;
    }

    @ConfigItem(
            keyName = "targetItems",
            name = "Item Goal (0 to hide)",
            description = "Optional target count (e.g., 250). Set to 0 to hide goal ETA."
    )
    default int targetItems()
    {
        return 0;
    }

    @ConfigItem(
            keyName = "savedLogs",
            name = "Saved Logs",
            description = "Internal storage for session logs",
            hidden = true
    )
    default String savedLogs()
    {
        return "";
    }

    @ConfigItem(
            keyName = "savedLogs",
            name = "",
            description = ""
    )
    void setSavedLogs(String logs);
}
