package be.stealingdapenta.coreai.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Utility for building colored Components easily.
 */
public enum TextBuilder {
    TEXT_BUILDER; // Singleton instance

    public static final TextComponent CORE_AI_PREFIX = Component.text("[CoreAI] ", NamedTextColor.GOLD);

    /**
     * Creates a builder local to the current message.
     */
    public Builder reset() {
        return new Builder();
    }

    public static final class Builder {

        private final TextComponent.Builder builder = Component.text();

        public Builder append(String text, NamedTextColor color) {
            builder.append(Component.text(text, color));
            return this;
        }

        public Builder append(Component component) {
            builder.append(component);
            return this;
        }

        public Builder coreAIPrefix() {
            builder.append(CORE_AI_PREFIX);
            return this;
        }

        public Component build() {
            return builder.build();
        }
    }
}
