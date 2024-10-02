package com.example.examplemod;

import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChestRegistry {
    private ConcurrentHashMap<BlockPos, Boolean> playerPlacedChests;
    private ConcurrentHashMap<BlockPos, Boolean> modModifiedChests;
    private Path memory;

    ChestRegistry(Path configDir) throws IOException {
        Path memory = configDir.resolve("chests.memo");
        List<String> lines = Files.readAllLines(memory);
        ArrayList<ConcurrentHashMap<BlockPos, Boolean>> result = parsePositions(lines);
        this.playerPlacedChests = result.get(0);
        this.modModifiedChests = result.get(1);
        this.memory = memory;
    }

    private ArrayList<ConcurrentHashMap<BlockPos, Boolean>> parsePositions(List<String> lines) {
        ArrayList<ConcurrentHashMap<BlockPos, Boolean>> rtn = new ArrayList<>();
        ConcurrentHashMap<BlockPos, Boolean> playerPlacedChests = new ConcurrentHashMap<>();
        ConcurrentHashMap<BlockPos, Boolean> modModifiedChests = new ConcurrentHashMap<>();

        for (String line : lines) {
            char type = line.charAt(0);
            BlockPos position = parseCoordinate(line.substring(1));
            if (type == 'P') {
                playerPlacedChests.put(position, true);
            }else{
                modModifiedChests.put(position, true);
            }
        }
        rtn.add(playerPlacedChests);
        rtn.add(modModifiedChests);
        return rtn;
    }

    private BlockPos parseCoordinate(String position) {
        String[] coordinates = position.split(",");
        int x = Integer.parseInt(coordinates[0]);
        int y = Integer.parseInt(coordinates[1]);
        int z = Integer.parseInt(coordinates[2]);
        return new BlockPos(x, y, z);
    }

    public boolean isPlayerPlaced(BlockPos position) {
        return this.playerPlacedChests.containsKey(position);
    }

    public boolean hasBeenAlteredByMod(BlockPos position) {
        return this.modModifiedChests.containsKey(position);
    }

    public void saveData() throws IOException {
        ArrayList<String> positions = new ArrayList<>();
        for(BlockPos position : playerPlacedChests.keySet()) {
            int x = position.getX();
            int y = position.getY();
            int z = position.getZ();
            positions.add(String.format("P%s,%s,%s", x, y, z));
        }

        for(BlockPos position : modModifiedChests.keySet()) {
            int x = position.getX();
            int y = position.getY();
            int z = position.getZ();
            positions.add(String.format("M%s,%s,%s", x, y, z));
        }

        Files.writeString(memory, String.join("\n", positions));
    }

    public void removeChest(BlockPos position) {
        this.playerPlacedChests.remove(position);
        this.modModifiedChests.remove(position);
    }

    public void placePlayerChest(BlockPos position) {
        this.playerPlacedChests.put(position, true);
    }

    public void addModifiedChest(BlockPos position) {
        this.modModifiedChests.put(position, true);
    }
}
