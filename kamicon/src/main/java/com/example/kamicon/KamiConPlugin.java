package com.example.kamicon;


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

import static com.example.kamicon.KamiConState.*;


@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
	name = "KamiCon",
	description = "KamiCon")

@Slf4j
public class KamiConPlugin extends Plugin
{


	//------------------------
	boolean DO_FULL_DEBUG = true;
	//--------------------


	LegacyMenuEntry targetMenu;
	Instant botTimer;
	Player player;
	KamiConState state;
	LocalPoint beforeLoc = new LocalPoint(0,0);
	List<Integer> butlerIDCollection = List.of(229);

	boolean startBot;

	long sleepLength;
	int tickLength;
	int timeout;

	boolean talkingToServant = false;
	DecorativeObject emptySpace;
	DecorativeObject capeSpace;
	NPC butler;

	boolean waitForPlanks = false;





	// Injects our config
	@Inject
	private KamiConPluginConfig config;
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
	private KamiConOverlay overlay;

	// Provides our config
	@Provides
	KamiConPluginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KamiConPluginConfig.class);
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
		log.info("stopping KamiCon plugin");
		startBot = false;
		botTimer = null;
		overlayManager.remove(overlay);
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("KamiConPluginConfig"))
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

				talkingToServant = false;
				waitForPlanks = false;
				butler = null;

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



	private KamiConState getState()
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

		GameObject portal = object.findNearestGameObject(4525);
		emptySpace = object.findNearestDecorObject(15394);

		capeSpace = object.findNearestDecorObject(31986);

		//buildObject = object.findNearestGameObject(buildObjID);


		if (portal != null) //if in house
		{
			Debug("1) found portal");
			if (inventory.containsItemAmount(8780, 3, false, false)) //if have >= 3 planks
			{
				Debug("2) have >=3 planks");
				waitForPlanks = false;
				if (capeSpace != null) //
				{
					Debug("3) cape space is filled");
					if (client.getWidget(WidgetInfo.DIALOG_OPTION_OPTION1) != null)
					{
						Debug("4) found cape remove chat box");
						return REMOVE_CAPE_CHATBOX;
					}
					else
					{
						Debug("5) no cape chatbox, clicking cape");
						return REMOVE_CAPE;
					}
				}
				else //cape is NOT on wall
				{
					Debug("6) Cape is not on wall");
					Widget viewportWidget = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_OLD_SCHOOL_BOX);

					if (viewportWidget != null)
					{
						Debug("6.1) viewport widget found");
						if(FindNameInWidgetChildren(viewportWidget, "Mythical Cape") == true)
						{
							Debug("7) Found build cape menu");
							return BUILD_CAPE_WIDGET;
						}
						else
						{
							Debug("7) no build cape menu");
							return BUILD_CAPE;
						}
					}
					else
					{
						Debug("6.2) viewport widget not found");
						return BUILD_CAPE;
					}

				}
			}
			else
			{
				if (waitForPlanks)
				{
					Debug("13) waiting for planks");
					return WAITING_FOR_PLANKS;
				}

				Debug("8) dont have unnoted planks");
				if(inventory.containsItem(8781));
				{
					Debug("9) have noted planks");
					butler = npc.findNearestNpcWithin(player.getWorldLocation(), 1, butlerIDCollection);
					if (butler != null)
					{
						Debug("10) found butler within 1 space");
						if (client.getWidget(WidgetInfo.DIALOG_OPTION_OPTION1) != null)
						{
							Debug("11) talking to butler");
							waitForPlanks = true;
							return CONFIRM_BUTLER;
						}
						else
						{
							Debug("12) butler close but not talking to");
							return TALK_TO_BUTLER;
						}
					}
					else
					{
						Debug("butler not nearby, calling");
						return CRASH;
					}

				}

			}
		}
		/*
		if in house X
			if have planks X
				if cape on wall X
					if in chat box to remove cape X
						remove cape X
					else not in chat box X
						click wall X
				else if cape not on wall X
					if in menu X
						build cape X
					else if not in menu X
						click empty wall object X
			else if have noted planks AND not waiting for servant
				if servant is close //229
					if servant chat box is open
						confirm
						set flag to wait for servant
					else talk to servant
				else call servant
		 */

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
				utils.sendGameMessage("KamiHerb - client must be set to resizable");
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
				case REMOVE_CAPE_CHATBOX:
					targetMenu = new  LegacyMenuEntry("Continue", "", 0, 30,
							1, 14352385, false);
					menu.setEntry(targetMenu);
					mouse.delayMouseClick(capeSpace.getConvexHull().getBounds(), sleepDelay());
					timeout = 1 + tickDelay();
					//MenuOption=Continue MenuTarget= Id=0 Opcode=30 Param0=1 Param1=14352385 CanvasX=334 CanvasY=595 Authentic=true
					break;
				case REMOVE_CAPE:
					targetMenu = new  LegacyMenuEntry("Remove", "<col=ffff>Mythical cape", 31986, 1001,
							57, 56, false);
					menu.setEntry(targetMenu);
					mouse.delayMouseClick(capeSpace.getConvexHull().getBounds(), sleepDelay());
					timeout =  tickDelay();
					//MenuOption=Continue MenuTarget= Id=0 Opcode=30 Param0=1 Param1=14352385 CanvasX=334 CanvasY=595 Authentic=true
					break;
				case BUILD_CAPE_WIDGET:
					targetMenu = new  LegacyMenuEntry("Build", "<col=ff9040>Mythical cape</col>", 1, 57,
							-1, 30015495, false);
					menu.setEntry(targetMenu);
					mouse.delayMouseClick(emptySpace.getConvexHull().getBounds(), sleepDelay());
					timeout = 1 + tickDelay();
					break;
				case BUILD_CAPE:
					targetMenu = new  LegacyMenuEntry("Build", "<col=ffff>Guild trophy space", 15394, 1001,
							57, 56, false);
					menu.setEntry(targetMenu);
					mouse.delayMouseClick(emptySpace.getConvexHull().getBounds(), sleepDelay());
					timeout =  tickDelay();
					break;
				case WAITING_FOR_PLANKS:
					timeout = 10;
					break;
				case CONFIRM_BUTLER:
					targetMenu = new  LegacyMenuEntry("Continue", "", 0, 30,
							1, 14352385, false);
					menu.setEntry(targetMenu);
					mouse.delayMouseClick(butler.getConvexHull().getBounds(), sleepDelay());
					timeout = tickDelay();
					break;
				case TALK_TO_BUTLER:
					targetMenu = new  LegacyMenuEntry("Talk-to", "<col=ffff00>Demon butler", butler.getIndex(), 9,
							0, 0, false);
					menu.setEntry(targetMenu);
					mouse.delayMouseClick(butler.getConvexHull().getBounds(), sleepDelay());
					timeout = tickDelay();
					break;

			}
			beforeLoc = player.getLocalLocation();
		}
	}

	boolean FindNameInWidgetChildren(Widget widget, String name)
	{

		List<Widget> childrenList = new ArrayList<>();
		Widget[] w = {widget};

		recurseChildren(childrenList, w);

		if(childrenList != null)
		{
			for (Widget widget1 : childrenList)
			{
				Debug(widget1.getName());
				if(widget1.getName() != null)
				{
					if (widget1.getName().contains("Mythical cape"))
					{
						return true;
					}
				}
			}
		}

		return false;


	}

	void recurseChildren(List<Widget> list, Widget[] widget)
	{
		for (Widget w : widget)
		{
			if(w != null)
			{
				list.add(w);
				Widget[] children = w.getChildren();
				if(children != null && children.length > 0)
					recurseChildren(list, children);

				children = w.getStaticChildren();
				if(children != null && children.length > 0)
					recurseChildren(list, children);

				children = w.getNestedChildren();
				if(children != null && children.length > 0)
					recurseChildren(list, children);

				children = w.getDynamicChildren();
				if(children != null && children.length > 0)
					recurseChildren(list, children);
			}
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