#! /bin/bash
### ---- parameters expected ----
# see readme for additional details
#
# 1. service identifier for the process being monitored  (optional)
### -----------------------------

if command -v realpath >/dev/null 2>&1; then
    SCRIPTPATH=`dirname $(realpath $0)`
else
    SCRIPTPATH=$( cd "$(dirname "$0")" ; pwd -P )
fi

LIBPATH="$SCRIPTPATH/../*:$SCRIPTPATH/../lib/*"
TNT4JOPTS="-Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"

### --- tnt4j file ----
if [ -z "$TNT4J_PROPERTIES" ]; then
  TNT4J_PROPERTIES="$SCRIPTPATH/../config/tnt4j.properties"
fi
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$TNT4J_PROPERTIES"
### -------------------
### --- log4j file ----
if [ -z "$LOG4J_PROPERTIES" ]; then
  LOG4J_PROPERTIES="$SCRIPTPATH/../config/log4j.properties"
fi
TNT4JOPTS="$TNT4JOPTS -Dlog4j.configuration=file:$LOG4J_PROPERTIES"
### -------------------

### ---- AppServer identifies source ----
if [ -z "$TNT4J_APPSERVER" ]; then
    TNT4J_APPSERVER="Default"
fi
if [ "x$1" != "x" ] && [ "x$1" != "x." ]; then
    TNT4J_APPSERVER="$1"
fi
TNT4JOPTS="$TNT4JOPTS -Dsjmx.serviceId=$TNT4J_APPSERVER"
### -------------------------------------

java $TNT4JOPTS -classpath "$LIBPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent "*:*" "" 10000 60000