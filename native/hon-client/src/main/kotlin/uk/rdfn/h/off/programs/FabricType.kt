package uk.rdfn.h.off.programs

import uk.rdfn.h.off.washer.FabricHint
import uk.rdfn.h.off.washer.WasherProgram

/**
 * Sorts programs by fabric, for the chips at the top of the Programs screen.
 *
 * h.on doesn't say which fabric a program is for, so this goes by words in
 * the program's id and name. Nicolas's choices (20 November 2026):
 * - Wool and silk count as Delicates.
 * - Programs for any fabric (Rapid 59, stain programs, Rinse, Spin...) go
 *   under Mixed.
 * - Drying-only programs get their own Drying chip.
 *
 * Ported from src/programs/fabricTypes.ts. The enum order is the chips'
 * order on screen, and the section order when "All" is chosen.
 */
enum class FabricType(val title: String) {
	COTTON("Cotton"),
	SYNTHETICS("Synthetics"),
	MIXED("Mixed"),
	DELICATES("Delicates"),
	DRYING("Drying");

	companion object {
		/**
		 * Lower-case words or phrases that put a program under a fabric. Each
		 * must start a word, so "mix" matches "Mixed" but "silk" doesn't match
		 * "milk". Checked in this order and the first match wins, so the more
		 * specific fabrics come first: "Delicate whites" is Delicates,
		 * "Synthetic and coloured" is Synthetics, "Mix and coloured 59" is Mixed.
		 */
		private val KEYWORDS_IN_MATCH_ORDER: List<Pair<FabricType, List<String>>> = listOf(
			DELICATES to listOf(
				"delicate", "wool", "silk", "cashmere", "lingerie", "underwear", "handwash", "hand wash",
				"soft care", "curtain", "masks",
			),
			SYNTHETICS to listOf(
				"synthetic", "technical", "sport", "gym", "ski", "swimsuit", "diving", "down jacket", "trainers",
				"backpack", "outdoor",
			),
			MIXED to listOf("mix", "all in one", "night and day", "eco 40", "20 degrees"),
			COTTON to listOf(
				"cotton", "bed linen", "towel", "bathrobe", "tablecloth", "shirt", "baby", "playsuit", "pets",
				"cuddly", "mats", "men s trousers", "duvet",
			),
		)

		/** Words that only name a colour. These programs take their fabric from h.on's hint. */
		private val COLOUR_ONLY_KEYWORDS = listOf("white", "colour", "color", "dark", "denim", "bleach")

		/** A saved fabric name back to a fabric, or null for a garbled value. Any letter case, so the old app's "cotton" still works. */
		fun fromSavedName(savedName: String?): FabricType? = entries.firstOrNull { it.name.equals(savedName, ignoreCase = true) }

		/** "PROGRAMS.WM_WD.IOT_DRY_SHIRTS" + "Dry shirts" → "iot dry shirts dry shirts". */
		private fun searchTextFor(program: WasherProgram): String {
			val idWords = program.id.substringAfterLast('.').replace('_', ' ')
			return "$idWords ${program.displayName}".lowercase()
		}

		private fun startsAWord(searchText: String, keyword: String): Boolean =
			Regex("(^|[^a-z])${Regex.escape(keyword)}").containsMatchIn(searchText)

		/**
		 * Drying only: h.on's "IOT_DRY_..." programs, and anything else that
		 * dries or tumbles without washing ("Low dry", "Wool dry", "Tumbling").
		 * A "Rapid wash and dry" washes too, so it follows its fabric instead.
		 */
		private fun isDryingOnly(searchText: String): Boolean {
			if (Regex("(^|[^a-z])iot dry ").containsMatchIn(searchText)) return true
			val mentionsWashing = Regex("(^|[^a-z])wash").containsMatchIn(searchText)
			val mentionsDrying = Regex("(^|[^a-z])(dry|drying|tumbl)").containsMatchIn(searchText)
			return mentionsDrying && !mentionsWashing
		}

		/** Which fabric chip a program belongs under. */
		fun of(program: WasherProgram): FabricType {
			val searchText = searchTextFor(program)
			if (isDryingOnly(searchText)) return DRYING

			KEYWORDS_IN_MATCH_ORDER.firstOrNull { (_, keywords) -> keywords.any { startsAWord(searchText, it) } }
				?.let { (fabric) -> return fabric }

			// "Whites", "Coloured", "Darks"...: h.on's dryType says which fabric.
			if (COLOUR_ONLY_KEYWORDS.any { startsAWord(searchText, it) }) {
				when (program.fabricHint) {
					FabricHint.COTTON -> return COTTON
					FabricHint.SYNTHETICS -> return SYNTHETICS
					null -> Unit
				}
			}

			// Anything else suits any fabric (Rapid 59, Stain remover, Rinse, Spin...).
			return MIXED
		}
	}
}
