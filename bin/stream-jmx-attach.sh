#!/usr/bin/env bash
RUNDIR=`pwd`/
TOOLS_PATH="$JAVA_HOME/lib/tools.jar"
CLASSPATH="$RUNDIR../tnt4j-stream-jmx*.jar:$RUNDIR../lib/*:$TOOLS_PATH"
TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$RUNDIR..config/tnt4j.properties"
#TNT4JOPTS="$TNT4JOPTS -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.validate.types=false"
java $TNT4JOPTS -classpath "$CLASSPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -attach -vm:$* -ap:./../lib/tnt4j-stream-jmx-0.4.5.jar -ao:*:*!!10000