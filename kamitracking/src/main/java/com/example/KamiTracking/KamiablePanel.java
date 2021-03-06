/*
 * Copyright (c) 2018 Abex
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.example.KamiTracking;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;

import lombok.Getter;
import net.runelite.api.Constants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ThinProgressBar;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Getter
public class KamiablePanel<T> extends JPanel
{
    private final T Kamiable;
    private final JLabel icon = new JLabel();
    private final JLabel farmingContractIcon = new JLabel();
    private final JToggleButton notifyButton = new JToggleButton();
    private final JLabel estimate = new JLabel();
    private final ThinProgressBar progress = new ThinProgressBar();
    private final JLabel text;

    public KamiablePanel(T Kamiable, String title, int maximumProgressValue)
    {
        this.Kamiable = Kamiable;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(7, 0, 0, 0));

        JPanel topContainer = new JPanel();
        topContainer.setBorder(new EmptyBorder(7, 7, 6, 0));
        topContainer.setLayout(new BorderLayout());
        topContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        icon.setMinimumSize(new Dimension(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT));
        farmingContractIcon.setMinimumSize(new Dimension(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT));

        JPanel infoPanel = new JPanel();
        infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        infoPanel.setLayout(new GridLayout(2, 1));
        infoPanel.setBorder(new EmptyBorder(4, 4, 4, 0));

        text = new JShadowedLabel(title);
        text.setFont(FontManager.getRunescapeSmallFont());
        text.setForeground(Color.WHITE);

        estimate.setFont(FontManager.getRunescapeSmallFont());
        estimate.setForeground(Color.GRAY);

        infoPanel.add(text);
        infoPanel.add(estimate);

        ImageIcon notifyIcon = new ImageIcon(new BufferedImage(14, 14, BufferedImage.TYPE_INT_RGB));
        ImageIcon notifySelectedIcon = new ImageIcon(new BufferedImage(14, 14, BufferedImage.TYPE_INT_RGB));

        notifyButton.setPreferredSize(new Dimension(30, 16));
        notifyButton.setBorder(new EmptyBorder(0, 0, 0, 10));
        notifyButton.setIcon(notifyIcon);
        notifyButton.setSelectedIcon(notifySelectedIcon);
        SwingUtil.removeButtonDecorations(notifyButton);
        SwingUtil.addModalTooltip(notifyButton, "Disable notifications", "Enable notifications");

        JPanel notifyPanel = new JPanel();
        notifyPanel.setLayout(new BorderLayout());
        notifyPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        notifyPanel.add(notifyButton, BorderLayout.CENTER);

        JPanel iconPanel = new JPanel();
        iconPanel.setLayout(new BorderLayout());
        iconPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        iconPanel.add(notifyPanel, BorderLayout.EAST);
        iconPanel.add(farmingContractIcon, BorderLayout.WEST);

        topContainer.add(iconPanel, BorderLayout.EAST);
        topContainer.add(icon, BorderLayout.WEST);
        topContainer.add(infoPanel, BorderLayout.CENTER);

        progress.setValue(0);
        progress.setMaximumValue(maximumProgressValue);

        add(topContainer, BorderLayout.NORTH);
        add(progress, BorderLayout.SOUTH);
    }
}
