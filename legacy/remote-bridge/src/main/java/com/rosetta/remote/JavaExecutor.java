package com.rosetta.remote;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.bukkit.plugin.Plugin;

/**
 * Compiles a snippet of Java source at runtime (server must run on a JDK) and executes it
 * on a throwaway class loader whose parent is the plugin class loader, so Bukkit/NMS/Forge
 * classes are all reachable from user code.
 *
 * The snippet is the body of:
 *   public Object run(org.bukkit.plugin.Plugin plugin, Object[] args) throws Throwable { <snippet> }
 */
final class JavaExecutor {

    private JavaExecutor() {
    }

    static String run(Plugin plugin, String code, String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("no system java compiler found - the server must run on a JDK, not a JRE");
        }
        String className = "RosettaTask" + System.nanoTime();
        Path dir = Files.createTempDirectory("rosetta-task");
        Path source = dir.resolve(className + ".java");
        String body = (code == null || code.isBlank()) ? "return null;" : code;
        String src = ""
                + "import java.util.*;\n"
                + "import java.util.stream.*;\n"
                + "import java.lang.reflect.*;\n"
                + "import org.bukkit.*;\n"
                + "import org.bukkit.entity.*;\n"
                + "import org.bukkit.plugin.*;\n"
                + "public class " + className + " implements com.rosetta.remote.Task {\n"
                + "  @SuppressWarnings(\"unchecked\")\n"
                + "  public Object run(org.bukkit.plugin.Plugin plugin, Object[] args) throws Throwable {\n"
                + body + "\n  }\n}\n";
        Files.write(source, src.getBytes(StandardCharsets.UTF_8));
        int exit = compiler.run(null, null, null,
                "-proc:none", "-nowarn",
                "-classpath", classpath(plugin),
                "-d", dir.toString(),
                source.toString());
        if (exit != 0) {
            throw new IllegalStateException("compilation failed (exit " + exit + ")");
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, plugin.getClass().getClassLoader())) {
            Class<?> clazz = Class.forName(className, true, loader);
            Task task = (Task) clazz.getDeclaredConstructor().newInstance();
            long start = System.currentTimeMillis();
            Object result;
            try {
                result = task.run(plugin, args == null ? new Object[0] : args);
            } catch (Throwable error) {
                throw new Exception("task threw: " + error, error);
            }
            String out = result == null ? "null" : String.valueOf(result);
            if (out.length() > 200_000) {
                out = out.substring(0, 200_000) + "...(truncated)";
            }
            return out + "  [" + (System.currentTimeMillis() - start) + "ms]";
        }
    }

    private static String classpath(Plugin plugin) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (String part : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!part.isBlank()) {
                paths.add(part);
            }
        }
        addCodeSource(paths, plugin.getClass());
        for (String name : new String[]{"org.bukkit.Bukkit", "net.minecraft.server.MinecraftServer", "net.minecraftforge.common.MinecraftForge"}) {
            try {
                addCodeSource(paths, Class.forName(name, false, plugin.getClass().getClassLoader()));
            } catch (Throwable ignored) {
            }
        }
        // Mohist/Forge: classes live in a union filesystem, so code-source paths do not resolve.
        // Scan the server libraries for the Forge universal jar (Bukkit/CraftBukkit), the SRG
        // server jar (NMS/Forge) and Mohist's eventbus.
        Path libraries = Paths.get("libraries");
        if (Files.isDirectory(libraries)) {
            try (Stream<Path> stream = Files.walk(libraries, 6)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.endsWith(".jar")
                                    && (name.contains("universal") || name.contains("-srg") || name.contains("eventbus"));
                        })
                        .limit(24)
                        .forEach(path -> paths.add(path.toAbsolutePath().toString()));
            } catch (Throwable ignored) {
            }
        }
        return String.join(File.pathSeparator, paths);
    }

    private static void addCodeSource(LinkedHashSet<String> paths, Class<?> type) {
        try {
            var source = type.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                paths.add(new File(source.getLocation().toURI()).getAbsolutePath());
            }
        } catch (Throwable ignored) {
        }
    }
}
