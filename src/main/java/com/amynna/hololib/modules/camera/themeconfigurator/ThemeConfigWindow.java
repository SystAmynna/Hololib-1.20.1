package com.amynna.hololib.modules.camera.themeconfigurator;

import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemeColor;
import com.amynna.hololib.holo.theme.HoloThemeEntry;
import com.amynna.hololib.holo.theme.HoloThemeLibrary;
import com.amynna.hololib.holo.theme.HoloThemePattern;
import com.amynna.hololib.holo.theme.HoloThemePatterns;
import com.amynna.hololib.holo.theme.HoloThemeSelector;
import com.amynna.hololib.holo.theme.HoloThemeSource;
import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetColorPicker;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fenetre de configuration des themes ({@link ThemeConfiguratorBase}), en trois colonnes sous les onglets
 * Standard / Staff (Staff : ops seulement) :
 * <ul>
 * <li>bibliotheque : "Suivre le serveur" (configurateur joueur) puis les themes ; un clic choisit le theme du jeu ;
 * Nouveau (theme integre copie), Dupliquer (theme montre copie), nom (modifiable), Supprimer (deux clics) ;</li>
 * <li>parametres : motif et six couleurs, avec leur pastille ; un clic choisit le parametre a regler ;</li>
 * <li>outil du parametre : {@link HoloWidgetColorPicker} ou liste des motifs du registre ; theme non modifiable
 * (serveur, autre joueur, suivi du serveur) : "Dupliquer pour modifier".</li>
 * </ul>
 * Reconstruite par le module quand la bibliotheque, la selection, l'onglet ou le parametre changent.
 */
final class ThemeConfigWindow extends ConfigWindow
{
    static final int WIDTH = 310;
    private static final int MARGIN = 6;
    private static final int GAP = 6;
    private static final int ROW = 12;
    private static final int TAB_WIDTH = 60;
    private static final int TAB_HEIGHT = 14;
    private static final int COLUMN = 90;
    private static final int TOOL_WIDTH = WIDTH - 2 * MARGIN - 2 * COLUMN - 2 * GAP;
    private static final int LIST_HEIGHT = 96;
    private static final int TOP = 4 + TAB_HEIGHT + GAP;
    private static final int COLUMN_HEIGHT = LIST_HEIGHT + 3 * (ROW + 2) + 2;
    private static final int CONTENT_HEIGHT = TOP + COLUMN_HEIGHT + MARGIN;

    static final HoloColor TAB_ON = HoloColor.primary(0xA0);
    static final HoloColor TAB_OFF = HoloColor.secondary(0x40);
    private static final HoloColor ROW_SELECTED = HoloColor.selection(0x90).darker(0.3f);
    private static final HoloColor ROW_IDLE = HoloColor.secondary(0x40);
    private static final HoloColor BORDER = HoloColor.primary(0xFF);
    private static final HoloColor BORDER_SELECTED = HoloColor.selection(0xFF);
    private static final HoloColor TEXT = HoloColor.special(0xFF);
    private static final HoloColor MUTED = HoloColor.special(0xFF).darker(0.4f);
    private static final HoloColor DELETE = HoloColor.critical(0x60).darker(0.4f);
    private static final HoloColor DELETE_CONFIRM = HoloColor.critical(0xC0).darker(0.2f);
    private static final HoloColor DELETE_BORDER = HoloColor.critical(0xFF);

    private final ThemeConfiguratorBase module;
    private final HoloWidgetButton standardTab;
    private final HoloWidgetButton staffTab;
    private final HoloLabel status;
    private final HoloWidgetList themes;
    private final HoloWidgetTextBox name;
    private final HoloWidgetButton delete;
    private final List<ThemeParamRow> params = new ArrayList<>();
    private final HoloWidgetColorPicker picker;
    private final HoloWidgetList patterns;
    private final HoloLabel locked;
    private final HoloLabel lockedDetail;
    private boolean confirmDelete;

