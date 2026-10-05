package com.amynna.hololib.holo.object;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Ou un {@link HoloObject} est ancre, et donc quelle famille de renderers le dessine. L'ancrage ne donne que le repere
 * d'origine et ses options ; la position dans ce repere est le {@link HoloTransform} de l'objet, interprete selon
 * l'ancrage. Changer l'ancrage d'un objet change sa famille en runtime, sans recreer l'objet. Valeurs immuables.
 * <ul>
 *     <li>{@link Sky} (celeste) : repere centre sur la camera, comme la skybox ; transform applique tel quel.</li>
 *     <li>{@link World} (monde) : repere centre sur l'hote de la memoire (marker, joueur proprietaire, origine du
 *     monde pour une memoire de dimension) ; cache par le terrain.</li>
 *     <li>{@link Local} (camera) : sur une sphere autour de l'oeil ({@code x}, {@code y} = angles, {@code z} =
 *     distance) ; la rotation de la camera s'applique (mode relatif) ou la sphere tourne avec elle (mode follow).</li>
 *     <li>{@link Hud} : plat, orthographique, a l'ecran, sous le HUD du jeu.</li>
 * </ul>
 * L'objet est toujours dessine dans son <b>repere local</b> : 1 unite = 1 bloc (1 pixel GUI en HUD a l'echelle 1),
 * centre a l'origine, face visible vers {@code +Z}, {@code +Y} en haut. Les angles suivent les conventions de
 * Minecraft ({@code yaw} : 0 = sud, positif en tournant vers la droite ; {@code pitch} : positif vers le bas).
 * <p>
 * La famille effective est resolue <b>par observateur</b>, cote client : un ancrage {@code Local} d'un autre joueur, ou
 * du joueur local a la troisieme personne, est dessine dans le monde autour de sa tete ; un {@code Hud} n'est dessine
 * que pour son proprietaire en premiere personne.
 */
public sealed interface HoloAnchor permits HoloAnchor.Sky, HoloAnchor.World, HoloAnchor.Local, HoloAnchor.Hud
{
    /** Distance maximale entre l'hote et un objet ancre dans le monde demandee par un client, en blocs. */
    double MAX_REACH = 64.0;

    /** Famille de renderers. */
    enum Family
    {
        SKY, WORLD, VIEW, HUD
    }

    /** Famille vue par le proprietaire en premiere personne. */
    Family family();

    /** Faux si {@code transform} est hors des bornes autorisees pour cet ancrage (demande d'un client). */
    boolean acceptable(HoloTransform transform);

    void write(FriendlyByteBuf buf);

    CompoundTag save();

    /**
     * Dans le ciel, derriere tout le monde. Le transform est exprime dans un repere centre sur l'oeil : l'objet suit
     * la camera comme le soleil. Rester sous le plan lointain (128 blocs au minimum), voir
     * {@code SkyRenderContext.SKY_RADIUS} et {@link HoloTransform#setLookingAtOrigin}.
     *
     * @param celestial repere celeste : l'objet suit le cycle jour/nuit
     */
    record Sky(boolean celestial) implements HoloAnchor
    {
        @Override
        public Family family() { return Family.SKY; }

        @Override
        public boolean acceptable(HoloTransform transform) { return transform.isValid(); }

        @Override
        public void write(FriendlyByteBuf buf)
        {
            buf.writeByte(0);
            buf.writeBoolean(celestial);
        }

        @Override
        public CompoundTag save()
        {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", "sky");
            tag.putBoolean("celestial", celestial);
            return tag;
        }
    }

    /**
     * Dans le monde, cache par le terrain et les entites. Le transform est exprime depuis la position de l'hote de la
     * memoire (axes du monde) : marker pour une memoire de position, joueur proprietaire pour une memoire de joueur,
     * origine du monde pour une memoire de dimension.
     */
    record World() implements HoloAnchor
    {
        @Override
        public Family family() { return Family.WORLD; }

        @Override
        public boolean acceptable(HoloTransform transform)
        {
            double x = transform.x();
            double y = transform.y();
            double z = transform.z();
            return transform.isValid() && x * x + y * y + z * z <= MAX_REACH * MAX_REACH;
        }

