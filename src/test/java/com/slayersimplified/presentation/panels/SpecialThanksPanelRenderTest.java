/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.panels;

import net.runelite.client.ui.PluginPanel;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

/**
 * Guards the Special Thanks pane: every contributor renders, the list is
 * scrollable so it can grow without clipping, and the back button returns to
 * settings. Add an assertion here whenever a contributor is credited.
 */
public class SpecialThanksPanelRenderTest
{
    @Test
    public void paneCreditsEveryContributorInsideAScrollPane()
    {
        SpecialThanksPanel panel = new SpecialThanksPanel(() -> {});

        List<JLabel> labels = new ArrayList<>();
        List<AbstractButton> buttons = new ArrayList<>();
        List<JScrollPane> scrollPanes = new ArrayList<>();
        collect(panel, labels, buttons, scrollPanes);

        Assert.assertFalse("contributor list is not inside a scroll pane", scrollPanes.isEmpty());

        Assert.assertTrue("vividflash credit missing",
                labels.stream().anyMatch(l -> text(l).contains("vividflash")));
        Assert.assertTrue("danielvxsp credit missing",
                labels.stream().anyMatch(l -> text(l).contains("danielvxsp")));
        Assert.assertTrue("Bruster112 credit missing",
                labels.stream().anyMatch(l -> text(l).contains("Bruster112")));

        // Each credited name needs a sentence saying what they actually did.
        Assert.assertTrue("vividflash contribution description missing",
                labels.stream().anyMatch(l -> text(l).contains("monster image")));
        Assert.assertTrue("danielvxsp contribution description missing",
                labels.stream().anyMatch(l -> text(l).contains("minimum window size")));
        Assert.assertTrue("Bruster112 contribution description missing",
                labels.stream().anyMatch(l -> text(l).contains("only show once you're actually fighting")));
    }

    /**
     * The bug this guards: HTML labels report their unwrapped width as their
     * minimum, so without an explicit CSS width the text runs off the pane.
     */
    @Test
    public void bodyTextWrapsInsideTheViewportAtAnyWidth()
    {
        for (int viewportWidth : new int[]{PluginPanel.PANEL_WIDTH, 150, 400})
        {
            SpecialThanksPanel panel = new SpecialThanksPanel(() -> {});
            panel.rewrapText(viewportWidth);

            List<JLabel> labels = new ArrayList<>();
            List<AbstractButton> buttons = new ArrayList<>();
            List<JScrollPane> scrollPanes = new ArrayList<>();
            collect(panel, labels, buttons, scrollPanes);

            for (JLabel label : labels)
            {
                if (!text(label).startsWith("<html>"))
                {
                    continue;
                }
                int width = label.getPreferredSize().width;
                Assert.assertTrue(
                        "text overflows a " + viewportWidth + "px viewport (needs " + width + "px): "
                                + text(label),
                        width <= viewportWidth);
            }
        }
    }

    @Test
    public void backButtonReturnsToSettings()
    {
        boolean[] closed = {false};
        SpecialThanksPanel panel = new SpecialThanksPanel(() -> closed[0] = true);

        List<JLabel> labels = new ArrayList<>();
        List<AbstractButton> buttons = new ArrayList<>();
        List<JScrollPane> scrollPanes = new ArrayList<>();
        collect(panel, labels, buttons, scrollPanes);

        AbstractButton back = buttons.stream()
                .filter(b -> "←".equals(b.getText()))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull("back button missing", back);

        back.doClick();
        Assert.assertTrue("back button did not close the pane", closed[0]);
    }

    private static String text(JLabel label)
    {
        return label.getText() == null ? "" : label.getText();
    }

    private static void collect(Container root, List<JLabel> labels,
                                List<AbstractButton> buttons, List<JScrollPane> scrollPanes)
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
            else if (c instanceof JScrollPane)
            {
                scrollPanes.add((JScrollPane) c);
            }
            if (c instanceof Container)
            {
                collect((Container) c, labels, buttons, scrollPanes);
            }
        }
    }
}
