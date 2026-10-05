/**
 * Memoires holographiques et leurs conteneurs vivants, cote serveur. Une
 * {@link com.amynna.hololib.holo.memory.HoloMemory} sauvegarde un ensemble de modules ; quand elle est chargee, un
 * {@link com.amynna.hololib.holo.memory.HoloContainer} porte ses modules vivants et les replique. Trois portees
 * ({@link com.amynna.hololib.holo.memory.HoloScope}) :
 * <ul>
 *     <li>{@code player} : plusieurs memoires par joueur (capability), une active, chargee tant que le joueur est
 *     connecte ; repliquee aux joueurs qui suivent son entite et l'inspectent ({@code holo.inspect}), seulement pour
 *     les modules partages. Chaque memoire de joueur enregistre aussi qui son proprietaire inspecte.</li>
 *     <li>{@code dimension} : une memoire par niveau ({@code SavedData}), accessible partout dans la dimension.</li>
 *     <li>{@code position} : memoire portee par un marker du monde ({@code HoloMarkers}, registre global des ids),
 *     chargee avec l'entite et repliquee aux joueurs qui suivent son chunk ; le marker est l'origine des objets
 *     ancres dans le monde, sa position est envoyee aux clients.</li>
 * </ul>
 * Un type de module n'est installable que dans le genre de memoire de sa compatibilite ({@code HoloModuleTarget}), en
 * une seule instance par memoire. {@link com.amynna.hololib.holo.memory.HoloContainers} tient l'index, gere les
 * evenements et expose l'API serveur ; {@link com.amynna.hololib.holo.memory.HoloMemories} modifie une memoire au
 * repos.
 */
package com.amynna.hololib.holo.memory;
