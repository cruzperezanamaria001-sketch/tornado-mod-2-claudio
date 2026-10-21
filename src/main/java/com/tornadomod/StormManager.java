package com.tornadomod;

import com.tornadomod.entity.StormEntity;
import com.tornadomod.entity.TornadoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Crea tornados/tormentas (comando y aparicion natural). */
public final class StormManager {
    private StormManager() {
    }

    /** Crea un fenomeno en la posicion dada. Devuelve null si no se pudo. */
    public static Entity spawn(ServerLevel sl, StormType type, int ef, Vec3 pos) {
        RandomSource r = sl.random;
        if (type.isTornado()) {
            TornadoEntity t = ModEntities.TORNADO.get().create(sl);
            if (t == null) {
                return null;
            }
            int min = Config.MIN_LIFE_SECONDS.get();
            int max = Math.max(min, Config.MAX_LIFE_SECONDS.get());
            int life = (min + r.nextInt(max - min + 1)) * 20;
            t.setup(type, ef, life);
            t.setPos(pos.x, pos.y, pos.z);
            sl.addFreshEntity(t);
            return t;
        }
        StormEntity s = ModEntities.STORM.get().create(sl);
        if (s == null) {
            return null;
        }
        int life = (type == StormType.CYCLONE ? 9000 : 6000) + r.nextInt(5000);
        s.setup(type, ef, life);
        s.setPos(pos.x, pos.y, pos.z);
        sl.addFreshEntity(s);
        return s;
    }

    /** Aparicion natural durante tormentas electricas (cada 10 s). */
    public static void tickLevel(ServerLevel sl) {
        if (!Config.NATURAL_STORMS.get() || sl.dimension() != Level.OVERWORLD) {
            return;
        }
        if (sl.getGameTime() % 200L != 0L || !sl.isThundering()) {
            return;
        }
        List<ServerPlayer> players = sl.players();
        if (players.isEmpty()) {
            return;
        }
        List<? extends StormEntity> storms = sl.getEntities(ModEntities.STORM.get(), e -> true);
        if (storms.size() >= Config.MAX_STORMS.get()) {
            return;
        }
        if (sl.random.nextDouble() > Config.NATURAL_CHANCE.get()) {
            return;
        }
        ServerPlayer p = players.get(sl.random.nextInt(players.size()));
        double ang = sl.random.nextDouble() * Math.PI * 2.0D;
        double dist = 80.0D + sl.random.nextDouble() * 30.0D;
        double x = p.getX() + Math.cos(ang) * dist;
        double z = p.getZ() + Math.sin(ang) * dist;
        BlockPos bp = BlockPos.containing(x, p.getY(), z);
        if (!sl.hasChunkAt(bp)) {
            return;
        }
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
        StormType type = sl.random.nextFloat() < 0.15F ? StormType.CYCLONE : StormType.SUPERCELL;
        int ef = 1 + sl.random.nextInt(3) + (sl.random.nextFloat() < 0.1F ? 1 : 0);
        spawn(sl, type, ef, new Vec3(x, y, z));
    }
}