        @Override
        public void write(FriendlyByteBuf buf) { buf.writeByte(1); }

        @Override
        public CompoundTag save()
        {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", "world");
            return tag;
        }
    }

    /**
     * Sur une sphere centree sur l'oeil : direction {@code (x, y)} du transform (yaw, pitch en degres), distance
     * {@code z}, face tournee vers l'oeil, puis rotation et echelle du transform. Dessine devant le terrain, sous la
     * main et le HUD.
     *
     * @param follow faux (relatif) : angles dans les axes du monde, la position suit la camera mais pas sa rotation,
     *               on peut detourner le regard (realite augmentee) ; vrai (follow) : angles relatifs a l'axe du regard
     *               ({@code yaw} > 0 a droite, {@code pitch} > 0 en bas), la sphere tourne avec la camera
     */
    record Local(boolean follow) implements HoloAnchor
    {
        public static final float MIN_DISTANCE = 0.1f;
        public static final float MAX_DISTANCE = 16f;

        @Override
        public Family family() { return Family.VIEW; }

        @Override
        public boolean acceptable(HoloTransform transform)
        {
            return transform.isValid() && transform.z() >= MIN_DISTANCE && transform.z() <= MAX_DISTANCE;
        }

        @Override
        public void write(FriendlyByteBuf buf)
        {
            buf.writeByte(2);
            buf.writeBoolean(follow);
        }

        @Override
        public CompoundTag save()
        {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", "local");
            tag.putBoolean("follow", follow);
            return tag;
        }
    }

    /**
     * A l'ecran, en projection orthographique, dessine avant le HUD du jeu (qui reste toujours devant). L'origine du
     * repere est le point {@code (alignX * largeur, alignY * hauteur)} ; le transform y ajoute {@code (x, y)} en
     * pixels GUI ({@code y} vers le bas, suit l'echelle du GUI des options), {@code z} donne l'ordre de dessin
     * (plus grand = devant), puis rotation et echelle (pixels GUI par unite du repere de l'objet).
     *
     * @param alignX 0 = bord gauche, 1 = bord droit
     * @param alignY 0 = haut, 1 = bas
     */
    record Hud(float alignX, float alignY) implements HoloAnchor
    {
        public static final float MAX_OFFSET = 4096f;
        public static final float MAX_ORDER = 1000f;

        @Override
        public Family family() { return Family.HUD; }

        @Override
        public boolean acceptable(HoloTransform transform)
        {
            return alignX >= 0f && alignX <= 1f && alignY >= 0f && alignY <= 1f && transform.isValid()
                    && Math.abs(transform.x()) <= MAX_OFFSET && Math.abs(transform.y()) <= MAX_OFFSET
                    && Math.abs(transform.z()) <= MAX_ORDER;
        }

        @Override
        public void write(FriendlyByteBuf buf)
        {
            buf.writeByte(3);
            buf.writeFloat(alignX);
            buf.writeFloat(alignY);
        }

        @Override
        public CompoundTag save()
        {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", "hud");
            tag.putFloat("alignX", alignX);
            tag.putFloat("alignY", alignY);
            return tag;
        }
    }

    static HoloAnchor read(FriendlyByteBuf buf)
    {
        int kind = buf.readByte();
        return switch (kind) {
            case 0 -> new Sky(buf.readBoolean());
            case 1 -> new World();
            case 2 -> new Local(buf.readBoolean());
            case 3 -> new Hud(buf.readFloat(), buf.readFloat());
            default -> throw new IllegalArgumentException("Ancrage inconnu : " + kind);
        };
    }

    /** @return l'ancrage sauvegarde, ou {@code null} si le tag est invalide */
    static HoloAnchor load(CompoundTag tag)
    {
        return switch (tag.getString("kind")) {
            case "sky" -> new Sky(tag.getBoolean("celestial"));
            case "world" -> new World();
            case "local" -> new Local(tag.getBoolean("follow"));
            case "hud" -> new Hud(tag.getFloat("alignX"), tag.getFloat("alignY"));
            default -> null;
        };
    }
}
