#\!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
#
# Gradle startup script for POSIX — Gradle 8.4 official format
#

APP_NAME="Gradle"
APP_BASE_NAME="${0##*/}"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
MAX_FD=maximum

warn() { echo "$*" >&2; }
die()  { echo >&2; echo "$*" >&2; echo >&2; exit 1; }

# Resolve APP_HOME (follow symlinks)
app_path=$0
while [ -h "$app_path" ]; do
    ls_out=$(ls -ld "$app_path")
    link="${ls_out#*' -> '}"
    case $link in
        /*) app_path=$link ;;
        *)  app_path="${app_path%/*}/$link" ;;
    esac
done
APP_HOME=$(cd "${app_path%/*}" && pwd -P) || exit

# OS detection
cygwin=false; msys=false; darwin=false; nonstop=false
case "$(uname)" in
    CYGWIN*)        cygwin=true  ;;
    Darwin*)        darwin=true  ;;
    MSYS*|MINGW*)   msys=true    ;;
    NONSTOP*)       nonstop=true ;;
esac

# Locate java
if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    [ -x "$JAVACMD" ] || die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and 'java' not found in PATH."
fi

# Classpath — auto-download wrapper jar if missing
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ \! -f "$CLASSPATH" ]; then
    WRAPPER_URL="https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
    echo "Downloading gradle-wrapper.jar..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    if command -v curl >/dev/null 2>&1; then
        curl -sSfL -o "$CLASSPATH" "$WRAPPER_URL" || die "ERROR: Failed to download gradle-wrapper.jar"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$CLASSPATH" "$WRAPPER_URL" || die "ERROR: Failed to download gradle-wrapper.jar"
    else
        die "ERROR: gradle-wrapper.jar missing. Open project in Android Studio to generate it."
    fi
fi

# Increase file descriptors
if \! "$cygwin" && \! "$darwin" && \! "$nonstop"; then
    case $MAX_FD in
        max*) MAX_FD=$(ulimit -H -n) ;;
    esac
    case $MAX_FD in
        ''|soft) ;;
        *) ulimit -n "$MAX_FD" ;;
    esac
fi

# Build the java command — eval properly splits the quoted JVM opts
eval set -- \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    $GRADLE_OPTS \
    "\"-Dorg.gradle.appname=$APP_BASE_NAME\"" \
    -classpath "\"$CLASSPATH\"" \
    org.gradle.wrapper.GradleWrapperMain \
    '"$@"'

exec "$JAVACMD" "$@"
