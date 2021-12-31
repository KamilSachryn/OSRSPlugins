package com.example.kamiWinter;


import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.iutils.*;

import org.pf4j.Extension;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.OverlayManager;

import static net.runelite.api.AnimationID.*;
import static net.runelite.api.widgets.WidgetInfo.BANK_PIN_INSTRUCTION_TEXT;
import static net.runelite.client.plugins.iutils.iUtils.iterating;

import static com.example.kamiWinter.KamiWinterState.*;
import static com.example.kamiWinter.KamiActivity.*;
import static com.example.kamiWinter.KamiInterruptType.*;


@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
        name = "KamiWinter",
        description = "KamiWinter"
)

@Slf4j
public class KamiWinterPlugin extends Plugin
{


    //------------------------
    boolean DO_FULL_DEBUG = true;
    //--------------------

    private static final int WINTERTODT_REGION = 6462;
    private static final int OUTSIDE_WINTERTODT_REGION = 6461;
    private static final int HOUSE_REGION = 36654;
    private static final int UNLIT_BRAIER =   29312;
    private static final int LIT_BRAZIER_ = 29314;
    private static final int CRATE_ID = 20703;
    final int tileSearchRadius = 15;

    private Instant lastActionTime;

    int timerValue;

    Set<Integer> SARA_BREWS = Set.of(ItemID.SARADOMIN_BREW1, ItemID.SARADOMIN_BREW2, ItemID.SARADOMIN_BREW3, ItemID.SARADOMIN_BREW4);
    Set<Integer> SARA_BREWS_LARGE = Set.of(ItemID.SARADOMIN_BREW2, ItemID.SARADOMIN_BREW3, ItemID.SARADOMIN_BREW4);

    WorldPoint cuttingSafeSpot = new WorldPoint(1622, 3988, 0);
    WorldPoint middleOfBossRoom = new WorldPoint(1631, 3981, 0);


    LegacyMenuEntry targetMenu;
    Instant botTimer;
    Player player;
    KamiWinterState state;
    LocalPoint beforeLoc = new LocalPoint(0, 0);

    WidgetItem tp_item = null;
    LegacyMenuEntry entry = null;
    Rectangle rectangle = null;
    Widget bankItem = null;

    Widget winterHp;
    int minPlayerHp = 19;
    int maxPlayerHp = 90;
    int minBossHp = 5;


    boolean startBot;

    long sleepLength;
    int tickLength;
    int timeout;
    private boolean isInWintertodt;

    private KamiActivity currentActivity = KamiActivity.IDLE;

    boolean wasInterrupted;


    // Injects our config
    @Inject
    private KamiWinterPluginConfig config;
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
    private KamiWinterOverlay overlay;
    @Inject
    private WalkUtils walk;

