package com.plotsquared.bukkit.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.SkullType;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.block.Jukebox;
import org.bukkit.block.NoteBlock;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.generator.AugmentedUtils;
import com.intellectualcrafters.plot.object.BlockLoc;
import com.intellectualcrafters.plot.object.ChunkLoc;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotArea;
import com.intellectualcrafters.plot.object.PlotBlock;
import com.intellectualcrafters.plot.object.PlotLoc;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.object.RegionWrapper;
import com.intellectualcrafters.plot.object.RunnableVal;
import com.intellectualcrafters.plot.util.ChunkManager;
import com.intellectualcrafters.plot.util.PlotChunk;
import com.intellectualcrafters.plot.util.SetQueue;
import com.intellectualcrafters.plot.util.TaskManager;
import com.intellectualcrafters.plot.util.UUIDHandler;
import com.intellectualcrafters.plot.util.WorldUtil;
import com.plotsquared.bukkit.object.entity.EntityWrapper;

public class BukkitChunkManager extends ChunkManager {
    @Override
    public Set<ChunkLoc> getChunkChunks(final String world) {
        Set<ChunkLoc> chunks = super.getChunkChunks(world);
        for (final Chunk chunk : Bukkit.getWorld(world).getLoadedChunks()) {
            final ChunkLoc loc = new ChunkLoc(chunk.getX() >> 5, chunk.getZ() >> 5);
            if (!chunks.contains(loc)) {
                chunks.add(loc);
            }
        }
        return chunks;
    }
    
    @Override
    public void regenerateChunk(final String world, final ChunkLoc loc) {
        final World worldObj = Bukkit.getWorld(world);
        worldObj.regenerateChunk(loc.x, loc.z);
        SetQueue.IMP.queue.sendChunk(world, Collections.singletonList(loc));
        for (Entry<String, PlotPlayer> entry : UUIDHandler.getPlayers().entrySet()) {
            PlotPlayer pp = entry.getValue();
            Location ploc = pp.getLocation();
            if (!ploc.getChunkLoc().equals(loc)) {
                continue;
            }
            PlotBlock plotblock = WorldUtil.IMP.getBlock(ploc);
            if (plotblock.id != 0) {
                Plot plot = pp.getCurrentPlot();
                pp.teleport(plot.getDefaultHome());
            }
        }
    }
    
    private static HashMap<BlockLoc, ItemStack[]> chestContents;
    private static HashMap<BlockLoc, ItemStack[]> furnaceContents;
    private static HashMap<BlockLoc, ItemStack[]> dispenserContents;
    private static HashMap<BlockLoc, ItemStack[]> dropperContents;
    private static HashMap<BlockLoc, ItemStack[]> brewingStandContents;
    private static HashMap<BlockLoc, ItemStack[]> beaconContents;
    private static HashMap<BlockLoc, ItemStack[]> hopperContents;
    private static HashMap<BlockLoc, Short[]> furnaceTime;
    private static HashMap<BlockLoc, Object[]> skullData;
    private static HashMap<BlockLoc, Short> jukeDisc;
    private static HashMap<BlockLoc, Short> brewTime;
    private static HashMap<BlockLoc, String> spawnerData;
    private static HashMap<BlockLoc, String> cmdData;
    private static HashMap<BlockLoc, String[]> signContents;
    private static HashMap<BlockLoc, Note> noteBlockContents;
    private static HashMap<BlockLoc, ArrayList<Byte[]>> bannerColors;
    private static HashMap<BlockLoc, Byte> bannerBase;
    private static HashSet<EntityWrapper> entities;
    private static HashMap<PlotLoc, PlotBlock[]> allblocks;
    
