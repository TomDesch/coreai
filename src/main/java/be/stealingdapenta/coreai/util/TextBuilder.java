package be.stealingdapenta.coreai.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Utility for building colored Components easily.
 */
public enum TextBuilder {
    TEXT_BUILDER; // Singleton instance

    public static final TextComponent CORE_AI_PREFIX = Component.text("[CoreAI] ", NamedTextColor.GOLD);
    private Builder builder = Component.text();

    /**
     * Resets the internal builder.
     */
    public TextBuilder reset() {
        builder = Component.text(); // <== recreate a new fresh builder
        return this;
    }

    /**
     * Appends text with a specific color.
     *
     * @param text  The text to append.
     * @param color The color to use.
     * @return This builder.
     */
    public TextBuilder append(String text, NamedTextColor color) {
        builder.append(Component.text(text, color));
        return this;
    }

    /**
     * Appends an existing component.
     *
     * @param component The component to append.
     * @return This builder.
     */
    public TextBuilder append(Component component) {
        builder.append(component);
        return this;
    }

    /**
     * Appends the builder with the Core AI prefix.
     *
     * @return This builder.
     */
    public TextBuilder coreAIPrefix() {
        builder.append(CORE_AI_PREFIX);
        return this;
    }

    /**
     * Builds and returns the final Component.
     *
     * @return The built Component.
     */
    public Component build() {
        return builder.build();
    }
}
