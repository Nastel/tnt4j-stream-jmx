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

