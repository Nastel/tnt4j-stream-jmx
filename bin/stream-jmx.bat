@echo off
setlocal

set RUNDIR=%~dp0
set LIBPATH="%RUNDIR%\..\tnt4j-stream-jmx*.jar;%RUNDIR%\..\lib\*"
set TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true" "-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true" "-Dtnt4j.config=%RUNDIR%\..\config\tnt4j.properties"

@echo on
java %TNT4JOPTS% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent "*:*" "" 10000 60000
