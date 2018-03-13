#! /bin/bash
if command -v realpath >/dev/null 2>&1; then
    SCRIPTPATH=`dirname $(realpath $0)`
else
    SCRIPTPATH=$( cd "$(dirname "$0")" ; pwd -P )
fi

TOOLS_PATH="$JAVA_HOME/lib/tools.jar"
LIBPATH="$SCRIPTPATH/../*:$SCRIPTPATH/../lib/*:$TOOLS_PATH"
TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"
# tnt4j file
if [ -z "$TNT4J_PROPERTIES" ]; then
  TNT4J_PROPERTIES="$SCRIPTPATH/../config/tnt4j.properties"
fi
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$TNT4J_PROPERTIES"

# NOTE: Double exclamation mark in bash has a special meaning (previous command)
# to pass arguments correctly you need escape both. I.E. "stream-jmx-attach.sh tomcat *:*\!\!13000"
AGENT_OPTIONS="*:*!!60000"
# jmx options from environment
if [ -z "$TNT4J_AGENT_OPTIONS" ]; then
    TNT4J_AGENT_OPTIONS="$AGENT_OPTIONS"
fi
# jmx options from command line overrides
if [ "x$2" != "x" ]; then
    TNT4J_AGENT_OPTIONS="$2"
fi

# jmx options from environment
if [ -z "$TNT4J_AGENT_OPTIONS" ]; then
    TNT4J_AGENT_OPTIONS="$AGENT_OPTIONS"
fi
# AppServer identifies source
if [ -z "$TNT4J_APPSERVER" ]; then
    TNT4J_APPSERVER="Default"
fi

java $TNT4JOPTS -classpath "$LIBPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -attach -vm:$1 -ap:./../lib/tnt4j-stream-jmx-0.6.0.jar -ao:$TNT4J_AGENT_OPTIONS