package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.lang.reflect.Proxy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class CheatTesterTotemSafetyTest {
    @Test
    void probeMovesTheExistingOffhandIntoStorageWithoutDestroyingIt() {
        Harness h = new Harness();
        CheatTesterSession.PreparedProbe probe = CheatTesterProbeEngine.prepareTotem(h.inventory);
        CheatTesterProbeEngine.beginTotem(h.inventory, probe);
        assertEquals(Material.TOTEM_OF_UNDYING, h.storage[0].getType());
        assertEquals(Material.SHIELD, h.storage[1].getType());
        assertNull(h.offhand);
    }

    @Test
    void fullInventoryRefusesPreparationWithoutMutation() {
        Harness h = new Harness();
        h.storage[1] = new Stack(Material.STONE);
        assertThrows(IllegalStateException.class, () -> CheatTesterProbeEngine.prepareTotem(h.inventory));
        assertEquals(Material.SHIELD, h.offhand.getType());
        assertEquals(Material.STONE, h.storage[1].getType());
    }

    @Test
    void changedReservationRefusesStartWithoutOverwritingItems() {
        Harness h = new Harness();
        CheatTesterSession.PreparedProbe probe = CheatTesterProbeEngine.prepareTotem(h.inventory);
        h.storage[1] = new Stack(Material.DIAMOND);
        assertThrows(IllegalStateException.class, () -> CheatTesterProbeEngine.beginTotem(h.inventory, probe));
        assertEquals(Material.SHIELD, h.offhand.getType());
        assertEquals(Material.DIAMOND, h.storage[1].getType());
    }

    private static final class Harness {
        final ItemStack[] storage = {new Stack(Material.TOTEM_OF_UNDYING), null};
        ItemStack offhand = new Stack(Material.SHIELD);
        final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getStorageContents" -> storage.clone();
                    case "getItemInOffHand" -> offhand;
                    case "setItem" -> { storage[(Integer) args[0]] = (ItemStack) args[1]; yield null; }
                    case "setItemInOffHand" -> { offhand = (ItemStack) args[0]; yield null; }
                    default -> throw new AssertionError(method.getName());
                });
    }

    @SuppressWarnings("deprecation")
    private static final class Stack extends ItemStack {
        private final Material material;
        Stack(Material material) { this.material = material; }
        @Override public Material getType() { return material; }
        @Override public boolean isEmpty() { return false; }
        @Override public ItemStack clone() { return new Stack(material); }
    }
}
