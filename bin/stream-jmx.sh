RUNDIR=`pwd`/
CLASSPATH="$RUNDIR../tnt4j-stream-jmx.jar:$RUNDIR../lib/tnt4j-api-final-all.jar"
TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug -Dorg.tnt4j.stream.jmx.agent.trace=true -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"
TNT4JOPTS=$TNT4JOPTS -Dtnt4j.config="$RUNDIR..config/tnt4j.properties"
java $TNT4JOPTS -classpath $CLASSPATH org.tnt4j.stream.jmx.StreamAgent "*:*" 10000 60000
