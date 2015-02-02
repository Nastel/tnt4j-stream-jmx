# PingJMX
Lightweight framework to stream JMX metrics into event sinks such as: file, socket, log4j, monitoring tools, user defined.

These metrics can be used to monitor health, performance and availability of your JVMs and applications.
Use PingJMX to imbed a monitoring agent within your application and monitor memory, GC activity, CPU as
well as user defined MBeans.

Here is what you can do with PingJMX:
* Periodic JVM heartbeat
* Monitor memory utilization, GC activity, memory leaks
* High/Low, normal vs. abnormal CPU usage
* Monitor threading, runtime and other JVM performance metrics
* Monitor standard and custom MBean attributes
* Application state dumps on VM shutdown for diagnostics
* Conditional streaming based on custom filters

# Why PingJMX
PingJMX provides and easy, lightweight and secure way to stream and monitor JMX metrics from within
java runtime containers.

* Stream JMX metrics out of the JVM container (vs. polling from outside/remote)
* Makes it easy to monitor farms of JMVs, application servers
* No need to enable each JVM for remote JMX, SSL, security, ports, firewalls
* Integration with monitoring tools for alerting, pro-active monitoring (AutoPilot M6)
* Integration with cloud analytics tools (https://www.jkoolcloud.com)
* Integration with log4j (via TNT4J event sinks)
* Imbedded application state dump framework for diagnostics
* Easily build your own extensions, monitors

# Using PingJMX
It is simple, just imbed the following code into your application:
```java
// obtain PingFactory instance
PingFactory factory = DefaultPingFactory.getInstance();
// create an instance of the pinger that will sample mbeans
Pinger platformJmx = factory.newInstance();
//schedule collection (ping) for given MBean filter and 30000 ms sampling period
platformJmx.setSchedule(Pinger.JMX_FILTER_ALL, 30000).run();
```
<b>Note that `setSchedule(..).run()` sequence must be called to run the schedule. `setSchedule(..)` just sets the
scheduling parameters, `run()` executes the schedule.</b>

To schedule metric collection for a specific MBean server:
```java
// obtain PingFactory instance
PingFactory factory = DefaultPingFactory.getInstance();
// create an instance of the pinger that will sample mbeans
Pinger platformJmx = factory.newInstance(ManagementFactory.getPlatformMBeanServer());
//schedule collection (ping) for given MBean filter and 30000 ms sampling period
platformJmx.setSchedule(Pinger.JMX_FILTER_ALL, 30000).run();
```
Below is an example of creating collection for all registered mbean servers:
```java
// obtain PingFactory instance
PingFactory factory = DefaultPingFactory.getInstance();
// find other registered mbean servers
ArrayList<MBeanServer> mlist = MBeanServerFactory.findMBeanServer(null);
for (MBeanServer server: mlist) {
	Pinger jmxp = factory.newInstance(server);
	jmxp.setSchedule(Pinger.JMX_FILTER_ALL, 30000).run();
}
```
PingJMX provides a helper class `PingAgent` that lets you schedule sampling for all registered `MBeanServer` instances.
```java
PingAgent.ping(Pinger.JMX_FILTER_ALL, 60000, TimeUnit.MILLISECONDS);
```
<b>NOTE:</b> Sampled MBean attributes and associated values are stored in a collection of `Snapshot` objects stored within `Activity` instance. Current `Activity` instance can be obtained via `AttributeSample` passed when calling listeners such as `Condition`, `SampleListener`. Snapshots can be accessed using `Activity.getSnapshots()` method call.

<b>NOTE:</b> Sampled output is written to underlying tnt4j event sink configured in `tnt4j.properties` file. Sink destinations could be a file, socket, log4j, user defined event sink implementations. 

For more information on TNT4J and `tnt4j.properties` see (https://github.com/Nastel/TNT4J/wiki/Getting-Started).

## Running PingJMX as standalone app
```java
java -Dlog4j.configuration=file:log4j.properties -classpath "tnt4j-ping-jmx.jar;lib/tnt4j-api-final-all.jar" org.tnt4j.pingjmx.PingAgent "*:*" 10000 60000
```

## Running PingJMX as -javaagent
PingJMX can be invoked as a a javaagent using `-javaagent` command line:
```java
java -javaagent:tnt4j-ping-jmx.jar="*:*!30000" -Dlog4j.configuration=file:log4j.properties -Dtnt4j.config=tnt4j.properties -classpath "tnt4j-ping-jmx.jar;lib/tnt4j-api-final-all.jar" your.class.name your-args
```
The options are `-javaagent:tnt4j-ping-jmx.jar="mbean-filter!sample-time-ms"`, classpath must include pingjmx jar files as well as locations of log4j and tnt4j configuration files.

## Where do the streams go?
PingJMX streams all collected metrics based on a scheduled interval via TNT4J event streaming framework.
All streams are written into TNT4J event sinks defined in `tnt4j.properties` file which is defined by `-Dtnt4j.config=tnt4j.properties` property. 

Below is an example of TNT4J stream definition where all PingJMX streams are written into a socket event sink
`com.nastel.jkool.tnt4j.sink.SocketEventSinkFactory`, formatted by `org.tnt4j.pingjmx.format.FactNameValueFormatter` :
```
;Stanza used for PingJMX sources
{
	source: org.tnt4j.pingjmx
	source.factory: com.nastel.jkool.tnt4j.source.SourceFactoryImpl
	source.factory.GEOADDR: NewYork
	source.factory.DATACENTER: YourDC
	source.factory.RootFQN: SERVER=?#DATACENTER=?#GEOADDR=?	
	
	tracker.factory: com.nastel.jkool.tnt4j.tracker.DefaultTrackerFactory
	dump.sink.factory: com.nastel.jkool.tnt4j.dump.DefaultDumpSinkFactory

	; Event sink definition where all streams are recorded

	event.sink.factory: com.nastel.jkool.tnt4j.sink.BufferedEventSinkFactory
	event.sink.factory.EventSinkFactory: com.nastel.jkool.tnt4j.sink.SocketEventSinkFactory
	event.sink.factory.EventSinkFactory.eventSinkFactory: com.nastel.jkool.tnt4j.sink.NullEventSinkFactory
	event.sink.factory.EventSinkFactory.Host: localhost
	event.sink.factory.EventSinkFactory.Port: 6060

	; Configure default sink filter 
	event.sink.factory.Filter: com.nastel.jkool.tnt4j.filters.EventLevelTimeFilter
	event.sink.factory.Filter.Level: TRACE
	
	event.formatter: org.tnt4j.pingjmx.format.FactNameValueFormatter
	tracking.selector: com.nastel.jkool.tnt4j.selector.DefaultTrackingSelector
	tracking.selector.Repository: com.nastel.jkool.tnt4j.repository.FileTokenRepository
}
```
To stream PingJMX into a log file:
```
;Stanza used for PingJMX sources
{
	source: org.tnt4j.pingjmx
	source.factory: com.nastel.jkool.tnt4j.source.SourceFactoryImpl
	source.factory.GEOADDR: NewYork
	source.factory.DATACENTER: YourDC
	source.factory.RootFQN: SERVER=?#DATACENTER=?#GEOADDR=?	
	
	tracker.factory: com.nastel.jkool.tnt4j.tracker.DefaultTrackerFactory
	dump.sink.factory: com.nastel.jkool.tnt4j.dump.DefaultDumpSinkFactory

	; Event sink definition where all streams are recorded
	event.sink.factory: com.nastel.jkool.tnt4j.sink.BufferedEventSinkFactory
	event.sink.factory.EventSinkFactory: com.nastel.jkool.tnt4j.sink.FileEventSinkFactory
	event.sink.factory.EventSinkFactory.FileName: MyStream.log

	; Configure default sink filter 
	event.sink.factory.Filter: com.nastel.jkool.tnt4j.filters.EventLevelTimeFilter
	event.sink.factory.Filter.Level: TRACE
	
	event.formatter: org.tnt4j.pingjmx.format.FactNameValueFormatter
	tracking.selector: com.nastel.jkool.tnt4j.selector.DefaultTrackingSelector
	tracking.selector.Repository: com.nastel.jkool.tnt4j.repository.FileTokenRepository
}
```
You can write your own custom event sinks (HTTPS, HTTP, etc) and your own stream formatters without having to change PingJMX
code or your application. TNT4J comes with a set of built-in event sink implementations such as: 

* `com.nastel.jkool.tnt4j.logger.Log4JEventSinkFactory` -- log4j
* `com.nastel.jkool.tnt4j.sink.BufferedEventSinkFactory` -- buffered sink
* `com.nastel.jkool.tnt4j.sink.FileEventSinkFactory` - standard log file
* `com.nastel.jkool.tnt4j.sink.SocketEventSinkFactory` -- socket (tcp/ip)
* `com.nastel.jkool.tnt4j.sink.NullEventSinkFactory` -- null (empty)

## Auto-generating application state dump
PingJMX is utilizing TNT4J state dump capability to generate application state dumps

(1) Dump on VM shutdown:
```java
java -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.provider.default=true -Dtnt4j.dump.folder=./ ...
```
(2) Dump on uncaught thread exceptions:
```java
java -Dtnt4j.dump.on.exceptionn=true -Dtnt4j.dump.provider.default=true -Dtnt4j.dump.folder=./ ...
```
`-Dtnt4j.dump.folder=./` specifies the destination folder where dump (.dump) files will be created (default is current working directory).

By default PingJMX will generate dumps with the following info:

* Java Properties Dump -- `PropertiesDumpProvider`
* Java Runtime Dump -- `MXBeanDumpProvider`
* Thread Stack Dump -- `ThreadDumpProvider`
* Thread Deadlock Dump -- `ThreadDumpProvider`
* Logging Stats Dump -- `LoggerDumpProvider`

You may create your own dump providers and handlers (https://github.com/Nastel/TNT4J/wiki/Getting-Started#application-state-dumps)
## Overriding default `PingFactory`
`PingFactory` instances are used to generate `Pinger` implementation for a specific runtime environment. PingJMX supplies pinger and ping factories for standard JVMs, JBoss,
WebSphere Application Server. You may want to override default `PingFactory` with your own or an altenative by specifying:
```java
java -Dorg.tnt4j.ping.factory=org.tnt4j.pingjmx.PlatformPingFactory ...
```
`PingFactory` is used to generate instances of the underlying pinger implementatons (objects that provide sampling of underlying mbeans).
```java
// return default or user defined PingFactory implementation
PingFactory factory = DefaultPingFactory.getInstance();
...
```
## Managing Sample Behavior
PingJMX provides a way to intercept sampling events such as pre, during an post for each sample run and control sample behavior. See `SampleListener` interface for more details. Applications may register more than one listener per `Pinger`. Each listener is called in registration order.
In addition to intercepting sample events, applications may want to control weather how one ore more attributes are sampled and whether the sample is reported/logged. See example below:
```java
// return default or user defined PingFactory implementation
PingFactory factory = DefaultPingFactory.getInstance();
// create an instance of the pinger that will sample mbeans
Pinger platformJmx = factory.newInstance();
platformJmx.setSchedule(Pinger.JMX_FILTER_ALL, 30000).addListener(new MySampleListener())).run();
```
Below is a sample of what `MySampleListener` may look like:
```java
class MySampleListener implements SampleListener {
	@Override
	public void pre(SampleStats stats, Activity activity) {
		// called once per sample, begining of each sample
		// set activity to NOOP to disable further sampling
		// no other attrubute will be sampled during this sample
		if (some-condition) {
			activity.setType(OpType.NOOP);
		}
	}

	@Override
	public boolean sample(SampleStats stats, AttributeSample sample) {
		// called once per sampled attribute
		// return false to skip this attribute from sampling collection
		if (some-condition) {
			return false;
		}
		return true;
	}

	@Override
	public void post(SampleStats stats, Activity activity) {
		// called once per sample, end of each sample
		// set activity to NOOP to disable sampling reporting
		if (some-condition) {
			activity.setType(OpType.NOOP);
		}
	}
}
```
## Conditions and Actions
PingJMX allows you to associate condtions with user defined actions based on values of MBean attributes on each sampling
interval. For example, what if you wanted to setup an action when a specific mbean attribute exceeds a certain threashold?
PingJMX `Condition` and `AttributeAction` interfaces allow you to call your action at runtime every time a condition is evaluated to true. See example below:
```java
// return default or user defined PingFactory implementation
PingFactory factory = DefaultPingFactory.getInstance();
// create an instance of the pinger that will sample mbeans
Pinger platformJmx = factory.newInstance();
// create a condition when ThreadCount > 100
Condition myCondition = new SimpleCondition("java.lang:type=Threading", "ThreadCount", 100, ">");
//schedule collection (ping) for given MBean filter and 30000 ms sampling period
platformJmx.setSchedule(Pinger.JMX_FILTER_ALL, 30000).register(myCondition, new MyAttributeAction()).run();
```
Below is a sample of what `MyAttributeAction` may look like:
```java
public class MyAttributeAction implements AttributeAction {
	@Override
	public Object action(Condition cond, AttributeSample sample) {
		Activity activity = sample.getActivity();
		Collection<Snapshot> metrics = activity.getSnapshots();
		System.out.println("Myaction called with value=" + sample.get()
			+ ", age.usec=" + sample.ageUsec()
			+ ", count=" + metrics.size());
		return null;
	}
}
```

# Project Dependencies
PingJMX requires the following:
* JDK 1.6+
* TNT4J (https://github.com/Nastel/TNT4J)

# Available Integrations
* TNT4J (https://github.com/Nastel/TNT4J)
* Log4J (http://logging.apache.org/log4j/1.2/)
* jkoolcloud.com (https://www.jkoolcloud.com)
* AutoPilot M6 (http://www.nastel.com/products/autopilot-m6.html)
