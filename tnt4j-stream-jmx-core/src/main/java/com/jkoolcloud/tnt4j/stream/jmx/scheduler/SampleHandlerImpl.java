/*
 * Copyright 2015-2022 JKOOL, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jkoolcloud.tnt4j.stream.jmx.scheduler;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.management.*;
import javax.management.relation.MBeanServerNotificationFilter;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.Activity;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.core.PropertySnapshot;
import com.jkoolcloud.tnt4j.core.Snapshot;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.stream.jmx.aggregations.AggregationsManager;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.*;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleContext;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.UnsupportedAttributeException;

/**
 * <p>
 * This class provides implementation for handling sample/heart-beats generated by {@link Scheduler} class, which
 * handles implementation of all metric collection for each sample.
 * </p>
 * 
 * @see Scheduler
 * @see SampleHandler
 * @see AttributeSample
 * @see AttributeCondition
 * 
 * @version $Revision: 1 $
 */
public class SampleHandlerImpl implements SampleHandler, NotificationListener {
	public static String STAT_NOOP_COUNT = "noop.count";
	public static String STAT_SAMPLE_COUNT = "sample.count";
	public static String STAT_TOTAL_ERROR_COUNT = "total.error.count";
	public static String STAT_TOTAL_EXCLUDE_COUNT = "total.exclude.count";
	public static String STAT_MBEAN_COUNT = "mbean.count";
	public static String STAT_CONDITION_COUNT = "condition.count";
	public static String STAT_LISTENER_COUNT = "listener.count";
	public static String STAT_TOTAL_ACTION_COUNT = "total.action.count";
	public static String STAT_TOTAL_METRIC_COUNT = "total.metric.count";
	public static String STAT_LAST_METRIC_COUNT = "last.metric.count";
	public static String STAT_SAMPLE_TIME_USEC = "sample.time.usec";

	private final ReentrantLock lock = new ReentrantLock();
	private final Source source;

	String mbeanIncFilter, mbeanExcFilter;
	long sampleCount = 0, totalMetricCount = 0, totalActionCount = 0;
	long lastMetricCount = 0, lastSampleTimeUsec = 0;
	long noopCount = 0, excCount = 0, errorCount = 0;

	MBeanServerConnection mbeanServer;
	SampleContext context;
	Throwable lastError;

	MBeanServerNotificationFilter MBeanFilter;
	List<ObjectName> iFilters = new ArrayList<>(5), eFilters = new ArrayList<>(5);
	Map<AttributeCondition, AttributeAction> conditions = new LinkedHashMap<>(89);
	Map<ObjectName, MBeanInfo> mbeans = new ConcurrentHashMap<>(89);

	final List<SampleListener> listeners = new ArrayList<>(5);

	/**
	 * Create new instance of {@code SampleHandlerImpl} with a given MBean server and a set of filters.
	 *
	 * @param mServerConn
	 *            MBean server connection instance
	 * @param incFilter
	 *            MBean include filters semicolon separated
	 * @param excFilter
	 *            MBean exclude filters semicolon separated
	 * @param source
	 *            sampler source
	 */
	public SampleHandlerImpl(MBeanServerConnection mServerConn, String incFilter, String excFilter, Source source) {
		mbeanServer = mServerConn;
		mbeanIncFilter = incFilter;
		mbeanExcFilter = excFilter;
		this.source = source;
		context = new SampleContextImpl(this);
	}

	/**
	 * Tokenize a given set of filters into JMX object names
	 * 
	 * @param filter
	 *            semicolon set of JMX filters
	 * @param filters
	 *            list of object names
	 * @throws MalformedObjectNameException
	 *             if filter defined object name is malformed
	 */
	private static void tokenizeFilters(String filter, List<ObjectName> filters) throws MalformedObjectNameException {
		StringTokenizer itk = new StringTokenizer(filter, ";");
		while (itk.hasMoreTokens()) {
			String objName = itk.nextToken().trim();
			try {
				filters.add(new ObjectName(objName));
			} catch (MalformedObjectNameException exc) {
				if (!exc.getMessage().contains(objName)) {
					MalformedObjectNameException mexc = new MalformedObjectNameException(
							exc.getMessage() + " for '" + objName + "'");
					mexc.setStackTrace(exc.getStackTrace());
					throw mexc;
				}
				throw exc;
			}
		}
	}

