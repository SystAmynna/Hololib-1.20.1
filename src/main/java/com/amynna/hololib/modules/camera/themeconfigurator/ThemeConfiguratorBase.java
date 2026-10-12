package com.amynna.hololib.modules.camera.themeconfigurator;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemeColor;
import com.amynna.hololib.holo.theme.HoloThemeEntry;
import com.amynna.hololib.holo.theme.HoloThemeLibrary;
import com.amynna.hololib.holo.theme.HoloThemeSelector;
import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.config.Configurable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Code commun des configurateurs de themes (memoire de joueur, {@link Configurable}) : fenetre de configuration
 * ({@link ThemeConfigWindow}) et fenetre d'apercu ({@link ThemePreviewWindow}) placee a sa droite. Deux onglets,
 * standard et staff (ops seulement) : chacun choisit un theme de la bibliotheque du serveur et edite ses parametres
 * (motif, six couleurs). Les sous-classes disent quelle selection est modifiee :
 * <ul>
 * <li>{@link ThemeConfiguratorModule} : la selection du joueur ("Suivre le serveur" par defaut) ; il edite ses
 * propres themes ;</li>
 * <li>{@link AdminThemeConfiguratorModule} : la selection du serveur ; il edite tous les themes (op verifie en
 * premier par le serveur).</li>
 * </ul>
 * Actions (proprietaire -> serveur -> {@link HoloThemeLibrary}) : {@code SELECT}, {@code CREATE} (copie d'un theme,
 * selectionnee), {@code RENAME}, {@code DELETE}, {@code SET_COLOR}, {@code SET_PATTERN}. Cote client, l'onglet, le
 * parametre choisi et le brouillon de l'apercu sont locaux (jamais synchronises).
 */
public abstract class ThemeConfiguratorBase extends HoloModule implements Configurable
{
    static final byte ACTION_SELECT = 0;
    static final byte ACTION_CREATE = 1;
    static final byte ACTION_RENAME = 2;
    static final byte ACTION_DELETE = 3;
    static final byte ACTION_COLOR = 4;
    static final byte ACTION_PATTERN = 5;

    /** Parametre "motif" dans la liste des parametres (les couleurs : ordinal du role). */
    static final int PARAM_PATTERN = -1;

    // Client : etat local de l'interface.
    private boolean staffTab;
    private int param = PARAM_PATTERN;
    /** Brouillon de l'apercu : theme affiche, modifie pendant un glisser du selecteur. */
    private HoloTheme draft;
    private int draftSource = Integer.MIN_VALUE;
    private int shownRevision = -1;
    private int shownSelection = Integer.MIN_VALUE;
    private boolean shownOp;

    private ThemeConfigWindow config;
    private ThemePreviewWindow preview;

    /** Vrai pour le configurateur admin (selection du serveur, tous les themes). */
    abstract boolean admin();

    /** Theme choisi pour un jeu : celui du joueur (ou {@link HoloThemeSelector#FOLLOW_SERVER}) ou du serveur. */
    abstract int selection(boolean staff);

    /** Serveur : applique une selection validee ; vrai si l'etat change. */
    abstract boolean applySelection(ServerPlayer player, HoloThemeLibrary library, boolean staff, int id);

    @Override
    public void onLoad()
    {
        config = addObject(new ThemeConfigWindow("config", configName(), this));
        preview = addObject(new ThemePreviewWindow("preview", this, config));
    }

    @Override
    public ConfigWindow configWindow() { return config; }

    // ---------------------------------------------------------------- client

    @Override
    public void clientTick()
    {
        boolean op = HoloThemes.localOp();
        if (!op && staffTab) staffTab = false;
        int revision = HoloThemes.libraryRevision();
        int selected = shownId();
        if (revision != shownRevision || selected != shownSelection || op != shownOp) {
            shownRevision = revision;
            shownSelection = selected;
            shownOp = op;
            resetDraft();
            config.rebuild();
        }
        if (host().canAct()) preview.follow();
    }

    @Override
    public void onSync() { shownRevision = -1; }

    boolean staffTab() { return staffTab; }

    int param() { return param; }

    /** Theme montre par l'onglet courant : celui choisi, sinon (suivre le serveur, theme disparu) celui du serveur. */
    int shownId()
    {
        int id = selection(staffTab);
        if (id == HoloThemeSelector.FOLLOW_SERVER || HoloThemes.entry(id) == null) {
            return HoloThemes.serverSelection(staffTab);
        }
        return id;
    }

    HoloThemeEntry shownEntry() { return HoloThemes.entry(shownId()); }

    /** Theme montre, ou le theme par defaut du jeu si la bibliotheque n'est pas encore recue. */
    HoloTheme shownTheme()
    {
        HoloThemeEntry entry = shownEntry();
        if (entry != null) return entry.theme();
        return staffTab ? HoloTheme.DEFAULT_STAFF : HoloTheme.DEFAULT;
    }

    /** Vrai si le theme montre est modifiable par le proprietaire (et non "suivre le serveur"). */
    boolean editable()
    {
        HoloThemeEntry entry = shownEntry();
        if (entry == null) return false;
        if (admin()) return HoloThemes.localOp();
        return selection(staffTab) != HoloThemeSelector.FOLLOW_SERVER && owner() != null
                && owner().equals(entry.creator());
    }

    /** Theme de l'apercu (brouillon). */
    HoloTheme draft()
    {
        if (draft == null) resetDraft();
        return draft;
    }

    void resetDraft()
    {
        draft = shownTheme();
        draftSource = shownId();
    }

