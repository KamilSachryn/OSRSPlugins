package com.example.KamiRedSalamander;


import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.iutils.*;

import net.runelite.client.util.PvPUtil;
import org.pf4j.Extension;

import java.time.Instant;
import java.util.*;

import net.runelite.api.coords.LocalPoint;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.OverlayManager;

import static net.runelite.api.MenuAction.GROUND_ITEM_THIRD_OPTION;
import static net.runelite.client.plugins.iutils.iUtils.iterating;

import static com.example.KamiRedSalamander.KamiRedSalamanderState.*;


@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
        name = "KamiRedSalamander",
        description = "KamiRedSalamander")

@Slf4j
public class KamiRedSalamanderPlugin extends Plugin
{


    //------------------------
    boolean DO_FULL_DEBUG = true;
    //--------------------


     LegacyMenuEntry targetMenu;
    Instant botTimer;
    Player player;
    KamiRedSalamanderState state;
    LocalPoint beforeLoc = new LocalPoint(0, 0);


    boolean startBot;

    long sleepLength;
    int tickLength;
    int timeout;

    int MAX_DISTANCE = 4;

    Set<Integer> itemsToDrop = Set.of(10147);
    Set<Integer> itemsNotToDrop = Set.of(954, 303);
    Set<Integer> itemsToPickup = Set.of(954, 303);
    Set<Integer> ropeItems = Set.of(954);
    Set<Integer> netItems = Set.of(303);

    WorldPoint startingPoint = new WorldPoint(2449, 3226, 0);

    //List<TileItem> netItems = new ArrayList<TileItem>();
  //  List<Tile> netTiles = new ArrayList<Tile>();
    //List<TileItem> ropeItems = new ArrayList<TileItem>();
  //  List<Tile> ropeTiles = new ArrayList<Tile>();

    GameObject emptyTree;
    GameObject readyTree;
    TileObject groundRope;
    TileObject groundNet;

    List<TileItem> loot = new ArrayList<>();


    // Injects our config
    @Inject
    private KamiRedSalamanderPluginConfig config;
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
    private KamiRedSalamanderOverlay overlay;

