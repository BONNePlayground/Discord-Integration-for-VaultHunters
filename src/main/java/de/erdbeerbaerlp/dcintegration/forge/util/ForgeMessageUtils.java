package de.erdbeerbaerlp.dcintegration.forge.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dcshadow.org.apache.commons.collections4.keyvalue.DefaultMapEntry;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import iskallia.vault.gear.item.VaultGearItem;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.*;


public class ForgeMessageUtils extends MessageUtils
{
    /**
     * Attempts to generate an {@link MessageEmbed} showing item info from an {@link Component} instance
     *
     * @param component The TextComponent to scan for item info
     * @return an {@link MessageEmbed} when there was an Item info, or {@link null} if there was no item info OR the
     * item info was disabled
     */
    public static MessageEmbed genItemStackEmbedIfAvailable(final Component component)
    {
        if (!Configuration.instance().forgeSpecific.sendItemInfo)
        {
            return null;
        }

        final JsonObject json = JsonParser.parseString(Component.Serializer.toJson(component)).getAsJsonObject();

        if (json.has("with"))
        {
            final JsonArray withArray = json.getAsJsonArray("with");

            for (JsonElement object : withArray)
            {
                if (object instanceof JsonObject singleElement)
                {
                    if (singleElement.has("hoverEvent"))
                    {
                        final JsonObject hoverEvent = singleElement.getAsJsonObject("hoverEvent");

                        if (hoverEvent.has("action") &&
                            hoverEvent.get("action").getAsString().equals("show_item") &&
                            hoverEvent.has("contents"))
                        {
                            if (hoverEvent.getAsJsonObject("contents").has("tag"))
                            {
                                return ForgeMessageUtils.parseJsonArgs(
                                    hoverEvent.getAsJsonObject("contents").getAsJsonObject());
                            }
                        }
                    }
                }
            }
        }

        return null;
    }


    /**
     * This method parses given item json data.
     * @param itemJson item json data.
     * @return MessageEmbed text for item.
     */
    private static MessageEmbed parseJsonArgs(JsonObject itemJson)
    {
        try
        {
            ItemStack itemStack = new ItemStack(itemreg.getValue(new ResourceLocation(itemJson.get("id").getAsString())));

            if (itemJson.has("tag"))
            {
                CompoundTag tag = (CompoundTag) NbtTagArgument.nbtTag().parse(
                    new StringReader(itemJson.get("tag").getAsString()));
                itemStack.setTag(tag);
            }

            CompoundTag itemTag = itemStack.getOrCreateTag();

            // Here we hook into Vault Hunters items.

            if (itemJson.get("id").getAsString().startsWith("the_vault"))
            {
                return ForgeMessageUtils.craftVaultHuntersItemMessage(itemJson, itemStack, itemTag);
            }

            EmbedBuilder embedBuilder = new EmbedBuilder();

            // Here we continue to process vanilla items.

            String title = itemStack.hasCustomHoverName() ?
                itemStack.getDisplayName().getContents() :
                new TranslatableComponent(itemStack.getItem().getDescriptionId()).getContents();

            ResourceLocation registryName = Objects.requireNonNull(itemStack.getItem().getRegistryName());

            if (title.isEmpty())
            {
                title = registryName.toString();
            }
            else
            {
                embedBuilder.setFooter(registryName.toString());
            }

            embedBuilder.setTitle(title);
            StringBuilder tooltip = new StringBuilder();

            // Enchantments, Modifiers, Unbreakable, CanDestroy, CanPlace, Other
            boolean[] flags = new boolean[6];
            // Set everything visible
            Arrays.fill(flags, false);

            if (itemTag.contains("HideFlags"))
            {
                final int input = (itemTag.getInt("HideFlags"));
                for (int i = 0; i < flags.length; i++)
                {
                    flags[i] = (input & (1 << i)) != 0;
                }
            }

            //Add Enchantments
            if (!flags[0])
            {
                //Implementing this code myself because the original is broken
                for (int i = 0; i < itemStack.getEnchantmentTags().size(); ++i)
                {
                    final CompoundTag compoundTag = itemStack.getEnchantmentTags().getCompound(i);
                    Registry.ENCHANTMENT.getOptional(ResourceLocation.tryParse(compoundTag.getString("id"))).
                        ifPresent((enchantment) ->
                    {
                        if (compoundTag.get("lvl") != null)
                        {
                            final int level;
                            if (compoundTag.get("lvl") instanceof StringTag)
                            {
                                level = Integer.parseInt(compoundTag.getString("lvl").
                                    replace("s", ""));
                            }
                            else
                                level = compoundTag.getInt("lvl") == 0 ?
                                    compoundTag.getShort("lvl") : compoundTag.getInt("lvl");
                            tooltip.append(ChatFormatting.stripFormatting(enchantment.getFullname(level).getString())).
                                append("\n");
                        }
                    });
                }
            }

            //Add Lores
            final ListTag list = itemTag.getCompound("display").getList("Lore", 8);
            list.forEach((nbt) ->
            {
                try
                {
                    if (nbt instanceof StringTag)
                    {
                        final TextComponent comp =
                            (TextComponent) ComponentArgument.textComponent()
                                .parse(new StringReader(nbt.getAsString()));
                        tooltip.append("_").append(comp.getContents()).append("_\n");
                    }
                }
                catch (CommandSyntaxException e)
                {
                    e.printStackTrace();
                }
            });

            //Add 'Unbreakable' Tag
            if (!flags[2] && itemTag.contains("Unbreakable") &&
                itemTag.getBoolean("Unbreakable"))
                tooltip.append("Unbreakable\n");

            embedBuilder.setDescription(tooltip.toString());

            return embedBuilder.build();
        }
        catch (CommandSyntaxException ignored)
        {
            //Just go on and ignore it
        }

        return null;
    }


    /**
     * This message crafts item descriptions for Vault Hunters items.
     * @param itemJson Item JSON data.
     * @param itemStack ItemStack item.
     * @param itemTag Item Tag.
     * @return MessageEmbed for Vault item.
     */
    private static MessageEmbed craftVaultHuntersItemMessage(JsonObject itemJson,
        ItemStack itemStack,
        CompoundTag itemTag)
    {
        try
        {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle(itemStack.getHoverName().getString());

            if (itemStack.getItem() instanceof VaultGearItem)
            {
                VaultItemsHandler.handleGearTooltip(builder, itemStack);
                return builder.build();
            }

            return null;
        }
        catch (Exception e)
        {
            // If I fail, then return nothing.
            return null;
        }
    }


    /**
     * This method formats given player entity name.
     * @param player The player name.
     * @return Formatted name text.
     */
    public static String formatPlayerName(Entity player)
    {
        final Map.Entry<UUID, String> entityMap = new DefaultMapEntry<>(player.getUUID(),
            player.getDisplayName().getContents().isEmpty() ?
                player.getName().getContents() :
                player.getDisplayName().getContents());

        return formatPlayerName(entityMap);
    }


    public static String formatPlayerName(Map.Entry<UUID, String> p)
    {
        return formatPlayerName(p, true);
    }


    public static String formatPlayerName(Map.Entry<UUID, String> p, boolean chatFormat)
    {
        return ChatFormatting.stripFormatting(p.getValue());
    }


    private static final IForgeRegistry<Item> itemreg = ForgeRegistries.ITEMS;
}
