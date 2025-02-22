package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.api.PowerNukkitDifference;
import cn.nukkit.api.PowerNukkitOnly;
import cn.nukkit.api.Since;
import cn.nukkit.block.*;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityMoveByPistonEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.IntTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.Faceable;
import cn.nukkit.utils.RedstoneComponent;

import java.util.ArrayList;
import java.util.List;

import static cn.nukkit.utils.Utils.dynamic;

/**
 * @author CreeperFace
 */
@PowerNukkitDifference(info = "The piston will work as close as possible to vanilla")
public class BlockEntityPistonArm extends BlockEntitySpawnable {

    @PowerNukkitOnly
    public static final float MOVE_STEP = dynamic(0.5f);

    public float progress;
    public float lastProgress = 1;

    public BlockFace facing;

    public boolean powered;

    public boolean extending;

    public BlockPistonBase.BlocksCalculator blocksCalculator;

    public boolean sticky;

    @Since("FUTURE")
    public byte state;

    @Since("FUTURE")
    public byte newState = 1;

    @PowerNukkitOnly
    public List<BlockVector3> attachedBlocks;

    @PowerNukkitOnly
    @Since("1.4.0.0-PN")
    public boolean finished = true;

    public BlockEntityPistonArm(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        if (namedTag.contains("Progress")) {
            this.progress = namedTag.getFloat("Progress");
        }

        if (namedTag.contains("LastProgress")) {
            this.lastProgress = (float) namedTag.getInt("LastProgress");
        }

        this.sticky = namedTag.getBoolean("Sticky");
        this.extending = namedTag.getBoolean("Extending");
        this.powered = namedTag.getBoolean("powered");

        if (namedTag.contains("facing")) {
            this.facing = BlockFace.fromIndex(namedTag.getInt("facing"));
        } else {
            Block b = this.getLevelBlock();

            if (b instanceof Faceable) {
                this.facing = ((Faceable) b).getBlockFace();
            } else {
                this.facing = BlockFace.NORTH;
            }
        }

        attachedBlocks = new ArrayList<>();

        if (namedTag.contains("AttachedBlocks")) {
            ListTag blocks = namedTag.getList("AttachedBlocks", IntTag.class);
            if (blocks != null && blocks.size() > 0) {
                for (int i = 0; i < blocks.size(); i += 3) {
                    this.attachedBlocks.add(new BlockVector3(
                            ((IntTag) blocks.get(i)).data,
                            ((IntTag) blocks.get(i + 1)).data,
                            ((IntTag) blocks.get(i + 1)).data
                    ));
                }
            }
        } else {
            namedTag.putList(new ListTag<>("AttachedBlocks"));
        }

        super.initBlockEntity();
    }

    private void moveCollidedEntities() {
        BlockFace pushDir = this.extending ? facing : facing.getOpposite();
        for (BlockVector3 pos : this.attachedBlocks) {
            BlockEntity blockEntity = this.level.getBlockEntity(pos.getSide(pushDir));

            if (blockEntity instanceof BlockEntityMovingBlock) {
                ((BlockEntityMovingBlock) blockEntity).moveCollidedEntities(this, pushDir);
            }
        }

        AxisAlignedBB bb = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1).getOffsetBoundingBox(
                this.x + (pushDir.getXOffset() * progress),
                this.y + (pushDir.getYOffset() * progress),
                this.z + (pushDir.getZOffset() * progress)
        );

        Entity[] entities = this.level.getCollidingEntities(bb);

