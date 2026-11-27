package io.github.johankrs.kiri.core.tv

/** A tuner of the TV as Android lists it: its [id], the [service] that runs it and its [name]. */
data class TunerSeen(val id: String, val service: String, val name: String)

/** Which of the TV's tuners get a place among its inputs. */
object TvTuners {
    /**
     * The ids of the tuners to show, in the TV's order: the first of each service and of each
     * name. Some TVs list one tuner once for each piece of hardware behind it (a Hisense lists
     * HW0 and HW6 of the same service, both called "Tuner"), and their own list of inputs shows
     * it once; two tuners of the same name could not be told apart anyway.
     */
    fun kept(all: List<TunerSeen>): Set<String> {
        val services = HashSet<String>()
        val names = HashSet<String>()
        val kept = LinkedHashSet<String>()
        for (tuner in all) {
            val name = tuner.name.trim().lowercase()
            if (tuner.service in services || name in names) continue
            services += tuner.service
            names += name
            kept += tuner.id
        }
        return kept
    }
}
