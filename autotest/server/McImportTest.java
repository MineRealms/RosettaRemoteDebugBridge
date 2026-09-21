package rainjava.server;

import net.minecraft.world.item.ItemStack;

public class McImportTest {
    public static void init() {
        if (ItemStack.EMPTY.isEmpty()) {
            System.out.println("[RainJava-AutoTest] MC import compile test OK");
        }
    }
}