        for (Entity entity : entities) {
            moveEntity(entity, pushDir);
        }
    }

    void moveEntity(Entity entity, BlockFace moveDirection) {
        if (!entity.canBePushed()) {
            return;
        }

        EntityMoveByPistonEvent event = new EntityMoveByPistonEvent(entity, entity.getPosition());
        this.level.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        if (entity instanceof Player) {
            return;
        }

        entity.onPushByPiston(this);

        if (!entity.closed) {
            float diff = Math.abs(this.progress - this.lastProgress);

            entity.move(
                    diff * moveDirection.getXOffset(),
                    diff * moveDirection.getYOffset(),
                    diff * moveDirection.getZOffset()
            );
        }
    }

    @PowerNukkitOnly
    public void move(boolean extending, List<BlockVector3> attachedBlocks, BlockPistonBase.BlocksCalculator blocksCalculator) {
        this.blocksCalculator = blocksCalculator;
        this.extending = extending;
        this.lastProgress = this.progress = extending ? 0 : 1;
        this.state = this.newState = (byte) (extending ? 1 : 3);
        this.attachedBlocks = attachedBlocks;
        this.movable = false;
        this.finished = false;

        this.level.addChunkPacket(getChunkX(), getChunkZ(), getSpawnPacket());
        this.lastProgress = extending ? -MOVE_STEP : 1 + MOVE_STEP;
        this.setDirty();
        this.moveCollidedEntities();
        this.scheduleUpdate();
    }

    @Override
    @PowerNukkitDifference(info = "Add option to see if blockentity is currently handling piston move (var finished)" +
            "+ update around redstone directly after moved block set", since = "1.4.0.0-PN")
    public boolean onUpdate() {
        boolean hasUpdate = true;

        if (this.extending) {
            this.progress = Math.min(1, this.progress + MOVE_STEP);
            this.lastProgress = Math.min(1, this.lastProgress + MOVE_STEP);
        } else {
            this.progress = Math.max(0, this.progress - MOVE_STEP);
            this.lastProgress = Math.max(0, this.lastProgress - MOVE_STEP);
        }

        this.moveCollidedEntities();

        if (this.progress == this.lastProgress) {
            this.state = this.newState = (byte) (extending ? 2 : 0);

            BlockFace pushDir = this.extending ? facing : facing.getOpposite();

            for (BlockVector3 pos : this.attachedBlocks) {
                BlockEntity movingBlock = this.level.getBlockEntity(pos.getSide(pushDir));

                if (movingBlock instanceof BlockEntityMovingBlock) {
                    movingBlock.close();
                    Block moved = ((BlockEntityMovingBlock) movingBlock).getMovingBlock();

                    CompoundTag blockEntity = ((BlockEntityMovingBlock) movingBlock).getMovingBlockEntityCompound();

                    if (blockEntity != null) {
                        blockEntity.putInt("x", movingBlock.getFloorX());
                        blockEntity.putInt("y", movingBlock.getFloorY());
                        blockEntity.putInt("z", movingBlock.getFloorZ());
                        BlockEntity.createBlockEntity(blockEntity.getString("id"), this.level.getChunk(movingBlock.getChunkX(), movingBlock.getChunkZ()), blockEntity);
                    }

                    if (this.level.setBlock(movingBlock, moved)) {
                        moved.onUpdate(Level.BLOCK_UPDATE_MOVED);
                        RedstoneComponent.updateAroundRedstone(moved);
                    }
                }
            }

            if (!extending) {
                if (this.level.getBlock(getSide(facing)).getId() == (sticky ? BlockID.PISTON_HEAD_STICKY : BlockID.PISTON_ARM_COLLISION)) {
                    this.level.setBlock(getSide(facing), new BlockAir());
                }
                this.movable = true;
            }

            this.attachedBlocks.clear();
            hasUpdate = false;
            this.finished = true;
            this.blocksCalculator.unlockBlocks();
            this.blocksCalculator.getLockedBlocks().forEach(BlockPistonBase::updatePistonsListenTo);
            this.blocksCalculator.getLockedBlocks().forEach(pos -> {
                this.level.scheduleUpdate(pos.getLevelBlock(), 1);
                if (pos.getSide(BlockFace.UP).getLevelBlock() instanceof BlockFallableMeta){
                    this.level.scheduleUpdate(pos.getSide(BlockFace.UP).getLevelBlock(), 1);
                }
            });
        }

        if (level != null) {
            this.level.addChunkPacket(getChunkX(), getChunkZ(), getSpawnPacket());
        }else{
            return true;
        }

        return super.onUpdate() || hasUpdate;
    }

    private float getExtendedProgress(float progress) {
        return this.extending ? progress - 1 : 1 - progress;
    }

    @Override
    public boolean isBlockEntityValid() {
        int id = getLevelBlock().getId();
        return id == BlockID.PISTON || id == BlockID.STICKY_PISTON;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putByte("State", this.state);
        this.namedTag.putByte("NewState", this.newState);
        this.namedTag.putFloat("Progress", this.progress);
        this.namedTag.putFloat("LastProgress", this.lastProgress);
        this.namedTag.putBoolean("powered", this.powered);
        this.namedTag.putList(getAttachedBlocks());
        this.namedTag.putInt("facing", this.facing.getIndex());
    }

    @Override
    public CompoundTag getSpawnCompound() {
        return new CompoundTag()
                .putString("id", BlockEntity.PISTON_ARM)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putFloat("Progress", this.progress)
                .putFloat("LastProgress", this.lastProgress)
                .putBoolean("isMovable", this.movable)
                .putList(getAttachedBlocks())
                .putList(new ListTag<>("BreakBlocks"))
                .putBoolean("Sticky", this.sticky)
                .putByte("State", this.state)
                .putByte("NewState", this.newState);
    }

    private ListTag<IntTag> getAttachedBlocks() {
        ListTag<IntTag> attachedBlocks = new ListTag<>("AttachedBlocks");
        for (BlockVector3 block : this.attachedBlocks) {
            attachedBlocks.add(new IntTag("", block.x));
            attachedBlocks.add(new IntTag("", block.y));
            attachedBlocks.add(new IntTag("", block.z));
        }

        return attachedBlocks;
    }
}
