package rosetta.server;

import net.minecraft.world.item.ItemStack;

public class McImportTest {
    public static void init() {
        if (ItemStack.EMPTY.isEmpty()) {
            System.out.println("[Rosetta-AutoTest] MC import compile test OK");
        }
    }
}
