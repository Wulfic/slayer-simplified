/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.panels;

import com.slayersimplified.SlayerSimplifiedConfig;
import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.StreakFillerMaster;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Guards that the settings panel builds without throwing and that the
 * community section renders: bug link, feature suggestion link, Ko-fi link,
 * and a Special Thanks button that navigates to its own pane.
 */
public class SettingsPanelRenderTest
{
    /** All config getters have defaults, so the stub only supplies the setters. */
    private static final class StubConfig implements SlayerSimplifiedConfig
    {
        @Override public void setPreferredMaster(SlayerMaster master) {}
        @Override public void setHighlightTarget(boolean value) {}
        @Override public void setHighlightColor(Color color) {}
        @Override public void setAutoNavigate(boolean value) {}
        @Override public void setDebugCoordinates(boolean value) {}
        @Override public void setRemindSlayerCape(boolean value) {}
        @Override public void setShowReminderOverlay(boolean value) {}
        @Override public void setStreakOptimizerEnabled(boolean value) {}
        @Override public void setStreakFillerMaster(StreakFillerMaster value) {}
        @Override public void setShowNonSlayerEnemies(boolean value) {}
        @Override public void setTileNotes(boolean value) {}
        @Override public void setCurrentTaskName(String taskName) {}
        @Override public void setCurrentTaskTotal(int total) {}
    }

    @Test
    public void panelBuildsAndShowsCommunityLinks()
    {
        SettingsPanel panel = new SettingsPanel(new StubConfig(), () -> {}, () -> {});
        panel.refresh();

        List<JLabel> labels = new ArrayList<>();
        List<AbstractButton> buttons = new ArrayList<>();
        collect(panel, labels, buttons);

        Assert.assertTrue("bug report link missing",
                labels.stream().anyMatch(l -> text(l).contains("Found a")));
        Assert.assertTrue("feature suggestion link missing",
                labels.stream().anyMatch(l -> text(l).contains("feature suggestion")));
        Assert.assertTrue("Ko-fi link missing",
                labels.stream().anyMatch(l -> text(l).contains("Ko-fi")));
        Assert.assertTrue("Done button missing",
                buttons.stream().anyMatch(b -> "Done".equals(b.getText())));
    }

    @Test
    public void panelCreditsTheUpstreamPluginsItWasBuiltOn()
    {
        SettingsPanel panel = new SettingsPanel(new StubConfig(), () -> {}, () -> {});

        List<JLabel> labels = new ArrayList<>();
        List<AbstractButton> buttons = new ArrayList<>();
        collect(panel, labels, buttons);

        Assert.assertTrue("Slayer Assistant credit missing",
                labels.stream().anyMatch(l -> text(l).contains("Slayer Assistant")
                        && text(l).contains("LeeOKeefe")));
        Assert.assertTrue("Loot Lookup credit missing",
                labels.stream().anyMatch(l -> text(l).contains("Loot Lookup")
                        && text(l).contains("donth77")));
        Assert.assertTrue("Shortest Path credit missing",
                labels.stream().anyMatch(l -> text(l).contains("Shortest Path")
                        && text(l).contains("Skretzo")));
    }

    @Test
    public void specialThanksButtonNavigatesToItsOwnPane()
    {
        boolean[] navigated = {false};
        SettingsPanel panel = new SettingsPanel(new StubConfig(), () -> {}, () -> navigated[0] = true);

        List<JLabel> labels = new ArrayList<>();
        List<AbstractButton> buttons = new ArrayList<>();
        collect(panel, labels, buttons);

        AbstractButton thanks = buttons.stream()
                .filter(b -> "Special Thanks".equals(b.getText()))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull("Special Thanks button missing", thanks);

        // Credits live on their own pane now, not inline in settings.
        Assert.assertFalse("contributor credits should not render inside settings",
                labels.stream().anyMatch(l -> text(l).contains("vividflash")));

        thanks.doClick();
        Assert.assertTrue("Special Thanks button did not request its pane", navigated[0]);
    }

    /**
     * "Non-Slayer Enemies", "Animals" and "Bosses" are task-list grouping headers,
     * not masters — they have no NPC and no WorldPoint, so picking one made
     * "navigate to master" a silent no-op. Only Animals and Bosses ever reached the
     * dropdown (issue #4), but the guard is the same for all three: a master the
     * player can be sent to must have somewhere to be sent.
     */
    @Test
    public void preferredMasterDropdownOffersOnlyRealMasters()
    {
        SettingsPanel panel = new SettingsPanel(new StubConfig(), () -> {}, () -> {});

        JComboBox<?> masterCombo = findMasterCombo(panel);
        Assert.assertNotNull("preferred master combo missing", masterCombo);

        List<SlayerMaster> offered = new ArrayList<>();
        for (int i = 0; i < masterCombo.getItemCount(); i++)
        {
            offered.add((SlayerMaster) masterCombo.getItemAt(i));
        }

        Assert.assertEquals(
                "every offered master must have an in-world location to navigate to",
                Arrays.stream(SlayerMaster.values())
                        .filter(SlayerMaster::isNavigable)
                        .collect(Collectors.toList()),
                offered);

        for (SlayerMaster pseudo : new SlayerMaster[]{
                SlayerMaster.NON_SLAYER_ENEMIES, SlayerMaster.ANIMALS, SlayerMaster.BOSSES})
        {
            Assert.assertFalse(pseudo.getDisplayName() + " is not a slayer master",
                    offered.contains(pseudo));
        }
    }

    /** The combo holding {@link SlayerMaster}s — the filler combo holds a different enum. */
    private static JComboBox<?> findMasterCombo(Container root)
    {
        for (Component c : root.getComponents())
        {
            if (c instanceof JComboBox)
            {
                JComboBox<?> combo = (JComboBox<?>) c;
                if (combo.getItemCount() > 0 && combo.getItemAt(0) instanceof SlayerMaster)
                {
                    return combo;
                }
            }
            if (c instanceof Container)
            {
                JComboBox<?> found = findMasterCombo((Container) c);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    private static String text(JLabel label)
    {
        return label.getText() == null ? "" : label.getText();
    }

    private static void collect(Container root, List<JLabel> labels, List<AbstractButton> buttons)
    {
        for (Component c : root.getComponents())
        {
            if (c instanceof JLabel)
            {
                labels.add((JLabel) c);
            }
            else if (c instanceof AbstractButton)
            {
                buttons.add((AbstractButton) c);
            }
            if (c instanceof Container)
            {
                collect((Container) c, labels, buttons);
            }
        }
    }
}
