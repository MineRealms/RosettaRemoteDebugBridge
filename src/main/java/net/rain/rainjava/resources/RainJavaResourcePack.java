/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonObject
 *  javax.annotation.Nullable
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.packs.PackResources
 *  net.minecraft.server.packs.PackResources$ResourceOutput
 *  net.minecraft.server.packs.PackType
 *  net.minecraft.server.packs.metadata.MetadataSectionSerializer
 *  net.minecraft.server.packs.resources.IoSupplier
 */
package net.rain.rainjava.resources;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.rain.rainjava.RainJava;

public class RainJavaResourcePack
implements PackResources {
    private final Path rootPath;
    private final PackType packType;
    private final Set<String> namespaces;

    public RainJavaResourcePack(Path rootPath, PackType packType) {
        this.rootPath = rootPath;
        this.packType = packType;
        this.namespaces = new HashSet<String>();
        this.scanNamespaces();
    }

    private void scanNamespaces() {
        if (!Files.exists(this.rootPath, new LinkOption[0])) {
            return;
        }
        try (Stream<Path> paths = Files.list(this.rootPath);){
            paths.filter(x$0 -> Files.isDirectory(x$0, new LinkOption[0])).map(path -> path.getFileName().toString()).forEach(this.namespaces::add);
        }
        catch (IOException e) {
            RainJava.LOGGER.error("Failed to scan namespaces in: {}", (Object)this.rootPath, (Object)e);
        }
        if (!this.namespaces.isEmpty()) {
            RainJava.LOGGER.info("Found namespaces in RainJava {}: {}", (Object)(this.packType == PackType.CLIENT_RESOURCES ? "assets" : "data"), this.namespaces);
        }
    }

    @Nullable
    public IoSupplier<InputStream> getRootResource(String ... paths) {
        if (paths.length == 0) {
            return null;
        }
        String fileName = String.join((CharSequence)"/", paths);
        Path file = this.rootPath.getParent().resolve(fileName);
        if (Files.exists(file, new LinkOption[0])) {
            return () -> Files.newInputStream(file, new OpenOption[0]);
        }
        if ("pack.mcmeta".equals(fileName)) {
            String meta = "{\n    \"pack\": {\n        \"pack_format\": 15,\n        \"description\": \"RainJava Dynamic Resources\"\n    }\n}\n";
            return () -> new ByteArrayInputStream(meta.getBytes());
        }
        return null;
    }

    @Nullable
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != this.packType) {
            return null;
        }
        Path file = this.rootPath.resolve(location.getNamespace()).resolve(location.getPath());
        if (Files.exists(file, new LinkOption[0]) && Files.isRegularFile(file, new LinkOption[0])) {
            RainJava.LOGGER.debug("Loading resource: {}", (Object)file);
            return () -> Files.newInputStream(file, new OpenOption[0]);
        }
        return null;
    }

    public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        if (type != this.packType) {
            return;
        }
        Path namespacePath = this.rootPath.resolve(namespace).resolve(path);
        if (!Files.exists(namespacePath, new LinkOption[0])) {
            return;
        }
        try (Stream<Path> paths = Files.walk(namespacePath, new FileVisitOption[0]);){
            paths.filter(x$0 -> Files.isRegularFile(x$0, new LinkOption[0])).forEach(file -> {
                try {
                    Path relative = namespacePath.relativize((Path)file);
                    String resourcePath = path + "/" + relative.toString().replace('\\', '/');
                    ResourceLocation location = new ResourceLocation(namespace, resourcePath);
                    resourceOutput.accept(location, () -> Files.newInputStream(file, new OpenOption[0]));
                }
                catch (Exception e) {
                    RainJava.LOGGER.error("Error visiting resource: {}", file, (Object)e);
                }
            });
        }
        catch (IOException e) {
            RainJava.LOGGER.error("Failed to list resources in: {}", (Object)namespacePath, (Object)e);
        }
    }

    public Set<String> getNamespaces(PackType type) {
        if (type != this.packType) {
            return Set.of();
        }
        return new HashSet<String>(this.namespaces);
    }

    @Nullable
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) throws IOException {
        try {
            if (deserializer.getMetadataSectionName().equals("pack")) {
                JsonObject pack = new JsonObject();
                pack.addProperty("pack_format", (Number)15);
                pack.addProperty("description", "RainJava Dynamic Resources");
                return (T)deserializer.fromJson(pack);
            }
        }
        catch (Exception e) {
            RainJava.LOGGER.error("Error reading pack metadata", (Throwable)e);
        }
        return null;
    }

    public String packId() {
        return "rainjava_" + (this.packType == PackType.CLIENT_RESOURCES ? "assets" : "data");
    }

    public void close() {
    }
}