	/**
	 * Install MBean add/delete listener
	 * 
	 * @throws IOException
	 *             if communication problem occurred when talking to the MBean server
	 * @throws InstanceNotFoundException
	 *             if MBean name provided does not match any of the registered MBeans
	 */
	private void listenForChanges() throws IOException, InstanceNotFoundException {
		if (MBeanFilter == null) {
			MBeanFilter = new MBeanServerNotificationFilter();
			MBeanFilter.enableAllObjectNames();
			mbeanServer.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, this, MBeanFilter, null);
		}
	}

	/**
	 * Determine if a given object name matches include/exclusion filters
	 * 
	 * @param oName
	 *            object name
	 * @return {@code true} if included, {@code false} otherwise
	 */
	public boolean isFilterIncluded(ObjectName oName) {
		for (ObjectName eFilter : eFilters) {
			if (eFilter.apply(oName)) {
				return false;
			}
		}
		for (ObjectName incFilter : iFilters) {
			if (incFilter.apply(oName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Load JMX beans based on a configured MBean filter list. All loaded MBeans are stored in {@link HashMap}.
	 */
	private void loadMBeans() {
		try {
			tokenizeFilters(mbeanIncFilter, iFilters);
			if (StringUtils.isNotEmpty(mbeanExcFilter)) {
				tokenizeFilters(mbeanExcFilter, eFilters);
			}
			listenForChanges();

			// run inclusion
			for (ObjectName nameFilter : iFilters) {
				Set<ObjectName> objSet = mbeanServer.queryNames(nameFilter, null);
				if (!eFilters.isEmpty()) {
					excludeFromSet(objSet, eFilters);
				}
				for (ObjectName oName : objSet) {
					mbeans.put(oName, mbeanServer.getMBeanInfo(oName));
					runRegister(oName);
				}
			}
		} catch (Exception ex) {
			doError(ex);
		}
	}

	/**
	 * Exclude MBeans based on a list of exclude object name patterns
	 * 
	 * @param objSet
	 *            JMX object name set
	 * @param eFilters
	 *            list of MBean exclusions
	 */
	private static void excludeFromSet(Set<ObjectName> objSet, List<ObjectName> eFilters) {
		for (ObjectName eName : eFilters) {
			objSet.removeIf(eName::apply);
		}
	}

	/**
	 * Sample MBeans based on a configured MBean filter list and store within given activity as snapshots.
	 * 
	 * @param activity
	 *            instance where sampled MBean attributes are stored
	 * @return number of metrics loaded from all MBeans
	 */
	private int sampleMBeans(Activity activity) {
		if (source != null) {
			activity.setSource(source);
		}

		int pCount = 0;
		for (Entry<ObjectName, MBeanInfo> entry : mbeans.entrySet()) {
			ObjectName name = entry.getKey();
			MBeanInfo info = entry.getValue();

			PropertySnapshot snapshot = new PropertySnapshot(name.getDomain(), name.getCanonicalName());
			AttributeSample sample = AttributeSample.newAttributeSample(activity, snapshot, mbeanServer, name,
					info.getAttributes());
			try {
				if (doPre(sample)) {
					sample(sample); // obtain a sample
					doPost(sample);
					if (sample.isError()) {
						doError(sample, OpLevel.WARNING);
					}
					if (sample.isError() && "ConnectException".equals(sample.getError().getClass().getSimpleName())) {
						return pCount;
					}
				}
			} catch (Throwable ex) {
				doError(sample, ex, OpLevel.ERROR);
			} finally {
				excCount += sample.excludes().size();
				evalAttrConditions(sample);
			}
			if (snapshot.size() > 0) {
				doComplete(activity, name, info, snapshot);

				pCount += snapshot.size();
				activity.addSnapshot(snapshot);
			}
		}
		return pCount;
	}

	/**
	 * Sample and retrieve the value associated with the MBean attribute.
	 *
	 * @param sample
	 *            MBean sample instance
	 * @return the value associated with the current attribute
	 * @throws Exception
	 *             if exception occurs while sampling attribute value
	 *
	 * @see AttributeSample#sample()
	 */
	protected Object sample(AttributeSample sample) throws Exception {
		return sample.sample();
	}

	/**
	 * Run and evaluate all registered conditions and invoke associated {@link AttributeAction} instances.
	 * 
	 * @param sample
	 *            MBean sample instance
	 * @see AttributeSample
	 */
	protected void evalAttrConditions(AttributeSample sample) {
		for (Map.Entry<AttributeCondition, AttributeAction> entry : conditions.entrySet()) {
			if (entry.getKey().evaluate(sample)) {
				totalActionCount++;
				entry.getValue().action(context, entry.getKey(), sample);
			}
		}
	}

	/**
	 * Finish processing of the activity sampling
	 * 
	 * @param activity
	 *            instance
	 * @return snapshot instance containing metrics at the end of each sample
	 */
	private PropertySnapshot finish(Activity activity) {
		AggregationsManager.aggregate(activity);

		PropertySnapshot snapshot = new PropertySnapshot(activity.getName(), "SampleContext");
		snapshot.add(STAT_NOOP_COUNT, noopCount);
		snapshot.add(STAT_SAMPLE_COUNT, sampleCount);
		snapshot.add(STAT_TOTAL_ERROR_COUNT, errorCount);
		snapshot.add(STAT_TOTAL_EXCLUDE_COUNT, excCount);
		snapshot.add(STAT_MBEAN_COUNT, mbeans.size());
		snapshot.add(STAT_CONDITION_COUNT, conditions.size());
		snapshot.add(STAT_LISTENER_COUNT, listeners.size());
		snapshot.add(STAT_TOTAL_ACTION_COUNT, totalActionCount);
		snapshot.add(STAT_TOTAL_METRIC_COUNT, totalMetricCount);
		snapshot.add(STAT_LAST_METRIC_COUNT, lastMetricCount);
		snapshot.add(STAT_SAMPLE_TIME_USEC, lastSampleTimeUsec);

		// get custom statistics
		Map<String, Object> stats = new HashMap<>();
		doStats(stats);
		snapshot.addAll(stats);

		activity.addSnapshot(snapshot);
		return snapshot;
	}

	@Override
	public void started(Activity activity) {
		lock.lock();
		try {
			lastError = null; // reset last sample error
			runPre(activity);
			if ((!activity.isNoop()) && (mbeans.isEmpty())) {
				loadMBeans();
			} else if (activity.isNoop()) {
				noopCount++;
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void stopped(Activity activity) {
		if (!activity.isNoop()) {
			lock.lock();
			try {
				long started = System.nanoTime();
				sampleCount++;
				lastMetricCount = sampleMBeans(activity);
				totalMetricCount += lastMetricCount;
				lastSampleTimeUsec = (System.nanoTime() - started) / 1000;

				// run post listeners
				runPost(activity);
				if (activity.isNoop()) {
					noopCount++;
				}
				// compute sampling statistics
				finish(activity);
			} catch (Throwable ex) {
				doError(ex);
			} finally {
				lock.unlock();
			}
		}
	}

	/**
	 * Reset all counters maintained by sampling handler
	 * 
	 * @return instance to the sampling context
	 */
	public SampleContext resetCounters() {
		lock.lock();
		try {
			sampleCount = 0;
			totalMetricCount = 0;
			totalActionCount = 0;
			lastMetricCount = 0;
			lastSampleTimeUsec = 0;
			noopCount = 0;
			excCount = 0;
			errorCount = 0;
			lastError = null;
			return context;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Run {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#register(SampleContext, ObjectName)} for all
	 * registered listeners.
	 * 
	 * @param name
	 *            MBean object name
	 */
	private void runRegister(ObjectName name) {
		synchronized (this.listeners) {
			for (SampleListener lst : listeners) {
				lst.register(context, name);
			}
		}
	}

	/**
	 * Run {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#unregister(SampleContext, ObjectName)} for all
	 * registered listeners.
	 * 
	 * @param name
	 *            MBean object name
	 */
	private void runUnRegister(ObjectName name) {
		synchronized (this.listeners) {
			for (SampleListener lst : listeners) {
				lst.unregister(context, name);
			}
		}
	}

	/**
	 * Run {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#post(SampleContext, Activity)} for all registered
	 * listeners.
	 * 
	 * @param activity
	 *            sampling activity instance
	 */
	private void runPost(Activity activity) {
		synchronized (this.listeners) {
			for (SampleListener lst : listeners) {
				lst.post(context, activity);
			}
		}
	}

	/**
	 * Run {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#pre(SampleContext, Activity)} for all registered
	 * listeners.
	 * 
	 * @param activity
	 *            sampling activity instance
	 */
	private void runPre(Activity activity) {
		synchronized (this.listeners) {
			for (SampleListener lst : listeners) {
				lst.pre(context, activity);
			}
		}
	}

	/**
	 * Run
	 * {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#pre(com.jkoolcloud.tnt4j.stream.jmx.core.SampleContext, com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample)}
	 * for all registered listeners.
	 * 
	 * @param sample
	 *            current attribute sample instance
	 */
	private boolean doPre(AttributeSample sample) {
		synchronized (this.listeners) {
			for (SampleListener lst : listeners) {
				lst.pre(context, sample);
			}
		}
		return sample.hasAttributesToSample();
	}

	/**
	 * Run
	 * {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#post(com.jkoolcloud.tnt4j.stream.jmx.core.SampleContext, com.jkoolcloud.tnt4j.core.Activity)}
	 * for all registered listeners.
	 * 
	 * @param sample
	 *            current attribute sample instance
	 * @throws UnsupportedAttributeException
	 *             if attribute value can not be resolved
	 */
	private void doPost(AttributeSample sample) throws UnsupportedAttributeException {
		synchronized (this.listeners) {
			for (SampleListener lst : listeners) {
				lst.post(context, sample);
			}
		}
	}

	/**
	 * Run
	 * {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#complete(com.jkoolcloud.tnt4j.stream.jmx.core.SampleContext, com.jkoolcloud.tnt4j.core.Activity, javax.management.ObjectName, javax.management.MBeanInfo, com.jkoolcloud.tnt4j.core.Snapshot)}
	 * for all registered listeners.
	 *
	 * @param activity
	 *            sampling activity instance
	 * @param name
	 *            MBean object name
	 * @param info
	 *            MBean info
	 * @param snapshot
	 *            MBean attribute values snapshot
	 */
	private void doComplete(Activity activity, ObjectName name, MBeanInfo info, Snapshot snapshot) {
		synchronized (this.listeners) {
			for (SampleListener lst : listeners) {
				lst.complete(context, activity, name, info, snapshot);
			}
		}
	}

	/**
	 * Run {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#error(SampleContext, AttributeSample, OpLevel)}
	 * for all registered listeners.
	 * 
	 * @param sample
	 *            current attribute sample instance
	 * @param ex
	 *            exception associated with the error
	 * @param level
	 *            error severity level
	 *
	 * @see #doError(AttributeSample, OpLevel)
	 */
	private void doError(AttributeSample sample, Throwable ex, OpLevel level) {
		sample.setError(ex);
		doError(sample, level);
	}

	/**
	 * Run {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#error(SampleContext, AttributeSample, OpLevel)}
	 * for all registered listeners.
	 *
	 * @param sample
	 *            current attribute sample instance
	 * @param level
	 *            error severity level
	 */
	private void doError(AttributeSample sample, OpLevel level) {
		errorCount++;
		lastError = sample.getError();
		synchronized (this.listeners) {
			for (SampleListener lst : listeners) {
				lst.error(context, sample, level);
			}
		}
	}

	/**
	 * Run {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#error(SampleContext, Throwable)} for all
	 * registered listeners.
	 * 
	 * @param ex
	 *            exception associated with the error
	 */
	private void doError(Throwable ex) {
		errorCount++;
		lastError = ex;
		synchronized (this.listeners) {
			for (SampleListener lst : listeners) {
				lst.error(context, ex);
			}
		}
	}

	/**
	 * Run {@link com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener#getStats(SampleContext, Map)} for all registered
	 * listeners.
	 * 
	 * @param stats
	 *            map of key/value statistics
	 */
	private void doStats(Map<String, Object> stats) {
		synchronized (this.listeners) {
			for (SampleListener lst : listeners) {
				lst.getStats(context, stats);
			}
		}
	}

	@Override
	public SampleHandler register(AttributeCondition cond, AttributeAction action) {
		conditions.put(cond, (action == null ? NoopAction.NOOP : action));
		return this;
	}

	@Override
	public SampleHandler register(AttributeCondition cond) {
		register(cond, NoopAction.NOOP);
		return this;
	}

	@Override
	public SampleHandler addListener(SampleListener listener) {
		listeners.add(listener);
		return this;
	}

	@Override
	public SampleHandler removeListener(SampleListener listener) {
		listeners.remove(listener);
		return this;
	}

	@Override
	public SampleContext getContext() {
		return context;
	}

	@Override
	public void handleNotification(Notification notification, Object handback) {
		if (notification instanceof MBeanServerNotification) {
			MBeanServerNotification mbeanEvent = (MBeanServerNotification) notification;
			if (notification.getType().equalsIgnoreCase(MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
				try {
					if (isFilterIncluded(mbeanEvent.getMBeanName())) {
						mbeans.put(mbeanEvent.getMBeanName(), mbeanServer.getMBeanInfo(mbeanEvent.getMBeanName()));
						runRegister(mbeanEvent.getMBeanName());
					}
				} catch (Throwable ex) {
					doError(ex);
				}
			} else if (notification.getType().equalsIgnoreCase(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
				mbeans.remove(mbeanEvent.getMBeanName());
				runUnRegister(mbeanEvent.getMBeanName());
			}
		}
	}

	@Override
	public void cleanup() {
		eFilters.clear();
		conditions.clear();
		mbeans.clear();
		listeners.clear();
	}

}