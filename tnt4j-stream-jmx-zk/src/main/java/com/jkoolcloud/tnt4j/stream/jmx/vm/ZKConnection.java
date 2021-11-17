/*
 * Copyright 2014-2021 JKOOL, LLC.
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

package com.jkoolcloud.tnt4j.stream.jmx.vm;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

/**
 * ZooKeeper connection wrapper-helper class.
 * 
 * @version $Revision: 1 $
 */
public class ZKConnection implements Closeable {

	private static final int DEFAULT_CONN_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(5);

	// ZooKeeper instance to access ZooKeeper ensemble
	private ZooKeeper zk;

	/**
	 * Connects to ZooKeeper ensemble. Waits until connection establishment is confirmed over watcher.
	 *
	 * @param connStr
	 *            ZooKeeper ensemble connection definition string
	 * @return zookeeper instance
	 *
	 * @throws java.io.IOException
	 *             if I/O exception occurs while initializing ZooKeeper connection
	 * @throws InterruptedException
	 *             if the current thread is interrupted while waiting
	 *
	 * @see #connect(String, int)
	 */
	public ZooKeeper connect(String connStr) throws IOException, InterruptedException {
		return connect(connStr, DEFAULT_CONN_TIMEOUT);
	}

	/**
	 * Connects to ZooKeeper ensemble. Waits until connection establishment is confirmed over watcher.
	 *
	 * @param connStr
	 *            ZooKeeper ensemble connection definition string
	 * @param timeout
	 *            connection timeout
	 * @return zookeeper instance
	 * 
	 * @throws java.io.IOException
	 *             if I/O exception occurs while initializing ZooKeeper connection
	 * @throws InterruptedException
	 *             if the current thread is interrupted while waiting
	 */
	public ZooKeeper connect(String connStr, int timeout) throws IOException, InterruptedException {
		CountDownLatch connectedSignal = new CountDownLatch(1);
		zk = new ZooKeeper(connStr, timeout, new Watcher() {
			@Override
			public void process(WatchedEvent we) {
				if (we.getState() == Event.KeeperState.SyncConnected) {
					connectedSignal.countDown();
				}
			}
		});

		connectedSignal.await();
		return zk;
	}

	/**
	 * Checks if connection to {@link org.apache.zookeeper.ZooKeeper} ensemble was performed.
	 *
	 * @return {@code true} if {@link org.apache.zookeeper.ZooKeeper} instance is not {@code null}, {@code false} -
	 *         otherwise
	 */
	public boolean isConnected() {
		return zk != null;
	}

	/**
	 * Returns {@link org.apache.zookeeper.ZooKeeper} instance connection was established to.
	 *
	 * @return zookeeper instance
	 */
	public ZooKeeper zk() {
		return zk;
	}

	/**
	 * Disconnects from ZooKeeper ensemble.
	 */
	@Override
	public void close() {
		if (isConnected()) {
			try {
				zk.close();
			} catch (InterruptedException exc) {
			}
		}
	}
}
