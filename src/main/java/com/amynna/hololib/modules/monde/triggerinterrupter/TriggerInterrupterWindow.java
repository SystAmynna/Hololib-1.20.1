package com.amynna.hololib.modules.monde.triggerinterrupter;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloThemeSource;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Fenetre du Trigger Interrupter : ancrage {@code World}, {@code PUBLIC}, utilisable par tous ({@code EVERYONE} : c'est
 * le module qui decide, cote serveur, si un joueur peut agir). Un titre, le bouton ({@link TriggerButton}) et un texte
 * facultatif en dessous. Les textes viennent de l'etat synchronise du module, relus par {@link #showState}.
 * <p>
 * La taille d'une fenetre est fixe : quand le texte du dessous est vide, il n'est pas dessine et <b>le bouton prend sa
 * place</b> (aucun espace vide a l'interieur). Editable par les operateurs (translation, rotation, echelle).
 */
public final class TriggerInterrupterWindow extends HoloWindow implements HoloEditTranslation, HoloEditRotation,
        HoloEditScale
{
    public static final int WIDTH = 120;
    public static final int HEIGHT = 80;
    /** Taille d'un pixel, en blocs. */
    public static final float PIXEL_SCALE = 1f / 100f;
    /** Hauteur par defaut du centre de la fenetre au-dessus du marker, en blocs. */
    public static final float DEFAULT_HEIGHT = 1.5f;

    private static final int MARGIN = 6;
    private static final int TITLE_HEIGHT = 11;
    private static final int BUTTON_Y = 17;
    private static final int FOOTER_HEIGHT = 10;
    private static final int GAP = 3;
    private static final HoloColor FOOTER = HoloColor.special(0xFF).darker(0.37f);

    private final TriggerInterrupterModule module;
    private final HoloLabel titleLabel;
    private final TriggerButton button;
    private final HoloLabel footerLabel;
    private boolean footerShown = true;

    /** @param module module qui porte la fenetre (evenements du bouton envoyes au serveur) */
    TriggerInterrupterWindow(String key, TriggerInterrupterModule module)
    {
        super(key, new HoloAnchor.World(), HoloTransform.translation(0f, DEFAULT_HEIGHT, 0f), WIDTH, HEIGHT,
                PIXEL_SCALE);
        this.module = Objects.requireNonNull(module);
        setVisibility(HoloVisibility.PUBLIC);
        setThemeSource(HoloThemeSource.SERVER);
        setAccess(HoloAccess.EVERYONE);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK, HoloControl.MIDDLE_CLICK);
        root().setPattern(ConfigWindow.BACKGROUND_ALPHA).setBorder(ConfigWindow.ACCENT, 1f);
        titleLabel = root().add(new HoloLabel(4, 3, WIDTH - 8, TITLE_HEIGHT, Component.empty())
                .setAlign(HoloLabel.Align.CENTER).setColor(ConfigWindow.ACCENT));
        button = root().add(new TriggerButton(MARGIN, BUTTON_Y, WIDTH - 2 * MARGIN, buttonHeight(true),
                new TriggerButton.Listener()
                {
                    @Override
                    public void down(int b) { module.requestDown(b); }

                    @Override
                    public void up(int b) { module.requestUp(b); }

                    @Override
                    public void beat(int heldMask) { module.requestBeat(heldMask); }

                    @Override
                    public void scroll(boolean up) { module.requestScroll(up); }
                }));
        footerLabel = root().add(new HoloLabel(MARGIN, HEIGHT - MARGIN - FOOTER_HEIGHT + 1, WIDTH - 2 * MARGIN,
                FOOTER_HEIGHT, Component.empty()).setAlign(HoloLabel.Align.CENTER).setColor(FOOTER));
    }

    private static float buttonHeight(boolean withFooter)
    {
        return HEIGHT - BUTTON_Y - MARGIN - (withFooter ? FOOTER_HEIGHT + GAP : 0);
    }

    /** Reserve aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut la placer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    /** Relit les textes du module ; texte du dessous vide : non dessine, le bouton prend sa place. */
    void showState()
    {
        titleLabel.setText(Component.literal(module.title()));
        button.setText(Component.literal(module.buttonText()));
        boolean footer = !module.footer().isEmpty();
        footerLabel.setText(Component.literal(module.footer()));
        if (footer != footerShown) {
            footerShown = footer;
            footerLabel.setVisible(footer);
            button.setBounds(MARGIN, BUTTON_Y, WIDTH - 2 * MARGIN, buttonHeight(footer));
        }
    }
}
