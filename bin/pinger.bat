set RUNDIR=%~p0
set CLASSPATH="%RUNDIR%..\tnt4j-ping-jmx.jar;%RUNDIR%..\lib\tnt4j-api-final-all.jar"
set TNT4JOPTS=-Dorg.tnt4j.pingagent.trace=true -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true -Dlog4j.configuration=file:"%RUNDIR%..\config\log4j.properties" -Dtnt4j.config="%RUNDIR%..\config\tnt4j.properties" -Dtnt4j.token.repository="%RUNDIR%..\config\tnt4j-tokens.properties"
java %TNT4JOPTS% -classpath %CLASSPATH% org.tnt4j.pingjmx.PingAgent "*:*" 10000 60000
