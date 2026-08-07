package io.github.minazukisora.rollercart.util;

import io.github.minazukisora.rollercart.block.TrackTiesBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class TrackSnapUtil {

    public static final Map<Direction, Map<Integer, Direction>> POINTING_MAP = new HashMap<>();

    static {
        Map<Integer, Direction> upMap = new HashMap<>();
        upMap.put(0, Direction.NORTH);
        upMap.put(1, Direction.WEST);
        upMap.put(2, Direction.SOUTH);
        upMap.put(3, Direction.EAST);
        Map<Integer, Direction> downMap = new HashMap<>();
        downMap.put(2, Direction.NORTH);
        downMap.put(1, Direction.WEST);
        downMap.put(0, Direction.SOUTH);
        downMap.put(3, Direction.EAST);
        Map<Integer, Direction> northMap = new HashMap<>();
        northMap.put(0, Direction.DOWN);
        northMap.put(1, Direction.WEST);
        northMap.put(2, Direction.UP);
        northMap.put(3, Direction.EAST);
        Map<Integer, Direction> eastMap = new HashMap<>();
        eastMap.put(1, Direction.NORTH);
        eastMap.put(2, Direction.UP);
        eastMap.put(3, Direction.SOUTH);
        eastMap.put(0, Direction.DOWN);
        Map<Integer, Direction> southMap = new HashMap<>();
        southMap.put(2, Direction.UP);
        southMap.put(3, Direction.WEST);
        southMap.put(0, Direction.DOWN);
        southMap.put(1, Direction.EAST);
        Map<Integer, Direction> westMap = new HashMap<>();
        westMap.put(3, Direction.NORTH);
        westMap.put(0, Direction.DOWN);
        westMap.put(1, Direction.SOUTH);
        westMap.put(2, Direction.UP);
        POINTING_MAP.put(Direction.UP, upMap);
        POINTING_MAP.put(Direction.DOWN, downMap);
        POINTING_MAP.put(Direction.NORTH, northMap);
        POINTING_MAP.put(Direction.EAST, eastMap);
        POINTING_MAP.put(Direction.SOUTH, southMap);
        POINTING_MAP.put(Direction.WEST, westMap);
    }
    
    public static BlockPos snapToTrackOnExit(World w, BlockPos from, BlockState state, boolean reverse) {
        Direction facing = state.get(TrackTiesBlock.FACING);
        int pointing = state.get(TrackTiesBlock.POINTING);
        Direction towards = POINTING_MAP.getOrDefault(facing, Map.of()).get(pointing);
        if(towards == null) return null;
        if(reverse) towards = towards.getOpposite();
        BlockPos fwPos = from.offset(towards);
        BlockState fwState = w.getBlockState(fwPos);
        if(!fwState.isIn(BlockTags.RAILS)) return null;
        if(!(fwState.getBlock() instanceof AbstractRailBlock b)) return null;
        if(!railShapeContainsDir(fwState.get(b.getShapeProperty()), towards)) return null;
        return fwPos;
    }

    public static boolean railShapeContainsDir(RailShape shape, Direction dir) {
        return switch(shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> switch(dir) {
                case NORTH, SOUTH -> true;
                default -> false;
            };
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> switch(dir) {
                case EAST, WEST -> true;
                default -> false;
            };
            case SOUTH_EAST -> switch(dir) {
                case SOUTH, EAST -> true;
                default -> false;
            };
            case SOUTH_WEST -> switch(dir) {
                case SOUTH, WEST -> true;
                default -> false;
            };
            case NORTH_WEST -> switch(dir) {
                case NORTH, WEST -> true;
                default -> false;
            };
            case NORTH_EAST -> switch(dir) {
                case NORTH, EAST -> true;
                default -> false;
            };
        };
    }
    
}
