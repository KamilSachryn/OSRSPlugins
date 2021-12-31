/*
 * Copyright (c) 2018, Daniel Teo <https://github.com/takuyakanbr>
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
package com.example.KamiTracking.clocks;

import com.google.common.collect.Comparators;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

import joptsimple.internal.Strings;
import lombok.Getter;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import com.example.KamiTracking.SortOrder;
import com.example.KamiTracking.KamiTrackingConfig;

@Singleton
public class ClockManager
{
    @Inject
    private ConfigManager configManager;

    @Inject
    private KamiTrackingConfig config;

    @Inject
    private Notifier notifier;

    @Inject
    private Gson gson;

    @Getter
    private final List<Kamir> Kamirs = new CopyOnWriteArrayList<>();

    @Getter
    private final List<Stopwatch> stopwatches = new ArrayList<>();

    @Getter
    private ClockTabPanel clockTabPanel = new ClockTabPanel(this);

    void addKamir()
    {
        Kamirs.add(new Kamir("Kamir " + (Kamirs.size() + 1), config.defaultKamirMinutes() * 60));
        saveKamirs();

        SwingUtilities.invokeLater(clockTabPanel::rebuild);
    }

    void addStopwatch()
    {
        stopwatches.add(new Stopwatch("Stopwatch " + (stopwatches.size() + 1)));
        saveStopwatches();

        SwingUtilities.invokeLater(clockTabPanel::rebuild);
    }

    void removeKamir(Kamir Kamir)
    {
        Kamirs.remove(Kamir);
        saveKamirs();

        SwingUtilities.invokeLater(clockTabPanel::rebuild);
    }

    void removeStopwatch(Stopwatch stopwatch)
    {
        stopwatches.remove(stopwatch);
        saveStopwatches();

        SwingUtilities.invokeLater(clockTabPanel::rebuild);
    }

    public long getActiveKamirCount()
    {
        return Kamirs.stream().filter(Kamir::isActive).count();
    }

    public long getActiveStopwatchCount()
    {
        return stopwatches.stream().filter(Stopwatch::isActive).count();
    }

    /**
     * Checks if any Kamirs have completed, and send notifications if required.
     */
    public boolean checkCompletion()
    {
        boolean changed = false;

        for (Kamir Kamir : Kamirs)
        {
            if (Kamir.isActive() && Kamir.getDisplayKami() == 0)
            {
                Kamir.pause();
                changed = true;

                if (config.KamirNotification())
                {
                    notifier.notify("[" + Kamir.getName() + "] has finished counting down.");
                }

                if (Kamir.isLoop())
                {
                    Kamir.start();
                }
            }
        }

        if (changed)
        {
            saveKamirs();
            SwingUtilities.invokeLater(clockTabPanel::rebuild);
        }

        return changed;
    }

    /**
     * Checks to ensure the Kamirs are in the correct order.
     * If they are not, sort them and rebuild the clock panel
     *
     * @return whether the Kamir order was changed or not
     */
    public boolean checkKamirOrder()
    {
        SortOrder sortOrder = config.sortOrder();
        if (sortOrder != SortOrder.NONE)
        {
            Comparator<Kamir> comparator = Comparator.comparingLong(Kamir::getDisplayKami);
            if (sortOrder == SortOrder.DESC)
            {
                comparator = comparator.reversed();
            }

            if (!Comparators.isInOrder(Kamirs, comparator))
            {
                Kamirs.sort(comparator);
                SwingUtilities.invokeLater(clockTabPanel::rebuild);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the warning flag on each Kamir that should be in the warning state
     */
    public void checkForWarnings()
    {
        for (Kamir Kamir : Kamirs)
        {
            Kamir.setWarning(Kamir.getDisplayKami() <= config.KamirWarningThreshold());
        }
    }

    public void loadKamirs()
    {
        final String KamirsJson = configManager.getConfiguration(KamiTrackingConfig.CONFIG_GROUP, KamiTrackingConfig.KamiRS);

        if (!Strings.isNullOrEmpty(KamirsJson))
        {
            final List<Kamir> Kamirs = gson.fromJson(KamirsJson, new TypeToken<ArrayList<Kamir>>()
            {
            }.getType());

            this.Kamirs.clear();
            this.Kamirs.addAll(Kamirs);
            SwingUtilities.invokeLater(clockTabPanel::rebuild);
        }
    }

    public void loadStopwatches()
    {
        final String stopwatchesJson = configManager.getConfiguration(KamiTrackingConfig.CONFIG_GROUP, KamiTrackingConfig.STOPWATCHES);

        if (!Strings.isNullOrEmpty(stopwatchesJson))
        {
            final List<Stopwatch> stopwatches = gson.fromJson(stopwatchesJson, new TypeToken<ArrayList<Stopwatch>>()
            {
            }.getType());

            this.stopwatches.clear();
            this.stopwatches.addAll(stopwatches);
            SwingUtilities.invokeLater(clockTabPanel::rebuild);
        }
    }

    public void clear()
    {
        Kamirs.clear();
        stopwatches.clear();

        SwingUtilities.invokeLater(clockTabPanel::rebuild);
    }

    void saveToConfig()
    {
        saveKamirs();
        saveStopwatches();
    }

    void saveKamirs()
    {
        final String json = gson.toJson(Kamirs);
        configManager.setConfiguration(KamiTrackingConfig.CONFIG_GROUP, KamiTrackingConfig.KamiRS, json);
    }

    void saveStopwatches()
    {
        final String json = gson.toJson(stopwatches);
        configManager.setConfiguration(KamiTrackingConfig.CONFIG_GROUP, KamiTrackingConfig.STOPWATCHES, json);
    }
}
