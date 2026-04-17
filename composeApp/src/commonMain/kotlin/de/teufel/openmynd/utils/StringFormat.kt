package de.teufel.openmynd.utils

/**
 * Simple indexed placeholder formatter for templates like "%1", "%1$s", "%1$d".
 * Converts all placeholders to the string value of provided args.
 */
fun formatIndexed(template: String, vararg args: Any?): String {
    var result = template
    for ((index, arg) in args.withIndex()) {
        val i = index + 1
        val value = arg?.toString() ?: ""
        result = result
            .replace("%${i}\$s", value)
            .replace("%${i}\$d", value)
            .replace("%${i}", value)
    }
    return result
}


