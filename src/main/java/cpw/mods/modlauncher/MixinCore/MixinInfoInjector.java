package cpw.mods.modlauncher.MixinCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.transformers.MixinClassReader;

public class MixinInfoInjector {
    public static ClassNode getMixinClassNode(IClassBytecodeProvider provider, String name, boolean runTransformers, int flags) {
        try {
            return provider.getClassNode(name, runTransformers);
        }
        catch (IOException | ClassNotFoundException e) {
            String relative = name.replace('.', '/');
            Path file = FMLPaths.GAMEDIR.get().resolve(".rain_mixin").resolve(relative + ".class");
            try {
                byte[] bytes = Files.readAllBytes(file);
                MixinClassReader reader = new MixinClassReader(bytes, name);
                ClassNode node = new ClassNode();
                reader.accept(node, flags);
                return node;
            }
            catch (IOException e2) {
                throw new RuntimeException(e2);
            }
        }
    }
}
