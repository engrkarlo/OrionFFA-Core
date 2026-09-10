package com.karlo.orionffa.arena;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class WorldEditSchematicService implements SchematicService {
    private final ClassLoader providerClassLoader;
    private final boolean asyncCapable;

    public WorldEditSchematicService(ClassLoader providerClassLoader, boolean asyncCapable) {
        this.providerClassLoader = providerClassLoader;
        this.asyncCapable = asyncCapable;
    }

    @Override
    public boolean asyncCapable() { return asyncCapable; }

    @Override
    public boolean giveSelectionWand(Player player) {
        PermissionAttachment attachment = player.addAttachment(findOwningPlugin(player), "worldedit.wand", true);
        try {
            boolean shape = player.performCommand("//sel cuboid");
            boolean wand = player.performCommand("//wand");
            return shape && wand;
        } finally {
            player.removeAttachment(attachment);
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
    public CompletableFuture<Void> saveSelection(Player player, File schematic) {
        final SelectionContext selection;
        try { selection = selectionContext(player); }
        catch (Exception exception) { return CompletableFuture.failedFuture(new IllegalStateException("FAWE selection is unavailable", exception)); }
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
