@echo off
setlocal

set RUNDIR=%~dp0

rem ---- WAS environment configuration ----
set WAS_HOME=C:\IBM\WebSphere\AppServer
#set WAS_HOME=C:\Program Files (x86)\IBM\WebSphere\AppServer
set JAVA_HOME=%WAS_HOME%\java
set WAS_PATH=%WAS_HOME%\runtimes\*;%WAS_HOME%\lib\webadmin\management.jar
set MYCLIENTSAS="-Dcom.ibm.CORBA.ConfigURL=file:%RUNDIR%..\config\sas.client.props"
set MYCLIENTSSL="-Dcom.ibm.SSL.ConfigURL=file:%RUNDIR%..\config\ssl.client.props"
rem ----------------------------------------

set TOOLS_PATH=%JAVA_HOME%\lib\tools.jar
rem ---- appending libs path with WAS libs ----
set LIBPATH=%RUNDIR%..\*;%RUNDIR%..\lib\*;%TOOLS_PATH%;%WAS_PATH%
rem -------------------------------------------

set TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true" "-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true"
set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.config=%RUNDIR%..\config\tnt4j.properties"
rem ---- adding WAS specific JMX sampler options ----
set TNT4JOPTS=%TNT4JOPTS% "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.forceObjectName=true" "-Dcom.jkoolcloud.tnt4j.stream.jmx.sampler.factory=com.jkoolcloud.tnt4j.stream.jmx.impl.WASSamplerFactory"
rem -------------------------------------------------

set AGENT_OPTIONS=%2
if "%AGENT_OPTIONS%"=="" set AGENT_OPTIONS=*:*!!10000

set CONN_OPTIONS=-connect -vm:service:jmx:iiop://localhost:2809/jndi/JMXConnector -cp:java.naming.provider.url=corbaname:iiop:localhost:2809
rem --- Uncomment of WAS and IBM JVM requires connection authentication or getting naming related exceptions ---
rem set CONN_OPTIONS=-connect -vm:service:jmx:iiop://localhost:2809/jndi/JMXConnector -ul:Admin -up:admin -cp:java.naming.factory.initial=com.ibm.websphere.naming.WsnInitialContextFactory -cp:java.naming.factory.url.pkgs=com.ibm.ws.naming -cp:java.naming.provider.url=corbaloc:iiop:localhost:2809/WsnAdminNameService
rem ------------------------------------------------------------------------------------------------------------

@echo on
rem --- using JAVA_HOME java and setting WAS specific options ---
"%JAVA_HOME%\bin\java" %TNT4JOPTS% %MYCLIENTSAS% %MYCLIENTSSL% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent %CONN_OPTIONS% -ao:%AGENT_OPTIONS%
rem -------------------------------------------------------------