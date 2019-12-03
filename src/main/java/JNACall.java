import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JNACall {
	public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InterruptedException {
		Class<Native> aClass = Native.class;
		Method dispose = aClass.getDeclaredMethod("dispose");
		dispose.setAccessible(true);
		JNATestDll.INSTANCE.hello();

		dispose.invoke(aClass);
		/**
		 * explicit call for a unloaded library will cause JVM crash here
		 * in hs_err*.log
		 * C  0x00007f3364113968 => here JVM cannot locate DLL
		 */
//		JNATestDll.INSTANCE.hello();

		System.out.println("abc");
	}

	public interface StdCallDll extends StdCallLibrary {
		StdCallDll INSTANCE = (StdCallDll) Native.loadLibrary("msvcrt", StdCallDll.class);

		void printf(String format, Object... args);
	}

	interface JNATestDll extends Library {
		JNATestDll INSTANCE = (JNATestDll) Native.loadLibrary("D:\\Learn\\cpp\\JNA\\x64\\Debug\\JNA", JNATestDll.class);

		public int add(int a, int b);

		public int factorial(int n);

		void hello();

		void stackOverflow();
	}

	interface JNATestDll2 extends Library {
		JNATestDll2 INSTANCE = (JNATestDll2) Native.loadLibrary("D:\\Learn\\cpp\\JNA\\x64\\Debug\\JNA2", JNATestDll2.class);

		public int add(int a, int b);

		public int factorial(int n);

		void hello();

		void stackOverflow();
	}
}
