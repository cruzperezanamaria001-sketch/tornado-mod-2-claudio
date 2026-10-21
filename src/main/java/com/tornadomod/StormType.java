package com.tornadomod;

/** Tipos de fenomeno que puede crear el mod. */
public enum StormType {
    TORNADO("tornado"),
    FIRE("fire"),
    SUPERCELL("supercell"),
    CYCLONE("cyclone");

    public final String id;

    StormType(String id) {
        this.id = id;
    }

    /** Tornado normal o de fuego (usa TornadoEntity). */
    public boolean isTornado() {
        return this == TORNADO || this == FIRE;
    }

    public static StormType byId(String s) {
        for (StormType t : values()) {
            if (t.id.equalsIgnoreCase(s)) {
                return t;
            }
        }
        return null;
    }

    public static StormType byOrdinal(int i) {
        StormType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