    ThemeConfigWindow(String key, Component title, ThemeConfiguratorBase module)
    {
        super(key, title, HoloTransform.translation(-20f, 0f, 0.75f), WIDTH, CONTENT_HEIGHT, 1f / 400f);
        this.module = module;
        if (module.admin()) setThemeSource(HoloThemeSource.SERVER).setStaffTheme(true);
        HoloPanel content = content();
        standardTab = content.add(new HoloWidgetButton(MARGIN, 4, TAB_WIDTH, TAB_HEIGHT,
                Component.literal("Standard"), button -> selectTab(false)));
        staffTab = content.add(new HoloWidgetButton(MARGIN + TAB_WIDTH + 4, 4, TAB_WIDTH, TAB_HEIGHT,
                Component.literal("Staff"), button -> selectTab(true)));
        float statusX = MARGIN + 2 * TAB_WIDTH + 12;
        status = content.add(new HoloLabel(statusX, 4, WIDTH - MARGIN - statusX, TAB_HEIGHT, Component.empty()));

        // Bibliotheque.
        float x = MARGIN;
        float y = TOP;
        themes = content.add(new HoloWidgetList(x, y, COLUMN, LIST_HEIGHT, ROW));
        y += LIST_HEIGHT + 2;
        float half = (COLUMN - 2) / 2f;
        content.add(new HoloWidgetButton(x, y, half, ROW, Component.literal("Nouveau"),
                button -> module.requestCreate(-1)));
        content.add(new HoloWidgetButton(x + half + 2, y, half, ROW, Component.literal("Dupliquer"),
                button -> module.requestCreate(module.shownId())));
        y += ROW + 2;
        name = content.add(new HoloWidgetTextBox(x, y, COLUMN, ROW, HoloTheme.MAX_NAME, false));
        name.setPlaceholder(Component.literal("Nom"));
        name.setOnSubmit((box, text) -> module.requestRename(text));
        y += ROW + 2;
        delete = content.add(new HoloWidgetButton(x, y, COLUMN, ROW, Component.literal("Supprimer"),
                button -> pressDelete()));

        // Parametres.
        x += COLUMN + GAP;
        y = TOP;
        content.add(new HoloLabel(x, y, COLUMN, ROW, Component.literal("Parametres")).setColor(MUTED));
        y += ROW + 2;
        params.add(content.add(new ThemeParamRow(x, y, COLUMN, ROW, ThemeConfiguratorBase.PARAM_PATTERN, module)));
        for (HoloThemeColor role : HoloThemeColor.values()) {
            y += ROW + 2;
            params.add(content.add(new ThemeParamRow(x, y, COLUMN, ROW, role.ordinal(), module)));
        }

        // Outil du parametre.
        x += COLUMN + GAP;
        picker = content.add(new HoloWidgetColorPicker(x, TOP, TOOL_WIDTH, COLUMN_HEIGHT - 20));
        picker.setOnChange((widget, rgb) -> {
            HoloThemeColor role = HoloThemeColor.byOrdinal(module.param());
            if (role != null) module.previewColor(role, rgb);
        });
        picker.setOnCommit((widget, rgb) -> {
            HoloThemeColor role = HoloThemeColor.byOrdinal(module.param());
            if (role != null) module.requestColor(role, rgb);
        });
        patterns = content.add(new HoloWidgetList(x, TOP, TOOL_WIDTH, COLUMN_HEIGHT, ROW));
        locked = content.add(new HoloLabel(x, TOP, TOOL_WIDTH, ROW, Component.literal("Non modifiable"))
                .setColor(MUTED));
        lockedDetail = content.add(new HoloLabel(x, TOP + ROW + 2, TOOL_WIDTH, ROW,
                Component.literal("Dupliquer pour modifier")).setColor(MUTED));
    }

    @Override
    public boolean shown() { return opened() && (!module.admin() || HoloThemes.localOp()); }

