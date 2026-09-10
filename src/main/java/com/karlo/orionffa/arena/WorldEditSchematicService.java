package com.karlo.orionffa.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WorldEditSchematicService implements SchematicService {
    private static final String WAND_PERMISSION = "worldedit.wand";

    private final ClassLoader providerClassLoader;
    private final boolean asyncCapable;
    private final Map<UUID, PermissionAttachment> temporaryWandPermissions = new HashMap<>();
    private final java.util.Set<UUID> providedWands = java.util.HashSet<>();

    public WorldEditSchematicService(ClassLoader providerClassLoader, boolean asyncCapable) {
        this.providerClassLoader = providerClassLoader;
        this.asyncCapable = asyncCapable;
    }

    @Override
    public boolean asyncCapable() { return asyncCapable; }

    @Override
    public synchronized boolean giveSelectionWand(Player player) {
        final org.bukkit.plugin.Plugin provider;
        try {
            provider = findOwningPlugin(player);
            releaseSelectionWand(player);

            // OPs and players who already have the provider permission need no temporary grant.
            if (!player.hasPermission(WAND_PERMISSION)) {
                temporaryWandPermissions.put(player.getUniqueId(), player.addAttachment(provider, WAND_PERMISSION, true));
            }

            // Force cuboid selection mode, then ask the provider for its real configured wand.
            // Player#performCommand may return false even when the provider executed the command,
            // so command return values are deliberately not used as the success signal.
            player.performCommand("//sel cuboid");
            int woodenAxesBefore = countWoodenAxes(player);
            player.performCommand("//wand");
            int woodenAxesAfter = countWoodenAxes(player);

            // WorldEdit/FAWE uses a wooden axe by default. If a provider installation did not
            // materialize the wand item (for example because of command dispatch differences),
            // supply the same default wand ourselves so the selection workflow is never empty.
            if (woodenAxesAfter <= woodenAxesBefore && woodenAxesAfter == 0) {
                player.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
                woodenAxesAfter = countWoodenAxes(player);
            }
            if (woodenAxesAfter > woodenAxesBefore || woodenAxesAfter > 0) {
                providedWands.add(player.getUniqueId());
            }
            return true;
        } catch (RuntimeException failure) {
            releaseSelectionWand(player);
            return false;
        }
    }

    @Override
    public synchronized void releaseSelectionWand(Player player) {
        PermissionAttachment attachment = temporaryWandPermissions.remove(player.getUniqueId());
        if (attachment != null) player.removeAttachment(attachment);
        if (providedWands.remove(player.getUniqueId())) removeOneWoodenAxe(player);
    }

    private static int countWoodenAxes(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.WOODEN_AXE) count += item.getAmount();
        }
        return count;
    }

    private static void removeOneWoodenAxe(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() != Material.WOODEN_AXE) continue;
            if (item.getAmount() <= 1) player.getInventory().setItem(slot, null);
            else item.setAmount(item.getAmount() - 1);
            return;
        }
    }

    private org.bukkit.plugin.Plugin findOwningPlugin(Player player) {
        org.bukkit.plugin.PluginManager manager = player.getServer().getPluginManager();
        org.bukkit.plugin.Plugin fawe = manager.getPlugin("FastAsyncWorldEdit");
        if (fawe != null && fawe.isEnabled()) return fawe;
        org.bukkit.plugin.Plugin worldEdit = manager.getPlugin("WorldEdit");
        if (worldEdit != null && worldEdit.isEnabled()) return worldEdit;
        throw new IllegalStateException("WorldEdit/FAWE is not enabled");
    }

    @Override
    public Optional<ArenaSelection> captureSelection(Player player) {
        try {
            SelectionContext selection = selectionContext(player);
            Object min = invoke(selection.region(), "getMinimumPoint");
            Object max = invoke(selection.region(), "getMaximumPoint");
            return Optional.of(new ArenaSelection(player.getWorld().getName(), coordinate(min, "x"), coordinate(min, "y"), coordinate(min, "z"), coordinate(max, "x"), coordinate(max, "y"), coordinate(max, "z")));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized CompletableFuture<Void> saveSelection(Player player, File schematic) {
        final SelectionContext selection;
        try {
            selection = selectionContext(player);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(new IllegalStateException("FAWE selection is unavailable", exception));
        }

        // The provider selection has been captured. The temporary permission and workflow wand
        // can now be removed before the potentially asynchronous schematic write begins.
        releaseSelectionWand(player);

        Runnable operation = () -> {
            try { saveSelectionInternal(selection, schematic); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        };
        if (asyncCapable) return CompletableFuture.runAsync(operation);
        try { operation.run(); return CompletableFuture.completedFuture(null); }
        catch (RuntimeException exception) { return CompletableFuture.failedFuture(exception); }
    }

    @Override
    public void paste(File file, Location target) throws Exception {
        Class<?> clipboardFormats = load("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
        Class<?> clipboardFormat = load("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
        Class<?> clipboardReader = load("com.sk89q.worldedit.extent.clipboard.io.ClipboardReader");
        Class<?> clipboard = load("com.sk89q.worldedit.extent.clipboard.Clipboard");
        Class<?> worldEdit = load("com.sk89q.worldedit.WorldEdit");
        Class<?> bukkitAdapter = load("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Class<?> editSession = load("com.sk89q.worldedit.EditSession");
        Class<?> clipboardHolder = load("com.sk89q.worldedit.session.ClipboardHolder");
        Class<?> blockVector3 = load("com.sk89q.worldedit.math.BlockVector3");
        Class<?> operations = load("com.sk89q.worldedit.function.operation.Operations");
        Class<?> operation = load("com.sk89q.worldedit.function.operation.Operation");
        Object format = clipboardFormats.getMethod("findByFile", File.class).invoke(null, file);
        if (format == null || !clipboardFormat.isInstance(format)) throw new IllegalArgumentException("Unsupported schematic format: " + file.getName());
        Object loadedClipboard;
        try (FileInputStream input = new FileInputStream(file)) {
            Object reader = clipboardFormat.getMethod("getReader", InputStream.class).invoke(format, input);
            try { loadedClipboard = clipboardReader.getMethod("read").invoke(reader); }
            finally { if (reader instanceof AutoCloseable closeable) closeable.close(); }
        }
        Object worldEditInstance = worldEdit.getMethod("getInstance").invoke(null);
        Object adaptedWorld = bukkitAdapter.getMethod("adapt", org.bukkit.World.class).invoke(null, target.getWorld());
        Object session = worldEdit.getMethod("newEditSession", load("com.sk89q.worldedit.world.World")).invoke(worldEditInstance, adaptedWorld);
        try {
            Object holder = clipboardHolder.getConstructor(clipboard).newInstance(loadedClipboard);
            Object vector = blockVector3.getMethod("at", int.class, int.class, int.class).invoke(null, target.getBlockX(), target.getBlockY(), target.getBlockZ());
            Object builder = clipboardHolder.getMethod("createPaste", load("com.sk89q.worldedit.extent.Extent")).invoke(holder, session);
            builder = builder.getClass().getMethod("to", blockVector3).invoke(builder, vector);
            builder = builder.getClass().getMethod("ignoreAirBlocks", boolean.class).invoke(builder, false);
            Object builtOperation = builder.getClass().getMethod("build").invoke(builder);
            operations.getMethod("complete", operation).invoke(null, builtOperation);
        } finally { editSession.getMethod("close").invoke(session); }
    }

    private void saveSelectionInternal(SelectionContext selection, File schematic) throws Exception {
        File parent = schematic.getParentFile(); if (parent != null) parent.mkdirs();
        Class<?> clipboard = load("com.sk89q.worldedit.extent.clipboard.Clipboard");
        Class<?> blockArrayClipboard = load("com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard");
        Class<?> forwardExtentCopy = load("com.sk89q.worldedit.function.operation.ForwardExtentCopy");
        Class<?> blockVector3 = load("com.sk89q.worldedit.math.BlockVector3");
        Class<?> operations = load("com.sk89q.worldedit.function.operation.Operations");
        Class<?> operation = load("com.sk89q.worldedit.function.operation.Operation");
        Class<?> builtInFormat = load("com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat");
        Class<?> clipboardFormat = load("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
        Class<?> clipboardWriter = load("com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter");
        Object clipboardObject = blockArrayClipboard.getConstructor(load("com.sk89q.worldedit.regions.Region")).newInstance(selection.region());
        Object minimum = invoke(selection.region(), "getMinimumPoint");
        Constructor<?> copyConstructor = forwardExtentCopy.getConstructor(load("com.sk89q.worldedit.extent.Extent"), load("com.sk89q.worldedit.regions.Region"), load("com.sk89q.worldedit.extent.Extent"), blockVector3);
        Object copy = copyConstructor.newInstance(selection.world(), selection.region(), clipboardObject, minimum);
        invoke(copy, "setCopyingEntities", false); invoke(copy, "setCopyingBiomes", true);
        operations.getMethod("complete", operation).invoke(null, copy);
        Object format = java.lang.Enum.valueOf((Class) builtInFormat, "SPONGE_V3_SCHEMATIC");
        if (!clipboardFormat.isInstance(format)) throw new IllegalStateException("FAWE schematic format unavailable");
        try (OutputStream output = new FileOutputStream(schematic)) {
            Object writer = clipboardFormat.getMethod("getWriter", OutputStream.class).invoke(format, output);
            try { clipboardWriter.getMethod("write", clipboard).invoke(writer, clipboardObject); }
            finally { if (writer instanceof AutoCloseable closeable) closeable.close(); }
        }
    }

    private SelectionContext selectionContext(Player player) throws Exception {
        Class<?> worldEdit = load("com.sk89q.worldedit.WorldEdit");
        Class<?> bukkitAdapter = load("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Object worldEditInstance = worldEdit.getMethod("getInstance").invoke(null);
        Object sessionManager = worldEdit.getMethod("getSessionManager").invoke(worldEditInstance);
        Object actor = bukkitAdapter.getMethod("adapt", Player.class).invoke(null, player);
        Object localSession = findMethod(sessionManager.getClass(), "get", 1).invoke(sessionManager, actor);
        Object adaptedWorld = bukkitAdapter.getMethod("adapt", org.bukkit.World.class).invoke(null, player.getWorld());
        Object region = findMethod(localSession.getClass(), "getSelection", 1).invoke(localSession, adaptedWorld);
        return new SelectionContext(adaptedWorld, region);
    }

    private static int coordinate(Object vector, String accessor) throws Exception { return ((Number) vector.getClass().getMethod(accessor).invoke(vector)).intValue(); }
    private static Object invoke(Object target, String method, Object... args) throws Exception {
        for (Method candidate : target.getClass().getMethods()) if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) try { return candidate.invoke(target, args); } catch (IllegalArgumentException ignored) { }
        throw new NoSuchMethodException(method);
    }
    private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }
    private Class<?> load(String name) throws ClassNotFoundException { return Class.forName(name, true, providerClassLoader); }
    private record SelectionContext(Object world, Object region) { }
}
