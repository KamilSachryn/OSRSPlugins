package com.example.kamiTEMPLATE;


import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.iutils.*;

import org.pf4j.Extension;

import java.time.Instant;
import java.util.*;

import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.OverlayManager;

import static net.runelite.client.plugins.iutils.iUtils.iterating;

import static com.example.kamiTEMPLATE.KamiTEMPLATEState.*;



@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
	name = "KamiTEMPLATE",
	description = "KamiTEMPLATE"
)

@Slf4j
public class KamiTEMPLATEPlugin extends Plugin
{


	//------------------------
	boolean DO_FULL_DEBUG = true;
	//--------------------


	MenuEntry targetMenu;
	Instant botTimer;
	Player player;
	KamiTEMPLATEState state;
	LocalPoint beforeLoc = new LocalPoint(0, 0);


	boolean startBot;

	long sleepLength;
	int tickLength;
	int timeout;






	// Injects our config
	@Inject
	private KamiTEMPLATEPluginConfig config;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Client client;
	@Inject
	private iUtils utils;
	@Inject
	private NPCUtils npc;
	@Inject
	private MouseUtils mouse;
	@Inject
	private PlayerUtils playerUtils;
	@Inject
	private BankUtils bank;
	@Inject
	private InventoryUtils inventory;
	@Inject
	private InterfaceUtils interfaceUtils;
	@Inject
	private CalculationUtils calc;
	@Inject
	private MenuUtils menu;
	@Inject
	private ObjectUtils object;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private KamiTEMPLATEOverlay overlay;

	// Provides our config
	@Provides
	KamiTEMPLATEPluginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KamiTEMPLATEPluginConfig.class);
	}

	@Override
	protected void startUp()
	{


	}


	@Override
	protected void shutDown()
	{
		resetVals();
	}


	private void resetVals()
	{
		log.info("stopping KamiTEMPLATE plugin");
		startBot = false;
		botTimer = null;
		overlayManager.remove(overlay);
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("KamiTEMPLATEPluginConfig"))
		{
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startBot)
			{
				startBot = true;
				botTimer = Instant.now();
				initCounters();
				state = null;
				targetMenu = null;


				overlayManager.add(overlay);





			}
			else
			{
				resetVals();
			}
		}
	}

	@Subscribe
	private void onConfigChange(ConfigChanged event)
	{

		if (!event.getGroup().equals("iCombinationRunecrafter"))
		{
			return;
		}

	}

	private void initCounters()
	{
		timeout = 0;
	}


	private long sleepDelay()
	{
		sleepLength = calc.randomDelay(config.sleepWeightedDistribution(), config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
		return sleepLength;
	}

	private int tickDelay()
	{
		tickLength = (int) calc.randomDelay(config.tickDelayWeightedDistribution(), config.tickDelayMin(), config.tickDelayMax(), config.tickDelayDeviation(), config.tickDelayTarget());
		return tickLength;
	}



	private KamiTEMPLATEState getState()
	{
		Debug("0) entered getState");
		if (timeout > 0)
		{
			playerUtils.handleRun(20, 30);
			return TIMEOUT;
		}
		if (iterating)
		{
			return ITERATING;
		}
		if (playerUtils.isMoving(beforeLoc) || player.getAnimation() == 714) //teleport animation
		{
			playerUtils.handleRun(20, 30);
			return MOVING;
		}


		//TODO: Start decision loop here




		Debug("999) this should never happen");
		return CRASH;
	}

	@Subscribe
	private void onGameTick(GameTick event)
	{
		if (!startBot)
		{
			return;
		}

		player = client.getLocalPlayer();
		if (client != null && player != null && client.getGameState() == GameState.LOGGED_IN)
		{
			if (!client.isResized())
			{
				utils.sendGameMessage("KamiTEMPLATE - client must be set to resizable");
				startBot = false;
				return;
			}

			state = getState();

			switch (state)
			{
				case TIMEOUT:
					timeout--;
					break;
				case ITERATING:
					break;
				case MOVING:
					timeout = tickDelay();
					break;


			}
			beforeLoc = player.getLocalLocation();
		}
	}




	void Debug(String str)
	{
		if (DO_FULL_DEBUG)
		{
			log.debug(str);
		}
	}
}