    // Provides our config
    @Provides
    KamiRedSalamanderPluginConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KamiRedSalamanderPluginConfig.class);
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
        log.info("stopping KamiRedSalamander plugin");
        startBot = false;
        botTimer = null;
        overlayManager.remove(overlay);
    }

    @Subscribe
    private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
    {
        if (!configButtonClicked.getGroup().equalsIgnoreCase("KamiRedSalamanderPluginConfig"))
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

                //			netItems = new ArrayList<TileItem>();
            //    netTiles = new ArrayList<Tile>();
                //			ropeItems = new ArrayList<TileItem>();
            //    ropeTiles = new ArrayList<Tile>();

                emptyTree = null;
                readyTree = null;
                groundNet = null;
                groundRope = null;

                loot.clear();


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
/*
    @Subscribe
    private void onItemSpawned(ItemSpawned event)
    {

        if (!startBot)
        {
            return;
        }


        TileItem item = event.getItem();
        Tile tile = event.getTile();

        int currentDistance = item.getTile().getWorldLocation().distanceTo(startingPoint);
        Debug("Found new tile item with distance to start point of " + currentDistance);
        if (MAX_DISTANCE < currentDistance)
        {
            return;
        }

        if (item.getId() == ItemID.ROPE)
        {
            //ropeItems.add(item);
            ropeTiles.add(tile);
        }
        else if (item.getId() == ItemID.SMALL_FISHING_NET)
        {
            //netItems.add(item);
            netTiles.add(tile);
        }
    }
*/

    @Subscribe
    private void onItemSpawned(ItemSpawned event)
    {
        if (!startBot)
        {
            return;
        }

        TileItem item = event.getItem();

        int currentDistance = item.getTile().getWorldLocation().distanceTo(startingPoint);
        if (MAX_DISTANCE < currentDistance)
        {
            return;
        }

        if (item.getId() == ItemID.ROPE || item.getId() == ItemID.SMALL_FISHING_NET)
        {
            loot.add(item);
        }

    }


    @Subscribe
    private void onItemDespawned(ItemDespawned event)
    {
        if (!startBot)
        {
            return;
        }
        loot.remove(event.getItem());
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


    private KamiRedSalamanderState getState()
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
        //empty tree: 8990
        //setting up tree: 8991
        //set tree: 8989
        //sprung tree: 8985
        //caught tree: 8986
        //failing tree: 8987

        //salamander id: 10147
        //rope id: 954
        //net id: 303

        //MenuOption=Set-trap MenuTarget=<col=ffff>Young tree Id=8990 Opcode=3 Param0=51 Param1=52 CanvasX=500 CanvasY=454 Authentic=true
        //MenuOption=Check MenuTarget=<col=ffff>Net trap Id=8986 Opcode=3 Param0=51 Param1=52 CanvasX=422 CanvasY=410 Authentic=true
        //MenuOption=Take MenuTarget=<col=ff9040>Rope Id=954 Opcode=20 Param0=52 Param1=52 CanvasX=426 CanvasY=433 Authentic=true
        //MenuOption=Take MenuTarget=<col=ff9040>Small fishing net Id=303 Opcode=20 Param0=52 Param1=52 CanvasX=495 CanvasY=429 Authentic=true
        //MenuOption=Release MenuTarget=Release Id=10147 Opcode=37 Param0=13 Param1=9764864 CanvasX=860 CanvasY=668 Authentic=true

        if(config.hop())
        {
            final Player local = client.getLocalPlayer();
            for (Player player : client.getPlayers())
            {
                if (player == null ||
                        player.equals(local))
                {
                    continue;
                }
                else
                {
                    return WAIT_HOP;
                }
            }
        }


        if (inventory.getEmptySlots() <= 3)
        {
            Debug("1) out of invi slots");
            return DROP_SALAMANDERS;

        }

        emptyTree = object.findNearestGameObjectWithin(startingPoint, MAX_DISTANCE, 8990);
        readyTree = object.findNearestGameObjectWithin(startingPoint, MAX_DISTANCE, 8986);

        groundRope = object.findNearestObjectWithin(startingPoint, MAX_DISTANCE, 954);
        groundNet = object.findNearestObjectWithin(startingPoint, MAX_DISTANCE, 303);



        if(loot.size() > 0)
        {
            return LOOT;
        }


        if (emptyTree != null)
        {
            Debug("5) found unset tree");
            return SET_TRAP;


        }

        if (readyTree != null)
        {
            Debug("6) found caught tree");
            return CHECK_TRAP;
        }





        Debug("999) waiting for something to happen");
        return WAIT;
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
                utils.sendGameMessage("KamiRedSalamander - client must be set to resizable");
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
                case ANIMATING:
                    timeout = 1;
                    break;
                case DROP_SALAMANDERS:
                    inventory.dropItems(itemsToDrop, true, config.sleepMin(), config.sleepMax());
                    timeout = tickDelay();
                    break;
                case SET_TRAP:
                    //MenuOption=Set-trap MenuTarget=<col=ffff>Young tree Id=8990 Opcode=3 Param0=51 Param1=52 CanvasX=500 CanvasY=454 Authentic=true

                    targetMenu = new  LegacyMenuEntry("Set-trap", "<col=ffff>Young tree", 8990, 3,
                            emptyTree.getSceneMinLocation().getX(), emptyTree.getSceneMinLocation().getY(), false);
                    menu.setEntry(targetMenu);
                    mouse.delayMouseClick(emptyTree.getConvexHull().getBounds(), sleepDelay());
                    timeout = tickDelay();
                    break;
                case CHECK_TRAP:
                    //MenuOption=Check MenuTarget=<col=ffff>Net trap Id=8986 Opcode=3 Param0=51 Param1=52 CanvasX=422 CanvasY=410 Authentic=true

                    targetMenu = new  LegacyMenuEntry("Check", "<col=ffff>Net trap", 8986, 3,
                            readyTree.getSceneMinLocation().getX(), readyTree.getSceneMinLocation().getY(), false);
                    menu.setEntry(targetMenu);
                    mouse.delayMouseClick(readyTree.getConvexHull().getBounds(), sleepDelay());
                    timeout = tickDelay();
                    break;
                case LOOT:
                    lootItem(loot);
                    timeout = 1;
                    break;
                case WAIT_HOP:
                    timeout = tickDelay();


            }
            beforeLoc = player.getLocalLocation();
        }
    }



    private TileItem getNearestTileItem(List<TileItem> tileItems)
    {
        int currentDistance;
        TileItem closestTileItem = tileItems.get(0);
        int closestDistance = closestTileItem.getTile().getWorldLocation().distanceTo(player.getWorldLocation());
        for (TileItem tileItem : tileItems)
        {
            currentDistance = tileItem.getTile().getWorldLocation().distanceTo(player.getWorldLocation());
            if (currentDistance < closestDistance)
            {
                closestTileItem = tileItem;
                closestDistance = currentDistance;
            }
        }
        return closestTileItem;
    }

    private void lootItem(List<TileItem> itemList)
    {
        TileItem lootItem = getNearestTileItem(itemList);
        if (lootItem != null)
        {
            targetMenu = new  LegacyMenuEntry("", "", lootItem.getId(), GROUND_ITEM_THIRD_OPTION.getId(),
                    lootItem.getTile().getSceneLocation().getX(), lootItem.getTile().getSceneLocation().getY(), false);
            menu.setEntry(targetMenu);
            mouse.delayMouseClick(lootItem.getTile().getItemLayer().getCanvasTilePoly().getBounds(), sleepDelay());
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