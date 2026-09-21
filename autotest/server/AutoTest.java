package rosetta.server;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AutoTest {
    private static final String DONE_PROPERTY = "rosetta_remote_debug_bridge.autotest.done";
    private static final StringBuilder RESULT = new StringBuilder();

    private static Path gameDir() {
        return Paths.get(System.getProperty("user.dir"));
    }

    private static Path resultFile() {
        return gameDir().resolve("rosetta-autotest-result.txt");
    }

    private static synchronized void save(String line) {
        RESULT.append(line).append("\n");
        try {
            Files.write(resultFile(), RESULT.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    public static void init() {
        if ("1".equals(System.getProperty(DONE_PROPERTY))) {
            return;
        }
        System.setProperty(DONE_PROPERTY, "1");
        Thread thread = new Thread(AutoTest::run, "RosettaRemoteDebugBridge-AutoTest");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        try {
            boolean isClient = false;
            try {
                Class<?> env = Class.forName("net.minecraftforge.fml.loading.FMLEnvironment");
                Object dist = env.getField("dist").get(null);
                isClient = "CLIENT".equals(String.valueOf(dist));
            } catch (Throwable ignored) {
            }
            if (!isClient) {
                return;
            }
            save("phase=client");

            Class<?> hooks = Class.forName("net.minecraftforge.server.ServerLifecycleHooks");
            Method getServer = hooks.getMethod("getCurrentServer");
            Object server = null;
            for (int i = 0; i < 240 && server == null; i++) {
                server = getServer.invoke(null);
                if (server == null) {
                    Thread.sleep(500);
                }
            }
            if (server == null) {
                save("FAILED=no_server");
                quitClient();
                return;
            }
            save("server=found");

            Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
            Method isRunning = serverClass.getMethod("isRunning");
            for (int i = 0; i < 240 && !Boolean.TRUE.equals(isRunning.invoke(server)); i++) {
                Thread.sleep(500);
            }
            save("serverRunning=" + isRunning.invoke(server));

            Object commands = serverClass.getMethod("getCommands").invoke(server);
            Object source = serverClass.getMethod("createCommandSourceStack").invoke(server);
            Class<?> sourceClass = Class.forName("net.minecraft.commands.CommandSourceStack");
            Method perform = commands.getClass().getMethod("performPrefixedCommand", sourceClass, String.class);
            Method execute = serverClass.getMethod("execute", Runnable.class);

            runCommand(server, execute, commands, perform, source, "java errors", "cmd_java_errors");
            runCommand(server, execute, commands, perform, source, "java reload startup", "cmd_java_reload_startup");
            runCommand(server, execute, commands, perform, source, "java hand getId", "cmd_java_hand_getId");

            int listenersBefore = listenerCount();
            save("listeners_before=" + listenersBefore);
            save("errors_before=" + errorCount("SERVER"));

            boolean network = networkUtilsCheck();
            save("networkutils=" + (network ? "OK" : "FAILED"));

            Path tempThrow = gameDir().resolve("RosettaRemoteDebugBridge").resolve("server").resolve("TempThrow.java");
            Files.write(tempThrow, "package rosetta.server;\n\npublic class TempThrow {\n    public static void init() {\n        throw new RuntimeException(\"intentional-autotest\");\n    }\n}\n".getBytes(StandardCharsets.UTF_8));
            runCommand(server, execute, commands, perform, source, "java reload server", "cmd_reload_with_temp_throw");
            int errorsDuring = errorCount("SERVER");
            save("errors_during_temp_throw=" + errorsDuring);
            save("temp_throw_detected=" + (errorsDuring >= 1 && errorMessages("SERVER").contains("intentional-autotest") ? "1" : "0"));

            Files.deleteIfExists(tempThrow);
            runCommand(server, execute, commands, perform, source, "java reload server", "cmd_reload_cleanup");
            int errorsAfter = errorCount("SERVER");
            save("errors_after_cleanup=" + errorsAfter);

            int listenersAfter = listenerCount();
            save("listeners_after=" + listenersAfter);
            save("listener_reload_stable=" + (listenersAfter == listenersBefore ? "1" : "0"));

            boolean pass = network
                    && listenersAfter == listenersBefore
                    && listenersAfter > 0
                    && errorsDuring >= 1
                    && errorsAfter == 0;
            save("PASS=" + (pass ? "1" : "0"));
            Thread.sleep(4000);
        } catch (Throwable t) {
            save("ERROR=" + t);
        }
        quitClient();
    }

    private static void runCommand(Object server, Method execute, Object commands, Method perform, Object source, String command, String label) {
        try {
            CompletableFuture<Void> done = new CompletableFuture<Void>();
            execute.invoke(server, (Runnable) () -> {
                try {
                    Object result = perform.invoke(commands, source, command);
                    save(label + "=" + result);
                } catch (Throwable t) {
                    save(label + "=EX:" + t);
                } finally {
                    done.complete(null);
                }
            });
            done.get(5, TimeUnit.MINUTES);
        } catch (Throwable t) {
            save(label + "=FAILED:" + t);
        }
    }

    private static int listenerCount() {
        try {
            Class<?> bridgeClass = Class.forName("com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge");
            Object bus = bridgeClass.getField("EVENT_BUS").get(null);
            return (Integer) bus.getClass().getMethod("getTotalListenerCount").invoke(bus);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static Object scriptType(String typeName) throws Exception {
        Class<?> scriptTypeClass = Class.forName("com.rosetta.remotedebugbridge.core.ScriptType");
        for (Object constant : scriptTypeClass.getEnumConstants()) {
            if (String.valueOf(constant).equalsIgnoreCase(typeName)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown script type: " + typeName);
    }

    private static int errorCount(String typeName) {
        try {
            Class<?> collector = Class.forName("com.rosetta.remotedebugbridge.logging.ScriptErrorCollector");
            Object type = scriptType(typeName);
            List<?> errors = (List<?>) collector.getMethod("getErrors", type.getClass()).invoke(null, type);
            return errors.size();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String errorMessages(String typeName) {
        try {
            Class<?> collector = Class.forName("com.rosetta.remotedebugbridge.logging.ScriptErrorCollector");
            Object type = scriptType(typeName);
            List<?> errors = (List<?>) collector.getMethod("getErrors", type.getClass()).invoke(null, type);
            StringBuilder sb = new StringBuilder();
            for (Object error : errors) {
                sb.append(String.valueOf(error)).append(";");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean networkUtilsCheck() {
        try {
            Class<?> network = Class.forName("com.rosetta.remotedebugbridge.script.util.NetworkUtils");
            return Boolean.TRUE.equals(network.getMethod("isInitialized").invoke(null));
        } catch (Throwable t) {
            save("networkutils_error=" + t);
            return false;
        }
    }

    private static void quitClient() {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            mcClass.getMethod("execute", Runnable.class).invoke(mc, (Runnable) () -> {
                try {
                    mcClass.getMethod("stop").invoke(mc);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
