package org.tnt4j.pingjmx;

import javax.management.MBeanServer;

public class WASPingFactory implements PingFactory {
	
	@Override
	public PlatformJmxPing newInstance() {
		return new WASJmxPing();
	}

	@Override
	public PlatformJmxPing newInstance(MBeanServer mserver) {
		return new WASJmxPing(mserver);
	}
}