    @Override
    public boolean copyRegion(final Location pos1, final Location pos2, final Location newPos, final Runnable whenDone) {
        final int relX = newPos.getX() - pos1.getX();
        final int relZ = newPos.getZ() - pos1.getZ();
        
        final int relCX = relX >> 4;
        final int relCZ = relZ >> 4;
        
        final RegionWrapper region = new RegionWrapper(pos1.getX(), pos2.getX(), pos1.getZ(), pos2.getZ());
        final World oldWorld = Bukkit.getWorld(pos1.getWorld());
        final World newWorld = Bukkit.getWorld(newPos.getWorld());
        final String newWorldname = newWorld.getName();
        final ArrayList<ChunkLoc> chunks = new ArrayList<>();
        
        ChunkManager.chunkTask(pos1, pos2, new RunnableVal<int[]>() {
            @Override
            public void run(int[] value) {
                initMaps();

                final int bx = value[2];
                final int bz = value[3];
                
                final int tx = value[4];
                final int tz = value[5];

                // Load chunks
                final ChunkLoc loc1 = new ChunkLoc(value[0], value[1]);
                final ChunkLoc loc2 = new ChunkLoc(loc1.x + relCX, loc1.z + relCZ);
                final Chunk c1 = oldWorld.getChunkAt(loc1.x, loc1.z);
                final Chunk c2 = newWorld.getChunkAt(loc2.x, loc2.z);
                c1.load(true);
                c2.load(true);
                chunks.add(loc2);
                // entities
                saveEntitiesIn(c1, region);
                // copy chunk
                setChunkInPlotArea(null, new RunnableVal<PlotChunk<?>>() {
                    @Override
                    public void run(PlotChunk<?> value) {
                        for (int x = (bx & 15); x <= (tx & 15); x++) {
                            for (int z = (bz & 15); z <= (tz & 15); z++) {
                                for (int y = 1; y < 256; y++) {
                                    Block block = c1.getBlock(x, y, z);
                                    int id = block.getTypeId();
                                    switch (id) {
                                        case 0:
                                        case 2:
                                        case 4:
                                        case 13:
                                        case 14:
                                        case 15:
                                        case 20:
                                        case 21:
                                        case 22:
                                        case 30:
                                        case 32:
                                        case 37:
                                        case 39:
                                        case 40:
                                        case 41:
                                        case 42:
                                        case 45:
                                        case 46:
                                        case 47:
                                        case 48:
                                        case 49:
                                        case 51:
                                        case 55:
                                        case 56:
                                        case 57:
                                        case 58:
                                        case 60:
                                        case 7:
                                        case 8:
                                        case 9:
                                        case 10:
                                        case 11:
                                        case 73:
                                        case 74:
                                        case 78:
                                        case 79:
                                        case 80:
                                        case 81:
                                        case 82:
                                        case 83:
                                        case 85:
                                        case 87:
                                        case 88:
                                        case 101:
                                        case 102:
                                        case 103:
                                        case 110:
                                        case 112:
                                        case 113:
                                        case 121:
                                        case 122:
                                        case 129:
                                        case 133:
                                        case 165:
                                        case 166:
                                        case 169:
                                        case 170:
                                        case 172:
                                        case 173:
                                        case 174:
                                        case 181:
                                        case 182:
                                        case 188:
                                        case 189:
                                        case 190:
                                        case 191:
                                        case 192: {
                                            value.setBlock(x, y, z, id, (byte) 0);
                                            break;
                                        }
                                        default: {
                                            value.setBlock(x, y, z, id, block.getData());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }, newWorldname, loc2);
                // restore chunk
                restoreBlocks(newWorld, relX, relZ);
                restoreEntities(newWorld, relX, relZ);
            }
        }, new Runnable() {
            @Override
            public void run() {
                SetQueue.IMP.queue.sendChunk(newWorldname, chunks);
                TaskManager.runTask(whenDone);
            }
        }, 5);
        return true;
    }
    
    public void saveRegion(final World world, int x1, int x2, int z1, int z2) {
        if (z1 > z2) {
            final int tmp = z1;
            z1 = z2;
            z2 = tmp;
        }
        if (x1 > x2) {
            final int tmp = x1;
            x1 = x2;
            x2 = tmp;
        }
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                saveBlocks(world, 256, x, z, 0, 0, true);
            }
        }
    }
    
    @Override
    public boolean regenerateRegion(final Location pos1, final Location pos2, final boolean ignoreAugment, final Runnable whenDone) {
        final String world = pos1.getWorld();
        
        final int p1x = pos1.getX();
        final int p1z = pos1.getZ();
        final int p2x = pos2.getX();
        final int p2z = pos2.getZ();
        final int bcx = p1x >> 4;
        final int bcz = p1z >> 4;
        final int tcx = p2x >> 4;
        final int tcz = p2z >> 4;
        
        final ArrayList<ChunkLoc> chunks = new ArrayList<ChunkLoc>();
        
        for (int x = bcx; x <= tcx; x++) {
            for (int z = bcz; z <= tcz; z++) {
                chunks.add(new ChunkLoc(x, z));
            }
        }
        final World worldObj = Bukkit.getWorld(world);
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                final long start = System.currentTimeMillis();
                while ((chunks.size() > 0) && ((System.currentTimeMillis() - start) < 5)) {
                    final ChunkLoc chunk = chunks.remove(0);
                    final int x = chunk.x;
                    final int z = chunk.z;
                    final int xxb = x << 4;
                    final int zzb = z << 4;
                    final int xxt = xxb + 15;
                    final int zzt = zzb + 15;
                    final Chunk chunkObj = worldObj.getChunkAt(x, z);
                    if (!chunkObj.load(false)) {
                        continue;
                    }
                    RegionWrapper currentPlotClear = new RegionWrapper(pos1.getX(), pos2.getX(), pos1.getZ(), pos2.getZ());
                    if ((xxb >= p1x) && (xxt <= p2x) && (zzb >= p1z) && (zzt <= p2z)) {
                        AugmentedUtils.bypass(ignoreAugment, new Runnable() {
                            @Override
                            public void run() {
                                regenerateChunk(world, chunk);
                            }
                        });
                        continue;
                    }
                    boolean checkX1 = false;
                    boolean checkX2 = false;
                    boolean checkZ1 = false;
                    boolean checkZ2 = false;
                    
                    int xxb2;
                    int zzb2;
                    int xxt2;
                    int zzt2;
                    
                    if (x == bcx) {
                        xxb2 = p1x - 1;
                        checkX1 = true;
                    } else {
                        xxb2 = xxb;
                    }
                    if (x == tcx) {
                        xxt2 = p2x + 1;
                        checkX2 = true;
                    } else {
                        xxt2 = xxt;
                    }
                    if (z == bcz) {
                        zzb2 = p1z - 1;
                        checkZ1 = true;
                    } else {
                        zzb2 = zzb;
                    }
                    if (z == tcz) {
                        zzt2 = p2z + 1;
                        checkZ2 = true;
                    } else {
                        zzt2 = zzt;
                    }
                    initMaps();
                    if (checkX1) {
                        saveRegion(worldObj, xxb, xxb2, zzb2, zzt2); //
                    }
                    if (checkX2) {
                        saveRegion(worldObj, xxt2, xxt, zzb2, zzt2); //
                    }
                    if (checkZ1) {
                        saveRegion(worldObj, xxb2, xxt2, zzb, zzb2); //
                    }
                    if (checkZ2) {
                        saveRegion(worldObj, xxb2, xxt2, zzt2, zzt); //
                    }
                    if (checkX1 && checkZ1) {
                        saveRegion(worldObj, xxb, xxb2, zzb, zzb2); //
                    }
                    if (checkX2 && checkZ1) {
                        saveRegion(worldObj, xxt2, xxt, zzb, zzb2); // ?
                    }
                    if (checkX1 && checkZ2) {
                        saveRegion(worldObj, xxb, xxb2, zzt2, zzt); // ?
                    }
                    if (checkX2 && checkZ2) {
                        saveRegion(worldObj, xxt2, xxt, zzt2, zzt); //
                    }
                    saveEntitiesOut(chunkObj, currentPlotClear);
                    AugmentedUtils.bypass(ignoreAugment, new Runnable() {
                        @Override
                        public void run() {
                            setChunkInPlotArea(null, new RunnableVal<PlotChunk<?>>() {
                                @Override
                                public void run(PlotChunk<?> value) {
                                    int cx = value.getX();
                                    int cz = value.getZ();
                                    int bx = cx << 4;
                                    int bz = cz << 4;
                                    for (int x = 0; x < 16; x++) {
                                        for (int z = 0; z < 16; z++) {
                                            PlotLoc loc = new PlotLoc(bx + x, bz + z);
                                            PlotBlock[] ids = allblocks.get(loc);
                                            if (ids != null) {
                                                for (int y = 0; y < Math.min(128, ids.length); y++) {
                                                    PlotBlock id = ids[y];
                                                    if (id != null) {
                                                        value.setBlock(x, y, z, id);
                                                    } else {
                                                        value.setBlock(x, y, z, 0, (byte) 0);
                                                    }
                                                }
                                                for (int y = Math.min(128, ids.length); y < ids.length; y++) {
                                                    PlotBlock id = ids[y];
                                                    if (id != null) {
                                                        value.setBlock(x, y, z, id);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }, world, chunk);
                        }
                    });
                    restoreBlocks(worldObj, 0, 0);
                    restoreEntities(worldObj, 0, 0);
                }
                if (chunks.size() != 0) {
                    TaskManager.runTaskLater(this, 1);
                } else {
                    TaskManager.runTaskLater(whenDone, 1);
                }
            }
        });
        return true;
    }
    
    public static void initMaps() {
        chestContents = new HashMap<>();
        furnaceContents = new HashMap<>();
        dispenserContents = new HashMap<>();
        dropperContents = new HashMap<>();
        brewingStandContents = new HashMap<>();
        beaconContents = new HashMap<>();
        hopperContents = new HashMap<>();
        furnaceTime = new HashMap<>();
        skullData = new HashMap<>();
        brewTime = new HashMap<>();
        jukeDisc = new HashMap<>();
        spawnerData = new HashMap<>();
        noteBlockContents = new HashMap<>();
        signContents = new HashMap<>();
        cmdData = new HashMap<>();
        bannerBase = new HashMap<>();
        bannerColors = new HashMap<>();
        entities = new HashSet<>();
        allblocks = new HashMap<>();
    }
    
    public static boolean isIn(final RegionWrapper region, final int x, final int z) {
        return ((x >= region.minX) && (x <= region.maxX) && (z >= region.minZ) && (z <= region.maxZ));
    }
    
    public static void saveEntitiesOut(final Chunk chunk, final RegionWrapper region) {
        for (final Entity entity : chunk.getEntities()) {
            final Location loc = BukkitUtil.getLocation(entity);
            final int x = loc.getX();
            final int z = loc.getZ();
            if (isIn(region, x, z)) {
                continue;
            }
            if (entity.getVehicle() != null) {
                continue;
            }
            final EntityWrapper wrap = new EntityWrapper(entity, (short) 2);
            entities.add(wrap);
        }
    }
    
    public static void saveEntitiesIn(final Chunk chunk, final RegionWrapper region) {
        saveEntitiesIn(chunk, region, 0, 0, false);
    }
    
    public static void saveEntitiesIn(final Chunk chunk, final RegionWrapper region, final int offset_x, final int offset_z, final boolean delete) {
        for (final Entity entity : chunk.getEntities()) {
            final Location loc = BukkitUtil.getLocation(entity);
            final int x = loc.getX();
            final int z = loc.getZ();
            if (!isIn(region, x, z)) {
                continue;
            }
            if (entity.getVehicle() != null) {
                continue;
            }
            final EntityWrapper wrap = new EntityWrapper(entity, (short) 2);
            wrap.x += offset_x;
            wrap.z += offset_z;
            entities.add(wrap);
            if (delete) {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            }
        }
    }
    
    public static void restoreEntities(final World world, final int x_offset, final int z_offset) {
        for (final EntityWrapper entity : entities) {
            try {
                entity.spawn(world, x_offset, z_offset);
            } catch (final Exception e) {
                PS.debug("Failed to restore entity (e): " + entity.x + "," + entity.y + "," + entity.z + " : " + entity.id + " : " + EntityType.fromId(entity.id));
                e.printStackTrace();
            }
        }
        entities.clear();
    }
    
    public static void restoreBlocks(final World world, final int x_offset, final int z_offset) {
        for (final BlockLoc loc : chestContents.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Chest) {
                    final Chest chest = (Chest) state;
                    chest.getInventory().setContents(chestContents.get(loc));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to regenerate chest: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate chest (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : signContents.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Sign) {
                    final Sign sign = (Sign) state;
                    int i = 0;
                    for (final String line : signContents.get(loc)) {
                        sign.setLine(i, line);
                        i++;
                    }
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to regenerate sign: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate sign: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : dispenserContents.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Dispenser) {
                    ((Dispenser) (state)).getInventory().setContents(dispenserContents.get(loc));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to regenerate dispenser: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate dispenser (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : dropperContents.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Dropper) {
                    ((Dropper) (state)).getInventory().setContents(dropperContents.get(loc));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to regenerate dispenser: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate dispenser (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : beaconContents.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Beacon) {
                    ((Beacon) (state)).getInventory().setContents(beaconContents.get(loc));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to regenerate beacon: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate beacon (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : jukeDisc.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Jukebox) {
                    ((Jukebox) (state)).setPlaying(Material.getMaterial(jukeDisc.get(loc)));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to restore jukebox: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate jukebox (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : skullData.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Skull) {
                    final Object[] data = skullData.get(loc);
                    if (data[0] != null) {
                        ((Skull) (state)).setOwner((String) data[0]);
                    }
                    if (((Integer) data[1]) != 0) {
                        ((Skull) (state)).setRotation(BlockFace.values()[(int) data[1]]);
                    }
                    if (((Integer) data[2]) != 0) {
                        ((Skull) (state)).setSkullType(SkullType.values()[(int) data[2]]);
                    }
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to restore skull: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate skull (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : hopperContents.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Hopper) {
                    ((Hopper) (state)).getInventory().setContents(hopperContents.get(loc));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to regenerate hopper: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate hopper (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : noteBlockContents.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof NoteBlock) {
                    ((NoteBlock) (state)).setNote(noteBlockContents.get(loc));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to regenerate note block: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate note block (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : brewTime.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof BrewingStand) {
                    ((BrewingStand) (state)).setBrewingTime(brewTime.get(loc));
                } else {
                    PS.debug("&c[WARN] Plot clear failed to restore brewing stand cooking: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to restore brewing stand cooking (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : spawnerData.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof CreatureSpawner) {
                    ((CreatureSpawner) (state)).setCreatureTypeId(spawnerData.get(loc));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to restore spawner type: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to restore spawner type (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : cmdData.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof CommandBlock) {
                    ((CommandBlock) (state)).setCommand(cmdData.get(loc));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to restore command block: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to restore command block (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : brewingStandContents.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof BrewingStand) {
                    ((BrewingStand) (state)).getInventory().setContents(brewingStandContents.get(loc));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to regenerate brewing stand: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate brewing stand (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : furnaceTime.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Furnace) {
                    final Short[] time = furnaceTime.get(loc);
                    ((Furnace) (state)).setBurnTime(time[0]);
                    ((Furnace) (state)).setCookTime(time[1]);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to restore furnace cooking: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to restore furnace cooking (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : furnaceContents.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Furnace) {
                    ((Furnace) (state)).getInventory().setContents(furnaceContents.get(loc));
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to regenerate furnace: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate furnace (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
        for (final BlockLoc loc : bannerBase.keySet()) {
            try {
                final Block block = world.getBlockAt(loc.x + x_offset, loc.y, loc.z + z_offset);
                final BlockState state = block.getState();
                if (state instanceof Banner) {
                    final Banner banner = (Banner) state;
                    final byte base = bannerBase.get(loc);
                    final ArrayList<Byte[]> colors = bannerColors.get(loc);
                    banner.setBaseColor(DyeColor.values()[base]);
                    for (final Byte[] color : colors) {
                        banner.addPattern(new Pattern(DyeColor.getByDyeData(color[1]), PatternType.values()[color[0]]));
                    }
                    state.update(true);
                } else {
                    PS.debug("&c[WARN] Plot clear failed to regenerate banner: " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
                }
            } catch (final Exception e) {
                PS.debug("&c[WARN] Plot clear failed to regenerate banner (e): " + (loc.x + x_offset) + "," + (loc.y) + "," + (loc.z + z_offset));
            }
        }
    }
    
    public static void saveBlocks(final World world, int maxY, final int x, final int z, final int offset_x, final int offset_z, boolean storeNormal) {
        maxY = Math.min(255, maxY);
        PlotBlock[] ids = storeNormal ? new PlotBlock[maxY + 1] : null;
        for (short y = 0; y <= maxY; y++) {
            final Block block = world.getBlockAt(x, y, z);
            final short id = (short) block.getTypeId();
            if (id != 0) {
                if (storeNormal) {
                    ids[y] = new PlotBlock(id, block.getData());
                }
                BlockLoc bl;
                try {
                    switch (id) {
                        case 54:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final InventoryHolder chest = (InventoryHolder) block.getState();
                            final ItemStack[] inventory = chest.getInventory().getContents().clone();
                            chestContents.put(bl, inventory);
                            break;
                        case 52:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final CreatureSpawner spawner = (CreatureSpawner) block.getState();
                            final String type = spawner.getCreatureTypeId();
                            if ((type != null) && (type.length() != 0)) {
                                spawnerData.put(bl, type);
                            }
                            break;
                        case 137:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final CommandBlock cmd = (CommandBlock) block.getState();
                            final String string = cmd.getCommand();
                            if ((string != null) && (string.length() > 0)) {
                                cmdData.put(bl, string);
                            }
                            break;
                        case 63:
                        case 68:
                        case 323:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final Sign sign = (Sign) block.getState();
                            sign.getLines();
                            signContents.put(bl, sign.getLines().clone());
                            break;
                        case 61:
                        case 62:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final Furnace furnace = (Furnace) block.getState();
                            final short burn = furnace.getBurnTime();
                            final short cook = furnace.getCookTime();
                            final ItemStack[] invFur = furnace.getInventory().getContents().clone();
                            furnaceContents.put(bl, invFur);
                            if (cook != 0) {
                                furnaceTime.put(bl, new Short[] { burn, cook });
                            }
                            break;
                        case 23:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final Dispenser dispenser = (Dispenser) block.getState();
                            final ItemStack[] invDis = dispenser.getInventory().getContents().clone();
                            dispenserContents.put(bl, invDis);
                            break;
                        case 158:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final Dropper dropper = (Dropper) block.getState();
                            final ItemStack[] invDro = dropper.getInventory().getContents().clone();
                            dropperContents.put(bl, invDro);
                            break;
                        case 117:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final BrewingStand brewingStand = (BrewingStand) block.getState();
                            final short time = (short) brewingStand.getBrewingTime();
                            if (time > 0) {
                                brewTime.put(bl, time);
                            }
                            final ItemStack[] invBre = brewingStand.getInventory().getContents().clone();
                            brewingStandContents.put(bl, invBre);
                            break;
                        case 25:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final NoteBlock noteBlock = (NoteBlock) block.getState();
                            final Note note = noteBlock.getNote();
                            noteBlockContents.put(bl, note);
                            break;
                        case 138:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final Beacon beacon = (Beacon) block.getState();
                            final ItemStack[] invBea = beacon.getInventory().getContents().clone();
                            beaconContents.put(bl, invBea);
                            break;
                        case 84:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final Jukebox jukebox = (Jukebox) block.getState();
                            final Material playing = jukebox.getPlaying();
                            if (playing != null) {
                                jukeDisc.put(bl, (short) playing.getId());
                            }
                            break;
                        case 154:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final Hopper hopper = (Hopper) block.getState();
                            final ItemStack[] invHop = hopper.getInventory().getContents().clone();
                            hopperContents.put(bl, invHop);
                            break;
                        case 397:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final Skull skull = (Skull) block.getState();
                            final String o = skull.getOwner();
                            final byte skulltype = getOrdinal(SkullType.values(), skull.getSkullType());
                            skull.getRotation();
                            final short rot = getOrdinal(BlockFace.values(), skull.getRotation());
                            skullData.put(bl, new Object[] { o, rot, skulltype });
                            break;
                        case 176:
                        case 177:
                            bl = new BlockLoc(x + offset_x, y, z + offset_z);
                            final Banner banner = (Banner) block.getState();
                            final byte base = getOrdinal(DyeColor.values(), banner.getBaseColor());
                            final ArrayList<Byte[]> types = new ArrayList<>();
                            for (final Pattern pattern : banner.getPatterns()) {
                                types.add(new Byte[] { getOrdinal(PatternType.values(), pattern.getPattern()), pattern.getColor().getDyeData() });
                            }
                            bannerBase.put(bl, base);
                            bannerColors.put(bl, types);
                            break;
                    }
                } catch (final Exception e) {
                    PS.debug("------------ FAILED TO DO SOMETHING --------");
                    e.printStackTrace();
                    PS.debug("------------ but we caught it ^ --------");
                }
            }
        }
        final PlotLoc loc = new PlotLoc(x, z);
        allblocks.put(loc, ids);
    }
    
    private static byte getOrdinal(final Object[] list, final Object value) {
        for (byte i = 0; i < list.length; i++) {
            if (list[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }
    
    @Override
    public void clearAllEntities(final Location pos1, final Location pos2) {
        final String world = pos1.getWorld();
        final List<Entity> entities = BukkitUtil.getEntities(world);
        final int bx = pos1.getX();
        final int bz = pos1.getZ();
        final int tx = pos2.getX();
        final int tz = pos2.getZ();
        for (final Entity entity : entities) {
            if (entity instanceof Player) {
                final org.bukkit.Location loc = entity.getLocation();
                if ((loc.getX() >= bx) && (loc.getX() <= tx) && (loc.getZ() >= bz) && (loc.getZ() <= tz)) {
                    final Player player = (Player) entity;
                    final PlotPlayer pp = BukkitUtil.getPlayer(player);
                    final Plot plot = pp.getCurrentPlot();
                    if (plot != null) {
                        final Location plotHome = plot.getDefaultHome();
                        if (pp.getLocation().getY() <= plotHome.getY()) {
                            pp.teleport(plotHome);
                        }
                    }
                }
            } else {
                final org.bukkit.Location loc = entity.getLocation();
                if ((loc.getX() >= bx) && (loc.getX() <= tx) && (loc.getZ() >= bz) && (loc.getZ() <= tz)) {
                    entity.remove();
                }
            }
        }
    }
    
    @Override
    public boolean loadChunk(final String world, final ChunkLoc loc, final boolean force) {
        return BukkitUtil.getWorld(world).getChunkAt(loc.x, loc.z).load(force);
    }
    
    @Override
    public void unloadChunk(final String world, final ChunkLoc loc, final boolean save, final boolean safe) {
        if (!PS.get().isMainThread(Thread.currentThread())) {
            TaskManager.runTask(new Runnable() {
                @Override
                public void run() {
                    BukkitUtil.getWorld(world).unloadChunk(loc.x, loc.z, save, safe);
                }
            });
        }
        else {
            BukkitUtil.getWorld(world).unloadChunk(loc.x, loc.z, save, safe);
        }
    }
    
    public static void swapChunk(final World world1, final World world2, final Chunk pos1, final Chunk pos2, final RegionWrapper r1, final RegionWrapper r2) {
        initMaps();
        final int relX = (r2.minX - r1.minX);
        final int relZ = (r2.minZ - r1.minZ);
        
        saveEntitiesIn(pos1, r1, relX, relZ, true);
        saveEntitiesIn(pos2, r2, -relX, -relZ, true);
        
        final int sx = pos1.getX() << 4;
        final int sz = pos1.getZ() << 4;
        
        String worldname1 = world1.getName();
        String worldname2 = world2.getName();

        for (int x = Math.max(r1.minX, sx); x <= Math.min(r1.maxX, sx + 15); x++) {
            for (int z = Math.max(r1.minZ, sz); z <= Math.min(r1.maxZ, sz + 15); z++) {
                saveBlocks(world1, 256, sx, sz, relX, relZ, false);
                for (int y = 0; y < 256; y++) {
                    final Block block1 = world1.getBlockAt(x, y, z);
                    final int id1 = block1.getTypeId();
                    final byte data1 = block1.getData();
                    final int xx = x + relX;
                    final int zz = z + relZ;
                    final Block block2 = world2.getBlockAt(xx, y, zz);
                    final int id2 = block2.getTypeId();
                    final byte data2 = block2.getData();
                    if (id1 == 0) {
                        if (id2 != 0) {
                            SetQueue.IMP.setBlock(worldname1, x, y, z, (short) id2, data2);
                            SetQueue.IMP.setBlock(worldname2, xx, y, zz, (short) 0, (byte) 0);
                        }
                    } else if (id2 == 0) {
                        SetQueue.IMP.setBlock(worldname1, x, y, z, (short) 0, (byte) 0);
                        SetQueue.IMP.setBlock(worldname2, xx, y, zz, (short) id1, data1);
                    } else if (id1 == id2) {
                        if (data1 != data2) {
                            block1.setData(data2);
                            block2.setData(data1);
                        }
                    } else {
                        SetQueue.IMP.setBlock(worldname1, x, y, z, (short) id2, data2);
                        SetQueue.IMP.setBlock(worldname2, xx, y, zz, (short) id1, data1);
                    }
                }
            }
        }
        while (SetQueue.IMP.forceChunkSet());
        restoreBlocks(world1, 0, 0);
        restoreEntities(world1, 0, 0);
    }
    
    @Override
    public void swap(final Location bot1, final Location top1, final Location bot2, final Location top2, final Runnable whenDone) {
        final RegionWrapper region1 = new RegionWrapper(bot1.getX(), top1.getX(), bot1.getZ(), top1.getZ());
        final RegionWrapper region2 = new RegionWrapper(bot2.getX(), top2.getX(), bot2.getZ(), top2.getZ());
        final World world1 = Bukkit.getWorld(bot1.getWorld());
        final World world2 = Bukkit.getWorld(bot2.getWorld());
        
        final int relX = bot2.getX() - bot1.getX();
        final int relZ = bot2.getZ() - bot1.getZ();
        
        for (int x = bot1.getX() >> 4; x <= (top1.getX() >> 4); x++) {
            for (int z = bot1.getZ() >> 4; z <= (top1.getZ() >> 4); z++) {
                final Chunk chunk1 = world1.getChunkAt(x, z);
                final Chunk chunk2 = world2.getChunkAt(x + (relX >> 4), z + (relZ >> 4));
                swapChunk(world1, world2, chunk1, chunk2, region1, region2);
            }
        }
        TaskManager.runTaskLater(whenDone, 1);
    }
    
    @Override
    public int[] countEntities(final Plot plot) {
        final int[] count = new int[6];
        PlotArea area = plot.area;
        final World world = BukkitUtil.getWorld(area.worldname);
        
        final Location bot = plot.getBottomAbs();
        final Location top = plot.getTopAbs();
        final int bx = bot.getX() >> 4;
        final int bz = bot.getZ() >> 4;
        
        final int tx = top.getX() >> 4;
        final int tz = top.getZ() >> 4;
        
        final int size = (tx - bx) << 4;
        
        final HashSet<Chunk> chunks = new HashSet<>();
        for (int X = bx; X <= tx; X++) {
            for (int Z = bz; Z <= tz; Z++) {
                chunks.add(world.getChunkAt(X, Z));
            }
        }
        
        boolean doWhole = false;
        List<Entity> entities = null;
        if (size > 200) {
            entities = world.getEntities();
            if (entities.size() < (16 + ((size * size) / 64))) {
                doWhole = true;
            }
        }
        
        if (doWhole) {
            for (final Entity entity : entities) {
                final org.bukkit.Location loc = entity.getLocation();
                final Chunk chunk = loc.getChunk();
                if (chunks.contains(chunk)) {
                    final int X = chunk.getX();
                    final int Z = chunk.getX();
                    if ((X > bx) && (X < tx) && (Z > bz) && (Z < tz)) {
                        count(count, entity);
                    } else {
                        Plot other = area.getPlot(BukkitUtil.getLocation(loc));
                        if (plot.equals(other)) {
                            count(count, entity);
                        }
                    }
                }
            }
        } else {
            for (final Chunk chunk : chunks) {
                final int X = chunk.getX();
                final int Z = chunk.getX();
                final Entity[] ents = chunk.getEntities();
                for (final Entity entity : ents) {
                    if ((X == bx) || (X == tx) || (Z == bz) || (Z == tz)) {
                        Plot other = area.getPlot(BukkitUtil.getLocation(entity));
                        if (plot.equals(other)) {
                            count(count, entity);
                        }
                    } else {
                        count(count, entity);
                    }
                }
            }
        }
        return count;
    }
    
    private void count(final int[] count, final Entity entity) {
        switch (entity.getType()) {
            case PLAYER: {
                // not valid
                return;
            }
            case SMALL_FIREBALL:
            case FIREBALL:
            case DROPPED_ITEM:
            case EGG:
            case THROWN_EXP_BOTTLE:
            case SPLASH_POTION:
            case SNOWBALL:
            case ENDER_PEARL:
            case ARROW: {
                // projectile
            }
            case PRIMED_TNT:
            case FALLING_BLOCK: {
                // Block entities 
            }
            case ENDER_CRYSTAL:
            case COMPLEX_PART:
            case FISHING_HOOK:
            case ENDER_SIGNAL:
            case EXPERIENCE_ORB:
            case LEASH_HITCH:
            case FIREWORK:
            case WEATHER:
            case LIGHTNING:
            case WITHER_SKULL:
            case UNKNOWN: {
                // non moving / unremovable
                break;
            }
            case ITEM_FRAME:
            case PAINTING:
            case ARMOR_STAND: {
                count[5]++;
                // misc
            }
            case MINECART:
            case MINECART_CHEST:
            case MINECART_COMMAND:
            case MINECART_FURNACE:
            case MINECART_HOPPER:
            case MINECART_MOB_SPAWNER:
            case MINECART_TNT:
            case BOAT: {
                count[4]++;
                break;
            }
            case RABBIT:
            case SHEEP:
            case MUSHROOM_COW:
            case OCELOT:
            case PIG:
            case HORSE:
            case SQUID:
            case VILLAGER:
            case IRON_GOLEM:
            case WOLF:
            case CHICKEN:
            case COW:
            case SNOWMAN:
            case BAT: {
                // animal
                count[3]++;
                count[1]++;
                break;
            }
            case BLAZE:
            case CAVE_SPIDER:
            case CREEPER:
            case ENDERMAN:
            case ENDERMITE:
            case ENDER_DRAGON:
            case GHAST:
            case GIANT:
            case GUARDIAN:
            case MAGMA_CUBE:
            case PIG_ZOMBIE:
            case SILVERFISH:
            case SKELETON:
            case SLIME:
            case SPIDER:
            case WITCH:
            case WITHER:
            case ZOMBIE: {
                // monster
                count[3]++;
                count[2]++;
                break;
            }
            default: {
                if (entity instanceof Creature) {
                    count[3]++;
                    if (entity instanceof Animals) {
                        count[1]++;
                    } else {
                        count[2]++;
                    }
                } else {
                    count[4]++;
                }
            }
        }
        count[0]++;
    }
}
