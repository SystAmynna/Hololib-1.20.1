package com.amynna.hololib.demo;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Demonstration de memoire de joueur : trois fenetres, une par rendu local, et un cube dans le monde.
 * <ul>
 *     <li>{@code hud} : a l'ecran, en haut a droite (jamais vu par les autres joueurs, masque en F5).</li>
 *     <li>{@code local} : camera normale ({@code Local} relatif) ; reste en place quand on tourne la tete. Orientee
 *     dans la direction du regard au premier tick en modifiant son transform (exemple de deplacement par un module),
 *     sinon elle apparaitrait plein sud.</li>
 *     <li>{@code follow} : camera follow ; suit le regard, un peu a droite et en bas du centre.</li>
 *     <li>{@code cube} : ancre dans le monde au-dessus de la tete du joueur (origine = joueur).</li>
 * </ul>
 * Les fenetres locales et le cube sont publics : les joueurs qui inspectent le proprietaire les voient autour de lui.
 */
public final class DemoPlayerModule extends HoloModule
{
    private static final float LOCAL_DISTANCE = 2.5f;

    /** Vrai une fois la fenetre {@code local} orientee vers le regard du joueur (sauvegarde). */
    private boolean oriented;
    private HoloWindow local;

    @Override
    public void onLoad()
    {
        addObject(DemoWindows.labeled("hud", new HoloAnchor.Hud(1f, 0f),
                HoloTransform.translation(-88f, 32f, 0f).setScale(160f), 1f / 160f,
                "HUD", "Ecran, sous le HUD du jeu", 0xFF40C0FF));

        local = addObject(DemoWindows.labeled("local", new HoloAnchor.Local(false),
                HoloTransform.translation(0f, 0f, LOCAL_DISTANCE), 1f / 160f, "Camera", "Reste en place",
                0xFF80FF80));
        local.setVisibility(HoloVisibility.PUBLIC);

        HoloWindow follow = addObject(DemoWindows.labeled("follow", new HoloAnchor.Local(true),
                HoloTransform.translation(22f, 12f, 2f), 1f / 200f, "Camera follow", "Suit le regard",
                0xFFFF80C0));
        follow.setVisibility(HoloVisibility.PUBLIC);

        DemoCube cube = addObject(new DemoCube("cube", new HoloAnchor.World(), HoloTransform.translation(0f, 2.4f, 0f),
                0.3f, 0xA0FF80C0));
        cube.setVisibility(HoloVisibility.PUBLIC);
    }

    @Override
    public void serverTick(ServerLevel level)
    {
        if (oriented) return;
        Player player = level.getPlayerByUUID(owner());
        if (player == null) return;
        local.transform().setTranslation(player.getYRot(), 0f, LOCAL_DISTANCE);
        oriented = true;
        markDirty();
    }

    @Override
    public void save(CompoundTag tag) { tag.putBoolean("oriented", oriented); }

    @Override
    public void load(CompoundTag tag) { oriented = tag.getBoolean("oriented"); }
}
