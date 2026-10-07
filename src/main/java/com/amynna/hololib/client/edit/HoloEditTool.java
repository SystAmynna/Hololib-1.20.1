package com.amynna.hololib.client.edit;

/** Outil du mode edition, et groupe de composantes du transform qu'il modifie. */
public enum HoloEditTool
{
    /** {@code x}, {@code y}, {@code z}. */
    TRANSLATE,
    /** {@code yaw}, {@code pitch}, {@code roll}. */
    ROTATE,
    /** {@code scaleX}, {@code scaleY}, {@code scaleZ}. */
    SCALE,
    /** Portee d'edition d'un objet ancre {@code World} (hors transform). */
    RANGE
}
