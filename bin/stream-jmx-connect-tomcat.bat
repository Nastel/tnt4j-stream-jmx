@echo off
setlocal

set RUNDIR=%~dp0
set TOOLS_PATH=%JAVA_HOME%\lib\tools.jar

rem LIBPATH addition for stream-jmx modules
for %%i in (%RUNDIR%..\tnt4j-stream-jmx-core-*.jar) do (
	call :concat_lib_path %%i
)

set LIBPATH=%LIBPATH%;%RUNDIR%..\lib\*;%TOOLS_PATH%
set TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true" "-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true"
set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.config=%RUNDIR%..\config\tnt4j.properties"

set AGENT_OPTIONS=%2
if "%AGENT_OPTIONS%"=="" set AGENT_OPTIONS=*:*!!10000

@echo on
java %TNT4JOPTS% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -connect -vm:%1 -ao:%AGENT_OPTIONS%

goto :eof
:concat_lib_path
IF ["%LIBPATH%"] EQU [""] (
  set "LIBPATH=%1"
) ELSE (
  set "LIBPATH=%LIBPATH%;%1"
)