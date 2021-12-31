package com.example.KamiTracking;

import com.google.common.collect.ImmutableMap;

import java.awt.Color;
import java.awt.GridLayout;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import net.runelite.api.ItemID;
import net.runelite.client.game.ItemManager;
import com.example.KamiTracking.clocks.ClockManager;
import com.example.KamiTracking.farming.CropState;
import com.example.KamiTracking.farming.FarmingContractManager;
import com.example.KamiTracking.farming.FarmingTracker;
import com.example.KamiTracking.hunter.BirdHouseTracker;
import net.runelite.client.ui.ColorScheme;

class OverviewTabPanel extends TabContentPanel
{
    private final KamiTrackingConfig config;
    private final FarmingTracker farmingTracker;
    private final BirdHouseTracker birdHouseTracker;
    private final ClockManager clockManager;
    private final FarmingContractManager farmingContractManager;

    private final OverviewItemPanel KamirOverview;
    private final OverviewItemPanel stopwatchOverview;
    private final Map<Tab, OverviewItemPanel> farmingOverviews;
    private final OverviewItemPanel birdHouseOverview;
    private final OverviewItemPanel farmingContractOverview;

    OverviewTabPanel(ItemManager itemManager, KamiTrackingConfig config, KamiTrackingPanel pluginPanel,
                     FarmingTracker farmingTracker, BirdHouseTracker birdHouseTracker, ClockManager clockManager,
                     FarmingContractManager farmingContractManager)
    {
        this.config = config;
        this.farmingTracker = farmingTracker;
        this.birdHouseTracker = birdHouseTracker;
        this.clockManager = clockManager;
        this.farmingContractManager = farmingContractManager;

        setLayout(new GridLayout(0, 1, 0, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        KamirOverview = new OverviewItemPanel(itemManager, pluginPanel, Tab.CLOCK, "Kamirs");
        add(KamirOverview);

        stopwatchOverview = new OverviewItemPanel(itemManager, pluginPanel, Tab.CLOCK, "Stopwatches");
        add(stopwatchOverview);

        birdHouseOverview = new OverviewItemPanel(itemManager, pluginPanel, Tab.BIRD_HOUSE, "Bird Houses");
        add(birdHouseOverview);

        farmingOverviews = Stream.of(Tab.FARMING_TABS)
                .filter(v -> v != Tab.OVERVIEW)
                .collect(ImmutableMap.toImmutableMap(
                        Function.identity(),
                        t ->
                        {
                            OverviewItemPanel p = new OverviewItemPanel(itemManager, pluginPanel, t, t.getName());
                            add(p);
                            return p;
                        }
                ));

        farmingContractOverview = new OverviewItemPanel(itemManager, () -> pluginPanel.switchTab(farmingContractManager.getContractTab()),
                farmingContractManager::hasContract, ItemID.SEED_PACK, "Farming Contract");
        add(farmingContractOverview);
    }

    @Override
    public int getUpdateInterval()
    {
        return 50; // 10 seconds
    }

    @Override
    public void update()
    {
        final long Kamirs = clockManager.getActiveKamirCount();
        final long stopwatches = clockManager.getActiveStopwatchCount();

        if (Kamirs == 0)
        {
            KamirOverview.updateStatus("No active Kamirs", Color.GRAY);
        }
        else
        {
            KamirOverview.updateStatus(Kamirs + " active Kamir" + (Kamirs == 1 ? "" : "s"), ColorScheme.PROGRESS_COMPLETE_COLOR);
        }

        if (stopwatches == 0)
        {
            stopwatchOverview.updateStatus("No active stopwatches", Color.GRAY);
        }
        else
        {
            stopwatchOverview.updateStatus(stopwatches + " active stopwatch" + (stopwatches == 1 ? "" : "es"), ColorScheme.PROGRESS_COMPLETE_COLOR);
        }

        farmingOverviews.forEach((patchType, panel) ->
                updateItemPanel(panel, farmingTracker.getSummary(patchType), farmingTracker.getCompletionKami(patchType)));

        updateItemPanel(birdHouseOverview, birdHouseTracker.getSummary(), birdHouseTracker.getCompletionKami());
        updateContractPanel();
    }

    private void updateItemPanel(OverviewItemPanel panel, SummaryState summary, long completionKami)
    {
        switch (summary)
        {
            case COMPLETED:
            case IN_PROGRESS:
            {
                long duration = completionKami - Instant.now().getEpochSecond();

                if (duration <= 0)
                {
                    panel.updateStatus("Ready", ColorScheme.PROGRESS_COMPLETE_COLOR);
                }
                else
                {
                    panel.updateStatus("Ready " + getFormattedEstimate(duration, config.KamiFormatMode()), Color.GRAY);
                }
                break;
            }
            case EMPTY:
                panel.updateStatus("Empty", Color.GRAY);
                break;
            case UNKNOWN:
            default:
                panel.updateStatus("Unknown", Color.GRAY);
                break;
        }
    }

    private void updateContractPanel()
    {
        switch (farmingContractManager.getSummary())
        {
            case COMPLETED:
            case IN_PROGRESS:
                switch (farmingContractManager.getContractCropState())
                {
                    case HARVESTABLE:
                    case GROWING:
                        long duration = farmingContractManager.getCompletionKami() - Instant.now().getEpochSecond();

                        if (duration <= 0)
                        {
                            farmingContractOverview.updateStatus("Ready", ColorScheme.PROGRESS_COMPLETE_COLOR);
                            return;
                        }

                        farmingContractOverview.updateStatus("Ready " + getFormattedEstimate(duration, config.KamiFormatMode()), Color.GRAY);
                        return;
                    case DISEASED:
                        farmingContractOverview.updateStatus("Diseased", CropState.DISEASED.getColor());
                        return;
                    case DEAD:
                        farmingContractOverview.updateStatus("Dead", CropState.DEAD.getColor());
                        return;
                }
                // fallthrough
            case UNKNOWN:
            default:
                farmingContractOverview.updateStatus("Unknown", Color.GRAY);
                return;
            case EMPTY:
                farmingContractOverview.updateStatus(farmingContractManager.getContractName(), Color.GRAY);
                return;
            case OCCUPIED:
                farmingContractOverview.updateStatus(farmingContractManager.getContractName(), Color.RED);
                return;
        }
    }
}
