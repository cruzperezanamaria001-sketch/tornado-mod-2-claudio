package com.tornadomod;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuracion del mod (config/tornadomod-common.toml). */
public class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue FIRE_SPREADS;
    public static final ForgeConfigSpec.BooleanValue AFFECT_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue LIGHTNING;
    public static final ForgeConfigSpec.BooleanValue SOUNDS;
    public static final ForgeConfigSpec.BooleanValue NATURAL_STORMS;

    public static final ForgeConfigSpec.DoubleValue SPEED_MULT;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULT;
    public static final ForgeConfigSpec.DoubleValue WIND_MULT;
    public static final ForgeConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ForgeConfigSpec.DoubleValue NATURAL_CHANCE;

    public static final ForgeConfigSpec.IntValue MAX_STORMS;
    public static final ForgeConfigSpec.IntValue MAX_FLYING_BLOCKS;
    public static final ForgeConfigSpec.IntValue MIN_LIFE_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_LIFE_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Destruccion y efectos").push("world");
        BREAK_BLOCKS = b.comment("Los tornados arrancan bloques y los lanzan por el aire (respeta la gamerule mobGriefing)")
                .define("breakBlocks", true);
        FIRE_SPREADS = b.comment("Los tornados de fuego prenden fuego al terreno (respeta la gamerule doFireTick)")
                .define("fireSpreads", true);
        AFFECT_PLAYERS = b.comment("Los tornados levantan y danan a los jugadores")
                .define("affectPlayers", true);
        LIGHTNING = b.comment("Las supercelulas y ciclones lanzan rayos")
                .define("lightning", true);
        SOUNDS = b.comment("Sonidos de viento y truenos")
                .define("sounds", true);
        MAX_FLYING_BLOCKS = b.comment("Maximo de bloques volando cerca de un tornado (rendimiento)")
                .defineInRange("maxFlyingBlocks", 60, 0, 500);
        b.pop();

        b.comment("Fuerza").push("strength");
        SPEED_MULT = b.comment("Multiplicador de velocidad de desplazamiento")
                .defineInRange("speedMultiplier", 1.0D, 0.1D, 5.0D);
        DAMAGE_MULT = b.comment("Multiplicador de dano")
                .defineInRange("damageMultiplier", 1.0D, 0.0D, 10.0D);
        WIND_MULT = b.comment("Multiplicador de la fuerza del viento (succion y giro)")
                .defineInRange("windMultiplier", 1.0D, 0.0D, 5.0D);
        MIN_LIFE_SECONDS = b.comment("Duracion minima de un tornado (segundos)")
                .defineInRange("minLifeSeconds", 90, 20, 3600);
        MAX_LIFE_SECONDS = b.comment("Duracion maxima de un tornado (segundos)")
                .defineInRange("maxLifeSeconds", 240, 20, 3600);
        b.pop();

        b.comment("Aparicion natural").push("natural");
        NATURAL_STORMS = b.comment("Aparecen supercelulas y ciclones solos durante las tormentas electricas")
                .define("naturalStorms", true);
        NATURAL_CHANCE = b.comment("Probabilidad cada 10 segundos de tormenta electrica de crear una tormenta")
                .defineInRange("naturalChance", 0.08D, 0.0D, 1.0D);
        MAX_STORMS = b.comment("Maximo de supercelulas/ciclones a la vez por dimension (aparicion natural)")
                .defineInRange("maxStorms", 1, 0, 20);
        b.pop();

        b.comment("Solo cliente (efectos visuales)").push("client");
        PARTICLE_DENSITY = b.comment("Densidad de particulas (0.3 = pocas, 1 = normal, 2 = muchas). Bajala si tienes lag")
                .defineInRange("particleDensity", 1.0D, 0.1D, 4.0D);
        b.pop();

        SPEC = b.build();
    }
}
