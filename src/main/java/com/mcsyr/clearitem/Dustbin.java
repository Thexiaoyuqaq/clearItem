package com.mcsyr.clearitem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Dustbin {
    public static ArrayList<Inventory> DustbinList = new ArrayList<>();
    public static int binSize = 0;
    private static final Object LOCK = new Object();
    private static final Map<Material, Integer> itemTypeCount = new HashMap<>();

    public Dustbin() {
    }

    public static void page() {
        for (Inventory inv : DustbinList) {
            ItemStack prev = new ItemStack(Material.BOOK);
            ItemMeta prevMeta = prev.getItemMeta();
            assert prevMeta != null;
            prevMeta.setDisplayName(Main.PublicDustbinPrePageName);
            ArrayList<String> prevLore = new ArrayList<>();
            prevLore.add(Main.PublicDustbinPrePageDes);
            prevMeta.setLore(prevLore);
            prev.setItemMeta(prevMeta);
            ItemStack next = new ItemStack(Material.BOOK);

            ItemMeta nextMeta = next.getItemMeta();
            assert nextMeta != null;
            nextMeta.setDisplayName(Main.PublicDustbinNextPageName);
            ArrayList<String> nextLore = new ArrayList<>();
            nextLore.add(Main.PublicDustbinNextPageDes);
            nextMeta.setLore(nextLore);
            next.setItemMeta(nextMeta);
            if (!inv.contains(prev)) {
                inv.setItem(inv.getSize() - 9, prev);
            }
            if (!inv.contains(next)) {
                inv.setItem(inv.getSize() - 1, next);
            }
        }
    }

    public static void cleanup() {
        synchronized(LOCK) {
            DustbinList.forEach(Inventory::clear);
            DustbinList.clear();
            itemTypeCount.clear();
        }
    }

    public static Boolean addItem(ItemStack itemStack) {
        synchronized(LOCK) {
            if (!isValidItem(itemStack)) {
                return false;
            }
            
            // 智能分配策略
            return distributeItem(itemStack);
        }
    }
    
    private static boolean distributeItem(ItemStack item) {
        try {
            Main.DustbinLock = true;
            Material material = item.getType();
            
            // 更新物品类型计数
            itemTypeCount.merge(material, item.getAmount(), Integer::sum);
            
            // 先尝试合并到现有堆
            for (Inventory inv : DustbinList) {
                if (tryMergeItem(inv, item)) {
                    return true;
                }
            }
            
            // 如果无法合并，寻找新空位
            for (Inventory inv : DustbinList) {
                if (tryAddToEmptySlot(inv, item)) {
                    return true;
                }
            }
            
            return false;
        } finally {
            Main.DustbinLock = false;
        }
    }

    private static boolean tryMergeItem(Inventory inv, ItemStack item) {
        for (ItemStack content : inv.getContents()) {
            if (content != null && content.isSimilar(item)) {
                int remainingSpace = content.getMaxStackSize() - content.getAmount();
                if (remainingSpace > 0) {
                    int amountToAdd = Math.min(remainingSpace, item.getAmount());
                    content.setAmount(content.getAmount() + amountToAdd);
                    item.setAmount(item.getAmount() - amountToAdd);
                    
                    if (item.getAmount() == 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean tryAddToEmptySlot(Inventory inv, ItemStack item) {
        int firstEmpty = inv.firstEmpty();
        if (firstEmpty != -1) {
            inv.setItem(firstEmpty, item.clone());
            return true;
        }
        return false;
    }

    private static boolean isValidItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItemMeta() == null) {
            return false;
        }
        
        if (tools.isIncludedString(Main.PublicDustbinWhiteListName, 
            itemStack.getItemMeta().getDisplayName())) {
            return false;    
        }
        
        if (!tools.isIncludedString(Main.PublicDustbinWhiteListName, Objects.requireNonNull(itemStack.getItemMeta()).getDisplayName())) {
            return true;
        }
        return false;
    }

    public static void ClearDustbin() {
        for (Inventory inventory : DustbinList) {
            inventory.clear();
        }
    }

    static {
        int Size = Main.PublicDustbinSize;
        int PageSize = Size / 54;
        if (Size > 54) {
            for (int i = 0; i < PageSize; i++) {
                DustbinList.add(Bukkit.createInventory((InventoryHolder) null, 54, Main.PublicDustbinName + "第" + (i + 1) + "页"));
                binSize++;
            }
            if (((Size % 54 + 5) / 9) * 9 != 0) {
                DustbinList.add(Bukkit.createInventory((InventoryHolder) null, ((Size % 54 + 5) / 9) * 9, Main.PublicDustbinName + "第" + (binSize + 1) + "页"));
                binSize++;
            }
        } else {
            DustbinList.add(Bukkit.createInventory((InventoryHolder) null, Size, Main.PublicDustbinName + "第" + 1 + "页"));
            binSize++;
        }
        if(PageSize>1)
            page();
    }
}
