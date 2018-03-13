@echo off
setlocal

set RUNDIR=%~dp0
set TOOLS_PATH=%JAVA_HOME%\lib\tools.jar
set LIBPATH=%RUNDIR%..\*;%RUNDIR%..\lib\*;%TOOLS_PATH%
set TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true" "-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true"
IF ["%TNT4J_PROPERTIES%"] EQU [""] set TNT4J_PROPERTIES="%RUNDIR%..\config\tnt4j.properties"
set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.config=%TNT4J_PROPERTIES%"

set AGENT_OPTIONS="*:*!!60000"
if "%TNT4J_AGENT_OPTIONS%"=="" set TNT4J_AGENT_OPTIONS=%AGENT_OPTIONS%
if NOT "%2"=="" set TNT4J_AGENT_OPTIONS=%2

rem ---- AppServer identifies source ----
if "%TNT4J_APPSERVER%"=="" set TNT4J_APPSERVER="Default"
set TNT4JOPTS="%TNT4JOPTS% -Dsjmx.serviceId=%TNT4J_APPSERVER%"
rem -------------------------------------

@echo on
java %TNT4JOPTS% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -attach -vm:%1 -ap:.\..\lib\tnt4j-stream-jmx-0.6.0.jar -ao:%TNT4J_AGENT_OPTIONS%