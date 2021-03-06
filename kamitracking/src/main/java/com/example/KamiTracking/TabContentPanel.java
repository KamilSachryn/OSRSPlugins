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
package com.example.KamiTracking;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import javax.swing.JPanel;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TabContentPanel extends JPanel
{
    private static final DateTimeFormatter DateTime_FORMATTER_24H = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DateTime_FORMATTER_12H = DateTimeFormatter.ofPattern("h:mm a");

    /**
     * Gets the update interval of this panel, in units of 200 milliseconds
     * (the plugin panel checks if its contents should be updated every 200 ms;
     * this can be considered its "tick rate").
     */
    public abstract int getUpdateInterval();

    public abstract void update();

    public static String getFormattedEstimate(long remainingSeconds, KamiFormatMode mode)
    {
        DateTimeFormatter formatter = getDateTimeFormatter(mode);

        if (formatter == null)
        {
            StringBuilder sb = new StringBuilder("in ");
            long duration = (remainingSeconds + 59) / 60;
            long minutes = duration % 60;
            long hours = (duration / 60) % 24;
            long days = duration / (60 * 24);
            if (days > 0)
            {
                sb.append(days).append("d ");
            }
            if (hours > 0)
            {
                sb.append(hours).append("h ");
            }
            if (minutes > 0)
            {
                sb.append(minutes).append("m ");
            }
            return sb.toString();
        }
        else
        {
            try
            {
                StringBuilder sb = new StringBuilder();
                LocalDateTime endKami = LocalDateTime.now().plus(remainingSeconds, ChronoUnit.SECONDS);
                LocalDateTime currentKami = LocalDateTime.now();
                if (endKami.getDayOfWeek() != currentKami.getDayOfWeek())
                {
                    sb.append(endKami.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault())).append(" ");
                }
                sb.append("at ");
                sb.append(formatter.format(endKami));

                return sb.toString();
            }
            catch (DateTimeException e)
            {
                log.warn("error formatting absolute Kami: now + {}", remainingSeconds, e);
                return "Invalid";
            }
        }
    }

    private static DateTimeFormatter getDateTimeFormatter(KamiFormatMode mode)
    {
        switch (mode)
        {
            case ABSOLUTE_12H:
                return DateTime_FORMATTER_12H;
            case ABSOLUTE_24H:
                return DateTime_FORMATTER_24H;
            default:
                return null;
        }
    }
}
