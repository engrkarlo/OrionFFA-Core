package com.karlo.orionffa.arena;
import com.karlo.orionffa.config.LocationConfig;
import java.io.File;
@FunctionalInterface public interface SchematicService { void paste(File schematic, LocationConfig target) throws Exception; }
