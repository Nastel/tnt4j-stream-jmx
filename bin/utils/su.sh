#! /bin/bash
if command -v readlink >/dev/null 2>&1; then
    SCRIPTPATH=$(dirname $(readlink -m $BASH_SOURCE))
else
    SCRIPTPATH=$(cd "$(dirname "$BASH_SOURCE")" ; pwd -P)
fi

LIBPATH="$LIBPATH:$SCRIPTPATH/../../*:$SCRIPTPATH/../../lib/*"
MAINCLASS="com.jkoolcloud.tnt4j.utils.SecurityUtils"

JAVA_EXEC="java"
if [[ "$JAVA_HOME" == "" ]]; then
  echo '"JAVA_HOME" env. variable is not defined!..'
else
  echo 'Will use java from:' "$JAVA_HOME"
  JAVA_EXEC="$JAVA_HOME/bin/java"
fi

$JAVA_EXEC -classpath "$LIBPATH" $MAINCLASS $*
