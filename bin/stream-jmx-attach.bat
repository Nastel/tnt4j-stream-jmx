@echo off
setlocal

set RUNDIR=%~p0
set TOOLS_PATH="%JAVA_HOME%\lib\tools.jar"
set CLASSPATH="%RUNDIR%\..\tnt4j-stream-jmx*.jar;%RUNDIR%\..\lib\*;%TOOLS_PATH%"
set TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true" "-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true" "-Dtnt4j.config=%RUNDIR%\..\config\tnt4j.properties"

@echo on
java %TNT4JOPTS% -classpath "%CLASSPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -attach -vm:%* -ap:.\..\lib\tnt4j-stream-jmx-0.4.5.jar -ao:*:*!!10000