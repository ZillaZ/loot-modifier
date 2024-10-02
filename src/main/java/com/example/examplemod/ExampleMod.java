package com.example.examplemod;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;

class ItemRng {
    public String item;
    public Integer minStack;
    public Integer maxStack;
    public Integer rng;
}

class PickedItem {
    public String item;
    public Integer amount;
    public Integer index;
    PickedItem(String item, Integer amount, Integer index) {
        this.item = item;
        this.amount = amount;
        this.index = index;
    }
}

class Chest {
    public Integer rng;
    public List<ItemRng> itemsRng;
    public Integer slotRng;
}

class Chests {
    public Chest commonChest;
    public Chest civilChest;
    public Chest militaryChest;
    public Chest medicChest;
    public Chest foodChest;
}

class Barrels {
    public Chest commonBarrel;
    public Chest rareBarrel;
}

@Mod("examplemod")
public class ExampleMod
{
    public static final Logger LOGGER = LogManager.getLogger();
    private final ConcurrentHashMap<BlockPos, Boolean> chestsToProcess = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BlockPos, Boolean> processed = new ConcurrentHashMap<>();
    private Chests document = null;
    private Barrels barrels = null;
    private final Random rand = new Random();
    private final ChestRegistry connection = new ChestRegistry(FMLPaths.CONFIGDIR.get());

    public ExampleMod() throws IOException {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::blockPlaceEvent);
        MinecraftForge.EVENT_BUS.addListener(this::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(this::blockBreakEvent);
        MinecraftForge.EVENT_BUS.addListener(this::onServerShutdown);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Hello Forge World!");

        String path = Paths.get("").toAbsolutePath().toString();
        LOGGER.info(path);

        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            Path filePath = configDir.resolve("rng.json");
            String json = new String(Files.readAllBytes(filePath));
            document = new Gson().fromJson(json, Chests.class);

            Path barrelPath = configDir.resolve("barrel.json");
            String barrelJson = new String(Files.readAllBytes(barrelPath));
            barrels = new Gson().fromJson(barrelJson, Barrels.class);
        } catch (Exception e) {
            LOGGER.error("Error loading chest/barrel JSON {}", e.getMessage());
        }

