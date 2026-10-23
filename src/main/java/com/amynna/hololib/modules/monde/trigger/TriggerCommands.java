package com.amynna.hololib.modules.monde.trigger;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.widget.control.HoloTextBuffer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Execution des commandes des modules Trigger (Trigger Area, Trigger Interrupter), cote serveur. Une commande vide
 * (ou blanche) est ignoree. La source reste le declencheur (joueur ou entite : {@code @s}) mais s'execute avec la
 * <b>permission {@value #PERMISSION}</b> imposee, comme un command block, quels que soient ses droits ; sa sortie est
 * supprimee. Les commandes ne sont donc ecrites que par les operateurs de niveau {@value TriggerOps#CONFIG_LEVEL}
 * (fenetres des Configurators) ; une erreur de syntaxe ou
 * d'execution ne remonte jamais au tick.
 * <p>
 * Un declencheur porte une <b>liste</b> de commandes, executees dans l'ordre ({@link #runAll}), au plus
 * {@value #MAX_COMMANDS}. Les listes sont des {@code List<String>} <b>immuables remplacees a chaque modification</b>
 * ({@link #with}, {@link #without}, {@link #moved}) : le tick lit la reference sans copie, et une commande qui
 * modifierait la liste pendant l'execution ne perturbe pas la boucle.
 * <p>
 * Une instance par module compte les executions du tick ({@link #beginTick}) : au-dela de {@value #BUDGET}, les
 * suivantes sont ignorees (un maintien ou une zone pleine d'entites ne doit pas saturer le serveur).
 */
public final class TriggerCommands
{
    /** Longueur maximale d'une commande. */
    public static final int MAX_LENGTH = 256;
    /** Niveau de permission impose a la source. */
    public static final int PERMISSION = 2;
    /** Commandes au plus par liste. */
    public static final int MAX_COMMANDS = 16;
    /** Executions au plus par tick et par module (chaque commande d'une liste en compte une). */
    public static final int BUDGET = 256;

    private int used;
    private boolean warned;

    /** A appeler au debut de chaque tick serveur du module. */
    public void beginTick()
    {
        used = 0;
        warned = false;
    }

    /** Execute dans l'ordre chaque commande de {@code list} (une qui echoue n'arrete pas les suivantes). */
    public void runAll(MinecraftServer server, CommandSourceStack source, List<String> list)
    {
        for (int i = 0; i < list.size(); i++) run(server, source, list.get(i));
    }

    /**
     * Execute {@code command} (le {@code /} initial est facultatif) avec {@code source} ; sans effet si la commande
     * est vide ou si le budget du tick est epuise.
     *
     * @return vrai si la commande a ete lancee
     */
    public boolean run(MinecraftServer server, CommandSourceStack source, String command)
    {
        if (command == null || command.isBlank()) return false;
        if (used >= BUDGET) {
            if (!warned) {
                warned = true;
                HoloLib.LOGGER.warn("Trigger : plus de {} commandes dans le meme tick, le reste est ignore", BUDGET);
            }
            return false;
        }
        used++;
        try {
            server.getCommands().performPrefixedCommand(source.withPermission(PERMISSION).withSuppressedOutput(),
                    command);
        } catch (RuntimeException e) {
            HoloLib.LOGGER.warn("Trigger : commande \"{}\" en echec", command, e);
        }
        return true;
    }

    /** Liste vide, partagee. */
    public static final List<String> NONE = List.of();

    /** Liste lue (sauvegarde, reseau) : commandes nettoyees, vides retirees, {@value #MAX_COMMANDS} au plus. */
    public static List<String> sanitizeAll(List<String> raw)
    {
        List<String> out = new ArrayList<>(Math.min(raw.size(), MAX_COMMANDS));
        for (String command : raw) {
            String clean = sanitize(command);
            if (!clean.isEmpty() && out.size() < MAX_COMMANDS) out.add(clean);
        }
        return out.isEmpty() ? NONE : List.copyOf(out);
    }

    /** {@code list} + {@code text} a la fin ; la meme liste si le texte est vide ou si la liste est pleine. */
    public static List<String> with(List<String> list, String text)
    {
        String clean = sanitize(text);
        if (clean.isEmpty() || list.size() >= MAX_COMMANDS) return list;
        List<String> next = new ArrayList<>(list);
        next.add(clean);
        return List.copyOf(next);
    }

    /** {@code list} sans l'element {@code index}, a condition qu'il soit {@code expected} ; sinon la meme liste. */
    public static List<String> without(List<String> list, int index, String expected)
    {
        if (index < 0 || index >= list.size() || !list.get(index).equals(sanitize(expected))) return list;
        List<String> next = new ArrayList<>(list);
        next.remove(index);
        return next.isEmpty() ? NONE : List.copyOf(next);
    }

    /**
     * {@code list} dont l'element {@code index} est remplace par {@code text}, a condition qu'il soit {@code expected} ;
     * la meme liste si le texte est vide ou identique, ou hors bornes.
     */
    public static List<String> replaced(List<String> list, int index, String expected, String text)
    {
        String clean = sanitize(text);
        if (clean.isEmpty() || index < 0 || index >= list.size() || !list.get(index).equals(sanitize(expected))
                || list.get(index).equals(clean)) {
            return list;
        }
        List<String> next = new ArrayList<>(list);
        next.set(index, clean);
        return List.copyOf(next);
    }

    /** {@code list} avec l'element {@code from} deplace en {@code to} ; la meme liste hors bornes ou si rien ne bouge. */
    public static List<String> moved(List<String> list, int from, int to)
    {
        if (from == to || from < 0 || to < 0 || from >= list.size() || to >= list.size()) return list;
        List<String> next = new ArrayList<>(list);
        next.add(to, next.remove(from));
        return List.copyOf(next);
    }

    /** Commande bornee et sur une seule ligne (le client n'est pas fiable). */
    public static String sanitize(String value)
    {
        return value == null ? "" : HoloTextBuffer.sanitize(value, MAX_LENGTH, 1).strip();
    }
}
