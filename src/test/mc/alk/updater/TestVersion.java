package test.mc.alk.updater;

import junit.framework.TestCase;
import mc.alk.plugin.updater.v1r4.Version;

public class TestVersion extends TestCase{

	public void testVersion(){
//		http://www.esper.net/publicirc.php
		Version v0 = new Version("3.6.9");
		Version v00 = new Version("3.6.10");
		Version v1 = new Version("3.7.0");
		Version v11 = new Version("v3.7.0");
		Version v2 = new Version("1.1.1.1");
		Version v3 = new Version("1.1.1.1a");
		Version v4 = new Version("1.1.3.4b");
		Version v5 = new Version("1.5.2");
		Version v6 = new Version("1.5.2-dev");
		System.out.println(v0.compareTo(0));
		System.out.println(v0.compareTo(v00));
		System.out.println(v0.compareTo(v1));
		System.out.println(v1.compareTo(v11));
		assertTrue(v6.compareTo(v5) > 0);
	}
}
