package com.mcsyr.clearitem;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class command implements TabExecutor {

  private static final Pattern PATTERN = Pattern.compile("^[1-9]\\d*$");
  private static final String CMD_OPEN = "open";
  private static final String CMD_DROP = "drop";
  private static final String CMD_DISCARD = "discard";
  private static final String CMD_STATS = "stats";
  private static final String CMD_HELP = "help";
  private static final String CMD_RELOAD = "reload";
  private static final String CMD_TYPE = "type";
  private static final String CMD_CLEAR = "publicclear";
  private static final String CMD_CLEAN = "publicclean";
  private static final String CMD_SHARE = "share";
  private static final String CMD_SHARECLEAN = "shareclean";

  private static final String ERROR = Main.Prefix + "§c";
  private static final String SUCCESS = Main.Prefix + "§a";
  private static final String INFO = Main.Prefix + "§7";

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
    if (!(sender instanceof Player)) {
      return handleConsoleCommand(sender, args);
    }
    
    Player player = (Player)sender;
    
    if (args.length < 1) {
      showHelp(player);
      return true;
    }
    
    String subCmd = args[0].toLowerCase();
    
    try {
      if (args.length == 1) {
        return handleSingleArgCommand(player, subCmd);
      } else if (args.length == 2) {
        return handleDoubleArgCommand(player, subCmd, args[1]); 
      }
    } catch (Exception e) {
      player.sendMessage(ERROR + "执行命令时发生错误: " + e.getMessage());
      e.printStackTrace();
    }
    
    return false;
  }

  private boolean handleSingleArgCommand(Player player, String subCmd) {
    switch(subCmd) {
      case CMD_OPEN:
      case "o":
        return handleOpenCommand(player);
      case CMD_DROP:
        return handleDropCommand(player);
      case CMD_DISCARD:
      case "d": 
        return handleDiscardCommand(player);
      case CMD_HELP:
        return handleHelpCommand(player);
      case CMD_RELOAD:
        return handleReloadCommand(player);
      case CMD_TYPE:
        return handleTypeCommand(player);
      case CMD_CLEAR:
      case "pclear":
        return handlePublicClearCommand(player);
      case CMD_CLEAN:
      case "pclean":
        return handlePublicCleanCommand(player);
      case CMD_STATS:
        return handleStatsCommand(player);
      case CMD_SHARE:
        return handleShareCommand(player);
      case CMD_SHARECLEAN:
      case "sclean":
        return handleShareCleanCommand(player);
      default:
        return false;
    }
  }

  private boolean handleShareCleanCommand(Player player) {
    player.sendMessage(INFO + "共享空间按清理中");
    tools.cleanShare();
    if (Share.pageSize > 1) {
      Share.page();
    }
    return true;
  }

  private boolean handleShareCommand(Player player) {
    if (!Main.ShareEnable) {
      player.sendMessage(ERROR + "共享空间功能已被禁用!");
      return true;
    }

    if (!player.hasPermission("ClearItem.share")) {
      player.sendMessage(ERROR + "你没有权限使用此命令!");
      return true;
    }

    player.openInventory(Share.shareList.getFirst());
    player.sendMessage(SUCCESS + Main.ShareAction + Main.ShareName);

    return true;
  }

  private boolean handleOpenCommand(Player player) {
    if (!Main.PublicDustbinEnable) {
      player.sendMessage(ERROR + "公共垃圾箱功能已被禁用!");
      return true;
    }
    
    if (!player.hasPermission("ClearItem.open")) {
      player.sendMessage(ERROR + "你没有权限使用此命令!");
      return true;
    }
    
    player.openInventory(Dustbin.dustbinList.getFirst());
    player.sendMessage(SUCCESS + Main.PublicDustbinAction + Main.PublicDustbinName);
    return true;
  }

  private boolean handleDropCommand(Player player) {
    if (!Main.DropEnable) {
      player.sendMessage(ERROR + "防丢弃功能已被禁用!");
      return true;
    }
    
    if (!player.hasPermission("ClearItem.drop")) {
      player.sendMessage(ERROR + "你没有权限使用此命令!");
      return true;
    }
    
    boolean currentState = Main.PlayerDropLock.get(player);
    if (currentState) {
      Main.PlayerDropLock.put(player, false);
      Main.PlayerDropLockTime.put(player, new Date());
      if (Main.DropEnableTimer) {
        player.sendMessage(Main.DropMessageClose.replaceAll("%time%", String.valueOf(Main.DropTime / 1000)));
      } else {
        player.sendMessage(Main.DropMessageCloseNoTimer);
      }
    } else {
      Main.PlayerDropLock.put(player, true);
      player.sendMessage(Main.DropMessageOpen);
    }
    return true;
  }

  private boolean handleDiscardCommand(Player player) {
    if (Main.PrivateDustbinEnable) {
      player.openInventory(Main.PlayerPrivateDustbin.get(player));
      player.sendMessage(Main.PrivateDustbinAction + Main.PrivateDustbinName);
      return true;
    } else {
      player.sendMessage("私人垃圾箱已被服务器禁用!");
      return true;
    }
  }

  private boolean handleHelpCommand(Player player) {
    showHelp(player);
    return true;
  }

  private boolean handleReloadCommand(Player player) {
    Main.loadConfig();
    Main.refreshInventories();
    player.sendMessage(SUCCESS + "插件重载成功!");
    return true;
  }

  private boolean handleTypeCommand(Player player) {
    player.sendMessage(INFO + "Type: " + player.getItemInHand().getType().name());
    return true;
  }

  private boolean handlePublicClearCommand(Player player) {
    player.sendMessage(INFO + "世界清理中");
    tools.clearWorld();
    return true;
  }

  private boolean handlePublicCleanCommand(Player player) {
    player.sendMessage(INFO + "公共垃圾桶清理中");
    tools.cleanPublicDustbin();
    if (Dustbin.pageSize > 1){
      Dustbin.page();
    }
    return true;
  }

  private boolean handleStatsCommand(Player player) {
    if (!player.hasPermission("ClearItem.admin")) {
      return false;
    }
    
    Map<String, Double> stats = tools.PerformanceStats.getAverages();
    player.sendMessage(INFO + "性能统计:");
    stats.forEach((op, avg) -> 
        player.sendMessage(String.format("§7- %s: §f%.2fms", op, avg)));
    
    return true;
  }

  private boolean handleDoubleArgCommand(Player player, String subCmd, String arg) {
    if ((subCmd.equalsIgnoreCase(CMD_OPEN) || subCmd.equalsIgnoreCase("o")) 
        && Main.PublicDustbinEnable) {
      
      if (!PATTERN.matcher(arg).matches()) {
        player.sendMessage(ERROR + "请输入有效的页码!");
        return true;
      }
      
      int page = Integer.parseInt(arg);
      int maxPage = Dustbin.dustbinList.size();
      
      if (page < 1 || page > maxPage) {
        player.sendMessage(ERROR + "页码范围: 1-" + maxPage);
        return true;
      }
      
      player.openInventory(Dustbin.dustbinList.get(page - 1));
      player.sendMessage(SUCCESS + Main.PublicDustbinAction + Main.PublicDustbinName + " §7(第" + page + "页)");
      return true;
    }
    return false;
  }

  private boolean handleConsoleCommand(CommandSender sender, String[] args) {
    if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
      Main.loadConfig();
      sender.sendMessage(SUCCESS + "插件重载成功!");
      return true;
    }
    return false;
  }

  private void showHelp(CommandSender sender) {
    sender.sendMessage("§8§m                    §r " + Main.Prefix + " §8§m                    ");
    sender.sendMessage("");
    sender.sendMessage(INFO + "基础命令:");
    sender.sendMessage("§8- §b/ci open §7(o) [页码] §f打开公共垃圾箱");
    sender.sendMessage("§8- §b/ci discard §7(d) §f打开私人垃圾箱");
    sender.sendMessage("§8- §b/ci drop §f切换防丢弃模式");
    sender.sendMessage("§8- §b/ci share §f打开共享空间");
    
    if (sender.hasPermission("ClearItem.admin")) {
      sender.sendMessage("");
      sender.sendMessage(INFO + "管理命令:");
      sender.sendMessage("§8- §b/ci type §f查看手持物品类型");
      sender.sendMessage("§8- §b/ci reload §f重载插件配置");
      sender.sendMessage("§8- §b/ci stats §f查看性能统计");
      sender.sendMessage("§8- §b/ci publicclear §7(pclear) §f清理世界掉落物");
      sender.sendMessage("§8- §b/ci publicclean §7(pclean) §f清空公共垃圾箱");
      sender.sendMessage("§8- §b/ci shareclean §7(sclean) §f清空公共空间");
    }
    
    sender.sendMessage("");
    sender.sendMessage("§8§m                    §r §8v" + Main.Version + " §8§m                    ");
  }

  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
    if(args.length==1){
      List<String> TabList = new ArrayList<>(Main.Arg1_TabCommand);
      if(sender.isOp())
        TabList.addAll(Main.Arg1_Op_TabCommand);
      TabList.removeIf(s -> !s.toLowerCase().startsWith(args[0].toLowerCase()));
      return TabList;
    }else {
      return null;
    }
  }
}
