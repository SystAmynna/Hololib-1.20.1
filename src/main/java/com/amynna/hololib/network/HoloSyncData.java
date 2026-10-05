package com.amynna.hololib.network;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Donnees de synchronisation, construites cote serveur au moment de l'envoi et copiees a la lecture (le buffer reseau
 * est libere avant que le fil principal ne les applique).
 */
public final class HoloSyncData
{
    private HoloSyncData() {}

    /**
     * Placement (ancrage + transform) et visibilite d'un objet. Le transform est une copie prise a l'envoi.
     *
     * @param anchor    {@code null} quand le destinataire n'a pas le droit de le connaitre (objet prive)
     * @param transform {@code null} dans le meme cas
     */
    public record ObjectData(String key, HoloVisibility visibility, HoloAnchor anchor, HoloTransform transform)
    {
        public static ObjectData of(HoloObject object, boolean forOwner)
        {
            boolean visible = forOwner || object.visibility() == HoloVisibility.PUBLIC;
            return new ObjectData(object.key(), object.visibility(), visible ? object.anchor() : null,
                    visible ? object.transform().copy() : null);
        }

        void write(FriendlyByteBuf buf)
        {
            buf.writeUtf(key);
            buf.writeByte(visibility.ordinal());
            buf.writeBoolean(anchor != null);
            if (anchor != null) {
                anchor.write(buf);
                transform.write(buf);
            }
        }

        static ObjectData read(FriendlyByteBuf buf)
        {
            String key = buf.readUtf();
            HoloVisibility visibility = HoloVisibility.byOrdinal(buf.readByte());
            if (!buf.readBoolean()) return new ObjectData(key, visibility, null, null);
            HoloAnchor anchor = HoloAnchor.read(buf);
            return new ObjectData(key, visibility, anchor, HoloTransform.read(buf));
        }
    }

    /**
     * Etat d'un module.
     *
     * @param state   octets ecrits par {@code HoloModule.writeSync}
     * @param objects objets transmis (vide pour une simple mise a jour d'etat)
     */
    public record ModuleData(ResourceLocation type, UUID instanceId, byte[] state, List<ObjectData> objects)
    {
        /**
         * @param withObjects ajoute les objets : tous pour le proprietaire, les objets publics pour les autres
         */
        public static ModuleData of(HoloModule module, boolean forOwner, boolean withObjects)
        {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            byte[] state;
            try {
                module.writeSync(buf, forOwner);
                state = new byte[buf.readableBytes()];
                buf.readBytes(state);
            } finally {
                buf.release();
            }
            List<ObjectData> objects = new ArrayList<>();
            if (withObjects) {
                for (HoloObject object : module.objects()) {
                    if (forOwner || object.visibility() == HoloVisibility.PUBLIC) {
                        objects.add(ObjectData.of(object, forOwner));
                    }
                }
            }
            return new ModuleData(module.type().id(), module.instanceId(), state, objects);
        }

        public static List<ModuleData> snapshot(Collection<HoloModule> modules, boolean forOwner)
        {
            List<ModuleData> list = new ArrayList<>(modules.size());
            for (HoloModule module : modules) list.add(of(module, forOwner, true));
            return list;
        }

        /** Buffer de lecture de l'etat, a liberer par l'appelant. */
        public FriendlyByteBuf stateBuffer() { return new FriendlyByteBuf(Unpooled.wrappedBuffer(state)); }

        void write(FriendlyByteBuf buf)
        {
            buf.writeResourceLocation(type);
            buf.writeUUID(instanceId);
            buf.writeByteArray(state);
            buf.writeVarInt(objects.size());
            for (ObjectData object : objects) object.write(buf);
        }

        static ModuleData read(FriendlyByteBuf buf)
        {
            ResourceLocation type = buf.readResourceLocation();
            UUID instanceId = buf.readUUID();
            byte[] state = buf.readByteArray();
            int count = buf.readVarInt();
            List<ObjectData> objects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) objects.add(ObjectData.read(buf));
            return new ModuleData(type, instanceId, state, objects);
        }
    }
}