    // Provides our config
    @Provides
    KamiWinterPluginConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KamiWinterPluginConfig.class);
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
        log.info("stopping KamiWinter plugin");
        startBot = false;
        botTimer = null;
        overlayManager.remove(overlay);
    }

    @Subscribe
    private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
    {
        if (!configButtonClicked.getGroup().equalsIgnoreCase("KamiWinterPluginConfig"))
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
                lastActionTime = null;
                tp_item = null;
                entry = null;
                rectangle = null;
                bankItem = null;




                overlayManager.add(overlay);


            }
            else
            {
                resetVals();
            }
        }
    }

    private void setActivity(KamiActivity action)
    {
        currentActivity = action;
        lastActionTime = Instant.now();
    }

    private boolean isInWintertodtRegion()
    {
        if (client.getLocalPlayer() != null)
        {
            return client.getLocalPlayer().getWorldLocation().getRegionID() == WINTERTODT_REGION;
        }

        return false;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {


        timerValue = client.getVar(Varbits.WINTERTODT_TIMER);
        winterHp = client.getWidget(396, 21);
        //ID: 25952277

    }


    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        if (!isInWintertodt)
        {
            return;
        }

        ChatMessageType chatMessageType = chatMessage.getType();

        if (chatMessageType != ChatMessageType.GAMEMESSAGE && chatMessageType != ChatMessageType.SPAM)
        {
            return;
        }

        MessageNode messageNode = chatMessage.getMessageNode();
        final KamiInterruptType interruptType;

        if (messageNode.getValue().startsWith("You carefully fletch the root"))
        {
            setActivity(FLETCHING);
            return;
        }

        if (messageNode.getValue().startsWith("The cold of"))
        {
            interruptType = COLD;
        }
        else if (messageNode.getValue().startsWith("The freezing cold attack"))
        {
            interruptType = SNOWFALL;
        }
        else if (messageNode.getValue().startsWith("The brazier is broken and shrapnel"))
        {
            interruptType = BRAZIER;
        }
        else if (messageNode.getValue().startsWith("You have run out of bruma roots"))
        {
            interruptType = OUT_OF_ROOTS;
        }
        else if (messageNode.getValue().startsWith("Your inventory is too full"))
        {
            interruptType = INVENTORY_FULL;
        }
        else if (messageNode.getValue().startsWith("You fix the brazier"))
        {
            interruptType = FIXED_BRAZIER;
        }
        else if (messageNode.getValue().startsWith("You light the brazier"))
        {
            interruptType = LIT_BRAZIER;
        }
        else if (messageNode.getValue().startsWith("The brazier has gone out."))
        {
            interruptType = BRAZIER_WENT_OUT;
        }
        else
        {
            return;
        }

        boolean wasInterrupted = false;
        boolean neverNotify = false;

        switch (interruptType)
        {
            case COLD:
            case BRAZIER:
            case SNOWFALL:


                // all actions except woodcutting and idle are interrupted from damage
                if (currentActivity != KamiActivity.WOODCUTTING && currentActivity != KamiActivity.IDLE)
                {
                    wasInterrupted = true;
                }

                break;
            case INVENTORY_FULL:
            case OUT_OF_ROOTS:
            case BRAZIER_WENT_OUT:
                wasInterrupted = true;
                break;
            case LIT_BRAZIER:
            case FIXED_BRAZIER:
                wasInterrupted = true;
                neverNotify = true;
                break;
        }

        if (wasInterrupted)
        {
            currentActivity = KamiActivity.IDLE;
        }
    }

    @Subscribe
    public void onAnimationChanged(final AnimationChanged event)
    {
        if (!isInWintertodt)
        {
            return;
        }

        final Player local = client.getLocalPlayer();

        if (event.getActor() != local)
        {
            return;
        }

        final int animId = local.getAnimation();
        switch (animId)
        {
            case WOODCUTTING_BRONZE:
            case WOODCUTTING_IRON:
            case WOODCUTTING_STEEL:
            case WOODCUTTING_BLACK:
            case WOODCUTTING_MITHRIL:
            case WOODCUTTING_ADAMANT:
            case WOODCUTTING_RUNE:
            case WOODCUTTING_GILDED:
            case WOODCUTTING_DRAGON:
            case WOODCUTTING_DRAGON_OR:
            case WOODCUTTING_INFERNAL:
            case WOODCUTTING_3A_AXE:
            case WOODCUTTING_CRYSTAL:
            case WOODCUTTING_TRAILBLAZER:
                setActivity(WOODCUTTING);
                break;

            case FLETCHING_BOW_CUTTING:
                setActivity(FLETCHING);
                break;

            case LOOKING_INTO:
                setActivity(FEEDING_BRAZIER);
                break;

            case FIREMAKING:
                setActivity(LIGHTING_BRAZIER);
                break;

            case CONSTRUCTION:
            case CONSTRUCTION_IMCANDO:
                setActivity(FIXING_BRAZIER);
                break;
        }
    }

    int GetWinterHP()
    {
        String winterText = winterHp.getText();
        winterText = winterText.substring(winterText.indexOf(':') + 2, winterText.length() - 1);
        Debug("WinterText = " + winterText);
        return Integer.parseInt(winterText);
    }

    @Subscribe
    private void onConfigChange(ConfigChanged event)
    {

        if (!event.getGroup().equals("KamiWinterPlugin"))
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

    private void checkActionTimeout()
    {
        if (currentActivity == KamiActivity.IDLE)
        {
            return;
        }

        int currentAnimation = client.getLocalPlayer() != null ? client.getLocalPlayer().getAnimation() : -1;
        if (currentAnimation != AnimationID.IDLE || lastActionTime == null)
        {
            return;
        }

        Duration actionTimeout = Duration.ofSeconds(3);
        Duration sinceAction = Duration.between(lastActionTime, Instant.now());

        if (sinceAction.compareTo(actionTimeout) >= 0)
        {
            log.debug("Activity timeout!");
            currentActivity = KamiActivity.IDLE;
        }
    }

    private KamiWinterState getState()
    {
        Debug("TImer value: " + timerValue);
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


        //if hp < 15
        if (client.getBoostedSkillLevel(Skill.HITPOINTS) <= minPlayerHp)
        {
            Debug("1) curr hp < " + minPlayerHp);
            //if at home
            if (object.findNearestGameObject(29241) != null)
            {
                Debug("2) at home region");
                return DRINK_FOUNTAIN;
            }
            else
            {
                Debug("3) not at home");
                if (inventory.containsItem(SARA_BREWS))
                {
                    Debug("4) has brew, drink");
                    return DRINK_BREW;
                }
                else
                {
                    Debug("5) no brew, tp home");
                    return TP_HOME;
                }
            }
        }

        //special case in home and < max hp
        if((client.getBoostedSkillLevel(Skill.HITPOINTS) <= maxPlayerHp) && object.findNearestGameObject(29241) != null)
        {
            return DRINK_FOUNTAIN;
        }

        //if have crate in invi
        if (inventory.containsItem(ItemID.SUPPLY_CRATE))
        {
            Debug("6) have supply crate");
            //do full reset
            //if at boss room
            if (client.getLocalPlayer().getWorldLocation().getRegionID() == WINTERTODT_REGION)
            {
                Debug("7) in boss room, tp home");
                return TP_HOME;
            }
            //if (client.getLocalPlayer().getWorldLocation().getRegionID() == HOUSE_REGION)
            if(object.findNearestGameObject(29241) != null)
            {
                Debug("8) in house");
                //if hp < 90
                if (client.getBoostedSkillLevel(Skill.HITPOINTS) <= maxPlayerHp)
                {
                    Debug("9) drink fountain");
                    return DRINK_FOUNTAIN;
                }
                else
                {
                    Debug("10) use house to winter");
                    return USE_HOUSE_NECKLACE;
                }
            }
            else
            {
                Debug("11) if no sara brews");
                //if sara brew = 1 dose or 0 doses
                if (!inventory.containsItem(SARA_BREWS))
                {
                    Debug("12) withdraw a brew");
                    if (client.getWidget(WidgetID.BANK_PIN_GROUP_ID, BANK_PIN_INSTRUCTION_TEXT.getChildId()) != null)
                    {
                        return ITERATING;
                    }

                    if(bank.isOpen())
                    {
                        return WITHDRAW_SARA;
                    }
                    else
                    {
                        return OPEN_BANK;
                    }

                }
                else if (inventory.containsItem(ItemID.SUPPLY_CRATE))
                {
                    Debug("13) deposit crate");

                    if (client.getWidget(WidgetID.BANK_PIN_GROUP_ID, BANK_PIN_INSTRUCTION_TEXT.getChildId()) != null)
                    {
                        return ITERATING;
                    }

                    if(bank.isOpen())
                    {
                        return DEPOSIT_CRATE;
                    }
                    else
                    {

                        return OPEN_BANK;
                    }
                }

            }
        }


        if(!inventory.containsItem(ItemID.SUPPLY_CRATE) && !inventory.containsItem(SARA_BREWS) && object.findNearestGameObject(29321) != null)
        {
            Debug("12) withdraw a brew");
            if (client.getWidget(WidgetID.BANK_PIN_GROUP_ID, BANK_PIN_INSTRUCTION_TEXT.getChildId()) != null)
            {
                return ITERATING;
            }

            if(bank.isOpen())
            {
                return WITHDRAW_SARA;
            }
            else
            {
                return OPEN_BANK;
            }
        }

        if (!inventory.containsItem(ItemID.SUPPLY_CRATE) &&
                client.getLocalPlayer().getWorldLocation().getRegionID() == OUTSIDE_WINTERTODT_REGION)
        {
            Debug("14) dont have crate and we're outside wt");
            return ENTER_BOSS;
        }

        if (!inventory.containsItem(ItemID.SUPPLY_CRATE) && object.findNearestGameObject(29241) != null)
        {
            return USE_HOUSE_NECKLACE;
        }

        if(timerValue > 4)
        {
            if((client.getBoostedSkillLevel(Skill.HITPOINTS) <= maxPlayerHp))
            {
                return TP_HOME;
            }
            if(player.getWorldLocation().distanceTo(middleOfBossRoom) > 2)
            {
                return MOVE_TO_MID;
            }
            else
            {
                return ITERATING;
            }


        }
        //if have any logs in invi
        if (inventory.containsItem(ItemID.BRUMA_ROOT))
        {
            Debug("15) have logs in invi");
            //if invi full
            if (inventory.getEmptySlots() == 0)
            {
                Debug("16) invi has no empty space");
                //if feeding
                return getFeedingState();

            }
            //else if invi partial full of logs
            else
            {
                Debug("16) invi not full but have roots");
                //if at cutting place
                if (player.getWorldLocation().distanceTo(cuttingSafeSpot) == 0)
                {
                    Debug("17) we're at safe spot");
                    //if timer <15%
                    if (GetWinterHP() < minBossHp)
                    {
                        Debug("18) energy at " + GetWinterHP() + " < " + minBossHp);
                        //if feeding
                        return getFeedingState();
                    }
                    //else timer >=15%
                    else
                    {
                        Debug("21) energy at " + GetWinterHP() + " > " + minBossHp);
                        //if at safespot
                        if (player.getWorldLocation().distanceTo(cuttingSafeSpot) == 0)
                        {
                            Debug("22) player at safespot");
                            //if cutting
                            if (currentActivity == WOODCUTTING)
                            {
                                Debug("23) woodcutting");
                                return CUTTING;
                                //keep cutting
                            }
                            //else if not cutting
                            else
                            {
                                Debug("24) not woodcutting but should be");
                                return CUT;
                                //cut some
                            }

                        }
                        //else not at safespot
                        else
                        {
                            Debug("25) not at safespot, going to safespot");
                            return GO_TO_SAFESPOT;
                            //go to safe spot
                        }
                    }
                }
                //else
                else
                {
                    return getFeedingState();
                }
            }
        }
        //Else if no logs in invi
        if (!inventory.containsItem(ItemID.BRUMA_ROOT))
        {
            Debug("29) invi has no logs");
            //if at safespot
            if (player.getWorldLocation().distanceTo(cuttingSafeSpot) == 0)
            {
                Debug("30) at safespot, am at " + player.getWorldLocation() + " with dist " + player.getWorldLocation().distanceTo(cuttingSafeSpot));
                //if cutting
                if (currentActivity == WOODCUTTING)
                {
                    Debug("31) cutting wood");
                    return CUTTING;
                    //keep cutting
                }
                //else if not cutting
                else
                {


                    Debug("32) not cutting");
                    return CUT;
                    //cut some
                }

            }
            //else not at safespot
            else
            {
                Debug("33) not at safespot, am at " + player.getWorldLocation());
                return GO_TO_SAFESPOT;
                //go to safe spot
            }
        }


        Debug("999) this should never happen");
        return CRASH;
    }

    private KamiWinterState getFeedingState()
    {
        if(object.findNearestGameObjectWithin(player.getWorldLocation(), tileSearchRadius, LIT_BRAZIER_) != null)
        {
            Debug("26) not at safespot but invi has logs");
            //if feeding
            if (currentActivity == FEEDING_BRAZIER)
            {
                Debug("27) feeding brazier");
                return FEEDING;
            }
            //if not feeding (interrupted or not started
            else
            {
                Debug("28) not feeding, going to feed");
                //feed
                return FEED;
            }
        }
        else
        {
            return ITERATING;
        }



    }

    @Subscribe
    private void onGameTick(GameTick event)
    {
        if (!startBot)
        {
            return;
        }

        isInWintertodt = isInWintertodtRegion();

        checkActionTimeout();




        player = client.getLocalPlayer();
        if (client != null && player != null && client.getGameState() == GameState.LOGGED_IN)
        {
            if (!client.isResized())
            {
                utils.sendGameMessage("KamiWinter - client must be set to resizable");
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
                case DRINK_FOUNTAIN:
                    GameObject fountain = object.findNearestGameObject(29241);

                    targetMenu = new  LegacyMenuEntry("", "",
                            29241, 3, fountain.getSceneMinLocation().getX(),
                            fountain.getSceneMinLocation().getY(), true);


                    menu.setEntry(targetMenu);
                    mouse.delayMouseClick(fountain.getConvexHull().getBounds(), sleepDelay());
                    timeout = tickDelay();
                    //MenuOption=Drink MenuTarget=<col=ffff>Ornate pool of Rejuvenation Id=29241 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=46 Param1=46 CanvasX=809 CanvasY=459

                    break;
                case DRINK_BREW:
                  // MenuOption=Drink MenuTarget=Drink Id=6685 Opcode=ITEM_FIRST_OPTION/33 Param0=8 Param1=9764864 CanvasX=1345 CanvasY=969
                    tp_item = inventory.getWidgetItem(SARA_BREWS);


                     entry = new  LegacyMenuEntry("", "", tp_item.getId(), 33,
                            tp_item.getIndex(), 9764864, true);
                    tp_item.getCanvasBounds().getBounds();
                    rectangle = tp_item.getCanvasBounds().getBounds();
                    utils.doActionGameTick(entry, rectangle, 2);

                    break;
                case TP_HOME:
                    tp_item = inventory.getWidgetItem(ItemID.TELEPORT_TO_HOUSE);

                    entry = new  LegacyMenuEntry("", "", ItemID.TELEPORT_TO_HOUSE, 33,
                            tp_item.getIndex(), 9764864, true);
                    tp_item.getCanvasBounds().getBounds();
                    rectangle = tp_item.getCanvasBounds().getBounds();
                    utils.doActionGameTick(entry, rectangle, 2);
                    //MenuOption=Break MenuTarget=Break Id=8013 Opcode=ITEM_FIRST_OPTION/33 Param0=0 Param1=9764864 CanvasX=1341 CanvasY=881

                    timeout = tickDelay();
                    break;
                case USE_HOUSE_NECKLACE:
                    //MenuOption=Wintertodt Camp MenuTarget=<col=ffff>Ornate Jewellery Box Id=29156 Opcode=GAME_OBJECT_THIRD_OPTION/5 Param0=54 Param1=49 CanvasX=750 CanvasY=664


                    GameObject neck = object.findNearestGameObject(29156);

                    targetMenu = new  LegacyMenuEntry("", "",
                            29156, 5, neck.getSceneMinLocation().getX(),
                            neck.getSceneMinLocation().getY(), true);


                    menu.setEntry(targetMenu);
                    mouse.delayMouseClick(neck.getConvexHull().getBounds(), sleepDelay());
                    timeout = tickDelay();
                    break;
                case WITHDRAW_SARA:

                    Widget bankItem = bank.getBankItemWidgetAnyOf(SARA_BREWS);

//                    targetMenu = new  LegacyMenuEntry("Withdraw-1", "<col=ff9040>Skills necklace(6)</col>",
//                            1, 57, bankItem.getIndex(),
//                            786444, false);
                    targetMenu = new  LegacyMenuEntry("Withdraw-1", "Withdraw-1",
                            (client.getVarbitValue(6590) == 0) ? 1 : 2, 57,
                            bankItem.getIndex(),
                            786445, false);

                    menu.setEntry(targetMenu);
                    mouse.delayMouseClick(bankItem.getBounds(), sleepDelay());
                    timeout = tickDelay();

                    break;
                case DEPOSIT_CRATE:
                    bank.depositAllOfItem(ItemID.SUPPLY_CRATE);
                    break;
                case ENTER_BOSS:
                    //MenuOption=Enter MenuTarget=<col=ffff>Doors of Dinh Id=29322 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=51 Param1=36 CanvasX=980 CanvasY=483
                    GameObject door = object.findNearestGameObject(29322);

                    targetMenu = new  LegacyMenuEntry("", "",
                            29322, 3, door.getSceneMinLocation().getX(),
                            door.getSceneMinLocation().getY(), true);


                    menu.setEntry(targetMenu);
                    mouse.delayMouseClick(door.getConvexHull().getBounds(), sleepDelay());
                    timeout = tickDelay() + 1;
                    break;
                case FEEDING:
                    timeout = tickDelay();
                    break;
                case FEED:
                    //MenuOption=Feed MenuTarget=<col=ffff>Burning brazier Id=29314 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=44 Param1=69 CanvasX=880 CanvasY=533


                    GameObject brazier = object.findNearestGameObject(LIT_BRAZIER_);

                    targetMenu = new  LegacyMenuEntry("", "",
                            29314, 3, brazier.getSceneMinLocation().getX(),
                            brazier.getSceneMinLocation().getY(), true);


                    menu.setEntry(targetMenu);
                    mouse.delayMouseClick(brazier.getConvexHull().getBounds(), sleepDelay());
                    timeout = tickDelay();
                    break;
                case CUTTING:
                    timeout = tickDelay();
                    break;
                case CUT:
                    //MenuOption=Chop MenuTarget=<col=ffff>Bruma roots Id=29311 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=44 Param1=60 CanvasX=910 CanvasY=401

                    GameObject brumaRoot = object.findNearestGameObject(29311);
                    targetMenu = new  LegacyMenuEntry("", "",
                            29311, 3, brumaRoot.getSceneMinLocation().getX(),
                            brumaRoot.getSceneMinLocation().getY(), true);


                    menu.setEntry(targetMenu);
                    mouse.delayMouseClick(brumaRoot.getConvexHull().getBounds(), sleepDelay());
                    timeout = tickDelay();

                    break;
                case GO_TO_SAFESPOT:
                    boolean success = walk.webWalk(cuttingSafeSpot, 0, playerUtils.isMoving(beforeLoc), sleepDelay());
                    timeout = 2;
                    break;
                case OPEN_BANK:
                    GameObject bank = object.findNearestGameObject(29321);
                    targetMenu = new  LegacyMenuEntry("", "",
                            29321, 3, bank.getSceneMinLocation().getX(),
                            bank.getSceneMinLocation().getY(), true);


                    menu.setEntry(targetMenu);
                    mouse.delayMouseClick(bank.getConvexHull().getBounds(), sleepDelay());
                    timeout = tickDelay();

                    break;
                case MOVE_TO_MID:
                    walk.webWalk(middleOfBossRoom, 1, playerUtils.isMoving(beforeLoc), sleepDelay());
                    timeout = 2;
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


/*
Chop Roots:
2021-08-31 17:26:29 [Client] INFO  injected-client - |MenuAction|: MenuOption=Chop MenuTarget=<col=ffff>Bruma roots Id=29311 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=44 Param1=60 CanvasX=910 CanvasY=401

Feed logs:
2021-08-31 17:27:32 [Client] INFO  injected-client - |MenuAction|: MenuOption=Feed MenuTarget=<col=ffff>Burning brazier Id=29314 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=44 Param1=69 CanvasX=880 CanvasY=533

Light brazier:
2021-08-31 17:27:48 [Client] INFO  injected-client - |MenuAction|: MenuOption=Light MenuTarget=<col=ffff>Brazier Id=29312 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=44 Param1=69 CanvasX=1203 CanvasY=413

Fix brazier:
2021-08-31 17:28:39 [Client] INFO  injected-client - |MenuAction|: MenuOption=Fix MenuTarget=<col=ffff>Brazier Id=29313 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=62 Param1=69 CanvasX=1373 CanvasY=574

select knife:
2021-08-31 17:28:53 [Client] INFO  injected-client - |MenuAction|: MenuOption=Use MenuTarget=Use Id=946 Opcode=ITEM_USE/38 Param0=4 Param1=9764864 CanvasX=1338 CanvasY=930

fletch:
2021-08-31 17:28:53 [Client] INFO  injected-client - |MenuAction|: MenuOption=Use MenuTarget=<col=ff9040>Knife<col=ffffff> -> <col=ff9040>Bruma root Id=20695 Opcode=ITEM_USE_ON_WIDGET_ITEM/31 Param0=9 Param1=9764864 CanvasX=1388 CanvasY=955

leave:
2021-08-31 17:30:09 [Client] INFO  injected-client - |MenuAction|: MenuOption=Enter MenuTarget=<col=ffff>Doors of Dinh Id=29322 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=51 Param1=36 CanvasX=584 CanvasY=520

enter:
2021-08-31 17:30:17 [Client] INFO  injected-client - |MenuAction|: MenuOption=Enter MenuTarget=<col=ffff>Doors of Dinh Id=29322 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=51 Param1=36 CanvasX=980 CanvasY=483

bank:
2021-08-31 17:30:34 [Client] INFO  injected-client - |MenuAction|: MenuOption=Bank MenuTarget=<col=ffff>Bank chest Id=29321 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=65 Param1=16 CanvasX=99 CanvasY=989

house tab:
2021-08-31 17:30:59 [Client] INFO  injected-client - |MenuAction|: MenuOption=Break MenuTarget=Break Id=8013 Opcode=ITEM_FIRST_OPTION/33 Param0=0 Param1=9764864 CanvasX=1341 CanvasY=881

rejuv:
2021-08-31 17:31:09 [Client] INFO  injected-client - |MenuAction|: MenuOption=Drink MenuTarget=<col=ffff>Ornate pool of Rejuvenation Id=29241 Opcode=GAME_OBJECT_FIRST_OPTION/3 Param0=46 Param1=46 CanvasX=809 CanvasY=459

winter tp from house:
2021-08-31 17:31:46 [Client] INFO  injected-client - |MenuAction|: MenuOption=Wintertodt Camp MenuTarget=<col=ffff>Ornate Jewellery Box Id=29156 Opcode=GAME_OBJECT_THIRD_OPTION/5 Param0=54 Param1=49 CanvasX=750 CanvasY=664

drink full brew:
2021-08-31 17:32:48 [Client] INFO  injected-client - |MenuAction|: MenuOption=Drink MenuTarget=Drink Id=6685 Opcode=ITEM_FIRST_OPTION/33 Param0=8 Param1=9764864 CanvasX=1345 CanvasY=969

//crate: 20703
//outside wt 6461
//house 36654







//TODO: stand in middle at 500pts+  1631, 3981, 0



 */