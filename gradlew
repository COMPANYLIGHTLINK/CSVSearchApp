#\!/bin/sh
# Gradle wrapper script for Unix

APP_HOME="`pwd -P`"
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD \!= -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ \! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

# Download and execute gradle wrapper jar
GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ \! -f "$GRADLE_WRAPPER_JAR" ] ; then
    # Download it
    if command -v curl >/dev/null 2>&1; then
        curl -sSL -o "$GRADLE_WRAPPER_JAR" "https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null
    fi
fi

exec "$JAVACMD" \
  $DEFAULT_JVM_OPTS \
  $JAVA_OPTS \
  $GRADLE_OPTS \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$GRADLE_WRAPPER_JAR" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
