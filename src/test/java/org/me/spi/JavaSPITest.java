package org.me.spi;

import org.junit.Test;

import java.util.ServiceLoader;

public class JavaSPITest {
	@Test
	public void test() {
		ServiceLoader<Robot> serviceLoader = ServiceLoader.load(Robot.class);
		System.out.println("Java SPI");
		serviceLoader.forEach(Robot::sayHello);
	}
}
