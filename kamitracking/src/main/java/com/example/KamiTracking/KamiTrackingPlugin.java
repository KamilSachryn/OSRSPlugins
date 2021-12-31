/*
 * Copyright (c) 2018 Abex
 * Copyright (c) 2018, Daniel Teo <https://github.com/takuyakanbr>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.example.KamiTracking;


//import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;


import com.google.inject.Inject;
import com.google.inject.Provides;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import static com.example.KamiTracking.KamiTrackingConfig.CONFIG_GROUP;
import static com.example.KamiTracking.KamiTrackingConfig.PREFER_SOONEST;
import static com.example.KamiTracking.KamiTrackingConfig.STOPWATCHES;
import static com.example.KamiTracking.KamiTrackingConfig.KamiRS;

import com.example.KamiTracking.clocks.ClockManager;
import com.example.KamiTracking.farming.FarmingContractManager;
import com.example.KamiTracking.farming.FarmingTracker;
import com.example.KamiTracking.hunter.BirdHouseTracker;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import org.pf4j.Extension;

@Extension
@PluginDescriptor(
        name = "Kami Tracking 2",
        description = "Enable the Kami Tracking panel, which contains Kamirs, stopwatches, and farming and bird house trackers",
        tags = {"birdhouse", "farming", "hunter", "notifications", "skilling", "stopwatches", "Kamirs", "panel"}
)
@Slf4j
public class KamiTrackingPlugin extends Plugin
{
    private static final String CONTRACT_COMPLETED = "You've completed a Farming Guild Contract. You should return to Guildmaster Jane.";

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private Client client;

    @Inject
    private FarmingTracker farmingTracker;

    @Inject
    private BirdHouseTracker birdHouseTracker;

    @Inject
    private FarmingContractManager farmingContractManager;

    @Inject
    private ClockManager clockManager;

    @Inject
    private InfoBoxManager infoBoxManager;

    @Inject
    private ScheduledExecutorService executorService;

    @Inject
    private ConfigManager configManager;

    private ScheduledFuture panelUpdateFuture;

    private ScheduledFuture notifierFuture;

    private KamiTrackingPanel panel;

    private NavigationButton navButton;

    private WorldPoint lastTickLocation;
    private boolean lastTickPostLogin;

    private int lastModalCloseTick = 0;

    @Provides
    KamiTrackingConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KamiTrackingConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        clockManager.loadKamirs();
        clockManager.loadStopwatches();
        birdHouseTracker.loadFromConfig();
        farmingTracker.loadCompletionKamis();

        //final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "watch2.png");

        final BufferedImage icon2 = new BufferedImage(14, 14, BufferedImage.TYPE_INT_RGB);

        panel = injector.getInstance(KamiTrackingPanel.class);

        navButton = NavigationButton.builder()
                .tooltip("Kami Tracking")
                .icon(icon2)
                .panel(panel)
                .priority(4)
                .build();

        clientToolbar.addNavigation(navButton);

        panelUpdateFuture = executorService.scheduleAtFixedRate(this::updatePanel, 200, 200, TimeUnit.MILLISECONDS);
        notifierFuture = executorService.scheduleAtFixedRate(this::checkCompletion, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    protected void shutDown() throws Exception
    {
        lastTickLocation = null;
        lastTickPostLogin = false;

        if (panelUpdateFuture != null)
        {
            panelUpdateFuture.cancel(true);
            panelUpdateFuture = null;
        }

        notifierFuture.cancel(true);
        clientToolbar.removeNavigation(navButton);
        infoBoxManager.removeInfoBox(farmingContractManager.getInfoBox());
        farmingContractManager.setInfoBox(null);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e)
    {
        if (!e.getGroup().equals(CONFIG_GROUP))
        {
            return;
        }

        if (clockManager.getKamirs().isEmpty() && e.getKey().equals(KamiRS))
        {
            clockManager.loadKamirs();
        }
        else if (clockManager.getStopwatches().isEmpty() && e.getKey().equals(STOPWATCHES))
        {
            clockManager.loadStopwatches();
        }
        else if (e.getKey().equals(PREFER_SOONEST))
        {
            farmingTracker.loadCompletionKamis();
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted commandExecuted)
    {
        if (commandExecuted.getCommand().equals("resetfarmtick"))
        {
            configManager.unsetRSProfileConfiguration(KamiTrackingConfig.CONFIG_GROUP, KamiTrackingConfig.FARM_TICK_OFFSET_PRECISION);
            configManager.unsetRSProfileConfiguration(KamiTrackingConfig.CONFIG_GROUP, KamiTrackingConfig.FARM_TICK_OFFSET);
        }
    }

    @Subscribe
    public void onGameTick(GameTick t)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            lastTickLocation = null;
            return;
        }

        // bird house data is only sent after exiting the post-login screen
        Widget motd = client.getWidget(WidgetInfo.LOGIN_CLICK_TO_PLAY_SCREEN_MESSAGE_OF_THE_DAY);
        if (motd != null && !motd.isHidden())
        {
            lastTickPostLogin = true;
            return;
        }

        if (lastTickPostLogin)
        {
            lastTickPostLogin = false;
            return;
        }

        WorldPoint loc = lastTickLocation;
        lastTickLocation = client.getLocalPlayer().getWorldLocation();

        if (loc == null || loc.getRegionID() != lastTickLocation.getRegionID())
        {
            return;
        }

        boolean birdHouseDataChanged = birdHouseTracker.updateData(loc);
        boolean farmingDataChanged = farmingTracker.updateData(loc, client.getTickCount() - lastModalCloseTick);
        boolean farmingContractDataChanged = farmingContractManager.updateData(loc);

        if (birdHouseDataChanged || farmingDataChanged || farmingContractDataChanged)
        {
            panel.update();
        }
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
    {
        farmingTracker.loadCompletionKamis();
        birdHouseTracker.loadFromConfig();
        farmingContractManager.loadContractFromConfig();
        panel.update();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE || !event.getMessage().equals(CONTRACT_COMPLETED))
        {
            return;
        }

        farmingContractManager.setContract(null);
    }

    @Subscribe
    private void onWidgetClosed(WidgetClosed ev)
    {
        if (ev.getModalMode() != WidgetModalMode.NON_MODAL)
        {
            lastModalCloseTick = client.getTickCount();
        }
    }

    private void checkCompletion()
    {
        boolean birdHouseDataChanged = birdHouseTracker.checkCompletion();

        if (birdHouseDataChanged)
        {
            panel.update();
        }

        farmingTracker.checkCompletion();
    }

    private void updatePanel()
    {
        long unitKami = Instant.now().toEpochMilli() / 200;

        boolean clockDataChanged = false;
        boolean KamirOrderChanged = false;

        if (unitKami % 5 == 0)
        {
            clockDataChanged = clockManager.checkCompletion();
            KamirOrderChanged = clockManager.checkKamirOrder();
            clockManager.checkForWarnings();
        }

        if (unitKami % panel.getUpdateInterval() == 0 || clockDataChanged || KamirOrderChanged)
        {
            panel.update();
        }
    }
}
