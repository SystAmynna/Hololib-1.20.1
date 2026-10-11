package com.amynna.hololib.client.render;

import com.amynna.hololib.HoloLib;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Shaderpack actif (Oculus) : {@code IrisApi.getInstance().isShaderPackInUse()} par reflexion, sans dependance de
 * compilation, resolu une seule fois. Sans Oculus, ou si l'appel echoue (journalise une fois) : toujours faux.
 * <p>
 * Avec un shaderpack, le monde est dessine dans les gbuffers d'Oculus puis compose ({@code composite}, {@code final})
 * vers la cible principale avant {@code AFTER_LEVEL} ; voir l'ordre de rendu dans {@code package-info}.
 */
public final class ShaderPacks
{
    private static final MethodHandle IN_USE = resolve();
    private static boolean failed;

    private ShaderPacks() {}

    /** Vrai si un shaderpack est utilise pour la frame courante. */
    public static boolean inUse()
    {
        if (IN_USE == null || failed) return false;
        try
        {
            return (boolean) IN_USE.invokeExact();
        }
        catch (Throwable e)
        {
            failed = true;
            HoloLib.LOGGER.error("Oculus : isShaderPackInUse a echoue, shaderpack considere inactif", e);
            return false;
        }
    }

    private static MethodHandle resolve()
    {
        if (!ModList.get().isLoaded("oculus")) return null;
        try
        {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Object instance = lookup.findStatic(api, "getInstance", MethodType.methodType(api)).invoke();
            return lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        }
        catch (Throwable e)
        {
            HoloLib.LOGGER.error("Oculus present mais IrisApi introuvable : shaderpacks ignores", e);
            return null;
        }
    }
}
