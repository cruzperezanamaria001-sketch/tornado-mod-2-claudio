# Tornado Mod (Forge 1.20.1)

Mod de Minecraft Java **1.20.1** con **Forge 47.x** (Java 17) con tornados realistas:

| Tipo | Que es |
|------|--------|
| `tornado` | Tornado normal. Se forma desde el suelo (remolino de polvo → embudo que sube), levanta animales, jugadores, items y bloques, y al final se afina en una "cuerda" y desaparece. |
| `fire` | Tornado de fuego: llamas, lava y humo; prende fuego al terreno y a lo que levanta. |
| `supercell` | Supercelula: nube gigante que rota con tormenta electrica (lluvia + rayos). Suelta tornados debajo de ella y estos la siguen. |
| `cyclone` | Ciclon: sistema enorme en espiral con ojo en calma, vientos fuertes, lluvia y rayos. |

Todo se dibuja con particulas (no hay modelos ni texturas).

## Comandos (necesitan OP / permiso 2)

```
/tornado spawn                              tornado EF2 donde estas
/tornado spawn <tipo> [intensidad] [pos]    tipo: tornado | fire | supercell | cyclone
                                            intensidad: 0 a 5 (como la escala EF)
/tornado clear [radio]                      elimina tornados y tormentas
/tornado info                               lista los que hay con sus coordenadas
/tornado grief <true|false>                 activa/desactiva destruir bloques
/tornado natural <true|false>               activa/desactiva tormentas naturales
/tornado help
```

Ejemplos:

```
/tornado spawn tornado 3
/tornado spawn fire 4 ~ ~ ~
/tornado spawn supercell 3
/tornado spawn cyclone 5 100 70 -200
```

> Los tornados **no aparecen de golpe**: tardan unos 20 segundos en formarse desde el suelo.
> Los creativos/espectadores no son afectados.

## Intensidad (EF0 – EF5)

La intensidad cambia el tamano, la altura, la fuerza del viento, el dano y la dureza de los bloques que arranca
(EF0: hojas y cristales · EF2: madera, tierra, piedra · EF5: casi todo menos obsidiana/bedrock).

## Configuracion

Se crea en `config/tornadomod-common.toml` al abrir el mundo: destruccion de bloques, jugadores afectados, velocidad,
dano, viento, duracion, maximo de bloques volando, tormentas naturales y **densidad de particulas**
(bajala si tienes lag).

Las tormentas naturales (supercelulas y ciclones) aparecen solas durante las tormentas electricas, de forma poco frecuente.

## Servidores multijugador

Si un jugador es levantado muchos segundos en el aire, Minecraft puede expulsarlo con "Flying is not enabled".
Pon `allow-flight=true` en `server.properties` si quieres evitarlo.

## Compilar con GitHub (sin instalar nada)

1. Crea un repositorio en GitHub y sube **todo el contenido de esta carpeta** (`Add file → Upload files`).
   - La carpeta oculta `.github/workflows/build.yml` tiene que quedar subida. Si no se sube al arrastrar, usa
     `Add file → Create new file`, escribe `.github/workflows/build.yml` como nombre y pega el contenido.
2. Ve a la pestana **Actions**. Se ejecuta solo el workflow **Build** (tarda unos minutos).
3. Cuando termine en verde, abre la ejecucion y descarga el `.jar` en **Artifacts → tornadomod-jar**.
4. Copia el `.jar` a la carpeta `mods` de Forge 1.20.1.

Para publicar el `.jar` en **Releases** automaticamente: crea un tag que empiece por `v` (por ejemplo `v1.0.0`).

Si el workflow falla en rojo, abre el paso que fallo y copia el error: es facil de corregir.

## Estructura

```
src/main/java/com/tornadomod/
  TornadoMod.java        mod principal
  ModEntities.java       registro de entidades
  Config.java            configuracion
  StormType.java         tipos (tornado, fire, supercell, cyclone)
  StormManager.java      crear tornados/tormentas y aparicion natural
  TornadoCommand.java    comandos /tornado
  ClientEvents.java      renderer vacio (todo es particulas)
  ForgeEvents.java       eventos de Forge
  entity/TornadoEntity.java   tornado normal y de fuego
  entity/StormEntity.java     supercelula y ciclon
```

Licencia: MIT
