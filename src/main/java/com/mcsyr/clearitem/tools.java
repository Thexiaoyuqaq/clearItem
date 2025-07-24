package com.mcsyr.clearitem;

import java.util.*;

import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

public class tools {
    // 添加常量定义
    private static final long SCHEDULER_INITIAL_DELAY = 200L;
    private static final long SCHEDULER_PERIOD = 20L;
    private static final int BROADCAST_TIME_60 = 60;
    private static final int BROADCAST_TIME_10 = 10;
    
    private static BukkitTask clearItemTask;

    private static final Map<String, Long> performanceMetrics = new HashMap<>();

    private tools() {
        // 私有构造函数防止实例化
    }

    public static void Scheduler() {
        if (Main.ClearItemTime != 0) {
            scheduleClearItemTask();
        }
    }

    private static void scheduleClearItemTask() {
        clearItemTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Main.plugin, () -> {
            Main.timer = Main.timer + 1;
            Main.ShareClearTime = Main.shareClearTimer + 1;
            handleClearItemSchedule();
        }, SCHEDULER_INITIAL_DELAY, SCHEDULER_PERIOD);
    }

    private static void handleClearItemSchedule() {
        int remainingTime = Main.ClearItemTime - Main.timer;
        
        if (remainingTime == BROADCAST_TIME_60) {
            broadcastClearWarning(BROADCAST_TIME_60);
        } else if (remainingTime == BROADCAST_TIME_10) {
            broadcastClearWarning(BROADCAST_TIME_10);
        } else if (remainingTime <= 0) {
            performClearItems();
        }

        CheckPlayerDropLock();

        if (Main.ShareEnable) {
            int remainingShareClearTime = Main.ShareClearTime - Main.shareClearTimer;
            if (remainingShareClearTime == BROADCAST_TIME_60) {
                broadcastShareClearWarning(BROADCAST_TIME_60);
            }  else if (remainingShareClearTime == BROADCAST_TIME_10) {
                broadcastShareClearWarning(BROADCAST_TIME_10);
            }  else if (remainingShareClearTime <= 0) {
                performShareClearItems();
            }
        }
    }

    private static void broadcastShareClearWarning(int seconds) {
        Bukkit.getServer().broadcastMessage(
                Main.ShareClearMessagePre.replace("%time%", String.valueOf(seconds)));
    }

    private static void broadcastClearWarning(int seconds) {
        Bukkit.getServer().broadcastMessage(
            Main.ClearItemMessageClearPre.replace("%time%", String.valueOf(seconds)));
    }

    private static void performShareClearItems() {
        Bukkit.getServer().broadcastMessage(Main.ShareClearMessageStart);
        Main.shareClearTimer = 0;

        cleanShare();
    }

    private static void performClearItems() {
        Bukkit.getServer().broadcastMessage(Main.ClearItemMessageClearStart);
        clearWorld();
        Main.timer = 0;
        Main.DustbinClearFrequency++;
        
        if (shouldCleanPublicDustbin()) {
            cleanPublicDustbin();
            Main.DustbinClearFrequency = 0;
        }
    }

    private static boolean shouldCleanPublicDustbin() {
        return Main.PublicDustbinEnable && 
               Main.DustbinClearFrequency % Main.PublicDustbinClearInterval == 0;
    }

    public static void CheckPlayerDropLock() {
        long currentTime = System.currentTimeMillis();
        
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (!Main.PlayerDropLock.getOrDefault(player, true)) {
                long lockTime = Main.PlayerDropLockTime.get(player).getTime();
                if (currentTime - lockTime > Main.DropTime && Main.DropEnableTimer) {
                    Main.PlayerDropLock.put(player, true);
                    player.sendMessage(Main.DropMessageOpen);
                }
            }
        }
    }

    public static void clearWorld() {
        long startTime = System.currentTimeMillis();
        try {
            List<World> worlds = Bukkit.getWorlds();
            for (int i = 0; i < worlds.size(); i++) {
                final int index = i;
                Bukkit.getScheduler().runTaskLater(Main.plugin, 
                    () -> clearWorldItem(worlds.get(index), index == worlds.size() - 1), 
                    10L * i);
            }
        } finally {
            logPerformance("clearWorld", startTime);
        }
    }

    private static void clearWorldItem(World world, boolean isDustbin) {
        EntityCounter counter = new EntityCounter();
        if (Dustbin.pageSize > 1) {
            Dustbin.page();  // 向垃圾桶添加物品前先添加上一页/下一页物品以防止被顶替
        }
        // 在主线程中执行实体清理和计数
        try {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    Item item = (Item) entity;
                    if (!Main.ClearItemWhiteList.contains(item.getItemStack().getType().name())) {
                        if (!Main.BlockBlackList.contains(item.getItemStack().getType().name()) && 
                            Dustbin.addItem(item.getItemStack())) {
                            counter.incrementDustbin();
                        }
                        entity.remove();
                        counter.incrementTotal();
                    }
                } else if (shouldClearEntity(entity)) {
                    entity.remove();
                    counter.incrementTotal();
                }
            }
            
            // 更新统计并发送消息
            if (counter.getTotal() > 0) {
                Main.DustbinCount = counter.getDustbinCount();  // ↓
                Main.WasteTotal = counter.getTotal();  // modify: 对于清理计数提示，没必要进行数字累计增加
                
                if (!Main.CleaningTipsEnable) {
                    Bukkit.getScheduler().runTask(Main.plugin, () -> {
                        broadcastWorldClear(world, counter.getTotal());
                        notifyDustbinStatus();
                    });
                }
            }

            if (isDustbin && Main.CleaningTipsEnable) {
                Bukkit.getScheduler().runTask(Main.plugin, () -> {
                    broadcastTotalCleared();
                    notifyDustbinStatus();
                });
            }
            
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error clearing world items: " + e.getMessage());
        }
    }

    private static boolean shouldClearEntity(Entity entity) {
        return (entity instanceof ItemFrame && Main.ClearItemItemFrame) ||
               (entity instanceof Boat && Main.ClearItemBoat) ||
               (entity instanceof ExperienceOrb && Main.ClearItemExpBall) ||
               (entity instanceof FallingBlock && Main.ClearItemFallingBlock) ||
               (entity instanceof Painting && Main.ClearItemPainting) ||
               (entity instanceof Minecart && Main.ClearItemMinecart) ||
               (entity instanceof Arrow && Main.ClearItemArrow) ||
               (entity instanceof Snowball && Main.ClearItemSnowball);
    }

    private static class EntityCounter {
        private int total = 0;
        private int dustbinCount = 0;
        
        void incrementTotal() { total++; }
        void incrementDustbin() { dustbinCount++; }
        int getTotal() { return total; }
        int getDustbinCount() { return dustbinCount; }
    }

    private static void notifyDustbinStatus() {
        TextComponent message = new TextComponent(
            Main.PublicDustbinMessageReminder.replace("%amount%", 
            String.valueOf(Main.DustbinCount)));
        TextComponent button = createDustbinButton();
        
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            player.spigot().sendMessage(message, button);
        }
    }

    private static TextComponent createDustbinButton() {
        TextComponent button = new TextComponent(Main.PublicDustbinMessageButton);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/citem open"));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder(Main.PublicDustbinMessageInfo).create()));
        return button;
    }

    public static void cleanPublicDustbin() {
        if (Main.PublicDustbinEnable) {
            Dustbin.ClearDustbin();
            Bukkit.getServer().broadcastMessage(Main.PublicDustbinMessageClear);
            if (Dustbin.pageSize > 1) {
                Dustbin.page();
            }
        }
    }

    public static void cleanShare() {
        if (Main.ShareEnable) {
            Share.ClearShareInv();
            Bukkit.getServer().broadcastMessage(Main.ShareClearMessageEnd);
            if (Share.pageSize > 1) {
                Share.page();
            }
        }
    }

    public static boolean isIncludedString(List<String> list, String string) {
        if (string == null || list == null) {
            return false;
        }
        return list.stream().anyMatch(string::contains);
    }

    public static void TraversePlayer() {
        Bukkit.getServer().getOnlinePlayers()
              .forEach(tools::initPlayerData);
    }

    public static void initPlayerData(Player player) {
        Main.PlayerDropLock.putIfAbsent(player, true);
        Main.PlayerDropLockTime.putIfAbsent(player, new Date());
        Main.PlayerPrivateDustbin.putIfAbsent(player, 
            Bukkit.createInventory(player, Main.PrivateDustbinSize, Main.PrivateDustbinName));
    }

    private static void broadcastWorldClear(World world, int count) {
        Bukkit.getServer().broadcastMessage(
            Main.ClearItemMessageClearWorld
                .replaceAll("%world%", IncludeWorldAlias(world.getName()))
                .replaceAll("%count%", String.valueOf(count)));

    }

    private static void broadcastTotalCleared() {
        Bukkit.getServer().broadcastMessage(
            Main.ClearItemMessageClear.replaceAll("%count%", 
            String.valueOf(Main.WasteTotal)));
        Main.WasteTotal = 0;
    }

    public static String IncludeWorldAlias(String name) {
        return Main.Config.getString("CleaningTips.WorldAlias." + name, name);
    }

    public static void clearEntityItem(Entity entity) {
        if (entity instanceof Item) {
            Item item = (Item) entity;
            if (!Main.ClearItemWhiteList.contains(item.getItemStack().getType().name())) {
                if (!Main.BlockBlackList.contains(item.getItemStack().getType().name())) {
                    Dustbin.addItem(item.getItemStack());
                }
                entity.remove();
            }
        } else if (shouldClearEntity(entity)) {
            entity.remove();
        }
    }

    public static void cancelTasks() {
        if (clearItemTask != null) clearItemTask.cancel();
    }

    public static void logPerformance(String operation, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        performanceMetrics.merge(operation, duration, Long::sum);
        
        // 如果操作时间过长，记录警告
        if (duration > 50) { // 50ms阈值
            Bukkit.getLogger().warning(String.format(
                "Operation %s took %dms to complete", 
                operation, duration));
        }
    }

    public static class PerformanceStats {
        private static final Map<String, Long> totalTime = new HashMap<>();
        private static final Map<String, Integer> callCount = new HashMap<>();
        
        public static void record(String operation, long duration) {
            synchronized (totalTime) {
                totalTime.merge(operation, duration, Long::sum);
                callCount.merge(operation, 1, Integer::sum);
            }
        }
        
        public static Map<String, Double> getAverages() {
            Map<String, Double> averages = new HashMap<>();
            synchronized (totalTime) {
                totalTime.forEach((op, time) -> 
                    averages.put(op, (double) time / callCount.get(op)));
            }
            return averages;
        }
    }
}
