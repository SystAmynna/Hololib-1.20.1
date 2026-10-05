package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.widget.HoloTextMetrics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Moteur d'edition de texte, sans dessin : texte, curseur et mise en page, pour {@link HoloTextInput} (voir
 * {@link HoloWidgetTextBox}). Code commun.
 * <p>
 * Retour a la ligne automatique a {@link #wrapWidth()} (par mot, sinon par caractere), mesure par
 * {@link HoloTextMetrics} ; {@link #visibleLines()} lignes visibles, la premiere ajustee pour garder le curseur en vue.
 * La mise en page (lignes visuelles, abscisse du curseur) est recalculee a chaque modification, jamais au dessin.
 * Bornes : {@link #maxLength()} caracteres (unites UTF-16), {@link #maxHardLines()} lignes tapees ; caracteres de
 * controle refuses (sauf le retour a la ligne). Les memes bornes cote serveur : {@link #sanitize}.
 */
public class HoloTextBuffer
{
    private final int maxLength;
    private final int maxHardLines;
    private final int visibleLines;
    private float wrapWidth;
    private final StringBuilder text = new StringBuilder();
    private int cursor;

    // Mise en page : lignes visuelles {debut, fin} dans le texte.
    private final List<int[]> lines = new ArrayList<>();
    private final List<Component> lineTexts = new ArrayList<>();
    private int cursorLine;
    private float cursorX;
    private int firstLine;

    /**
     * @param maxHardLines 1 = une seule ligne (pas de retour a la ligne tape)
     * @param wrapWidth    largeur de coupure, en pixels
     */
    public HoloTextBuffer(int maxLength, int maxHardLines, int visibleLines, float wrapWidth)
    {
        if (maxLength <= 0 || maxHardLines <= 0 || visibleLines <= 0) throw new IllegalArgumentException("Bornes");
        this.maxLength = maxLength;
        this.maxHardLines = maxHardLines;
        this.visibleLines = visibleLines;
        this.wrapWidth = wrapWidth;
        layout();
    }

    public int maxLength() { return maxLength; }

    public int maxHardLines() { return maxHardLines; }

    public int visibleLines() { return visibleLines; }

    public float wrapWidth() { return wrapWidth; }

    public void setWrapWidth(float wrapWidth)
    {
        this.wrapWidth = wrapWidth;
        layout();
    }

    public String text() { return text.toString(); }

    public int length() { return text.length(); }

    public boolean isEmpty() { return text.length() == 0; }

    /** Remplace le texte (borne par {@link #sanitize}) ; curseur a la fin. */
    public void setText(String value)
    {
        text.setLength(0);
        text.append(sanitize(value, maxLength, maxHardLines));
        cursor = text.length();
        layout();
    }

    public int cursor() { return cursor; }

    // ---------------------------------------------------------------- edition

    /** Insere au curseur ce qui respecte les bornes (le reste est ignore). */
    public void insert(String value)
    {
        int hardLines = hardLineCount();
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint != '\n' && Character.isISOControl(codePoint)) continue;
            int size = Character.charCount(codePoint);
            if (text.length() + size > maxLength) break;
            if (codePoint == '\n') {
                if (hardLines >= maxHardLines) continue;
                hardLines++;
            }
            text.insert(cursor, Character.toChars(codePoint));
            cursor += size;
        }
        layout();
    }

    public void deleteBackward()
    {
        if (cursor == 0) return;
        int start = text.offsetByCodePoints(cursor, -1);
        text.delete(start, cursor);
        cursor = start;
        layout();
    }

    public void deleteForward()
    {
        if (cursor >= text.length()) return;
        text.delete(cursor, text.offsetByCodePoints(cursor, 1));
        layout();
    }

    public void left()
    {
        if (cursor > 0) cursor = text.offsetByCodePoints(cursor, -1);
        layout();
    }

    public void right()
    {
        if (cursor < text.length()) cursor = text.offsetByCodePoints(cursor, 1);
        layout();
    }

    /** Debut de la ligne visuelle. */
    public void home()
    {
        cursor = lines.get(cursorLine)[0];
        layout();
    }

    /** Fin de la ligne visuelle. */
    public void end()
    {
        cursor = lastPosition(cursorLine);
        layout();
    }

    public void up() { moveVertically(-1); }

    public void down() { moveVertically(1); }

    public void moveToEnd()
    {
        cursor = text.length();
        layout();
    }

    /** Ligne visuelle precedente ou suivante, a l'abscisse la plus proche de celle du curseur. */
    private void moveVertically(int direction)
    {
        int target = cursorLine + direction;
        if (target < 0 || target >= lines.size()) return;
        int[] line = lines.get(target);
        int last = lastPosition(target);
        int best = line[0];
        float x = 0f;
        float bestDistance = Math.abs(cursorX);
        for (int i = line[0]; i < last; ) {
            int size = Character.charCount(text.codePointAt(i));
            x += HoloTextMetrics.width(text.substring(i, i + size));
            i += size;
            float distance = Math.abs(cursorX - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        cursor = best;
        layout();
    }

    /**
     * Derniere position du curseur sur une ligne visuelle : sa fin, ou juste avant pour une ligne coupee (sa fin est le
     * debut de la suivante, ou le curseur s'afficherait).
     */
    private int lastPosition(int index)
    {
        int[] line = lines.get(index);
        boolean wrapped = index + 1 < lines.size() && lines.get(index + 1)[0] == line[1];
        return wrapped && line[1] > line[0] ? text.offsetByCodePoints(line[1], -1) : line[1];
    }

    private int hardLineCount()
    {
        int count = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') count++;
        return count;
    }

    // ---------------------------------------------------------------- mise en page (lecture)

    /** Nombre de lignes visuelles (au moins 1). */
    public int lineCount() { return lines.size(); }

    public Component lineText(int index) { return lineTexts.get(index); }

    /** Premiere ligne visible (defilement). */
    public int firstLine() { return firstLine; }

    /** Ligne visuelle du curseur. */
    public int cursorLine() { return cursorLine; }

    /** Abscisse du curseur dans sa ligne, en pixels. */
    public float cursorX() { return cursorX; }

    // ---------------------------------------------------------------- mise en page (calcul)

    private void layout()
    {
        lines.clear();
        lineTexts.clear();
        int start = 0;
        while (true) {
            int newline = text.indexOf("\n", start);
            int end = newline < 0 ? text.length() : newline;
            wrap(start, end);
            if (newline < 0) break;
            start = newline + 1;
        }
        cursorLine = 0;
        for (int i = 0; i < lines.size(); i++) {
            int[] line = lines.get(i);
            if (cursor < line[0] || cursor > line[1]) continue;
            cursorLine = i;
            // Fin d'une ligne coupee = debut de la suivante : le curseur s'affiche sur la suivante.
            boolean continued = cursor == line[1] && i + 1 < lines.size() && lines.get(i + 1)[0] == line[1];
            if (!continued) break;
        }
        int[] line = lines.get(cursorLine);
        cursorX = HoloTextMetrics.width(text.substring(line[0], cursor));
        if (cursorLine < firstLine) firstLine = cursorLine;
        if (cursorLine >= firstLine + visibleLines) firstLine = cursorLine - visibleLines + 1;
        firstLine = Math.max(0, Math.min(firstLine, lines.size() - visibleLines));
    }

    /** Coupe la ligne tapee {@code [start, end)} : apres le dernier espace qui tient, sinon au dernier caractere. */
    private void wrap(int start, int end)
    {
        if (start == end) {
            addLine(start, end);
            return;
        }
        int position = start;
        while (position < end) {
            float width = 0f;
            int index = position;
            int lastSpace = -1;
            while (index < end) {
                int codePoint = text.codePointAt(index);
                int size = Character.charCount(codePoint);
                float charWidth = HoloTextMetrics.width(text.substring(index, index + size));
                if (width + charWidth > wrapWidth && index > position) break;
                width += charWidth;
                if (codePoint == ' ') lastSpace = index;
                index += size;
            }
            int cut = index < end && lastSpace >= position ? lastSpace + 1 : index;
            addLine(position, cut);
            position = cut;
        }
    }

    private void addLine(int start, int end)
    {
        lines.add(new int[] {start, end});
        lineTexts.add(Component.literal(text.substring(start, end)));
    }

    /** Applique les bornes d'un buffer a un texte (cote serveur : le client n'est pas fiable). */
    public static String sanitize(String value, int maxLength, int maxHardLines)
    {
        StringBuilder out = new StringBuilder();
        int hardLines = 1;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            if (out.length() + Character.charCount(codePoint) > maxLength) break;
            if (codePoint == '\n') {
                if (hardLines >= maxHardLines) continue;
                hardLines++;
                out.append('\n');
            } else if (!Character.isISOControl(codePoint)) {
                out.appendCodePoint(codePoint);
            }
        }
        return out.toString();
    }
}
