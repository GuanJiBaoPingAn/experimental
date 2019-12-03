public class NativeStackOverflow {

	private static void recursion(int depth, Runnable nativeCall) {
		nativeCall.run();
		recursion(depth + 1, nativeCall);
	}

	public static void main(String[] args) {
//		System.load("D:\\Learn\\cpp\\DLLGen\\Debug\\DLLGen.dll");
//		System.load("D:\\Learn\\cpp\\DLLGen\\x64\\Debug\\DLLGen.dll");
//		System.loadLibrary("deepNative");

		// Fails with StackOverflowError
/*		recursion(0, new Runnable() {
			@Override
			public void run() {
				NativeStackOverflow.deepNative64K();
			}
		});*/

		// Crashes JVM, unless StackShadowPages > 32
		/*recursion(0, new Runnable() {
			@Override
			public void run() {
				NativeStackOverflow.deepNative128K();
			}
		});*/

		Runnable r = new Runnable() {
			@Override
			public void run() {
				NativeStackOverflow.invalidMemoryAccess(null);
			}
		};
		r.run();
	}

	// JNIEXPORT void JNICALL
	// Java_NativeStackOverflow_deepNative64K() {
	//     char buf[64 * 1024];
	//     memset(buf, sizeof(buf), 1);
	// }
	private static native void deepNative64K();

	// JNIEXPORT void JNICALL
	// Java_NativeStackOverflow_deepNative128K() {
	//     char buf[128 * 1024];
	//     memset(buf, sizeof(buf), 1);
	// }
	private static native void deepNative128K();

	private static native void invalidMemoryAccess(Object o);

}
