package com.karlo.orionffa.arena;

import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * WorldEdit/FAWE schematic adapter loaded through the provider plugin's classloader.
 * This is deliberately reflective so a standalone WorldEdit and FAWE installation
 * cannot cause OrionFFA's own classloader to bind to the wrong provider.
 */
public final class WorldEditSchematicService implements SchematicService {
    private final ClassLoader providerClassLoader;
    private final boolean asyncCapable;

    public WorldEditSchematicService(ClassLoader providerClassLoader, boolean asyncCapable) {
        this.providerClassLoader = providerClassLoader;
        this.asyncCapable = asyncCapable;
    }

    @Override
    public boolean asyncCapable() {
        return asyncCapable;
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
        if (format == null || !clipboardFormat.isInstance(format)) {
            throw new IllegalArgumentException("Unsupported schematic format: " + file.getName());
        }

        Object loadedClipboard;
        try (FileInputStream input = new FileInputStream(file)) {
            Method getReader = clipboardFormat.getMethod("getReader", InputStream.class);
            Object reader = getReader.invoke(format, input);
            try {
                loadedClipboard = clipboardReader.getMethod("read").invoke(reader);
            } finally {
                if (reader instanceof AutoCloseable closeable) closeable.close();
            }
        }

        Object worldEditInstance = worldEdit.getMethod("getInstance").invoke(null);
        Object adaptedWorld = bukkitAdapter.getMethod("adapt", org.bukkit.World.class).invoke(null, target.getWorld());
        Object session = worldEdit.getMethod("newEditSession", load("com.sk89q.worldedit.world.World"))
                .invoke(worldEditInstance, adaptedWorld);

        try {
            Constructor<?> holderConstructor = clipboardHolder.getConstructor(clipboard);
            Object holder = holderConstructor.newInstance(loadedClipboard);
            Object vector = blockVector3.getMethod("at", int.class, int.class, int.class)
                    .invoke(null, target.getBlockX(), target.getBlockY(), target.getBlockZ());

            Object builder = clipboardHolder.getMethod("createPaste", load("com.sk89q.worldedit.extent.Extent"))
                    .invoke(holder, session);
            builder = builder.getClass().getMethod("to", blockVector3).invoke(builder, vector);
            builder = builder.getClass().getMethod("ignoreAirBlocks", boolean.class).invoke(builder, false);
            Object builtOperation = builder.getClass().getMethod("build").invoke(builder);
            operations.getMethod("complete", operation).invoke(null, builtOperation);
        } finally {
            editSession.getMethod("close").invoke(session);
        }
    }

    private Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, providerClassLoader);
    }
}
