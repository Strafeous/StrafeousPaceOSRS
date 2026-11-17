package com.skillpace;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

public class SkillPacePanel extends PluginPanel
{
    private final SkillPacePlugin plugin;
    private final JPanel logsContainer = new JPanel();

    public SkillPacePanel(SkillPacePlugin plugin)
    {
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel(SkillPacePlugin.PLUGIN_TITLE);
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());
        topPanel.add(title, BorderLayout.NORTH);

        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 6, 0));
        buttonRow.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton exportBtn = new JButton("Export Log");
        exportBtn.setFocusPainted(false);
        exportBtn.addActionListener(e -> plugin.exportLogsToDesktop());

        JButton clearBtn = new JButton("Clear All");
        clearBtn.setFocusPainted(false);
        clearBtn.addActionListener(e -> plugin.clearAllLogs());

        buttonRow.add(exportBtn);
        buttonRow.add(clearBtn);
        topPanel.add(buttonRow, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        logsContainer.setLayout(new BoxLayout(logsContainer, BoxLayout.Y_AXIS));
        logsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        logsContainer.setBorder(new EmptyBorder(10, 0, 0, 0));

        add(logsContainer, BorderLayout.CENTER);
        refreshLogs();
    }

    public void refreshLogs()
    {
        logsContainer.removeAll();
        List<String> entries = plugin.getLogEntries();

        if (entries.isEmpty())
        {
            JLabel emptyLabel = new JLabel("<html>No logged runs yet.<br>Sessions save automatically when you go AFK (120s), hop worlds, log out, or switch skills!</html>");
            emptyLabel.setForeground(Color.LIGHT_GRAY);
            emptyLabel.setFont(FontManager.getRunescapeSmallFont());
            logsContainer.add(emptyLabel);
        }
        else
        {
            for (int i = 0; i < entries.size(); i++)
            {
                String[] parts = entries.get(i).split("\\|", -1);
                if (parts.length < 8)
                {
                    continue;
                }

                final int index = i;
                JPanel card = createLogCard(parts, index);
                logsContainer.add(card);
                logsContainer.add(Box.createRigidArea(new Dimension(0, 10)));
            }
        }

        logsContainer.revalidate();
        logsContainer.repaint();
    }

    private JPanel createLogCard(String[] parts, int index)
    {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel titleLabel = new JLabel("Trained " + parts[0]);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeBoldFont());

        JButton deleteBtn = new JButton("X");
        deleteBtn.setMargin(new Insets(0, 4, 0, 4));
        deleteBtn.setForeground(Color.RED);
        deleteBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        deleteBtn.setBorderPainted(false);
        deleteBtn.setFocusPainted(false);
        deleteBtn.addActionListener(e -> plugin.deleteLogEntry(index));

        header.add(titleLabel, BorderLayout.WEST);
        header.add(deleteBtn, BorderLayout.EAST);

        JPanel statsPanel = new JPanel(new GridLayout(5, 1, 0, 2));
        statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel dateLabel = new JLabel(parts[1]);
        dateLabel.setForeground(new Color(220, 170, 50));
        dateLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel timeLabel = new JLabel(parts[2]);
        timeLabel.setForeground(Color.GREEN);
        timeLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel paceLabel = new JLabel(parts[3]);
        paceLabel.setForeground(Color.CYAN);
        paceLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel xpLabel = new JLabel("XP: " + parts[4] + " | Avg: " + parts[5]);
        xpLabel.setForeground(Color.LIGHT_GRAY);
        xpLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel extraLabel = new JLabel(parts[6]);
        extraLabel.setForeground(Color.LIGHT_GRAY);
        extraLabel.setFont(FontManager.getRunescapeSmallFont());

        statsPanel.add(dateLabel);
        statsPanel.add(timeLabel);
        statsPanel.add(paceLabel);
        statsPanel.add(xpLabel);
        statsPanel.add(extraLabel);

        card.add(header, BorderLayout.NORTH);
        card.add(statsPanel, BorderLayout.CENTER);

        if (parts.length >= 9 && !parts[8].isEmpty())
        {
            long totalSeconds = 0;
            try
            {
                totalSeconds = Long.parseLong(parts[7]);
            }
            catch (NumberFormatException ignored)
            {
            }

            String[] lootTokens = parts[8].split(",");
            int cols = 5;
            int rows = (int) Math.ceil(lootTokens.length / (double) cols);

            JPanel lootGrid = new JPanel(new GridLayout(rows, cols, 2, 2));
            lootGrid.setBackground(new Color(30, 30, 30));
            lootGrid.setBorder(new EmptyBorder(4, 4, 4, 4));

            for (String token : lootTokens)
            {
                String[] kv = token.split(":");
                if (kv.length != 2)
                {
                    continue;
                }

                try
                {
                    int itemId = Integer.parseInt(kv[0]);
                    int qty = Integer.parseInt(kv[1]);

                    JPanel slot = new JPanel(new BorderLayout());
                    slot.setBackground(new Color(45, 45, 45));
                    slot.setPreferredSize(new Dimension(36, 36));

                    AsyncBufferedImage itemImg = plugin.getItemManager().getImage(itemId, qty, qty > 1);
                    JLabel iconLabel = new JLabel();
                    iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    itemImg.addTo(iconLabel);

                    String itemName = plugin.getItemManager().getItemComposition(itemId).getName();
                    long perHr = totalSeconds > 0 ? Math.round((qty * 3600.0) / totalSeconds) : 0;
                    slot.setToolTipText(String.format("%s x %,d (%,d/hr)", itemName, qty, perHr));

                    slot.add(iconLabel, BorderLayout.CENTER);
                    lootGrid.add(slot);
                }
                catch (NumberFormatException ignored)
                {
                }
            }

            int remainder = (rows * cols) - lootTokens.length;
            for (int r = 0; r < remainder; r++)
            {
                JPanel emptySlot = new JPanel();
                emptySlot.setBackground(new Color(45, 45, 45));
                emptySlot.setPreferredSize(new Dimension(36, 36));
                lootGrid.add(emptySlot);
            }

            card.add(lootGrid, BorderLayout.SOUTH);
        }

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }
}
