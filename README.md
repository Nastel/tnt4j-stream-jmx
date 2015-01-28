# PingJMX
Framework to monitor JMX or any other metrics and write to TNT4J event sink: file, socket, other TNT4J event sinks.

# Using PingJMX
It is simple, just imbed the following code into your application:
```java
PingFactory factory = DefaultPingFactory.getInstance().
PlatformJmxPing platformJmx = factory.newInstance();
//schedule jmx collection (ping) for given jmx filter and 30000 ms sampling period
platformJmx.scheduleJmxPing(PingJMX.JMX_FILTER_ALL, 30000);
```
To schedule jmx collection for a specific mbean server:
```java
MBeanServer mserver = ManagementFactory.getPlatformMBeanServer();
PingFactory factory = DefaultPingFactory.getInstance().
PlatformJmxPing platformJmx = factory.newInstance(mserver);
//schedule jmx collection (ping) for given jmx filter and 30000 ms sampling period
platformJmx.scheduleJmxPing(PingJMX.JMX_FILTER_ALL, 30000);
```
Below is an example of creating jmx collection for all registered mbean servers:
```java
PingFactory factory = DefaultPingFactory.getInstance().
// find other registered mbean servers
ArrayList<MBeanServer> mlist = MBeanServerFactory.findMBeanServer(null);
for (MBeanServer server: mlist) {
	PlatformJmxPing jmxp = factory.newInstance(server);
	jmxp.scheduleJmxPing(PingJMX.JMX_FILTER_ALL, 30000);
}
```
All PingJMX output is written to underlying tnt4j event sink configured in `tnt4j.properties` file. Sink destinations could be a file, socket, log4j, user defined event sink implementations.

## Running PingJMX as standalone app
```
java -Dlog4j.configuration=file:log4j.properties -classpath "tnt4j-ping-jmx.jar;lib/tnt4j-api-final-all.jar" org.tnt4j.pingjmx.PingAgent "*:*" 10000 
```

## Running PingJMX as -javaagent
PingJMX can be invoked as a a javaagent using `-javaagent` command line:
```java
java -javaagent:tnt4j-ping-jmx.jar="*:*!30000" -Dlog4j.configuration=file:log4j.properties -Dtnt4j.config=tnt4j.properties -classpath "tnt4j-ping-jmx.jar;lib/tnt4j-api-final-all.jar" your.class.name your-args
```
The options are `-javaagent:tnt4j-ping-jmx.jar="mbean-filter!sample-time-ms"`, classpath must include pingjmx jar files as well as locations of log4j and tnt4j configuration files.

# Project Dependencies
PingJMX requires the following:
* TNT4J (https://github.com/Nastel/TNT4J)
