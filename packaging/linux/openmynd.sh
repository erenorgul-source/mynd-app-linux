#!/bin/sh
# Launcher for the OpenMynd desktop client.
#
# Environment:
#   OPENMYND_JAVA       java binary to use (default: a Java 17+ runtime from /usr/lib/jvm)
#   OPENMYND_JAVA_OPTS  extra JVM options
#   OPENMYND_JAR        application jar (default: /usr/share/java/openmynd/openmynd.jar)
#   OPENMYND_THEME      dark|light to override the desktop's color scheme
#   OPENMYND_SCALE      UI scale factor, e.g. 1.5, if auto-detection gets it wrong

JAR="${OPENMYND_JAR:-/usr/share/java/openmynd/openmynd.jar}"

java_major() {
    # Prints the major version of the JDK/JRE home in $1, using its "release" file.
    sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$1/release" 2>/dev/null
}

find_java() {
    for home in /usr/lib/jvm/default-runtime /usr/lib/jvm/default /usr/lib/jvm/java-*-openjdk /usr/lib/jvm/java-*; do
        [ -x "$home/bin/java" ] || continue
        major="$(java_major "$home")"
        if [ -n "$major" ] && [ "$major" -ge 17 ]; then
            echo "$home/bin/java"
            return
        fi
    done
    echo java
}

JAVA="${OPENMYND_JAVA:-$(find_java)}"

# shellcheck disable=SC2086 # OPENMYND_JAVA_OPTS is intentionally word-split
exec "$JAVA" \
    --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED \
    $OPENMYND_JAVA_OPTS \
    -jar "$JAR" "$@"
