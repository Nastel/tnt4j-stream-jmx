@echo off
setlocal

set RUNDIR=%~dp0
set TOOLS_PATH=%JAVA_HOME%\lib\tools.jar

set MODULE_SET=core
rem --- Uncomment when connecting some J2EE implementing application instance JMX
rem set MODULE_SET=core j2ee
rem --- Uncomment when connecting IBM Websphere Application Server (WAS) instance JMX
rem set MODULE_SET=core j2ee was
rem --- Uncomment when connecting IBM Websphere Liberty Server instance JMX
rem set MODULE_SET=core j2ee liberty

rem LIBPATH initialization for stream-jmx modules
for %%a in (%MODULE_SET%) do (
	call :search_lib_path %%a
)
rem echo %LIBPATH%

set LIBPATH=%LIBPATH%;%RUNDIR%..\lib\*;%TOOLS_PATH%
set TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true" "-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true"
set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.config=%RUNDIR%..\config\tnt4j.properties"

set AGENT_OPTIONS=%2
if "%AGENT_OPTIONS%"=="" set AGENT_OPTIONS=*:*!!10000

@echo on
java %TNT4JOPTS% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -connect -vm:%1 -ao:%AGENT_OPTIONS%
@echo off
goto :eof

:concat_lib_path
IF ["%LIBPATH%"] EQU [""] (
  set "LIBPATH=%1"
) ELSE (
  set "LIBPATH=%LIBPATH%;%1"
)
EXIT /B 0
:search_lib_path
for /f "delims=" %%a in ('
    dir /a-d /b /s %RUNDIR%..\tnt4j-stream-jmx-%1*.jar ^| find /i /v "-sources" ^| find /i /v "-javadoc" ^| find /i /v "-test"
') do call :concat_lib_path %%~fa
EXIT /B 0
:eof