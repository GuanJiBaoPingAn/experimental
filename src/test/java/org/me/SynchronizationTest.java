package org.me;

import org.junit.Before;
import org.junit.Test;
import org.openjdk.jol.datamodel.CurrentDataModel;
import org.openjdk.jol.vm.VM;
import org.openjdk.jol.vm.VirtualMachine;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * all test under 64 bits platform
 *
 * |------------------------------------------------------------------|--------------------|
 * |                  Mark Word (64 bits)                  	          |       State        |
 * |------------------------------------------------------------------|--------------------|
 * |  unused:25 | hash:31 | unused:1 | age:4 | biased_lock:1 | lock:2 |       Normal       |
 * |------------------------------------------------------------------|--------------------|
 * |  thread:54 | epoch:2 | unused:1 | age:4 | biased_lock:1 | lock:2 |       Biased       |
 * |------------------------------------------------------------------|--------------------|
 * |               ptr_to_lock_record:30         			 | lock:2 | Lightweight Locked |
 * |------------------------------------------------------------------|--------------------|
 * |               ptr_to_heavyweight_monitor:30  			 | lock:2 | Heavyweight Locked |
 * |------------------------------------------------------------------|--------------------|
 * |                                              			 | lock:2 |    Marked for GC   |
 * |------------------------------------------------------------------|--------------------|
 *
 *  [JavaThread* | epoch | age | 1 | 01] lock is biased toward given thread
 *  [0           | epoch | age | 1 | 01] lock is anonymously biased
 *
 * |--------------------------------|
 * | biased_lock | lock	|   states  |
 * |-------------|------|-----------|
 * |      0	     | 01	|  unlocked |
 * |-------------|------|-----------|
 * |      0	     | 00	|   locked  |
 * |-------------|------|-----------|
 * |      0	     | 10	|   monitor |
 * |-------------|------|-----------|
 * |      0	     | 11	|   marked  | used by markSweep to mark an object not valid at any other time
 * |-------------|------|-----------|
 *
 */
public class SynchronizationTest {

	static boolean DEFAULT_IS_BIG_ENDIAN = false;

	static VirtualMachine vm = null;

	// on 64 bit platform
	static int HEADER_SIZE = 8;

	//
	static int PTR_COMPRESSED = 4;

	static int PTR_WITHOUT_COMPRESSED = 8;

	@Before
	public void init() {
		vm = VM.current();
	}

	/**
	 * Class instance's header size on 64 bits platform should be 8 bytes
	 * -XX:+UseCompressedOops is default enabled
	 */
	@Test
	public void javaHeaderSizeTest() {
		CurrentDataModel currentDataModel = new CurrentDataModel();
		assertEquals(currentDataModel.headerSize(), HEADER_SIZE + PTR_COMPRESSED);
	}

	/**
	 * Class instance's header size on 64 bits platform should be 8 bytes
	 * make sure -XX:-UseCompressedOops is enabled
	 */
	@Test
	public void compressedOopsTest() {
		CurrentDataModel currentDataModel = new CurrentDataModel();
		assertEquals(currentDataModel.headerSize(), HEADER_SIZE + PTR_WITHOUT_COMPRESSED);
	}

	/**
	 * make sure -XX:-UseBiasedLocking is enabled
	 */
	@Test
	public void testObjectSync() {
		TestObject instance = new TestObject();
		int head8 = vm.getInt(instance, 0);
		assertEquals(getLockState(head8), LockStates.UNLOCKED);
		synchronized (instance) {
			head8 = vm.getInt(instance, 0);
			assertEquals(getLockState(head8), LockStates.LOCKED);
		}
		head8 = vm.getInt(instance, 0);
		assertEquals(getLockState(head8), LockStates.UNLOCKED);
	}

	/**
	 * make sure -XX:-UseBiasedLocking is enabled
	 */
	@Test
	public void testObjectMethodSync() {
		TestObject instance = new TestObject();
		int head8 = vm.getInt(instance, 0);
		int head4 = vm.getInt(instance, 4);
		assertEquals(getLockState(head8), LockStates.UNLOCKED);
		instance.synchronizedMethod(vm);
	}

	/**
	 * make sure -XX:+UseBiasedLocking (default enabled) and
	 * -XX:BiasedLockingStartupDelay=0 enabled
	 */
	@Test
	public void testBiasLock() {
		TestObject instance = new TestObject();
		int head8 = vm.getInt(instance, 0);
		assertEquals(getLockState(head8), LockStates.UNLOCKED);
		synchronized (instance) {
			head8 = vm.getInt(instance, 0);
			assertEquals(getLockState(head8), LockStates.UNLOCKED);
		}
		head8 = vm.getInt(instance, 0);
		assertEquals(getLockState(head8), LockStates.UNLOCKED);
	}

