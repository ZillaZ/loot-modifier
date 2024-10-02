package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class CustomChest extends ChestBlockEntity {
    public boolean isPlayerPlaced;
    public boolean alteredByMod;
    private static final String TAG_ALTERED_BY_MOD = "AlteredByMod";
    private static final String TAG_PLAYER_PLACED = "PlayerPlaced";

    public CustomChest(BlockPos p_155331_, BlockState p_155332_) {
        super(p_155331_, p_155332_);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        this.alteredByMod = tag.getBoolean(TAG_ALTERED_BY_MOD);
        this.isPlayerPlaced = tag.getBoolean(TAG_PLAYER_PLACED);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(TAG_ALTERED_BY_MOD, alteredByMod);
        tag.putBoolean(TAG_PLAYER_PLACED, isPlayerPlaced);
    }
}
