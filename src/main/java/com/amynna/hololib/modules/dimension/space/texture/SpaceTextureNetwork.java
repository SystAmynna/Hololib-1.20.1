package com.amynna.hololib.modules.dimension.space.texture;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Begin;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Chunk;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Data;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Missing;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Request;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets.Result;
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
 * Canal reseau <b>dedie aux textures Space</b> (T45, decision D27) : {@code SimpleChannel} {@code hololib:space_textures},
 * protocole propre ({@value #PROTOCOL}), independant de {@code HoloNetwork} (inchange). Il transporte une image importee
 * par un operateur vers le serveur, en morceaux, puis la renvoie <b>au seul client</b> qui la demande. Enregistre au
 * setup commun par ce meme abonne ({@link #onCommonSetup}).
 * <p>
 * Paquets ({@link SpaceTexturePackets}) :
 * <pre>
 * client -> serveur
 *   0 Begin   (empreinte 20 o, varint taille)          debut d'un import (operateur)
 *   1 Chunk   (varint decalage, octets <= 30 000)      morceau de l'import en cours
 *   2 Request (empreinte 20 o)                         demande d'une texture importee inconnue du client
 * serveur -> client
 *   3 Result  (empreinte, octet etat, utf message)     reponse a un import : ACCEPT (envoyer les morceaux), DONE
 *                                                      (texture stockee ou deja presente), REFUSED (message)
 *   4 Data    (empreinte, varint taille, varint decalage, octets <= 30 000)
 *                                                      morceau d'une texture demandee
 *   5 Missing (empreinte)                              texture demandee absente du serveur
 * </pre>
 * Logique serveur : {@link SpaceTextureServer} ; stockage et validation : {@link SpaceTextureStore} ; logique client :
 * {@code client.modules.dimension.space.SpaceTextureClient} (atteinte par {@code DistExecutor}, aucune classe client
 * chargee sur un serveur dedie).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpaceTextureNetwork
{
    /** Protocole du canal (distinct de celui de {@code HoloNetwork}). */
    public static final String PROTOCOL = "1";

    /** Taille maximale d'un morceau (octets) : sous la limite de 32 767 o d'un paquet client -> serveur. */
    public static final int CHUNK_SIZE = 30_000;
    /** Taille maximale d'une texture importee, PNG encode (C79 : 2 Mio). */
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Largeur maximale d'une texture importee (pixels) ; hauteur = largeur / 2 (equirectangulaire 2:1). */
    public static final int MAX_WIDTH = 1024;
    /** Largeur minimale d'une texture importee (pixels). */
    public static final int MIN_WIDTH = 16;
    /** Nombre maximal de textures importees stockees par dimension (C79). */
    public static final int QUOTA = 64;
    /** Morceaux envoyes au plus par tick et par transmission (environ 2,4 Mo/s). */
    public static final int CHUNKS_PER_TICK = 4;
    /** Un import sans morceau recu depuis ce delai (ticks serveur) est abandonne. */
    public static final int UPLOAD_TIMEOUT_TICKS = 200;
    /** Demandes de textures en attente au plus par joueur (au-dela, ignorees). */
    public static final int MAX_PENDING_REQUESTS = QUOTA;
    /** Niveau de permission d'un operateur (comme le Configurator). */
    public static final int OP_LEVEL = 2;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "space_textures"), () -> PROTOCOL, PROTOCOL::equals,
            PROTOCOL::equals);

    private SpaceTextureNetwork() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(SpaceTextureNetwork::register);
    }

    private static void register()
    {
        int id = 0;
        CHANNEL.messageBuilder(Begin.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Begin::encode).decoder(Begin::decode).consumerMainThread(Begin::handle).add();
        CHANNEL.messageBuilder(Chunk.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Chunk::encode).decoder(Chunk::decode).consumerMainThread(Chunk::handle).add();
        CHANNEL.messageBuilder(Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Request::encode).decoder(Request::decode).consumerMainThread(Request::handle).add();
        CHANNEL.messageBuilder(Result.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Result::encode).decoder(Result::decode).consumerMainThread(Result::handle).add();
        CHANNEL.messageBuilder(Data.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Data::encode).decoder(Data::decode).consumerMainThread(Data::handle).add();
        CHANNEL.messageBuilder(Missing.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Missing::encode).decoder(Missing::decode).consumerMainThread(Missing::handle).add();
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
