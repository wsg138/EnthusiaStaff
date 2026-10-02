package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class RegistryFreeItemStack extends ItemStack implements Cloneable {
    private final Material material;
    private int amount;

    RegistryFreeItemStack(Material material, int amount) {
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
        return other instanceof RegistryFreeItemStack stack && material == stack.material;
    }

    @Override
    public RegistryFreeItemStack clone() {
        return new RegistryFreeItemStack(material, amount);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RegistryFreeItemStack stack
                && material == stack.material
                && amount == stack.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, amount);
    }
}
