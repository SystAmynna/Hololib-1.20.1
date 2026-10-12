package com.amynna.hololib.holo.theme.pattern;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemePattern;
import com.amynna.hololib.holo.theme.HoloThemePatterns;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Motifs integres au mod, enregistres dans {@link HoloThemePatterns} (identifiants : constantes de
 * {@link HoloTheme}).
 */
public final class HoloLibThemePatterns
{
    public static final DeferredRegister<HoloThemePattern> REGISTER =
            DeferredRegister.create(HoloThemePatterns.REGISTRY_NAME, HoloLib.MODID);

    static {
        REGISTER.register(HoloTheme.SOLID.getPath(), SolidPattern::new);
        REGISTER.register(HoloTheme.SCANLINES.getPath(), ScanlinePattern::new);
        REGISTER.register(HoloTheme.DIAGONAL.getPath(), DiagonalPattern::new);
        REGISTER.register(HoloTheme.GRID.getPath(), GridPattern::new);
    }

    private HoloLibThemePatterns() {}
}
