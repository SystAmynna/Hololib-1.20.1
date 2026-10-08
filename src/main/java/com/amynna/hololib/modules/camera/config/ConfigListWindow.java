package com.amynna.hololib.modules.camera.config;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Liste de Config : un bouton par module {@link Configurable} de la memoire du joueur ; un clic ouvre la fenetre de
 * configuration du module devant le regard (ou l'y ramene). La ligne d'une fenetre ouverte est surlignee. Ouverte par
 * le bouton ecrou, a droite du regard par defaut.
 * <p>
 * Le contenu vient des modules voisins : {@link #sync} est appele cote client chaque tick.
 */
public final class ConfigListWindow extends ConfigWindow
{
    private static final int WIDTH = 150;
    private static final int CONTENT_HEIGHT = 104;
    private static final int MARGIN = 5;
    private static final float ROW_HEIGHT = 16f;
    private static final int ROW = 0x6020A0FF;
    private static final int ROW_OPEN = 0xA040C0FF;

    private final HoloWidgetList list;
    private final List<Configurable> entries = new ArrayList<>();
    private final List<ConfigWindow> windows = new ArrayList<>();
    private final List<ConfigWindow> windowsView = Collections.unmodifiableList(windows);
    private final List<String> ids = new ArrayList<>();
    private final List<HoloWidgetButton> rows = new ArrayList<>();
    private final List<Configurable> scratch = new ArrayList<>();

    public ConfigListWindow(String key)
    {
        super(key, Component.literal("Configuration"), HoloTransform.translation(24f, -4f, 0.7f), WIDTH,
                CONTENT_HEIGHT, 1f / 400f);
        list = content().add(new HoloWidgetList(MARGIN, MARGIN, WIDTH - 2 * MARGIN, CONTENT_HEIGHT - 2 * MARGIN,
                ROW_HEIGHT));
    }

    /** Fenetres de configuration listees, dans l'ordre des lignes (vue non modifiable). */
    public List<ConfigWindow> windows() { return windowsView; }

    /**
     * Cote client : modules {@link Configurable} parmi {@code modules} (memoire du joueur). Reconstruit les lignes et
     * relie les epingles a {@code config} si l'ensemble a change, sinon recopie seulement les etats.
     */
    public void sync(Collection<HoloModule> modules, ConfigModule config)
    {
        scratch.clear();
        for (HoloModule module : modules) {
            if (module instanceof Configurable configurable && configurable.configWindow() != null) {
                scratch.add(configurable);
            }
        }
        if (!scratch.equals(entries)) rebuild(config);
        for (int i = 0; i < windows.size(); i++) {
            ConfigWindow window = windows.get(i);
            window.setPinned(config.pinned(ids.get(i)));
            rows.get(i).setColors(window.isOpen() ? ROW_OPEN : ROW, ACCENT, 0xFFFFFFFF);
        }
        setPinned(config.pinned(config.listId()));
    }

    private void rebuild(ConfigModule config)
    {
        entries.clear();
        entries.addAll(scratch);
        windows.clear();
        ids.clear();
        rows.clear();
        list.clearItems();
        for (Configurable entry : entries) {
            ConfigWindow window = entry.configWindow();
            String id = ((HoloModule) entry).type().id().toString();
            window.link(pinned -> config.requestPinned(id, pinned));
            windows.add(window);
            ids.add(id);
            rows.add(list.addItem(new HoloWidgetButton(0, 0, 1, ROW_HEIGHT, entry.configName(),
                    button -> window.open())));
        }
    }
}