    private void selectTab(boolean staff)
    {
        module.selectTab(staff);
        rebuild();
    }

    private void pressDelete()
    {
        if (!confirmDelete) {
            confirmDelete = true;
            delete.setText(Component.literal("Confirmer ?"));
            delete.setColors(DELETE_CONFIRM, DELETE_BORDER, TEXT);
            return;
        }
        confirmDelete = false;
        module.requestDelete();
    }

    /** Widgets d'apres la bibliotheque, la selection et l'etat local du module (client). */
    void rebuild()
    {
        boolean op = HoloThemes.localOp();
        boolean staff = module.staffTab();
        standardTab.setColors(staff ? TAB_OFF : TAB_ON, BORDER, TEXT);
        staffTab.setColors(staff ? TAB_ON : TAB_OFF, BORDER, TEXT);
        staffTab.setVisible(op);

        HoloThemeEntry shown = module.shownEntry();
        boolean editable = module.editable();
        int selection = module.selection(staff);
        String text = selection == HoloThemeSelector.FOLLOW_SERVER ? "Serveur : " : "";
        status.setText(Component.literal(text + module.shownTheme().name()));

        themes.clearItems();
        if (!module.admin()) {
            addTheme(Component.literal("Suivre le serveur"), HoloThemeSelector.FOLLOW_SERVER,
                    selection == HoloThemeSelector.FOLLOW_SERVER);
        }
        for (HoloThemeEntry entry : HoloThemes.library()) {
            addTheme(ThemeConfiguratorBase.label(entry), entry.id(), entry.id() == selection);
        }

        name.setVisible(editable);
        if (shown != null) name.setText(shown.theme().name());
        confirmDelete = false;
        delete.setText(Component.literal("Supprimer"));
        delete.setColors(DELETE, DELETE_BORDER, TEXT);
        delete.setVisible(editable && shown != null && !HoloThemeLibrary.builtin(shown.id()));

        for (ThemeParamRow row : params) row.refresh();
        HoloThemeColor role = HoloThemeColor.byOrdinal(module.param());
        picker.setVisible(editable && role != null);
        if (role != null) picker.setRgb(module.draft().rgb(role));
        patterns.setVisible(editable && role == null);
        if (patterns.items().isEmpty()) buildPatterns();
        refreshPatterns();
        locked.setVisible(!editable);
        lockedDetail.setVisible(!editable);
    }

    private void addTheme(Component label, int id, boolean selected)
    {
        HoloWidgetButton row = themes.addItem(new HoloWidgetButton(0, 0, COLUMN, ROW, label,
                button -> module.requestSelect(id)));
        row.setColors(selected ? ROW_SELECTED : ROW_IDLE, selected ? BORDER_SELECTED : BORDER, TEXT);
    }

    private void buildPatterns()
    {
        for (Map.Entry<ResourceKey<HoloThemePattern>, HoloThemePattern> entry
                : HoloThemePatterns.registry().getEntries()) {
            ResourceLocation id = entry.getKey().location();
            patterns.addItem(new PatternRow(id, entry.getValue().name()));
        }
    }

    private void refreshPatterns()
    {
        ResourceLocation current = module.draft().patternId();
        for (var item : patterns.items()) {
            if (item instanceof PatternRow row) {
                boolean selected = row.id.equals(current);
                row.setColors(selected ? ROW_SELECTED : ROW_IDLE, selected ? BORDER_SELECTED : BORDER, TEXT);
            }
        }
    }

    /** Ligne de la liste des motifs : un clic applique le motif au theme montre. */
    private final class PatternRow extends HoloWidgetButton
    {
        private final ResourceLocation id;

        PatternRow(ResourceLocation id, Component name)
        {
            super(0, 0, TOOL_WIDTH, ROW, name, button -> {});
            this.id = id;
            setOnPress(button -> {
                module.requestPattern(this.id);
                refreshPatterns();
            });
        }
    }
}
