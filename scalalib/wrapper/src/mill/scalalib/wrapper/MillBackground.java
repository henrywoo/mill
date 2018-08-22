package mill.scalalib.wrapper;

import java.io.PrintStream;

/**
 * Wraps the given main method, proxying both output streams over System.out to
 * avoid race conditions on the read-side, and starts a suicide-thread to kill
 * the process if the PID file is deleted or changed (indicating a new background
 * process wants to start)
 */
public class MillBackground {
    public static void main(String[] args) throws Throwable{

        PrintStream out = System.out;
        System.setOut(new PrintStream(new mill.main.client.ProxyOutputStream(out, 1)));
        System.setErr(new PrintStream(new mill.main.client.ProxyOutputStream(out, -1)));
        String realMain = args[0];
        String watched = args[1];
        String tombstone = args[2];
        String expected = args[3];
        Thread watcher = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try{
                        Thread.sleep(50);
                        String token = new String(
                            java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(watched))
                        );
                        if (!token.equals(expected)) {
                            new java.io.File(tombstone).createNewFile();
                            System.exit(0);
                        }
                    }catch(Exception e){
                        try {
                            new java.io.File(tombstone).createNewFile();
                        }catch(Exception e2){}
                        System.exit(0);
                    }

                }
            }
        });
        watcher.setDaemon(true);
        watcher.start();

        String[] realArgs = new String[args.length - 4];
        for(int i = 0; i < args.length-4; i++){
            realArgs[i] = args[i+4];
        }
        java.lang.reflect.Method main = Class.forName(realMain).getMethod("main", String[].class);
        try{
            main.invoke(null, (Object)realArgs);
        }catch(java.lang.reflect.InvocationTargetException e){
            StackTraceElement[] s = e.getCause().getStackTrace();
            int found = -1;
            for(int i = 0; i < s.length; i ++){
                if (s[i].getClassName().equals(realMain)) found = i;
            }
            e.getCause().setStackTrace(
                    java.util.Arrays.copyOfRange(s, 0, found == -1 ? s.length : found)
            );
            throw e.getCause();
        }
    }
}