        event.getServer().getCommands().getDispatcher().register(Commands.literal("reload_chests")
                .executes(context -> {
                    Path configDir = FMLPaths.CONFIGDIR.get();
                    Path filePath = configDir.resolve("rng.json");
                    try {
                        String json = new String(Files.readAllBytes(filePath));
                        document = new Gson().fromJson(json, Chests.class);

                        Path barrelPath = configDir.resolve("barrel.json");
                        String barrelJson = new String(Files.readAllBytes(barrelPath));
                        barrels = new Gson().fromJson(barrelJson, Barrels.class);
                    }catch(Exception e) {
                        LOGGER.info(e.getMessage());
                    }
                    ServerLevel world = context.getSource().getLevel();
                    reloadChests(processed.keySet(), world);
                    context.getSource().sendSuccess(() -> Component.literal("Reloaded " + processed.size() + " chests."), true);
                    return processed.size();
                }));
    }

    @SubscribeEvent
    public void onServerShutdown(ServerStoppingEvent event) {
        try {
            connection.saveData();
        }catch(Exception e) {
            LOGGER.info(e.getMessage());
        }
    }

    private void reloadChests(Set<BlockPos> positions, ServerLevel world) {
        for (BlockPos position : positions) {
            BlockEntity entity = world.getBlockEntity(position);
            if (entity instanceof ChestBlockEntity chest) {
                regenerateChest(chest);
            }
            if (entity instanceof  BarrelBlockEntity barrel) {
                regenerateBarrel(barrel);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side.isServer() && event.phase == TickEvent.Phase.END) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            ServerLevel world = server.overworld();

            for (BlockPos position : chestsToProcess.keySet()) {
                BlockEntity entity = world.getBlockEntity(position);
                if(entity instanceof ChestBlockEntity blockEntity) {
                    regenerateChest(blockEntity);
                    processed.put(position, true);
                }
                if(entity instanceof  BarrelBlockEntity barrel) {
                    regenerateBarrel(barrel);
                    processed.put(position, true);
                }
            }
            chestsToProcess.clear();
        }
    }

    @SubscribeEvent
    public void blockBreakEvent(BlockEvent.BreakEvent event) {
        BlockPos position = event.getPos();
        BlockEntity entity = event.getLevel().getBlockEntity(position);
        if (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity) {
            try{
                connection.removeChest(position);
            }catch(Exception e) {
                LOGGER.info(e.getMessage());
            }
        }
        processed.remove(event.getPos());
    }

    @SubscribeEvent
    public void blockPlaceEvent(BlockEvent.EntityPlaceEvent event) {
        if (!event.getLevel().isClientSide()) {
            BlockPos position = event.getPos();
            LevelAccessor world = event.getLevel();
            BlockEntity entity = world.getBlockEntity(position);
            if (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity) {
                try {
                    connection.placePlayerChest(position);
                }catch(Exception e) {
                    LOGGER.info(e.getMessage());
                }
            }
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if(event.getLevel().isClientSide()) return;
        ChunkAccess chunk = event.getChunk();

        for(BlockPos position : chunk.getBlockEntitiesPos()) {
            BlockEntity entity = chunk.getBlockEntity(position);
            if (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity) {
                try {
                    if(connection.isPlayerPlaced(position)) {
                        continue;
                    }
                    if (connection.hasBeenAlteredByMod(position)) {
                        processed.put(position, true);
                    }else{
                        chestsToProcess.put(position, true);
                        connection.addModifiedChest(position);
                    }
                }catch(Exception e) {
                    LOGGER.info(e.getMessage());
                }
            }
        }
    }

    private void regenerateChest(ChestBlockEntity chest) {
        chest.clearContent();
        Chest chestType = pickChest();
        if (chestType != null) {
            generateItems(chestType, chest);
        }
    }

    private void regenerateBarrel(BarrelBlockEntity barrel) {
        barrel.clearContent();
        Chest chestType = pickBarrel();
        if (chestType != null) {
            generateItems(chestType, barrel);
        }
    }

    private Chest pickBarrel() {
        if(barrels != null) {
            int total = barrels.commonBarrel.rng + barrels.rareBarrel.rng;
            int rng = rand.nextInt(total + 1);
            if (rng <= barrels.commonBarrel.rng) {
                return barrels.commonBarrel;
            }else{
                return barrels.rareBarrel;
            }
        }
        return null;
    }

    private Chest pickChest() {
        if (document != null) {
            int total = document.commonChest.rng + document.civilChest.rng + document.foodChest.rng + document.militaryChest.rng + document.medicChest.rng;
            int rng = rand.nextInt(total + 1);
            int aux = document.civilChest.rng;
            if (rng <= aux) {
                return document.commonChest;
            }
            aux += document.civilChest.rng;
            if (rng <= aux) {
                return document.civilChest;
            }
            aux += document.foodChest.rng;
            if (rng <= aux) {
                return document.foodChest;
            }
            aux += document.medicChest.rng;
            if (rng <= aux) {
                return document.medicChest;
            }
            aux += document.militaryChest.rng;
            if (rng <= aux) {
                return document.militaryChest;
            }
        }
        return null;
    }

    private void generateItems(Chest chest, ChestBlockEntity entity) {
        int size = entity.getContainerSize();
        for (int i = 0; i < size; i++) {
            int rng = rand.nextInt(100);
            if (rng < chest.slotRng) {
                PickedItem item = pickItem(chest.itemsRng);
                if (item != null) {
                    ItemStack stack = new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation(item.item))));
                    stack.setCount(item.amount);
                    entity.setItem(i, stack);
                }
            }
        }
    }

    private void generateItems(Chest chest, BarrelBlockEntity entity) {
        int size = entity.getContainerSize();
        for (int i = 0; i < size; i++) {
            int rng = rand.nextInt(100);
            if (rng < chest.slotRng) {
                PickedItem item = pickItem(chest.itemsRng);
                if (item != null) {
                    ItemStack stack = new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation(item.item))));
                    stack.setCount(item.amount);
                    entity.setItem(i, stack);
                }
            }
        }
    }

    public PickedItem pickItem(List<ItemRng> items) {
        if(items != null && items.isEmpty()) return null;
        int total = items.stream().map(element -> element.rng).reduce(Integer::sum).get();
        int itemRng = rand.nextInt(total + 1);
        int aux = 0;
        for (int index = 0; index < items.size(); index++) {
            ItemRng item = items.get(index);
            aux += item.rng;
            if (itemRng <= aux) {
                Integer randomAmount = ThreadLocalRandom.current().nextInt(item.minStack, item.maxStack);
                return new PickedItem(item.item, randomAmount, index);
            }
        }
        return null;
    }
}
