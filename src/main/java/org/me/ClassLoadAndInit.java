package org.me;

public class ClassLoadAndInit {
	/**
	 * -XX:+TraceClassLoading
	 * -XX:+TraceClassLoadingPreorder
	 * -XX:+TraceClassInitialization
	 * -XX:+TraceClassResolution
	 * -XX:+TraceClassUnloading
	 * -XX:+TraceLoaderConstraints
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("hello");
	}
}
