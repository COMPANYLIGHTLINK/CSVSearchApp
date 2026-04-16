#\!/bin/sh
# Gradle wrapper — no eval, hardcoded JVM opts (fixes -Xmx64m class error)

APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Auto-download wrapper jar if missing
if [ \! -f "$CLASSPATH" ]; then
    echo "gradle-wrapper.jar not found, downloading..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    JAR_URL="https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
    if command -v curl >/dev/null 2>&1; then
        curl -sSfL "$JAR_URL" -o "$CLASSPATH"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$JAR_URL" -O "$CLASSPATH"
    else
        echo "ERROR: curl/wget not found" >&2; exit 1
    fi
fi

# Locate java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Run Gradle wrapper — JVM opts hardcoded, no eval, no quoting bugs
exec "$JAVACMD" \
    -Xmx64m \
    -Xms64m \
    "-Dorg.gradle.appname=$(basename "$0")" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
