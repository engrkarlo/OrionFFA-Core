package com.karlo.orionffa.arena;
import com.karlo.orionffa.config.LocationConfig;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import java.io.File;
import java.io.FileInputStream;
public final class WorldEditSchematicService implements SchematicService {
    @Override public void paste(File file, LocationConfig target) throws Exception {
        ClipboardFormat format=ClipboardFormats.findByFile(file); if(format==null) throw new IllegalArgumentException("Unsupported schematic format");
        Clipboard clipboard; try(ClipboardReader reader=format.getReader(new FileInputStream(file))){clipboard=reader.read();}
        Location location=target.resolve().orElseThrow(()->new IllegalStateException("Arena world is unavailable"));
        try(EditSession edit=WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(location.getWorld()))){
            Operation op=new ClipboardHolder(clipboard).createPaste(edit).to(BlockVector3.at(location.getBlockX(),location.getBlockY(),location.getBlockZ())).ignoreAirBlocks(false).build();
            Operations.complete(op); edit.flushSession();
        }
    }
}
