#!/usr/bin/env bash
RUNDIR=`pwd`
TOOLS_PATH="$JAVA_HOME/lib/tools.jar"
CLASSPATH="$RUNDIR/../tnt4j-stream-jmx*.jar:$RUNDIR/../lib/*:$TOOLS_PATH"
TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$RUNDIR/../config/tnt4j.properties"

# NOTE! Double exclamation mark in bash has a special meaning (previous command)
# to pass arguments correctly you need escape both. I.E. "stream-jmx-attach.sh tomcat *:*\!\!13000"
AGENT_OPTIONS=$2

if ["$AGENT_OPTIONS" == ""]; then
    AGENT_OPTIONS="*:*!!10000"
fi

java $TNT4JOPTS -classpath "$CLASSPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -attach -vm:$1 -ap:./../lib/tnt4j-stream-jmx-0.5.0.jar -ao:$AGENT_OPTIONS