	@Test
	public void lockInflationTest() throws InterruptedException {
		final Object object = new Object();
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		final AtomicInteger lockCount1 = new AtomicInteger(0);
		final AtomicInteger lockCount2 = new AtomicInteger(0);

		Thread t1 = new Thread(new Runnable() {
			@Override
			public void run() {
				LockStates lockStates = getLockState(object);
				while (lockStates.value != LockStates.MONITOR.value) {
					synchronized (object) {
						lockCount1.getAndIncrement();
						lockStates = getLockState(object);
					}
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				if (countDownLatch.getCount() > 0) {
					countDownLatch.countDown();
				}
				System.out.println(lockCount1 + " " + lockCount2);
			}
		});

		Thread t2 = new Thread(new Runnable() {
			@Override
			public void run() {
				LockStates lockStates = getLockState(object);
				while (lockStates.value != LockStates.MONITOR.value) {
					synchronized (object) {
						lockCount2.getAndIncrement();
						lockStates = getLockState(object);
					}
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				if (countDownLatch.getCount() > 0) {
					countDownLatch.countDown();
				}
				System.out.println(lockCount1 + " " + lockCount2);
			}
		});

		t1.start();
		t2.start();

		countDownLatch.await();
	}

	@Test
	public void test() throws IOException, InterruptedException {
		CurrentDataModel currentDataModel = new CurrentDataModel();
		TestObject instance = new TestObject();
//		System.out.println(ClassLayout.parseInstance(instance).toPrintable());

		int head8 = vm.getInt(instance, 0);
		int head4 = vm.getInt(instance, 4);
		String head4Str = getHex(head4);
		String head8Str = getHex(head8);
		System.out.println(head4Str + " " + head8Str);
		System.out.println(getBinary(head4) + " " + getBinary(head8));

		System.out.println("----------------------------------");

		synchronized (instance) {
			TimeUnit.SECONDS.sleep(3);
			int head28 = vm.getInt(instance, 0);
			int head24 = vm.getInt(instance, 4);
			String head24Str = getHex(head24);
			String head28Str = getHex(head28);
			System.out.println(head24Str + " " + head28Str);
			System.out.println(getBinary(head24) + " " + getBinary(head28));
		}
	}

	@Test
	public void biasedTest() {
		for (int i = 0; i < 10; i++) {
			System.out.println(getBinary(i));
			System.out.println(getLockState(i));
			System.out.println();
		}
	}

	private static boolean isBiasedLock(int x) {
		return ((x >> 2) & 1) == 1;
	}

	public static LockStates getLockState(Object object) {
		int head8 = vm.getInt(object, 0);
		return getLockState(head8);
	}

	public static LockStates getLockState(int x) {
		boolean lastBit1 = (x & 1) == 1;
		boolean lastBit2 = ((x >> 1) & 1) == 1;
		if (lastBit1) {
			if (lastBit2) {
				// 0b11
				return LockStates.MARKED;
			} else {
				// 0b01
				return LockStates.UNLOCKED;
			}
		} else {
			if (lastBit2) {
				// 0b10
				return LockStates.MONITOR;
			} else {
				// 0b00
				return LockStates.LOCKED;
			}
		}
	}

	/**
	 * big endian layout to binary for a 32 bit integer
	 * @param x
	 * @return
	 */
	private static String getBinary(int x) {
		return getBinary(x, DEFAULT_IS_BIG_ENDIAN);
	}

	private static String getBinary(int x, boolean isBigEndian) {
		String res = null;
		if (isBigEndian) {
			res = toBinary((x >> 0) & 0xFF) + " " +
					toBinary((x >> 8) & 0xFF) + " " +
					toBinary((x >> 16) & 0xFF) + " " +
					toBinary((x >> 24) & 0xFF);
		} else {
			res = toBinary((x >> 24) & 0xFF) + " " +
					toBinary((x >> 16) & 0xFF) + " " +
					toBinary((x >> 8) & 0xFF) + " " +
					toBinary((x >> 0) & 0xFF);
		}
		return res;
	}

	/**
	 * big endian layout to hex for a 32 bit integer
	 * @param x
	 * @return
	 */
	private static String getHex(int x) {
		return getHex(x, DEFAULT_IS_BIG_ENDIAN);
	}

	private static String getHex(int x, boolean isBigEndian) {
		String res = null;
		if (isBigEndian) {
			res = toHex((x >> 0)  & 0xFF) + " " +
					toHex((x >> 8)  & 0xFF) + " " +
					toHex((x >> 16) & 0xFF) + " " +
					toHex((x >> 24) & 0xFF);
		} else {
			res = toHex((x >> 24)  & 0xFF) + " " +
					toHex((x >> 16)  & 0xFF) + " " +
					toHex((x >> 8) & 0xFF) + " " +
					toHex((x >> 0) & 0xFF);
		}
		return res;
	}

	private static String toBinary(int x) {
		String s = Integer.toBinaryString(x);
		int deficit = 8 - s.length();
		for (int c = 0; c < deficit; c++) {
			s = "0" + s;
		}
		return s;
	}

	private static String toHex(int x) {
		String s = Integer.toHexString(x);
		int deficit = 2 - s.length();
		for (int c = 0; c < deficit; c++) {
			s = "0" + s;
		}
		return s;
	}

}

class TestObject {

	int field;

	public void normalMethod() {

	}

	public synchronized void synchronizedMethod(VirtualMachine vm) {
		int head28 = vm.getInt(this, 0);
		int head24 = vm.getInt(this, 4);
		assertEquals(SynchronizationTest.getLockState(head28), LockStates.LOCKED);
	}
}

enum LockStates {
	LOCKED(0, "locked"),		// 0b00
	UNLOCKED(1, "unlocked"),  // 0b01
	MONITOR(2, "monitor"),   // 0b10
	MARKED(3, "marked"),		// 0b11
	;

	int value;

	String name;

	LockStates(int value, String name) {
		this.value = value;
		this.name = name;
	}


	@Override
	public String toString() {
		return this.name;
	}
}