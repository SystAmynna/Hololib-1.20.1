package com.amynna.hololib.modules.monde.areadisplayer.terrain;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainPackets.Header;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainPackets.Sections;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainPackets.Subscribe;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainPackets.Tracks;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainPackets.Unsubscribe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal reseau <b>dedie au terrain de l'Area Displayer</b> : {@code SimpleChannel} {@code hololib:area_terrain},
 * protocole propre ({@value #PROTOCOL}), independant de {@code HoloNetwork} (inchange ; exception validee comme le
 * canal des textures Space). Le terrain et les positions suivies ne passent pas par l'etat du module : seuls les
 * clients abonnes (qui ont le miroir du module) les recoivent, sections modifiees seulement. Enregistre au setup
 * commun par ce meme abonne ({@link #onCommonSetup}).
 * <p>
 * Paquets ({@link AreaTerrainPackets}) :
 * <pre>
 * client -> serveur
 *   0 Subscribe   (utf marker, uuid instance)            le client a le miroir du Displayer : terrain demande
 *   1 Unsubscribe (utf marker, uuid instance)            miroir retire
 * serveur -> client
 *   2 Header      (utf marker, uuid instance, varint generation, boolean zone [+ 3 int coin min, 3 varint taille,
 *                 varint resolution])
 *                                                        nouvelle grille (abonnement, zone changee) : voxels vides
 *   3 Sections    (utf marker, varint generation, varint n, n x (varlong cle de section, byte uniforme ou -1
 *                 [+ octets deflates]))                  sections changees, 2 bits par voxel
 *   4 Tracks      (utf marker, varint generation, varint n, n x (varint id, byte genre, 3 float relatifs au coin
 *                 min en blocs, boolean nom [+ utf nom]))
 *                                                        joueurs et entites de la zone, toutes les 2 ticks
 * </pre>
 * Logique serveur : {@code AreaDisplayerModule} (abonnes, envois) ; logique client :
 * {@code client.modules.monde.areadisplayer.AreaTerrainClient} (atteinte par {@code DistExecutor}, aucune classe
 * client chargee sur un serveur dedie).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AreaTerrainNetwork
{
    /** Protocole du canal (distinct de celui de {@code HoloNetwork}). */
    public static final String PROTOCOL = "3";
    /** Octets compresses envoyes au plus par tick et par abonne (paquet de sections). */
    public static final int BYTES_PER_TICK = 64 * 1024;
    /** Entrees au plus par paquet de positions suivies. */
    public static final int MAX_TRACKS = 256;
    /** Longueur maximale d'un nom suivi. */
    public static final int MAX_NAME = 64;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "area_terrain"), () -> PROTOCOL, PROTOCOL::equals,
            PROTOCOL::equals);

    private AreaTerrainNetwork() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(AreaTerrainNetwork::register);
    }

    private static void register()
    {
        int id = 0;
        CHANNEL.messageBuilder(Subscribe.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Subscribe::encode).decoder(Subscribe::decode).consumerMainThread(Subscribe::handle).add();
        CHANNEL.messageBuilder(Unsubscribe.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Unsubscribe::encode).decoder(Unsubscribe::decode).consumerMainThread(Unsubscribe::handle)
                .add();
        CHANNEL.messageBuilder(Header.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Header::encode).decoder(Header::decode).consumerMainThread(Header::handle).add();
        CHANNEL.messageBuilder(Sections.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sections::encode).decoder(Sections::decode).consumerMainThread(Sections::handle).add();
        CHANNEL.messageBuilder(Tracks.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Tracks::encode).decoder(Tracks::decode).consumerMainThread(Tracks::handle).add();
    }

    /** Serveur : paquet au seul joueur {@code player}. */
    public static void send(ServerPlayer player, Object packet)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Client : paquet au serveur. */
    public static void sendToServer(Object packet)
    {
        CHANNEL.sendToServer(packet);
    }
}
