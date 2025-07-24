package com.mcsyr.clearitem;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.Material;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class Event implements Listener {

  // 添加常量
  private static final int NEARBY_RADIUS_X = 16;
  private static final int NEARBY_RADIUS_Y = 32;
  private static final int NEARBY_RADIUS_Z = 16;
  private static final int ITEM_DENSITY_THRESHOLD = 10; // 物品密度阈值
  private static final double MIN_DISTANCE_BETWEEN_ITEMS = 1.5; // 最小物品间距
  private static final int TIME_WINDOW_SECONDS = 10; // 检测时间窗口
  private static final DecimalFormat COORDINATE_FORMAT = new DecimalFormat("0.0");

  // 用于追踪物品生成历史
  private final Map<Location, List<Long>> itemSpawnHistory = new HashMap<>();
  private final Map<String, Integer> chunkItemCount = new HashMap<>();
  
  // 清理记录缓存
  private final Set<String> recentlyClearedChunks = new HashSet<>();
  private final Map<Location, Long> lastCleanTime = new HashMap<>();

  // 添加智能预测系统
  private final PredictionSystem predictionSystem = new PredictionSystem();
  
  // 添加缓存机制
  private final LoadingCache<String, Boolean> chunkClearCache = CacheBuilder.newBuilder()
      .expireAfterWrite(5, TimeUnit.SECONDS)
      .build(new CacheLoader<String, Boolean>() {
          @Override
          public Boolean load(String key) {
              return false;
          }
      });

  private static class PredictionSystem {
    private final Map<String, Double> chunkProbability = new HashMap<>();
    
    public void updateProbability(String chunkKey, boolean wasCleared) {
      double currentProb = chunkProbability.getOrDefault(chunkKey, 0.5);
      if (wasCleared) {
        currentProb = Math.min(1.0, currentProb + 0.1);
      } else {
        currentProb = Math.max(0.0, currentProb - 0.1);
      }
      chunkProbability.put(chunkKey, currentProb);
    }
    
    public boolean shouldPreemptivelyCheck(String chunkKey) {
      return chunkProbability.getOrDefault(chunkKey, 0.5) > 0.7;
    }
  }

  public Event() {
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    tools.initPlayerData(event.getPlayer());
  }

  @EventHandler(
          priority = EventPriority.HIGHEST
  )
  public void onPlayerDropItem(PlayerDropItemEvent event) {
    Player player = event.getPlayer();
    if (!Main.DropEnable || !Main.PlayerDropLock.getOrDefault(player, true)) {
      return;
    }
    
    event.setCancelled(true);
    player.sendMessage(Main.DropMessageDiscardInOpen);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onItemSpawn(ItemSpawnEvent event) {
    Location location = event.getLocation();
    String chunkKey = getChunkKey(location);
    
    // 更新物品生成历史
    updateItemSpawnHistory(location);
    
    // 智能检测是否需要清理
    if (shouldClearItems(event.getEntity())) {
      handleItemClear(event, location, chunkKey);
    }
  }
  
  private void handleItemClear(ItemSpawnEvent event, Location location, String chunkKey) {
    // 检查是否最近已清理过该区块
    if (recentlyClearedChunks.contains(chunkKey)) {
      event.setCancelled(true);
      return;
    }
    
    // 检查清理冷却时间
    long now = System.currentTimeMillis();
    if (lastCleanTime.containsKey(location) && 
        now - lastCleanTime.get(location) < 5000) { // 5秒冷却
      event.setCancelled(true);
      return;
    }
    
    // 执行清理
    event.setCancelled(true);
    Bukkit.getScheduler().runTask(Main.plugin, () -> {
      clearItemsNearLocation(location);
      broadcastClearMessage(location);
      
      // 更新清理记录
      recentlyClearedChunks.add(chunkKey);
      lastCleanTime.put(location, now);
      
      // 5秒后移除清理记录
      Bukkit.getScheduler().runTaskLater(Main.plugin, () -> {
        recentlyClearedChunks.remove(chunkKey);
      }, 100L); // 5秒 = 100 ticks
    });
  }
  
  private boolean shouldClearItems(Entity entity) {
    Location loc = entity.getLocation();
    List<Entity> nearbyEntities = entity.getNearbyEntities(
        NEARBY_RADIUS_X, NEARBY_RADIUS_Y, NEARBY_RADIUS_Z);
            
    // 基础数量检查
    if (nearbyEntities.size() < Main.ClearItemChunkMaxItems) {
      return false;
    }
    
    // 计算物品密度
    int itemCount = 0;
    Map<Material, Integer> materialCounts = new HashMap<>();
    
    for (Entity nearby : nearbyEntities) {
      if (nearby instanceof Item) {
        itemCount++;
        Item item = (Item) nearby;
        Material material = item.getItemStack().getType();
        materialCounts.merge(material, 1, Integer::sum);
        
        // 检查物品间距
        if (isItemsTooClose(loc, nearby.getLocation())) {
          return true;
        }
      }
    }
    
    // 检查物品密度
    double density = (double) itemCount / (NEARBY_RADIUS_X * NEARBY_RADIUS_Y * NEARBY_RADIUS_Z);
    if (density > ITEM_DENSITY_THRESHOLD) {
      return true;
    }
    
    // 检查单一物品类型数量
    return materialCounts.values().stream().anyMatch(count -> count > Main.ClearItemChunkMaxItems / 2);
  }
  
  private boolean isItemsTooClose(Location loc1, Location loc2) {
    return loc1.distance(loc2) < MIN_DISTANCE_BETWEEN_ITEMS;
  }
  
  private void updateItemSpawnHistory(Location location) {
    String chunkKey = getChunkKey(location);
    long now = System.currentTimeMillis();
    
    // 更新生成历史
    itemSpawnHistory.computeIfAbsent(location, k -> new ArrayList<>()).add(now);
    
    // 清理过期记录
    itemSpawnHistory.values().forEach(times -> 
      times.removeIf(time -> now - time > TIME_WINDOW_SECONDS * 1000));
    
    // 更新区块物品计数
    chunkItemCount.merge(chunkKey, 1, Integer::sum);
  }
  
  private String getChunkKey(Location location) {
    return location.getWorld().getName() + ":" + 
           location.getBlockX() / 16 + ":" + 
           location.getBlockZ() / 16;
  }
  
  private void clearItemsNearLocation(Location location) {
    long startTime = System.currentTimeMillis();
    try {
        Collection<Entity> entities = location.getWorld().getNearbyEntities(location, 
            NEARBY_RADIUS_X, NEARBY_RADIUS_Y, NEARBY_RADIUS_Z);
            
        Map<Material, ItemStack> combinedItems = new HashMap<>();
        int totalItems = 0;
        
        // 批量处理物品
        for (Entity entity : entities) {
            if (entity instanceof Item) {
                totalItems++;
                Item item = (Item) entity;
                ItemStack itemStack = item.getItemStack();
                
                if (!shouldPreserveItem(itemStack)) {
                    Material material = itemStack.getType();
                    combinedItems.merge(material, itemStack.clone(), this::combineItemStacks);
                    entity.remove();
                }
            }
        }
        
        // 批量添加到垃圾箱
        if (!combinedItems.isEmpty()) {
            Bukkit.getScheduler().runTask(Main.plugin, () -> 
                combinedItems.values().forEach(Dustbin::addItem));
        }
        
        // 更新统计
        updateStats(location, totalItems);
        
    } finally {
        tools.logPerformance("clearItems", startTime);
    }
  }
  
  private boolean shouldPreserveItem(ItemStack itemStack) {
    return Main.ClearItemWhiteList.contains(itemStack.getType().name()) ||
           Main.BlockBlackList.contains(itemStack.getType().name());
  }
  
  private ItemStack combineItemStacks(ItemStack existing, ItemStack additional) {
    if (existing.isSimilar(additional)) {
      existing.setAmount(Math.min(existing.getAmount() + additional.getAmount(), 
                                existing.getMaxStackSize()));
    }
    return existing;
  }
  
  private void broadcastClearMessage(Location location) {
    Player nearestPlayer = findNearestPlayer(location.getWorld());
    String message = formatClearMessage(location, nearestPlayer);
    Bukkit.getServer().broadcastMessage(message);
  }

  @EventHandler(
          priority = EventPriority.HIGHEST
  )
  public void onInventoryClick(InventoryClickEvent event) {
    if (!isValidClick(event)) {
      return;
    }
    
    handleInventoryClick(event);
  }
  
  private boolean isValidClick(InventoryClickEvent event) {
    ItemStack item = event.getCurrentItem();
    return item != null && 
           !item.getType().name().equals("AIR") && 
           event.getView().getTitle() != null;
  }
  
  private void handleInventoryClick(InventoryClickEvent event) {
    String title = event.getView().getTitle();

    if (title.contains(Main.PublicDustbinName)) {
        handleDustbinClick(event, isDustbinLocked(title));
    } else if (title.contains(Main.ShareName)) {
      handleShareClick(event);
    } else if (isPrivateDustbin(title)) {
      handlePrivateDustbinClick(event, Objects.requireNonNull(event.getCurrentItem()));
    }
  }

  private void handleShareClick(InventoryClickEvent event) {
    if(Objects.requireNonNull(event.getCurrentItem()).hasItemMeta() && Objects.requireNonNull(event.getCurrentItem().getItemMeta()).hasDisplayName()){
      if(event.getCurrentItem().getItemMeta().getDisplayName().equals(Main.SharePre)){
        event.setCancelled(true);
        int count=Integer.parseInt(event.getView().getTitle().substring(event.getView().getTitle().length()-2,event.getView().getTitle().length()-1))-1;
        if(count>0){
          count--;
        }else {
          count=Share.shareList.size()-1;
        }
        Player player = (Player) event.getWhoClicked();
        player.closeInventory();
        player.openInventory(Share.shareList.get(count));
      }else if(event.getCurrentItem().getItemMeta().getDisplayName().equals(Main.ShareNext)){
        event.setCancelled(true);
        int count=Integer.parseInt(event.getView().getTitle().substring(event.getView().getTitle().length()-2,event.getView().getTitle().length()-1))-1;
        if(count<Share.shareList.size()-1){
          count++;
        }else
          count=0;
        Player player = (Player) event.getWhoClicked();
        player.closeInventory();
        player.openInventory(Share.shareList.get(count));
      }
    }
  }

  private boolean isDustbinLocked(String title) {
    return title.contains(Main.PublicDustbinName) && Main.DustbinLock;
  }
  
  private boolean isPrivateDustbin(String title) {
    return Main.DropEnable && title.contains(Main.PrivateDustbinName);
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!isPrivateDustbinClose(event)) {
      return;
    }
    
    handlePrivateDustbinClose(event);
  }
  
  private boolean isPrivateDustbinClose(InventoryCloseEvent event) {
    String title = event.getView().getTitle();
    return title != null && 
           Main.PrivateDustbinEnable && 
           title.equals(Main.PrivateDustbinName);
  }
  
  private void handlePrivateDustbinClose(InventoryCloseEvent event) {
    Player player = (Player) event.getPlayer();
    Inventory inventory = Main.PlayerPrivateDustbin.get(player);
    TransferResult result = transferItems(inventory);
    
    if (result.hasChanges()) {
      notifyTransferResult(player, result);
    }
  }
  
  private static class TransferResult {
    final int cleared;
    final int preserved;
    
    TransferResult(int cleared, int preserved) {
      this.cleared = cleared;
      this.preserved = preserved;
    }
    
    boolean hasChanges() {
      return cleared > 0 || preserved > 0;
    }
  }
  
  private TransferResult transferItems(Inventory inventory) {
    int cleared = 0;
    int preserved = 0;
    
    for (ItemStack item : inventory.getContents()) {
      if (item == null) continue;
      
      if (Dustbin.addItem(item)) {
        inventory.remove(item);
        cleared++;
      } else {
        preserved++;
      }
    }
    
    return new TransferResult(cleared, preserved);
  }
  
  private void notifyTransferResult(Player player, TransferResult result) {
    String message = Main.PrivateDustbinMessageClear
        .replace("%clear%", String.valueOf(result.cleared))
        .replace("%preserve%", String.valueOf(result.preserved));
    player.sendMessage(message);
  }

  private void handleDustbinClick(InventoryClickEvent event, boolean locked) {
    if (locked) {
      event.getWhoClicked().sendMessage(Main.PublicDustbinName + "垃圾箱已被锁住，请稍等1秒后操作...");
      event.setCancelled(true);
    } else {
      if(Objects.requireNonNull(event.getCurrentItem()).hasItemMeta() && Objects.requireNonNull(event.getCurrentItem().getItemMeta()).hasDisplayName()){
        if(event.getCurrentItem().getItemMeta().getDisplayName().equals(Main.PublicDustbinPrePageName)){
          event.setCancelled(true);
          int count=Integer.parseInt(event.getView().getTitle().substring(event.getView().getTitle().length()-2,event.getView().getTitle().length()-1))-1;
          if(count>0){
            count--;
          }else {
            count=Dustbin.dustbinList.size()-1;
          }
          Player player = (Player) event.getWhoClicked();
          player.closeInventory();
          player.openInventory(Dustbin.dustbinList.get(count));
        }else if(event.getCurrentItem().getItemMeta().getDisplayName().equals(Main.PublicDustbinNextPageName)){
          event.setCancelled(true);
          int count=Integer.parseInt(event.getView().getTitle().substring(event.getView().getTitle().length()-2,event.getView().getTitle().length()-1))-1;
          if(count<Dustbin.dustbinList.size()-1){
            count++;
          }else
            count=0;
          Player player = (Player) event.getWhoClicked();
          player.closeInventory();
          player.openInventory(Dustbin.dustbinList.get(count));
        }
      }
    }
  }

  private void handlePrivateDustbinClick(InventoryClickEvent event, ItemStack itemStack) {
    if (itemStack.getItemMeta() == null) {
      return;
    }
    
    String name = itemStack.getItemMeta().getDisplayName();
    if (tools.isIncludedString(Main.PrivateDustbinWhiteListName, name)) {
      event.setCancelled(true);
      return;
    }
    
    List<String> lores = itemStack.getItemMeta().getLore();
    if (lores != null) {
      for (String lore : lores) {
        if (tools.isIncludedString(Main.PrivateDustbinWhiteListLore, lore)) {
          event.setCancelled(true);
          return;
        }
      }
    }
  }

  private Player findNearestPlayer(org.bukkit.World world) {
    Player nearestPlayer = null;
    double nearestDistance = Double.MAX_VALUE;
    
    for (Player player : world.getPlayers()) {
      if (nearestPlayer == null) {
        nearestPlayer = player;
        continue;
      }
      
      double distance = player.getLocation().distanceSquared(world.getSpawnLocation());
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearestPlayer = player;
      }
    }
    
    return nearestPlayer;
  }

  private String formatClearMessage(Location location, Player nearestPlayer) {
    String message = Main.ClearItemMessageClearChunkMaxItems
        .replace("%world%", "%myworlds_world_alias%");
    
    if (nearestPlayer != null && Main.PlaceholderStatus) {
      message = PlaceholderAPI.setPlaceholders(nearestPlayer, message);
    }
    
    return message.replace("%X%", COORDINATE_FORMAT.format(location.getX()))
                 .replace("%Y%", COORDINATE_FORMAT.format(location.getY()))
                 .replace("%Z%", COORDINATE_FORMAT.format(location.getZ()));
  }

  private void updateStats(Location location, int itemCount) {
    String chunkKey = getChunkKey(location);
    predictionSystem.updateProbability(chunkKey, itemCount > 0);
    
    if (itemCount > 0) {
        try {
            chunkClearCache.put(chunkKey, true);
        } catch (Exception e) {
            // 忽略缓存错误
        }
    }
  }
}
