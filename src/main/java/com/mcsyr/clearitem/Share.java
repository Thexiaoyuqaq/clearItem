package com.mcsyr.clearitem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

/**
 * @author KlNon
 * @version 1.0
 * @className Share
 * @description
 * @date 2022/8/23 12:28
 **/
public class Share {
    public static ArrayList<Inventory> shareList = new ArrayList<Inventory>();


    public static int shareLarge = 0;
    public static int pageSize;

    public Share() {
    }

    public static void page(){
        for(Inventory inv: shareList){
            ItemStack prev = new ItemStack(Material.BOOK);
            ItemMeta prevMeta = prev.getItemMeta();
            assert prevMeta != null;
            prevMeta.setDisplayName(Main.SharePre);
            ArrayList<String> prevLore =new ArrayList<>();
            prevLore.add(Main.SharePreInfo);
            prevMeta.setLore(prevLore);
            prev.setItemMeta(prevMeta);

            ItemStack next = new ItemStack(Material.BOOK);
            ItemMeta nextMeta = next.getItemMeta();
            assert nextMeta != null;
            nextMeta.setDisplayName(Main.ShareNext);
            ArrayList<String> nextLore =new ArrayList<>();
            nextLore.add(Main.ShareNextInfo);
            nextMeta.setLore(nextLore);
            next.setItemMeta(nextMeta);
            if(!inv.contains(prev)){
                inv.setItem(inv.getSize()-9, prev);
            }
            if(!inv.contains(next)){
                inv.setItem(inv.getSize()-1, next);
            }
        }
    }

    public static void ClearShareInv() {
        for (Inventory inventory : shareList) {
            inventory.clear();
        }
    }

    public static void onInitialize() {
        int size = Main.ShareSize;
        pageSize = size / 54;
        if (size > 54) {
            for (int i = 0; i < pageSize; i++) {
                shareList.add(Bukkit.createInventory((InventoryHolder) null, 54, Main.ShareName + "第" + (i + 1) + "页"));
                shareLarge++;
            }
            if (((size%54+5)/9)*9!=0) {
                shareList.add(Bukkit.createInventory((InventoryHolder) null, ((size%54+5)/9)*9, Main.ShareName + "第" + (shareLarge + 1) + "页"));
                shareLarge++;
            }
        } else {
            shareList.add(Bukkit.createInventory((InventoryHolder) null, size, Main.ShareName + "第" + 1 + "页"));
            shareLarge++;
        }
        if (pageSize > 1) {
            page();
        }
    }

    static {
        onInitialize();
    }
}
