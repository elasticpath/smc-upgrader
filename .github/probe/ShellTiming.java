// Temporary PB-14428 probe: times ShellCommand under different stdin setups. Remove before merging.
import com.elasticpath.tools.smcupgrader.ShellCommand;

public class ShellTiming {
	public static void main(String[] args) throws Exception {
		ShellCommand shell = new ShellCommand();
		if (args.length > 0 && args[0].equals("reader")) {
			// Mimic Surefire's command reader: a thread with a read always pending on stdin.
			Thread reader = new Thread(() -> {
				try {
					int b = System.in.read();
					System.out.println("reader got byte " + b);
				} catch (java.io.IOException e) {
					e.printStackTrace();
				}
			});
			reader.setDaemon(true);
			reader.start();
			Thread.sleep(500);
		}
		long start = System.nanoTime();
		System.out.println("shell=" + shell.resolveShell() + " resolve ms=" + (System.nanoTime() - start) / 1_000_000);
		time("default pipes", shell, pb -> { });
		time("discard out/err", shell, pb -> pb.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD));
		time("inheritIO", shell, ProcessBuilder::inheritIO);
		time("inherit out/err, stdin pipe", shell, pb -> pb.redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT));
		time("inherit stdin only", shell, pb -> pb.redirectInput(ProcessBuilder.Redirect.INHERIT));
	}

	private static void time(String label, ShellCommand shell, java.util.function.Consumer<ProcessBuilder> configurer) throws Exception {
		for (int i = 0; i < 2; i++) {
			long start = System.nanoTime();
			int exit = shell.run("echo hi from sh", configurer);
			System.out.println(label + " #" + i + ": exit=" + exit + " ms=" + (System.nanoTime() - start) / 1_000_000);
		}
	}
}
