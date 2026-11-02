package ca.shadowfoxtv.taskkiller;

/** A real subprocess for shell I/O tests; does not invoke su or an Android device. */
public final class ShellFixture {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "large":
                byte[] output = new byte[262144];
                java.util.Arrays.fill(output, (byte) 'x');
                System.out.write(output);
                break;
            case "hang": Thread.sleep(30000); break;
            case "fail": System.out.print("denied"); System.exit(1); break;
            case "input":
                int value;
                while ((value = System.in.read()) != -1) System.out.write(value);
                break;
            default: throw new IllegalArgumentException(args[0]);
        }
    }
}
