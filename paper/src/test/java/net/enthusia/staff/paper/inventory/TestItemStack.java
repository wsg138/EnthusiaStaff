package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class TestItemStack extends ItemStack {
    private final Material material;
    private int amount;

    TestItemStack(Material material, int amount) {
        super();
        this.material = Objects.requireNonNull(material, "material");
        this.amount = amount;
    }

    @Override
    public Material getType() {
        return material;
    }

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean isSimilar(ItemStack other) {
        return other instanceof TestItemStack stack && material == stack.material;
    }

    @Override
    public TestItemStack clone() {
        return new TestItemStack(material, amount);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TestItemStack stack
                && material == stack.material
                && amount == stack.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, amount);
    }
}
