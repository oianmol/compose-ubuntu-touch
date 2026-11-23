package playground

/** Key/value facts about the runtime; each target decides what it can report. */
expect fun platformDetails(): List<Pair<String, String>>
