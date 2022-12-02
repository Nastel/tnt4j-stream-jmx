#! /bin/bash
### ---- parameters expected ----
# see readme for additional details
#
# 1. service identifier for the process being monitored  (optional)
### -----------------------------

if command -v readlink >/dev/null 2>&1; then
    SCRIPTPATH=$(dirname $(readlink -m $BASH_SOURCE))
else
    SCRIPTPATH=$(cd "$(dirname "$BASH_SOURCE")" ; pwd -P)
fi

LIBPATH="$SCRIPTPATH/../*:$SCRIPTPATH/../lib/*"
TNT4JOPTS="-Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"

### --- tnt4j file ----
if [[ -z "$TNT4J_PROPERTIES" ]]; then
  TNT4J_PROPERTIES="$SCRIPTPATH/../config/tnt4j.properties"
fi
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$TNT4J_PROPERTIES"
### -------------------
### --- log4j file ----
if [[ -z "$LOG4J_PROPERTIES" ]]; then
  LOG4J_PROPERTIES="$SCRIPTPATH/../config/log4j2.xml"
fi
TNT4JOPTS="$TNT4JOPTS -Dlog4j2.configurationFile=file:$LOG4J_PROPERTIES"
### --- stream log file name ---
#TNT4JOPTS="$TNT4JOPTS -Dtnt4j.stream.log.filename=$SCRIPTPATH/../logs/tnt4j-stream-jmx.log"
### --- streamed activities log file name ---
#TNT4JOPTS="$TNT4JOPTS -Dtnt4j.activities.log.filename=$SCRIPTPATH/../logs/tnt4j-stream-jmx_samples.log"
### -------------------

### ---- AppServer identifies source ----
if [[ -z "$TNT4J_APPSERVER" ]]; then
    TNT4J_APPSERVER="Default"
fi
if [[ "x$1" != "x" ]] && [[ "x$1" != "x." ]]; then
    TNT4J_APPSERVER="$1"
fi
TNT4JOPTS="$TNT4JOPTS -Dfile.encoding=UTF-8 -Dsjmx.serviceId=$TNT4J_APPSERVER"
### -------------------------------------

JAVA_EXEC="java"
if [[ "$JAVA_HOME" == "" ]]; then
  echo '"JAVA_HOME" env. variable is not defined!..'
else
  echo 'Will use java from:' "$JAVA_HOME"
  JAVA_EXEC="$JAVA_HOME/bin/java"
fi

$JAVA_EXEC $TNT4JOPTS -classpath "$LIBPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent "*:*" "" 10000 60000