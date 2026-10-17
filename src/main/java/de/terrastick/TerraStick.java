package de.terrastick;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TerraStick extends JavaPlugin implements Listener, TabExecutor {

    private static final List<String> MODES = List.of("hill", "mountain", "wall", "crater", "tower", "sphere", "brush");
    private static final int MAX_SIZE = 40;
    private static final int MAX_HEIGHT = 60;
    private static final int MAX_UNDO = 10;

    private NamespacedKey modeKey, sizeKey, materialKey, heightKey;
    private final Map<UUID, Deque<List<BlockState>>> undoMap = new HashMap<>();

    @Override
    public void onEnable() {
        modeKey = new NamespacedKey(this, "mode");
        sizeKey = new NamespacedKey(this, "size");
        materialKey = new NamespacedKey(this, "material");
        heightKey = new NamespacedKey(this, "height");

        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("terra");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("TerraStick aktiviert.");
    }

    // ---------------------------------------------------------------- Befehl

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler koennen diesen Befehl nutzen.");
            return true;
        }
        if (args.length == 0) {
            msg(p, "Nutzung: /terra <" + String.join("|", MODES) + "|undo> [groesse] [block(e)] [hoehe]", NamedTextColor.YELLOW);
            msg(p, "Zufallsblöcke: Bloecke mit Komma trennen, z.B. stone,cobblestone,andesite", NamedTextColor.GRAY);
            return true;
        }

        String mode = args[0].toLowerCase();

        if (mode.equals("undo")) {
            undo(p);
            return true;
        }

        if (!MODES.contains(mode)) {
            msg(p, "Unbekannter Modus. Moeglich: " + String.join(", ", MODES) + ", undo", NamedTextColor.RED);
            return true;
        }

        int size = 10;
        if (args.length >= 2) {
            try {
                size = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                msg(p, "Groesse muss eine Zahl sein.", NamedTextColor.RED);
                return true;
            }
        }
        size = Math.max(2, Math.min(MAX_SIZE, size));

        String material = "AUTO";
        if (args.length >= 3 && !args[2].equalsIgnoreCase("auto")) {
            List<Material> pal = parsePalette(args[2]);
            if (pal == null) {
                msg(p, "Unbekannter Block in: " + args[2], NamedTextColor.RED);
                return true;
            }
            StringJoiner sj = new StringJoiner(",");
            pal.forEach(m -> sj.add(m.name()));
            material = sj.toString();
        }

        int height = mode.equals("tower") ? 15 : 5;
        if (args.length >= 4) {
            try {
                height = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                msg(p, "Hoehe muss eine Zahl sein.", NamedTextColor.RED);
                return true;
            }
        }
        height = Math.max(1, Math.min(MAX_HEIGHT, height));

        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        meta.displayName(Component.text("Terraform-Stab: " + mode, NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Groesse: " + size, NamedTextColor.GRAY),
                Component.text("Block(e): " + material.toLowerCase(), NamedTextColor.GRAY),
                Component.text("Hoehe (Mauer/Turm): " + height, NamedTextColor.GRAY),
                Component.text("Rechtsklick = ausfuehren", NamedTextColor.DARK_GRAY)
        ));
        meta.setEnchantmentGlintOverride(true);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(modeKey, PersistentDataType.STRING, mode);
        pdc.set(sizeKey, PersistentDataType.INTEGER, size);
        pdc.set(materialKey, PersistentDataType.STRING, material);
        pdc.set(heightKey, PersistentDataType.INTEGER, height);
        stick.setItemMeta(meta);

        p.getInventory().addItem(stick);
        msg(p, "Du hast einen Terraform-Stab (" + mode + ", Groesse " + size + ") bekommen.", NamedTextColor.GREEN);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(MODES);
            out.add("undo");
        } else if (args.length == 2) {
            out.addAll(List.of("5", "10", "20", "30"));
        } else if (args.length == 3) {
            out.addAll(List.of("auto", "stone", "grass_block", "dirt", "stone_bricks", "cobblestone", "sand",
                    "stone,cobblestone,andesite", "stone_bricks,mossy_stone_bricks,cracked_stone_bricks"));
        } else if (args.length == 4) {
            out.addAll(List.of("3", "5", "10", "20"));
        }
        String last = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.startsWith(last));
        return out;
    }

    // ---------------------------------------------------------------- Rechtsklick

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) return;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String mode = pdc.get(modeKey, PersistentDataType.STRING);
        if (mode == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("terrastick.use")) {
            msg(p, "Dazu hast du keine Berechtigung.", NamedTextColor.RED);
            return;
        }

        int size = pdc.getOrDefault(sizeKey, PersistentDataType.INTEGER, 10);
        int height = pdc.getOrDefault(heightKey, PersistentDataType.INTEGER, 5);
        String matName = pdc.getOrDefault(materialKey, PersistentDataType.STRING, "AUTO");
        List<Material> pal = matName.equals("AUTO") ? null : parsePalette(matName);

        Block target = p.getTargetBlockExact(100);
        if (target == null) {
            msg(p, "Kein Block in Reichweite (100 Bloecke).", NamedTextColor.RED);
            return;
        }

        List<BlockState> undo = new ArrayList<>();
        switch (mode) {
            case "hill" -> buildHill(target, size, pal, undo);
            case "mountain" -> buildMountain(target, size, pal, undo);
            case "wall" -> buildWall(target, p.getFacing(), size, height, pal, undo);
            case "crater" -> buildCrater(target, size, undo);
            case "tower" -> buildTower(target, size, height, pal, undo);
            case "sphere" -> buildSphere(target, size, pal, undo);
            case "brush" -> buildBrush(target, size, pal, undo);
            default -> {
                return;
            }
        }

        if (undo.isEmpty()) {
            msg(p, "Hier wurde nichts veraendert.", NamedTextColor.YELLOW);
            return;
        }
        Deque<List<BlockState>> stack = undoMap.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        stack.push(undo);
        while (stack.size() > MAX_UNDO) stack.removeLast();

        msg(p, undo.size() + " Bloecke veraendert. (/terra undo zum Rueckgaengigmachen)", NamedTextColor.GREEN);
    }

    // ---------------------------------------------------------------- Formen

    /** Runder Huegel (Kuppel). size = Radius. */
    private void buildHill(Block center, int radius, List<Material> pal, List<BlockState> undo) {
        double maxH = Math.max(2, radius / 2.0);
        int baseY = center.getY() + 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double ratio = Math.sqrt(dx * dx + dz * dz) / radius;
                if (ratio > 1) continue;
                int h = (int) Math.round(maxH * (1 - ratio * ratio));
                for (int i = 0; i < h; i++) {
                    Block b = center.getWorld().getBlockAt(center.getX() + dx, baseY + i, center.getZ() + dz);
                    Material m = pal != null ? pick(pal) : (i == h - 1 ? Material.GRASS_BLOCK : Material.DIRT);
                    place(b, m, undo);
                }
            }
        }
    }

    /** Zackiger Berg mit Schneespitze. size = Radius. */
    private void buildMountain(Block center, int radius, List<Material> pal, List<BlockState> undo) {
        double maxH = radius * 1.5;
        int baseY = center.getY() + 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double ratio = Math.sqrt(dx * dx + dz * dz) / radius;
                if (ratio > 1) continue;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                double noise = Math.sin(x * 0.7) * Math.cos(z * 0.6) + Math.sin((x + z) * 0.3);
                double hd = maxH * (1 - ratio) + noise * radius * 0.12 * (1 - ratio);
                int h = (int) Math.round(hd);
                for (int i = 0; i < h; i++) {
                    Block b = center.getWorld().getBlockAt(x, baseY + i, z);
                    Material m;
                    if (pal != null) m = pick(pal);
                    else if (i >= maxH * 0.8) m = Material.SNOW_BLOCK;
                    else if (i == h - 1 && ratio > 0.6) m = Material.GRASS_BLOCK;
                    else m = Material.STONE;
                    place(b, m, undo);
                }
            }
        }
    }

    /** Mauer quer zur Blickrichtung. size = Laenge. */
    private void buildWall(Block start, BlockFace facing, int length, int height, List<Material> pal, List<BlockState> undo) {
        boolean alongX = (facing == BlockFace.NORTH || facing == BlockFace.SOUTH);
        int baseY = start.getY() + 1;
        for (int i = -length / 2; i < length - length / 2; i++) {
            for (int y = 0; y < height; y++) {
                int x = start.getX() + (alongX ? i : 0);
                int z = start.getZ() + (alongX ? 0 : i);
                Material m = pal != null ? pick(pal) : Material.STONE_BRICKS;
                place(start.getWorld().getBlockAt(x, baseY + y, z), m, undo);
            }
        }
    }

    /** Krater: Schale wird in den Boden gegraben. size = Radius (Tiefe = Radius/2). */
    private void buildCrater(Block center, int radius, List<BlockState> undo) {
        double maxD = Math.max(2, radius / 2.0);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double ratio = Math.sqrt(dx * dx + dz * dz) / radius;
                if (ratio > 1) continue;
                int d = (int) Math.round(maxD * (1 - ratio * ratio));
                for (int i = 0; i < d; i++) {
                    Block b = center.getWorld().getBlockAt(center.getX() + dx, center.getY() - i, center.getZ() + dz);
                    remove(b, undo);
                }
                // Ueberstehendes ueber dem Kraterrand entfernen (z.B. Baeume/Gras)
                Block above = center.getWorld().getBlockAt(center.getX() + dx, center.getY() + 1, center.getZ() + dz);
                if (above.isReplaceable()) remove(above, undo);
            }
        }
    }

    /** Runder, hohler Turm mit Zinnen. size = Radius, hoehe = Hoehe (Standard 15). */
    private void buildTower(Block center, int radius, int height, List<Material> pal, List<BlockState> undo) {
        int baseY = center.getY() + 1;
        double outer = radius + 0.5;
        double inner = radius - 0.5;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > outer) continue;
                boolean isWall = dist >= inner || radius <= 2;
                boolean isFloor = true; // Boden bei y=0
                for (int y = 0; y < height; y++) {
                    boolean top = (y == height - 1);
                    boolean wallHere = isWall && !(top && ((dx + dz) & 1) != 0);
                    boolean floorHere = (y == 0) && isFloor;
                    if (wallHere || floorHere) {
                        Material m = pal != null ? pick(pal) : Material.STONE_BRICKS;
                        place(center.getWorld().getBlockAt(center.getX() + dx, baseY + y, center.getZ() + dz), m, undo);
                    }
                }
            }
        }
    }

    /** Volle Kugel, sitzt auf dem angeklickten Block. size = Radius. */
    private void buildSphere(Block base, int radius, List<Material> pal, List<BlockState> undo) {
        int cy = base.getY() + 1 + radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius + radius * 0.5) continue;
                    Material m = pal != null ? pick(pal) : Material.STONE;
                    place(base.getWorld().getBlockAt(base.getX() + dx, cy + dy, base.getZ() + dz), m, undo);
                }
            }
        }
    }

    /** Pinsel: faerbt/ersetzt alles (ausser Luft) in einer Kugel mit zufaelligen Bloecken. size = Radius. */
    private void buildBrush(Block center, int radius, List<Material> pal, List<BlockState> undo) {
        List<Material> palette = pal != null ? pal
                : List.of(Material.STONE, Material.COBBLESTONE, Material.ANDESITE, Material.GRAVEL);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    Block b = center.getWorld().getBlockAt(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    Material t = b.getType();
                    if (t.isAir() || t == Material.BEDROCK || b.isLiquid() || !t.isSolid()) continue;
                    undo.add(b.getState());
                    b.setType(pick(palette), false);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Hilfen

    private Material pick(List<Material> pal) {
        return pal.get(ThreadLocalRandom.current().nextInt(pal.size()));
    }

    /** "stone,dirt" -> Liste. Gibt null zurueck, wenn ein Eintrag kein Block ist. */
    private List<Material> parsePalette(String s) {
        List<Material> list = new ArrayList<>();
        for (String part : s.split(",")) {
            if (part.isBlank()) continue;
            Material m = Material.matchMaterial(part.trim());
            if (m == null || !m.isBlock() || m.isAir()) return null;
            list.add(m);
        }
        return list.isEmpty() ? null : list;
    }

    private void place(Block b, Material m, List<BlockState> undo) {
        if (!(b.getType().isAir() || b.isReplaceable() || b.isLiquid())) return;
        undo.add(b.getState());
        b.setType(m, false);
    }

    private void remove(Block b, List<BlockState> undo) {
        Material t = b.getType();
        if (t.isAir() || t == Material.BEDROCK) return;
        undo.add(b.getState());
        b.setType(Material.AIR, false);
    }

    private void undo(Player p) {
        Deque<List<BlockState>> stack = undoMap.get(p.getUniqueId());
        if (stack == null || stack.isEmpty()) {
            msg(p, "Nichts zum Rueckgaengigmachen.", NamedTextColor.YELLOW);
            return;
        }
        List<BlockState> states = stack.pop();
        for (int i = states.size() - 1; i >= 0; i--) {
            states.get(i).update(true, false);
        }
        msg(p, states.size() + " Bloecke wiederhergestellt.", NamedTextColor.GREEN);
    }

    private void msg(Player p, String text, NamedTextColor color) {
        p.sendMessage(Component.text("[TerraStick] ", NamedTextColor.GOLD).append(Component.text(text, color)));
    }
}
