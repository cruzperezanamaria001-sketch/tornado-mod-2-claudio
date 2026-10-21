package com.tornadomod.entity;

import com.tornadomod.Config;
import com.tornadomod.ModEntities;
import com.tornadomod.StormType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Supercelula y ciclon.
 *
 * - SUPERCELULA: nube gigante que rota, con tormenta electrica. Crea tornados debajo (los tornados la siguen).
 * - CICLON: sistema enorme en espiral con ojo en el centro, vientos fuertisimos, lluvia y rayos.
 *
 * El servidor mueve la tormenta, fuerza el clima y empuja entidades; el cliente dibuja las nubes con particulas.
 */
public class StormEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_TYPE =
            SynchedEntityData.defineId(StormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EF =
            SynchedEntityData.defineId(StormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_GROWTH =
            SynchedEntityData.defineId(StormEntity.class, EntityDataSerializers.FLOAT);

    private static final int FORM_TICKS = 300;
    private static final int DIE_TICKS = 400;
    private static final double TWO_PI = Math.PI * 2.0D;

    private int age;
    private int lifespan = 9000;
    private float heading;
    private float speed = 0.1F;
    private int tornadoesSpawned;
    private int nextTornadoAge = 700;

    /** Velocidad actual (la leen los tornados hijos). */
    public double vx;
    public double vz;

    public StormEntity(EntityType<? extends StormEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(StormType type, int ef, int lifeTicks) {
        this.entityData.set(DATA_TYPE, type.ordinal());
        this.entityData.set(DATA_EF, Mth.clamp(ef, 0, 5));
        this.lifespan = Math.max(600, lifeTicks);
        this.heading = this.random.nextFloat() * (float) TWO_PI;
        this.speed = 0.06F + this.random.nextFloat() * 0.08F;
        this.nextTornadoAge = 600 + this.random.nextInt(600);
    }

    public StormType getStormType() {
        return StormType.byOrdinal(this.entityData.get(DATA_TYPE));
    }

    public int getEf() {
        return this.entityData.get(DATA_EF);
    }

    public float getGrowth() {
        return this.entityData.get(DATA_GROWTH);
    }

    private boolean isCyclone() {
        return getStormType() == StormType.CYCLONE;
    }

    /** Radio de la tormenta (bloques). */
    public float stormRadius() {
        return isCyclone() ? 70.0F + getEf() * 10.0F : 42.0F + getEf() * 8.0F;
    }

    /** Altura de la base de las nubes sobre el suelo. */
    public float cloudHeight() {
        return isCyclone() ? 38.0F : 50.0F;
    }

    // ------------------------------------------------------------------ datos

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TYPE, StormType.SUPERCELL.ordinal());
        this.entityData.define(DATA_EF, 2);
        this.entityData.define(DATA_GROWTH, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_TYPE, tag.getInt("Type"));
        this.entityData.set(DATA_EF, Mth.clamp(tag.getInt("Ef"), 0, 5));
        this.age = tag.getInt("Age");
        this.lifespan = tag.getInt("Life");
        if (this.lifespan <= 0) {
            this.lifespan = 9000;
        }
        this.heading = tag.getFloat("Heading");
        this.speed = tag.getFloat("Speed");
        if (this.speed <= 0.0F) {
            this.speed = 0.1F;
        }
        this.tornadoesSpawned = tag.getInt("Spawned");
        this.nextTornadoAge = tag.getInt("NextTornado");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Type", this.entityData.get(DATA_TYPE));
        tag.putInt("Ef", getEf());
        tag.putInt("Age", this.age);
        tag.putInt("Life", this.lifespan);
        tag.putFloat("Heading", this.heading);
        tag.putFloat("Speed", this.speed);
        tag.putInt("Spawned", this.tornadoesSpawned);
        tag.putInt("NextTornado", this.nextTornadoAge);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    // =================================================================== SERVIDOR

    private void serverTick() {
        ServerLevel sl = (ServerLevel) this.level();
        this.age++;
        if (this.age >= this.lifespan) {
            this.discard();
            return;
        }
        updateGrowth();
        moveStorm(sl);

        if (this.age % 100 == 1) {
            ensureWeather(sl);
        }

        float g = getGrowth();
        if (g < 0.5F) {
            return;
        }

        if (getStormType() == StormType.SUPERCELL) {
            spawnTornadoes(sl);
        }
        lightning(sl, g);
        if (this.age % 2 == 0) {
            windField(sl, g);
        }
        if (isCyclone() && this.age % 20 == 0) {
            weakBlocks(sl, g);
        }
        if (Config.SOUNDS.get() && this.age % 300 == 0 && this.random.nextFloat() < 0.6F) {
            sl.playSound(null, getX(), getY() + cloudHeight(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER, 12.0F, 0.6F);
        }
    }

    private void updateGrowth() {
        float form = 1.0F;
        if (this.age < FORM_TICKS) {
            float x = this.age / (float) FORM_TICKS;
            form = x * x * (3.0F - 2.0F * x);
        }
        float die = 1.0F;
        int left = this.lifespan - this.age;
        if (left < DIE_TICKS) {
            float x = Math.max(0, left) / (float) DIE_TICKS;
            die = x * x * (3.0F - 2.0F * x);
        }
        float g = Math.min(form, die);
        g = Math.round(g * 100.0F) / 100.0F;
        this.entityData.set(DATA_GROWTH, g);
    }

    private void ensureWeather(ServerLevel sl) {
        if (sl.dimension() != Level.OVERWORLD) {
            return;
        }
        if (!sl.isRaining() || !sl.isThundering()) {
            sl.setWeatherParameters(0, 6000, true, true);
        }
    }

    private void moveStorm(ServerLevel sl) {
        if (this.age % 60 == 0) {
            this.heading += (this.random.nextFloat() - 0.5F) * 0.4F;
        }
        double spd = this.speed * Config.SPEED_MULT.get();
        this.vx = Math.cos(this.heading) * spd;
        this.vz = Math.sin(this.heading) * spd;

        double nx = getX() + this.vx;
        double nz = getZ() + this.vz;
        BlockPos bp = BlockPos.containing(nx, getY(), nz);
        if (!sl.hasChunkAt(bp)) {
            this.heading += (float) Math.PI;
            this.vx = 0.0D;
            this.vz = 0.0D;
            return;
        }
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
        double ny = getY() + (gy - getY()) * 0.2D;
        this.setPos(nx, ny, nz);
    }

    private int countTornadoes(ServerLevel sl) {
        return sl.getEntities(ModEntities.TORNADO.get(), e -> true).size();
    }

    /** La supercelula va soltando tornados debajo de ella. */
    private void spawnTornadoes(ServerLevel sl) {
        if (this.age % 20 != 0 || this.age < this.nextTornadoAge || this.tornadoesSpawned >= 3) {
            return;
        }
        if (this.random.nextFloat() > 0.35F) {
            return;
        }
        if (countTornadoes(sl) >= Math.max(2, Config.MAX_STORMS.get() * 2)) {
            return;
        }
        double ox = (this.random.nextDouble() - 0.5D) * 36.0D;
        double oz = (this.random.nextDouble() - 0.5D) * 36.0D;
        double x = getX() + ox;
        double z = getZ() + oz;
        BlockPos bp = BlockPos.containing(x, getY(), z);
        if (!sl.hasChunkAt(bp)) {
            return;
        }
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());

        TornadoEntity t = ModEntities.TORNADO.get().create(sl);
        if (t == null) {
            return;
        }
        int ef = Mth.clamp(getEf() - this.random.nextInt(2) + (this.random.nextFloat() < 0.2F ? 1 : 0), 0, 5);
        StormType type = this.random.nextFloat() < 0.06F ? StormType.FIRE : StormType.TORNADO;
        t.setup(type, ef, 2400 + this.random.nextInt(3600));
        t.setParent(getUUID());
        t.setPos(x, gy, z);
        sl.addFreshEntity(t);

        this.tornadoesSpawned++;
        this.nextTornadoAge = this.age + 1200 + this.random.nextInt(1800);
    }

    private void lightning(ServerLevel sl, float g) {
        if (!Config.LIGHTNING.get() || this.age % 40 != 0) {
            return;
        }
        if (this.random.nextFloat() > (isCyclone() ? 0.25F : 0.55F)) {
            return;
        }
        double r = stormRadius() * 0.8D * g;
        double ang = this.random.nextDouble() * TWO_PI;
        double d = Math.sqrt(this.random.nextDouble()) * r;
        int bx = Mth.floor(getX() + Math.cos(ang) * d);
        int bz = Mth.floor(getZ() + Math.sin(ang) * d);
        if (!sl.hasChunk(bx >> 4, bz >> 4)) {
            return;
        }
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) {
            bolt.moveTo(bx + 0.5D, gy, bz + 0.5D);
            sl.addFreshEntity(bolt);
        }
    }

    /** Viento de la tormenta: empuja (en espiral) a lo que este a la intemperie. */
    private void windField(ServerLevel sl, float g) {
        boolean cyc = isCyclone();
        double radius = stormRadius() * (0.4D + 0.6D * g);
        double strength = (cyc ? 0.02D : 0.006D) * (1 + getEf()) * Config.WIND_MULT.get() * 2.0D;
        if (strength <= 0.0D) {
            return;
        }
        double eye = radius * 0.12D;
        AABB box = new AABB(getX() - radius, getY() - 5.0D, getZ() - radius,
                getX() + radius, getY() + 30.0D, getZ() + radius);
        List<Entity> list = sl.getEntities(this, box, e -> {
            if (e.isRemoved() || e.isPassenger()) {
                return false;
            }
            if (e instanceof Player p) {
                return Config.AFFECT_PLAYERS.get() && !p.isSpectator() && !p.isCreative();
            }
            return e instanceof LivingEntity || e instanceof ItemEntity || e instanceof Boat;
        });

        for (Entity e : list) {
            if (!sl.canSeeSky(e.blockPosition())) {
                continue; // a cubierto no hay viento
            }
            double rx = e.getX() - getX();
            double rz = e.getZ() - getZ();
            double dist = Math.sqrt(rx * rx + rz * rz);
            if (dist > radius || dist < 0.001D) {
                continue;
            }
            double f;
            if (cyc) {
                if (dist < eye) {
                    continue; // el ojo esta en calma
                }
                f = Math.exp(-(dist - eye) / (radius * 0.5D));
            } else {
                f = 1.0D - dist / radius;
            }
            double ex = rx / dist;
            double ez = rz / dist;
            double a = strength * f;
            double ax = -ez * a - ex * a * 0.4D;
            double az = ex * a - ez * a * 0.4D;
            double nx = e.getDeltaMovement().x + ax;
            double nz = e.getDeltaMovement().z + az;
            double h = Math.sqrt(nx * nx + nz * nz);
            double max = 0.55D;
            if (h > max) {
                nx = nx / h * max;
                nz = nz / h * max;
            }
            e.setDeltaMovement(nx, e.getDeltaMovement().y, nz);
            e.hurtMarked = true;
        }
    }

    /** El ciclon arranca hojas, cristales y plantas cerca de los jugadores. */
    private void weakBlocks(ServerLevel sl, float g) {
        if (getEf() < 1 || !Config.BREAK_BLOCKS.get()
                || !sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        double radius = stormRadius() * (0.4D + 0.6D * g);
        double eye = radius * 0.12D;
        int tries = 6 + getEf() * 3;
        for (ServerPlayer p : sl.players()) {
            double dx = p.getX() - getX();
            double dz = p.getZ() - getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius * 0.9D || dist < eye) {
                continue;
            }
            for (int i = 0; i < tries; i++) {
                double ang = this.random.nextDouble() * TWO_PI;
                double d = Math.sqrt(this.random.nextDouble()) * 20.0D;
                int bx = Mth.floor(p.getX() + Math.cos(ang) * d);
                int bz = Mth.floor(p.getZ() + Math.sin(ang) * d);
                if (!sl.hasChunk(bx >> 4, bz >> 4)) {
                    continue;
                }
                if (this.random.nextFloat() > 0.15F + 0.05F * getEf()) {
                    continue;
                }
                int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                int by = gy - 1 + this.random.nextInt(12);
                BlockPos pos = new BlockPos(bx, by, bz);
                BlockState st = sl.getBlockState(pos);
                if (st.isAir() || !st.getFluidState().isEmpty()) {
                    continue;
                }
                float hard = st.getDestroySpeed(sl, pos);
                if (hard < 0.0F || hard > 0.3F) {
                    continue;
                }
                if (sl.getBlockEntity(pos) != null) {
                    continue;
                }
                sl.destroyBlock(pos, this.random.nextFloat() < 0.15F);
            }
        }
    }

    // =================================================================== CLIENTE

    private int randRound(double x) {
        int n = (int) Math.floor(x);
        if (this.random.nextDouble() < x - n) {
            n++;
        }
        return n;
    }

    private ParticleOptions pickCloudParticle() {
        float f = this.random.nextFloat();
        if (f < 0.50F) {
            return ParticleTypes.CAMPFIRE_COSY_SMOKE;
        }
        if (f < 0.85F) {
            return ParticleTypes.LARGE_SMOKE;
        }
        return ParticleTypes.CLOUD;
    }

    private void clientTick() {
        float g = getGrowth();
        if (g <= 0.02F) {
            return;
        }
        Level lvl = this.level();
        double density = Config.PARTICLE_DENSITY.get();
        boolean cyc = isCyclone();
        double radius = stormRadius() * (0.4D + 0.6D * g);
        double cx = getX();
        double cz = getZ();
        double baseY = getY() + cloudHeight();
        double eye = radius * 0.12D;

        int n = randRound(density * (cyc ? 55.0D : 45.0D) * g);
        for (int i = 0; i < n; i++) {
            double ang;
            double r;
            double py;
            double u = this.random.nextDouble();
            if (cyc) {
                // brazos en espiral alrededor de un ojo vacio
                r = eye + (radius - eye) * Math.pow(u, 0.7D);
                ang = this.random.nextInt(3) * (TWO_PI / 3.0D) - (r / radius) * 4.5D
                        + (this.random.nextDouble() - 0.5D) * 0.7D;
                double thick = 14.0D * (1.0D - 0.5D * r / radius);
                py = baseY + (this.random.nextDouble() - 0.3D) * thick;
                if (r < eye * 2.2D) {
                    py += 10.0D; // pared del ojo, mas alta
                }
            } else {
                r = radius * Math.sqrt(u);
                ang = this.random.nextDouble() * TWO_PI;
                double thick = 12.0D * (1.0D - 0.6D * r / radius);
                py = baseY + (this.random.nextDouble() - 0.3D) * thick;
            }
            double px = cx + Math.cos(ang) * r;
            double pz = cz + Math.sin(ang) * r;
            double sp = (cyc ? 0.09D * (1.3D - r / radius) : 0.05D + 0.05D * (r / radius));
            double vx2 = -Math.sin(ang) * sp;
            double vz2 = Math.cos(ang) * sp;
            lvl.addParticle(pickCloudParticle(), true, px, py, pz, vx2, 0.0D, vz2);
        }

        // nube pared (wall cloud) bajando del centro de la supercelula
        if (!cyc && g > 0.6F) {
            int w = randRound(density * 6.0D);
            for (int i = 0; i < w; i++) {
                double ang = this.random.nextDouble() * TWO_PI;
                double drop = this.random.nextDouble() * 14.0D;
                double r = 9.0D * (1.0D - 0.4D * drop / 14.0D) * (0.7D + this.random.nextDouble() * 0.5D);
                double px = cx + Math.cos(ang) * r;
                double pz = cz + Math.sin(ang) * r;
                lvl.addParticle(ParticleTypes.LARGE_SMOKE, true, px, baseY - drop, pz,
                        -Math.sin(ang) * 0.18D, 0.0D, Math.cos(ang) * 0.18D);
            }
        }
    }
}
