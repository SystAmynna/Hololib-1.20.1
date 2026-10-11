package com.amynna.hololib.client.render.modules.dimension.space;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.dimension.space.SpaceBodyTextures;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceTextureRef;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Textures des astres cote client (T30, decisions D21, C69, C70) : resout la reference d'un corps ({@code auto},
 * integree, importee) en {@link Texture} liee, prete a dessiner (un {@code RenderType} local par texture).
 * <p>
 * <b>Chargement a la demande</b> : une texture integree n'est lue que lorsqu'un renderer la demande ({@link #texture}) ;
 * le ciel demande celles de <b>tous les corps du systeme courant</b> a chaque frame (puis la Space Map celles du systeme
 * affiche, T37). Decodage du PNG et calcul des niveaux de mipmap hors du fil de rendu ({@code Util.backgroundExecutor}),
 * televersement sur le fil de rendu par {@link #beginFrame} (au plus {@link #UPLOADS_PER_FRAME} par frame). Tant
 * qu'une texture n'est pas prete (ou si son chargement echoue), {@link #texture} rend {@code null} : le renderer garde
 * son dessin en couleur unie (repli).
 * <p>
 * <b>Textures importees</b> (T45, C79) : point d'entree {@link #registerCustom} (image deja decodee, enregistree par
 * empreinte SHA-1) ; un corps {@code custom:<sha1>} affiche sa texture integree de repli
 * ({@link SpaceBodyTextures#builtinFor}, c'est-a-dire {@code auto}) tant que l'image manque, et son empreinte est
 * passee au <b>demandeur</b> ({@link #setCustomRequester}, installe par {@code SpaceTextureClient} : demande au serveur
 * par le canal des textures Space, D27) tant qu'aucune image n'est enregistree pour elle.
 * <p>
 * Chaque texture est une {@link AbstractTexture} enregistree dans le {@code TextureManager} sous une cle propre
 * ({@code hololib:space_body/builtin/<id>}, {@code hololib:space_body/custom/<sha1>}) : filtre lineaire avec mipmaps,
 * repetition en {@code u} (couture de la longitude) et bord fige en {@code v} (poles). Un rechargement des ressources
 * (F3+T) relit les integrees (packs de ressources compris) ; une importee garde son image deja televersee.
 * <b>Liberation</b> : une texture integree qui n'a pas ete demandee depuis {@link #EVICT_AFTER_MS} est rendue au
 * {@code TextureManager} (memoire GPU) ; une importee reste jusqu'a {@link #releaseCustom} ou
 * {@link #releaseAllCustom} (deconnexion : changement de monde ou de serveur).
 * <p>
 * Fil de rendu uniquement. Sans allocation par frame : {@link #texture} et {@link #beginFrame} ne font que des
 * recherches dans des tables ; les allocations ont lieu au premier chargement d'une texture.
 */
public final class SpaceBodyTextureCache
{
    /** Televersements au plus par frame (un niveau 0 de 1024 x 512 et ses mipmaps : environ 2,7 Mo). */
    static final int UPLOADS_PER_FRAME = 2;
    /** Une texture integree non demandee depuis ce delai est liberee (ms). */
    static final long EVICT_AFTER_MS = 60_000L;
    /** Intervalle entre deux recherches de textures a liberer (ms). */
    private static final long EVICT_CHECK_MS = 5_000L;
    /** Nombre maximal de niveaux de mipmap au-dela du niveau 0 (1024 x 512 -> 2 x 1). */
    private static final int MAX_MIP_LEVELS = 9;

    private static final SpaceBodyTextureCache INSTANCE = new SpaceBodyTextureCache();

    /** Cache partage par les renderers Space (ciel, Space Map). */
    public static SpaceBodyTextureCache instance() { return INSTANCE; }

    /** Etat d'une texture. */
    private enum State
    {
        LOADING,
        READY,
        FAILED
    }

    /**
     * Texture d'astre chargee : {@code RenderType} a demander au {@code MultiBufferSource} (format
     * {@code POSITION_COLOR_TEX}, quads opaques, faces arriere eliminees, profondeur normale) et teinte (vrai : image
     * en luminance, a multiplier par la couleur du corps).
     */
    public static final class Texture
    {
        private final ResourceLocation key;
        /** Texture integree ; {@code null} pour une importee. */
        private final SpaceBodyTextures.Builtin builtin;
        /** PNG d'une texture integree ; {@code null} pour une importee. */
        private final ResourceLocation source;
        private final boolean tinted;
        private final boolean custom;
        private State state = State.LOADING;
        private CompletableFuture<Prepared> pending;
        private BodyTexture texture;
        private RenderType renderType;
        private long lastUsed;
        /** Couleur moyenne de l'image ({@code 0xRRGGBB}) et sa luminance (0 a 1), posees au chargement. */
        private int average = 0xFFFFFF;
        private float luminance = 1f;

        private Texture(ResourceLocation key, SpaceBodyTextures.Builtin builtin, boolean custom)
        {
            this.key = key;
            this.builtin = builtin;
            this.source = builtin != null ? builtin.location() : null;
            this.tinted = builtin != null && builtin.tinted();
            this.custom = custom;
        }

        /** Type de rendu de cette texture (sommets {@code vertex, color, uv}). */
        public RenderType renderType() { return renderType; }

        /** Vrai si l'image est une luminance a teinter par la couleur du corps (etoiles). */
        public boolean tinted() { return tinted; }

        /**
         * Couleur moyenne de l'image ({@code 0xRRGGBB}, dernier niveau de mipmap) : teinte d'un corps vu comme un point
         * (a multiplier par la couleur du corps si {@link #tinted}).
         */
        public int averageColor() { return average; }

        /** Luminance moyenne de l'image (0 a 1, coefficients Rec. 709). */
        public float luminance() { return luminance; }
    }

    /** Resultat d'un chargement : niveaux de mipmap et couleur moyenne (calcules hors du fil de rendu). */
    private record Prepared(NativeImage[] images, int average) {}

    private final Map<SpaceBodyTextures.Builtin, Texture> builtins = new IdentityHashMap<>();
    private final Map<String, Texture> customs = new HashMap<>();
    /** Toutes les textures (parcours sans iterateur). */
    private final List<Texture> entries = new ArrayList<>();
    /** Demandeur des images importees manquantes (T45), {@code null} sans canal installe. */
    private Consumer<String> customRequester;
    /** Temps de la frame ({@code Util.getMillis}), pose par {@link #beginFrame}. */
    private long now;
    private long nextEvictCheck;

    private SpaceBodyTextureCache() {}

    // ---------------------------------------------------------------- frame

    /**
     * A appeler une fois par frame avant de dessiner (fil de rendu) : televerse les chargements termines (au plus
     * {@link #UPLOADS_PER_FRAME}) et libere de temps en temps les textures integrees inutilisees.
     */
    public void beginFrame()
    {
        now = Util.getMillis();
        int budget = UPLOADS_PER_FRAME;
        for (int i = 0; i < entries.size() && budget > 0; i++) {
            Texture entry = entries.get(i);
            if (entry.state == State.LOADING && entry.pending != null && entry.pending.isDone()) {
                finish(entry);
                budget--;
            }
        }
        if (now >= nextEvictCheck) {
            nextEvictCheck = now + EVICT_CHECK_MS;
            evictUnused();
        }
    }

    // ---------------------------------------------------------------- resolution

    /**
     * Texture a dessiner pour le corps d'indice {@code index} de {@code system} : son image importee si elle est
     * prete, sinon sa texture integree ({@link SpaceBodyTextures#builtinFor} : reference {@code builtin}, ou regle
     * {@code auto}, aussi pour une importee manquante). Demande le chargement de la texture integree si besoin, et
     * l'image importee au demandeur ({@link #setCustomRequester}) si aucune n'est enregistree pour son empreinte.
     *
     * @return la texture prete, ou {@code null} (chargement en cours ou echoue : dessin en couleur unie)
     */
    public Texture texture(SpaceSystem system, int index)
    {
        String hash = SpaceBodyTextures.customHash(system.bodies().get(index));
        if (hash != null) {
            Texture custom = customs.get(hash);
            if (custom != null && custom.state == State.READY) {
                custom.lastUsed = now;
                return custom;
            }
            if (custom == null && customRequester != null) customRequester.accept(hash);
        }
        SpaceBodyTextures.Builtin builtin = SpaceBodyTextures.builtinFor(system, index);
        Texture entry = builtins.get(builtin);
        if (entry == null) entry = load(builtin);
        entry.lastUsed = now;
        return entry.state == State.READY ? entry : null;
    }

    // ---------------------------------------------------------------- textures importees (T45)

    /**
     * Enregistre l'image importee d'empreinte {@code sha1} (40 hexadecimaux) : le cache en prend possession (fermee
     * apres televersement, ou si elle est remplacee). Image au format RGBA ({@code NativeImage} par defaut), en
     * equirectangulaire 2:1 (C79 : au plus 1024 x 512). Remplace une image deja enregistree sous la meme empreinte.
     * Fil de rendu.
     */
    public void registerCustom(String sha1, NativeImage image)
    {
        RenderSystem.assertOnRenderThread();
        if (!SpaceTextureRef.validSha1(sha1)) throw new IllegalArgumentException("Empreinte invalide : " + sha1);
        if (image.format() != NativeImage.Format.RGBA) {
            image.close();
            throw new IllegalArgumentException("Texture importee hors RGBA : " + sha1);
        }
        String hash = SpaceTextureRef.custom(sha1).id();
        releaseCustom(hash);
        Texture entry = new Texture(ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "space_body/custom/" + hash),
                null, true);
        entry.pending = CompletableFuture.supplyAsync(() -> prepare(mipmaps(image)), Util.backgroundExecutor());
        customs.put(hash, entry);
        entries.add(entry);
    }

    /** Vrai si l'image importee d'empreinte {@code sha1} est enregistree (prete ou en cours de televersement). */
    public boolean hasCustom(String sha1)
    {
        Texture entry = sha1 == null ? null : customs.get(sha1.toLowerCase(Locale.ROOT));
        return entry != null && entry.state != State.FAILED;
    }

    /**
     * Branche le demandeur des images importees (T45) : appele avec l'empreinte (40 hexadecimaux minuscules) d'un corps
     * {@code custom} dessine sans image enregistree, a chaque demande de texture (il filtre lui-meme les doublons, sans
     * allocation). Setup client.
     */
    public void setCustomRequester(Consumer<String> requester)
    {
        this.customRequester = requester;
    }

    /** Oublie toutes les images importees (deconnexion, T45). Fil de rendu. */
    public void releaseAllCustom()
    {
        for (Texture entry : customs.values()) discard(entry);
        customs.clear();
    }

    /** Oublie l'image importee d'empreinte {@code sha1} (memoire GPU rendue) ; sans effet si absente. */
    public void releaseCustom(String sha1)
    {
        if (sha1 == null) return;
        Texture entry = customs.remove(sha1.toLowerCase(Locale.ROOT));
        if (entry != null) discard(entry);
    }

    // ---------------------------------------------------------------- interne

    /** Lance le chargement d'une texture integree (decodage et mipmaps hors du fil de rendu). */
    private Texture load(SpaceBodyTextures.Builtin builtin)
    {
        Texture entry = new Texture(
                ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "space_body/builtin/" + builtin.id()), builtin,
                false);
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        ResourceLocation source = builtin.location();
        entry.pending = CompletableFuture.supplyAsync(() -> prepare(decode(resources, source)),
                Util.backgroundExecutor());
        builtins.put(builtin, entry);
        entries.add(entry);
        return entry;
    }

    /** Chargement termine : televersement et enregistrement dans le {@code TextureManager}, ou echec. */
    private void finish(Texture entry)
    {
        Prepared prepared = null;
        Throwable error = null;
        try {
            prepared = entry.pending.join();
        } catch (RuntimeException e) {
            error = e.getCause() != null ? e.getCause() : e;
        }
        entry.pending = null;
        if (prepared == null) {
            entry.state = State.FAILED;
            HoloLib.LOGGER.warn("Texture d'astre illisible : {}", entry.source != null ? entry.source : entry.key, error);
            return;
        }
        BodyTexture texture = new BodyTexture(entry.source, prepared.images());
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        manager.register(entry.key, texture);
        if (manager.getTexture(entry.key, null) != texture) {
            // Echec du televersement (deja journalise par le TextureManager) : repli en couleur unie.
            texture.closePrepared();
            entry.state = State.FAILED;
            return;
        }
        entry.texture = texture;
        entry.average = prepared.average();
        entry.luminance = (0.2126f * (entry.average >> 16 & 0xFF) + 0.7152f * (entry.average >> 8 & 0xFF)
                + 0.0722f * (entry.average & 0xFF)) / 255f;
        entry.renderType = TexturedType.of(entry.key);
        entry.state = State.READY;
    }

    /** Libere les textures integrees non demandees depuis {@link #EVICT_AFTER_MS} (prets ou en echec). */
    private void evictUnused()
    {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Texture entry = entries.get(i);
            if (entry.custom || entry.state == State.LOADING || now - entry.lastUsed < EVICT_AFTER_MS) continue;
            builtins.remove(entry.builtin);
            discard(entry);
        }
    }

    /** Retire une texture de la liste et du {@code TextureManager} ; un chargement en cours est ferme a sa fin. */
    private void discard(Texture entry)
    {
        entries.remove(entry);
        if (entry.pending != null) {
            entry.pending.thenAccept(prepared -> closeAll(prepared.images()));
            entry.pending = null;
        }
        if (entry.texture != null) Minecraft.getInstance().getTextureManager().release(entry.key);
        entry.texture = null;
        entry.renderType = null;
        entry.state = State.FAILED;
    }

    /** Lecture d'un PNG integre en RGBA et calcul de ses mipmaps (hors du fil de rendu). */
    private static NativeImage[] decode(ResourceManager resources, ResourceLocation source)
    {
        try (InputStream in = resources.open(source)) {
            return mipmaps(NativeImage.read(NativeImage.Format.RGBA, in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Niveaux de mipmap d'une image RGBA (le niveau 0 est l'image elle-meme). */
    private static NativeImage[] mipmaps(NativeImage image)
    {
        int levels = Math.min(MAX_MIP_LEVELS, Mth.log2(Math.max(1, Math.min(image.getWidth(), image.getHeight()))));
        return MipmapGenerator.generateMipLevels(new NativeImage[] {image}, levels);
    }

    /**
     * Couleur moyenne ({@code 0xRRGGBB}) du dernier niveau de mipmap (quelques pixels, moyenne de toute l'image) ; les
     * pixels de {@code NativeImage} sont ranges en ABGR (rouge dans l'octet faible).
     */
    private static Prepared prepare(NativeImage[] images)
    {
        NativeImage smallest = images[images.length - 1];
        long r = 0, g = 0, b = 0;
        int width = smallest.getWidth();
        int height = smallest.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = smallest.getPixelRGBA(x, y);
                r += pixel & 0xFF;
                g += pixel >> 8 & 0xFF;
                b += pixel >> 16 & 0xFF;
            }
        }
        long n = (long) width * height;
        return new Prepared(images, (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
    }

    private static void closeAll(NativeImage[] images)
    {
        if (images == null) return;
        for (NativeImage image : images) if (image != null) image.close();
    }

    /**
     * Texture GPU d'un astre : niveaux de mipmap fournis a la creation (televerses au premier {@code load}) ; un
     * rechargement des ressources relit le PNG d'une integree, garde l'image deja televersee d'une importee.
     */
    private static final class BodyTexture extends AbstractTexture
    {
        private final ResourceLocation source;
        private NativeImage[] prepared;
        private boolean uploaded;

        BodyTexture(ResourceLocation source, NativeImage[] prepared)
        {
            this.source = source;
            this.prepared = prepared;
        }

        @Override
        public void load(ResourceManager resources) throws IOException
        {
            NativeImage[] images = prepared;
            prepared = null;
            if (images == null) {
                if (source == null) {
                    if (uploaded) return;
                    throw new IOException("Texture importee sans image");
                }
                try {
                    images = decode(resources, source);
                } catch (UncheckedIOException e) {
                    throw e.getCause();
                }
            }
            upload(images);
        }

        /** Niveaux {@code 0..n} : filtre lineaire avec mipmaps, {@code u} repete, {@code v} borne. Ferme les images. */
        private void upload(NativeImage[] images)
        {
            int maxLevel = images.length - 1;
            try {
                TextureUtil.prepareImage(getId(), maxLevel, images[0].getWidth(), images[0].getHeight());
                for (int level = 0; level <= maxLevel; level++) {
                    NativeImage image = images[level];
                    image.upload(level, 0, 0, 0, 0, image.getWidth(), image.getHeight(), true, false, maxLevel > 0,
                            false);
                }
                // Texture liee par prepareImage : repetition en u (couture), bord fige en v (pas de melange des poles).
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                uploaded = true;
            } finally {
                closeAll(images);
            }
        }

        void closePrepared()
        {
            closeAll(prepared);
            prepared = null;
        }

        @Override
        public void close()
        {
            closePrepared();
        }
    }

    /**
     * {@code RenderType} local d'une texture d'astre (sous-classe pour acceder aux etats proteges, jamais instanciee) :
     * {@code POSITION_COLOR_TEX}, quads opaques, faces arriere eliminees, test et ecriture de profondeur normaux (la
     * profondeur de la passe du ciel), filtre lineaire avec mipmaps.
     */
    private static final class TexturedType extends RenderType
    {
        static RenderType of(ResourceLocation texture)
        {
            return create(HoloLib.MODID + ":space_body_" + texture.getPath(), DefaultVertexFormat.POSITION_COLOR_TEX,
                    VertexFormat.Mode.QUADS, 256, false, false,
                    CompositeState.builder()
                            .setShaderState(POSITION_COLOR_TEX_SHADER)
                            .setTextureState(new TextureStateShard(texture, true, true))
                            .createCompositeState(false));
        }

        private TexturedType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                             boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear)
        {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
