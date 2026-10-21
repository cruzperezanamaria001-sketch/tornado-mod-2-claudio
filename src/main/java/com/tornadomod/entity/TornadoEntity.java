package com.tornadomod.entity;

import com.tornadomod.Config;
import com.tornadomod.StormType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Tornado (normal o de fuego).
 *
 * - Se forma DESDE EL SUELO: empieza como un remolino de polvo, luego el embudo sube poco a poco.
 * - Al final se encoge hasta una "cuerda" y desaparece.
 * - El servidor hace la fisica (succion, giro, dano, bloques, fuego).
 * - El cliente dibuja el embudo con particulas (no hay modelo).
 */
public class TornadoEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_TYPE =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EF =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_GROWTH =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);

    /** Dureza maxima de bloque que arranca cada intensidad (EF0..EF5). */
    private static final double[] MAX_HARDNESS = {0.6D, 1.0D, 2.2D, 3.5D, 5.5D, 9.0D};

    private static final int FORM_TICKS = 400;   // 20 s para formarse
    private static final int DIE_TICKS = 300;    // 15 s para disiparse
    private static final double TWO_PI = Math.PI * 2.0D;

    private int age;
    private int lifespan = 4800;
    private float heading;
    private float speed = 0.14F;
    private UUID parent;
    private double offX;
    private double offZ;

    // solo cliente
    private BlockState debrisState = Blocks.DIRT.defaultBlockState();

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Configura el tornado justo despues de crearlo. */
    public void setup(StormType type, int ef, int lifeTicks) {
        this.entityData.set(DATA_TYPE, type.ordinal());
        this.entityData.set(DATA_EF, Mth.clamp(ef, 0, 5));
        this.lifespan = Math.max(200, lifeTicks);
        this.heading = this.random.nextFloat() * (float) TWO_PI;
        this.speed = 0.10F + this.random.nextFloat() * 0.12F;
    }

    public void setParent(UUID parent) {
        this.parent = parent;
        this.offX = (this.random.nextDouble() - 0.5D) * 30.0D;
        this.offZ = (this.random.nextDouble() - 0.5D) * 30.0D;
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

    /** Radio del embudo en la base (bloques). */
    public float baseRadius() {
        return 2.5F + getEf() * 1.9F;
    }

    /** Altura total del embudo cuando esta completamente formado. */
    public float fullHeight() {
        return 28.0F + getEf() * 8.0F;
    }

    // ------------------------------------------------------------------ datos

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TYPE, 0);
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
            this.lifespan = 4800;
        }
        this.heading = tag.getFloat("Heading");
        this.speed = tag.getFloat("Speed");
        if (this.speed <= 0.0F) {
            this.speed = 0.14F;
        }
        this.offX = tag.getDouble("OffX");
        this.offZ = tag.getDouble("OffZ");
        if (tag.hasUUID("Parent")) {
            this.parent = tag.getUUID("Parent");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Type", this.entityData.get(DATA_TYPE));
        tag.putInt("Ef", getEf());
        tag.putInt("Age", this.age);
        tag.putInt("Life", this.lifespan);
        tag.putFloat("Heading", this.heading);
        tag.putFloat("Speed", this.speed);
        tag.putDouble("OffX", this.offX);
        tag.putDouble("OffZ", this.offZ);
        if (this.parent != null) {
            tag.putUUID("Parent", this.parent);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    // ------------------------------------------------------------------- tick

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
        moveTornado(sl);

        float g = getGrowth();
        if (g < 0.1F) {
            return;
        }
        affectEntities(sl, g);
        if (this.age % 3 == 0) {
            damageBlocks(sl, g);
        }
        if (getStormType() == StormType.FIRE && this.age % 5 == 0) {
            spreadFire(sl, g);
        }
        playSounds(sl);
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

    private StormEntity findParent(ServerLevel sl) {
        if (this.parent == null) {
            return null;
        }
        Entity e = sl.getEntity(this.parent);
        if (e instanceof StormEntity storm && !storm.isRemoved()) {
            return storm;
        }
        this.parent = null;
        return null;
    }

    private void moveTornado(ServerLevel sl) {
        double spd = this.speed * Config.SPEED_MULT.get();
        if (this.age < FORM_TICKS) {
            spd *= 0.35D;
        } else if (this.lifespan - this.age < DIE_TICKS) {
            spd *= 0.6D;
        }

        double vx;
        double vz;
        StormEntity p = findParent(sl);
        if (p != null) {
            if (this.age % 100 == 0) {
                this.offX = Mth.clamp(this.offX + (this.random.nextDouble() - 0.5D) * 8.0D, -26.0D, 26.0D);
                this.offZ = Mth.clamp(this.offZ + (this.random.nextDouble() - 0.5D) * 8.0D, -26.0D, 26.0D);
            }
            double tx = p.getX() + this.offX;
            double tz = p.getZ() + this.offZ;
            vx = p.vx + (tx - getX()) * 0.02D;
            vz = p.vz + (tz - getZ()) * 0.02D;
            double len = Math.sqrt(vx * vx + vz * vz);
            double max = spd * 1.6D + 0.05D;
            if (len > max) {
                vx = vx / len * max;
                vz = vz / len * max;
            }
        } else {
            if (this.age % 30 == 0) {
                this.heading += (this.random.nextFloat() - 0.5F) * 0.5F;
            }
            vx = Math.cos(this.heading) * spd;
            vz = Math.sin(this.heading) * spd;
        }

        double nx = getX() + vx;
        double nz = getZ() + vz;
        BlockPos bp = BlockPos.containing(nx, getY(), nz);
        if (!sl.hasChunkAt(bp)) {
            // llegamos al borde de la zona cargada: damos la vuelta
            this.heading += (float) Math.PI;
            return;
        }
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
        double ny = getY() + (gy - getY()) * 0.3D;
        this.setPos(nx, ny, nz);
    }

    private boolean canAffect(Entity e) {
        if (e == this || e.isRemoved() || e.isPassenger()) {
            return false;
        }
        if (e instanceof TornadoEntity || e instanceof StormEntity) {
            return false;
        }
        if (e instanceof EnderDragon || e instanceof WitherBoss) {
            return false;
        }
        if (e instanceof Player p) {
            if (!Config.AFFECT_PLAYERS.get() || p.isSpectator() || p.isCreative()) {
                return false;
            }
        }
        return true;
    }

    /** Succion + giro + elevacion + dano. Afecta a animales, monstruos, jugadores, items, bloques que vuelan, barcos... */
    private void affectEntities(ServerLevel sl, float g) {
        int ef = getEf();
        double radius = baseRadius();
        double pullR = (radius * 3.2D + 4.0D) * (0.35D + 0.65D * g);
        double top = fullHeight() * g + 8.0D;
        AABB box = new AABB(getX() - pullR, getY() - 4.0D, getZ() - pullR,
                getX() + pullR, getY() + top, getZ() + pullR);
        List<Entity> list = sl.getEntities(this, box, this::canAffect);

        double wind = Config.WIND_MULT.get();
        double dmgMult = Config.DAMAGE_MULT.get();
        boolean fire = getStormType() == StormType.FIRE;
        double core = radius * 1.15D * (0.4D + 0.6D * g);
        double liftR = core * 2.2D;
        double maxUp = 0.65D + ef * 0.08D;
        double maxH = 0.9D + ef * 0.15D;

        for (Entity e : list) {
            double rx = e.getX() - getX();
            double rz = e.getZ() - getZ();
            double dist = Math.sqrt(rx * rx + rz * rz);
            if (dist > pullR) {
                continue;
            }
            double f = 1.0D - dist / pullR;
            double inv = dist < 0.001D ? 0.0D : 1.0D / dist;
            double ex = rx * inv; // hacia fuera
            double ez = rz * inv;
            double base = (0.035D + 0.02D * ef) * wind * (0.3D + 0.7D * f);

            // inward (succion) + tangencial (giro antihorario)
            double ax = -ex * base * 0.9D - ez * base * 1.1D;
            double az = -ez * base * 0.9D + ex * base * 1.1D;

            double lift = 0.0D;
            if (dist < liftR) {
                lift = (0.06D + 0.03D * ef) * (1.0D - dist / liftR) * (0.5D + g);
            }

            Vec3 m = e.getDeltaMovement();
            double nx = m.x + ax;
            double nz = m.z + az;
            double ny = Math.min(m.y + lift, maxUp);
            if (dist < liftR && m.y < 0.0D && lift > 0.0D) {
                ny = Math.max(ny, m.y * 0.4D + lift); // frena la caida dentro del embudo
            }
            double h = Math.sqrt(nx * nx + nz * nz);
            if (h > maxH) {
                nx = nx / h * maxH;
                nz = nz / h * maxH;
            }
            e.setDeltaMovement(nx, ny, nz);
            e.hurtMarked = true;
            e.hasImpulse = true;

            // los animales y mobs giran como locos
            if (e instanceof LivingEntity le && !(e instanceof Player) && dist < liftR) {
                le.setYRot(le.getYRot() + 22.0F);
                le.setYHeadRot(le.getYHeadRot() + 22.0F);
            }

            // dano en el nucleo
            if (this.age % 10 == 0 && dist < core) {
                if (e instanceof LivingEntity le) {
                    float dmg = (float) ((1.5D + ef * 1.8D) * dmgMult);
                    if (dmg > 0.0F) {
                        le.hurt(sl.damageSources().generic(), dmg);
                    }
                    if (fire) {
                        le.setSecondsOnFire(8);
                    }
                } else if (fire && e instanceof ItemEntity) {
                    e.setSecondsOnFire(5);
                }
            } else if (fire && dist < core * 1.4D && this.age % 10 == 5 && e instanceof LivingEntity le) {
                le.setSecondsOnFire(4);
            }
        }
    }

    /** Arranca bloques cerca del suelo y los lanza girando. */
    private void damageBlocks(ServerLevel sl, float g) {
        if (g < 0.3F || !Config.BREAK_BLOCKS.get()
                || !sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        int ef = getEf();
        double r = baseRadius() * 1.15D * (0.4D + 0.6D * g);
        double maxHard = MAX_HARDNESS[ef];
        int tries = 5 + ef * 5;
        int up = 4 + (int) (ef * 1.5D);

        AABB box = new AABB(getX() - r - 8.0D, getY() - 5.0D, getZ() - r - 8.0D,
                getX() + r + 8.0D, getY() + 40.0D, getZ() + r + 8.0D);
        int flying = sl.getEntitiesOfClass(FallingBlockEntity.class, box).size();
        boolean canLaunch = flying < Config.MAX_FLYING_BLOCKS.get();

        for (int i = 0; i < tries; i++) {
            double ang = this.random.nextDouble() * TWO_PI;
            double d = Math.sqrt(this.random.nextDouble()) * r;
            int bx = Mth.floor(getX() + Math.cos(ang) * d);
            int bz = Mth.floor(getZ() + Math.sin(ang) * d);
            if (!sl.hasChunk(bx >> 4, bz >> 4)) {
                continue;
            }
            // mas probable cerca del centro
            if (this.random.nextDouble() > (1.0D - d / r) * 0.9D + 0.1D) {
                continue;
            }
            int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            int by = gy - 1 + this.random.nextInt(up + 2);
            BlockPos pos = new BlockPos(bx, by, bz);
            BlockState st = sl.getBlockState(pos);
            if (st.isAir() || !st.getFluidState().isEmpty()) {
                continue;
            }
            float hard = st.getDestroySpeed(sl, pos);
            if (hard < 0.0F || hard > maxHard) {
                continue;
            }
            if (sl.getBlockEntity(pos) != null) {
                continue;
            }
            boolean surface = by < gy;
            if (surface && (ef < 2 || this.random.nextFloat() > 0.25F)) {
                continue;
            }

            boolean multiBlock = st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    || st.hasProperty(BlockStateProperties.BED_PART);
            if (multiBlock || hard < 0.25F || !canLaunch) {
                sl.destroyBlock(pos, false);
                continue;
            }

            FallingBlockEntity fe = FallingBlockEntity.fall(sl, pos, st);
            fe.dropItem = false;
            double tx = -Math.sin(ang);
            double tz = Math.cos(ang);
            fe.setDeltaMovement(
                    tx * 0.35D + (this.random.nextDouble() - 0.5D) * 0.2D,
                    0.45D + this.random.nextDouble() * 0.5D,
                    tz * 0.35D + (this.random.nextDouble() - 0.5D) * 0.2D);
            fe.hurtMarked = true;
        }
    }

    /** Tornado de fuego: prende el suelo a su alrededor. */
    private void spreadFire(ServerLevel sl, float g) {
        if (!Config.FIRE_SPREADS.get() || !sl.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            return;
        }
        int ef = getEf();
        double r = baseRadius() * 1.6D * (0.4D + 0.6D * g);
        int tries = 3 + ef;
        for (int i = 0; i < tries; i++) {
            double ang = this.random.nextDouble() * TWO_PI;
            double d = Math.sqrt(this.random.nextDouble()) * r;
            int bx = Mth.floor(getX() + Math.cos(ang) * d);
            int bz = Mth.floor(getZ() + Math.sin(ang) * d);
            if (!sl.hasChunk(bx >> 4, bz >> 4)) {
                continue;
            }
            int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            BlockPos pos = new BlockPos(bx, gy + this.random.nextInt(2), bz);
            if (BaseFireBlock.canBePlacedAt(sl, pos, Direction.UP)) {
                sl.setBlockAndUpdate(pos, BaseFireBlock.getState(sl, pos));
            }
        }
    }

    private void playSounds(ServerLevel sl) {
        if (!Config.SOUNDS.get()) {
            return;
        }
        float vol = 3.0F + getEf();
        if (this.age % 25 == 0) {
            if (getStormType() == StormType.FIRE) {
                sl.playSound(null, getX(), getY() + 4.0D, getZ(), SoundEvents.FIRE_AMBIENT,
                        SoundSource.WEATHER, vol, 0.5F);
                sl.playSound(null, getX(), getY() + 4.0D, getZ(), SoundEvents.LAVA_AMBIENT,
                        SoundSource.WEATHER, vol * 0.5F, 0.6F);
            }
            sl.playSound(null, getX(), getY() + 6.0D, getZ(), SoundEvents.WEATHER_RAIN,
                    SoundSource.WEATHER, vol * 2.0F, 0.5F);
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

    private ParticleOptions pickFunnelParticle(boolean fire) {
        float f = this.random.nextFloat();
        if (fire) {
            if (f < 0.55F) {
                return ParticleTypes.FLAME;
            }
            if (f < 0.80F) {
                return ParticleTypes.LARGE_SMOKE;
            }
            if (f < 0.90F) {
                return ParticleTypes.SMOKE;
            }
            return ParticleTypes.LAVA;
        }
        if (f < 0.50F) {
            return ParticleTypes.LARGE_SMOKE;
        }
        if (f < 0.80F) {
            return ParticleTypes.CLOUD;
        }
        if (f < 0.92F) {
            return ParticleTypes.SMOKE;
        }
        return ParticleTypes.ASH;
    }

    private void refreshDebris() {
        BlockPos p = BlockPos.containing(getX(), getY() - 1.0D, getZ());
        BlockState st = this.level().getBlockState(p);
        if (st.isAir() || st.getRenderShape() != RenderShape.MODEL) {
            st = this.level().getBlockState(p.below());
        }
        if (!st.isAir() && st.getRenderShape() == RenderShape.MODEL && st.getFluidState().isEmpty()) {
            this.debrisState = st;
        }
    }

    private void clientTick() {
        float g = getGrowth();
        if (g <= 0.005F) {
            return;
        }
        Level lvl = this.level();
        double density = Config.PARTICLE_DENSITY.get();
        boolean fire = getStormType() == StormType.FIRE;
        float radius = baseRadius();
        float wf = 0.4F + 0.6F * g;           // el embudo se afina al disiparse
        float height = fullHeight() * g;       // y crece desde el suelo
        double cx = getX();
        double cy = getY();
        double cz = getZ();

        if (this.tickCount % 10 == 0) {
            refreshDebris();
        }

        // inclinacion del embudo (mas inclinado arriba), fija por tornado
        double leanAng = ((getUUID().hashCode() & 1023) / 1024.0D) * TWO_PI;
        double lean = 0.22D * height;

        // ---- embudo
        if (height > 3.0F) {
            double spacing = 1.6D;
            int rings = Math.max(1, (int) (height / spacing));
            for (int i = 0; i < rings; i++) {
                double h = (i + this.random.nextDouble()) * spacing;
                double t = h / height;
                double r = radius * (0.4D + 1.6D * Math.pow(t, 2.2D)) * wf;
                double lx = Math.cos(leanAng) * lean * t * t;
                double lz = Math.sin(leanAng) * lean * t * t;
                int n = randRound(density * (1.5D + 2.0D * t));
                for (int k = 0; k < n; k++) {
                    double ang = this.random.nextDouble() * TWO_PI;
                    double rr = r * (0.75D + this.random.nextDouble() * 0.5D);
                    double px = cx + lx + Math.cos(ang) * rr;
                    double pz = cz + lz + Math.sin(ang) * rr;
                    double sp = 0.20D + 0.08D * this.random.nextDouble();
                    double vx = -Math.sin(ang) * sp;
                    double vz = Math.cos(ang) * sp;
                    double vy = 0.10D + this.random.nextDouble() * 0.10D;
                    lvl.addParticle(pickFunnelParticle(fire), true, px, cy + h, pz, vx, vy, vz);
                }
            }
        }

        // ---- polvo y escombros en la base (remolino en el suelo)
        int dust = randRound(density * (3.0D + radius * 0.5D) * (0.5D + g));
        boolean solid = !this.debrisState.isAir();
        for (int k = 0; k < dust; k++) {
            double ang = this.random.nextDouble() * TWO_PI;
            double rr = radius * (0.5D + this.random.nextDouble() * 1.1D) * wf;
            double px = cx + Math.cos(ang) * rr;
            double pz = cz + Math.sin(ang) * rr;
            double py = cy + 0.1D + this.random.nextDouble() * 0.8D;
            double vx = -Math.sin(ang) * 0.30D;
            double vz = Math.cos(ang) * 0.30D;
            double vy = 0.10D + this.random.nextDouble() * 0.15D;
            ParticleOptions opt;
            if (fire) {
                opt = this.random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE;
            } else if (solid && this.random.nextFloat() < 0.5F) {
                opt = new BlockParticleOption(ParticleTypes.BLOCK, this.debrisState);
            } else {
                opt = this.random.nextBoolean() ? ParticleTypes.POOF : ParticleTypes.CLOUD;
            }
            lvl.addParticle(opt, true, px, py, pz, vx, vy, vz);
        }

        // ---- escombros dentro del embudo
        if (solid && !fire && height > 4.0F) {
            int deb = randRound(density * (2.0D + getEf()) * g);
            for (int k = 0; k < deb; k++) {
                double h = this.random.nextDouble() * height * 0.7D;
                double t = h / height;
                double r = radius * (0.4D + 1.6D * Math.pow(t, 2.2D)) * wf * this.random.nextDouble();
                double ang = this.random.nextDouble() * TWO_PI;
                double px = cx + Math.cos(ang) * r;
                double pz = cz + Math.sin(ang) * r;
                lvl.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, this.debrisState), true,
                        px, cy + h, pz, -Math.sin(ang), 0.4D, Math.cos(ang));
            }
        }
    }
}