    /** Couleur en cours de reglage (apercu seulement). */
    void previewColor(HoloThemeColor role, int rgb) { draft = draft().withColor(role, rgb); }

    void selectTab(boolean staff)
    {
        if (staff && !HoloThemes.localOp() || staff == staffTab) return;
        staffTab = staff;
        shownSelection = Integer.MIN_VALUE;
    }

    void selectParam(int param)
    {
        if (param == this.param) return;
        this.param = param;
        config.rebuild();
    }

    void requestSelect(int id)
    {
        if (!host().canAct()) return;
        boolean staff = staffTab;
        sendAction(buf -> {
            buf.writeByte(ACTION_SELECT);
            buf.writeBoolean(staff);
            buf.writeVarInt(id);
        });
        localSelect(staff, id);
    }

    /** Selection appliquee localement en attendant le serveur (configurateur joueur). */
    void localSelect(boolean staff, int id) {}

    /** Nouveau theme : copie de {@code from} (-1 : theme integre du jeu), selectionne pour l'onglet courant. */
    void requestCreate(int from)
    {
        if (!host().canAct()) return;
        boolean staff = staffTab;
        sendAction(buf -> {
            buf.writeByte(ACTION_CREATE);
            buf.writeBoolean(staff);
            buf.writeVarInt(from);
        });
    }

    void requestRename(String name)
    {
        int id = shownId();
        if (!host().canAct() || !editable()) return;
        sendAction(buf -> {
            buf.writeByte(ACTION_RENAME);
            buf.writeVarInt(id);
            buf.writeUtf(HoloTheme.cleanName(name), HoloTheme.MAX_NAME * 4);
        });
    }

    void requestDelete()
    {
        int id = shownId();
        if (!host().canAct() || !editable()) return;
        sendAction(buf -> {
            buf.writeByte(ACTION_DELETE);
            buf.writeVarInt(id);
        });
    }

    void requestColor(HoloThemeColor role, int rgb)
    {
        int id = shownId();
        previewColor(role, rgb);
        if (!host().canAct() || !editable()) return;
        sendAction(buf -> {
            buf.writeByte(ACTION_COLOR);
            buf.writeVarInt(id);
            buf.writeByte(role.ordinal());
            buf.writeMedium(rgb & 0xFFFFFF);
        });
    }

    void requestPattern(ResourceLocation pattern)
    {
        int id = shownId();
        draft = draft().withPattern(pattern);
        if (!host().canAct() || !editable()) return;
        sendAction(buf -> {
            buf.writeByte(ACTION_PATTERN);
            buf.writeVarInt(id);
            buf.writeResourceLocation(pattern);
        });
    }

    // ---------------------------------------------------------------- serveur

    /** Vrai si le joueur peut utiliser le module (admin : op). */
    boolean allowed(ServerPlayer player) { return !admin() || player.hasPermissions(HoloThemeLibrary.OP_LEVEL); }

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!player.getUUID().equals(owner()) || !allowed(player)) return;
        HoloThemeLibrary library = HoloThemeLibrary.of(player.server);
        byte action = buf.readByte();
        switch (action) {
            case ACTION_SELECT -> {
                boolean staff = buf.readBoolean();
                int id = buf.readVarInt();
                if (staff && !player.hasPermissions(HoloThemeLibrary.OP_LEVEL)) return;
                if (applySelection(player, library, staff, id)) markDirty();
            }
            case ACTION_CREATE -> {
                boolean staff = buf.readBoolean();
                int from = buf.readVarInt();
                if (staff && !player.hasPermissions(HoloThemeLibrary.OP_LEVEL)) return;
                HoloThemeEntry source = library.get(from);
                HoloTheme base = source != null ? source.theme() : staff ? HoloTheme.DEFAULT_STAFF : HoloTheme.DEFAULT;
                String name = source != null ? base.name() + " 2" : "Theme";
                HoloThemeEntry created = library.create(admin() ? null : player, name, base);
                if (created != null && applySelection(player, library, staff, created.id())) markDirty();
            }
            case ACTION_RENAME -> {
                HoloThemeEntry entry = library.get(buf.readVarInt());
                String name = buf.readUtf(HoloTheme.MAX_NAME * 4);
                if (HoloThemeLibrary.mayEdit(player.getUUID(), admin(), entry)) library.rename(entry.id(), name);
            }
            case ACTION_DELETE -> {
                HoloThemeEntry entry = library.get(buf.readVarInt());
                if (HoloThemeLibrary.mayEdit(player.getUUID(), admin(), entry)) library.delete(entry.id());
            }
            case ACTION_COLOR -> {
                HoloThemeEntry entry = library.get(buf.readVarInt());
                HoloThemeColor role = HoloThemeColor.byOrdinal(buf.readUnsignedByte());
                int rgb = buf.readUnsignedMedium();
                if (role != null && HoloThemeLibrary.mayEdit(player.getUUID(), admin(), entry)) {
                    library.setColor(entry.id(), role, rgb);
                }
            }
            case ACTION_PATTERN -> {
                HoloThemeEntry entry = library.get(buf.readVarInt());
                ResourceLocation pattern = buf.readResourceLocation();
                if (HoloThemeLibrary.mayEdit(player.getUUID(), admin(), entry)) library.setPattern(entry.id(), pattern);
            }
            default -> {}
        }
    }

    /** Nom affiche d'un theme dans les listes : nom, et createur pour un theme de joueur. */
    static Component label(HoloThemeEntry entry)
    {
        if (entry.serverTheme()) return Component.literal(entry.theme().name());
        return Component.literal(entry.theme().name() + " (" + entry.creatorName() + ")");
    }
}
