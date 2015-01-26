# PingJMX
Framework to monitor JMX or any other metrics and write to TNT4J event sink: file, socket, other TNT4J event sinks.

# Using PingJMX
It is simple, just imbed the following code into your application:
```java
PlatformJmxPing platformJmx = newInstance();
//schedule jmx collection (ping) for given jmx filter and 30000 ms sampling period
platformJmx.scheduleJmxPing(PingJMX.JMX_FILTER_ALL, 30000);
```
To schedule jmx collection for a specific mbean server:
```java
MBeanServer mserver = ManagementFactory.getPlatformMBeanServer();
PlatformJmxPing platformJmx = newInstance(mserver);
//schedule jmx collection (ping) for given jmx filter and 30000 ms sampling period
platformJmx.scheduleJmxPing(PingJMX.JMX_FILTER_ALL, 30000);
```
Below is an example of creating jmx collection for all registered mbean servers:
```java
// find other registered mbean servers
ArrayList<MBeanServer> mlist = MBeanServerFactory.findMBeanServer(null);
for (MBeanServer server: mlist) {
	PlatformJmxPing jmxp = newInstance(server);
	jmxp.scheduleJmxPing(PingJMX.JMX_FILTER_ALL, 30000);
}
```

