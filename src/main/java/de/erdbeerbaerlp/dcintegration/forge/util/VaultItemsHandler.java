//
// Created by BONNe
// Copyright - 2022
//


package de.erdbeerbaerlp.dcintegration.forge.util;


import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import iskallia.vault.config.EtchingConfig;
import iskallia.vault.config.gear.VaultGearTierConfig;
import iskallia.vault.dynamodel.model.armor.ArmorPieceModel;
import iskallia.vault.gear.VaultGearState;
import iskallia.vault.gear.attribute.VaultGearAttribute;
import iskallia.vault.gear.attribute.VaultGearModifier;
import iskallia.vault.gear.data.VaultGearData;
import iskallia.vault.gear.item.VaultGearItem;
import iskallia.vault.init.ModConfigs;
import iskallia.vault.init.ModDynamicModels;
import iskallia.vault.init.ModGearAttributes;
import net.dv8tion.jda.api.EmbedBuilder;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;


/**
 * This class allows to parse VaultHunters item tooltips to discord chat.
 */
public class VaultItemsHandler
{
    /**
     * This method parses gear tooltip into discord chat.
     * @param builder Embed Builder.
     * @param itemStack Vault Gear ItemStack.
     */
    public static void handleGearTooltip(EmbedBuilder builder, ItemStack itemStack)
    {
        VaultGearData data = VaultGearData.read(itemStack);
        VaultGearState state = data.getState();

        // Add gear Level
        builder.appendDescription("**Level:** " + data.getItemLevel()).appendDescription("\n");

        // Add crafter name
        data.getFirstValue(ModGearAttributes.CRAFTED_BY).ifPresent(crafter ->
            builder.appendDescription("**Crafted by:** " + crafter).appendDescription("\n"));

        // Add Rarity
        switch (state)
        {
            case UNIDENTIFIED ->
            {
                Objects.requireNonNull(ModConfigs.VAULT_GEAR_TYPE_CONFIG);
                data.getFirstValue(ModGearAttributes.GEAR_ROLL_TYPE).
                    flatMap(ModConfigs.VAULT_GEAR_TYPE_CONFIG::getRollPool).
                    ifPresent(pool -> builder.appendDescription("**Roll:** " + pool.getName()).appendDescription("\n"));
            }
            case IDENTIFIED ->
            {
                builder.appendDescription("**Roll:** " + data.getRarity().getDisplayName().getString()).appendDescription("\n");
            }
        }

        if (state == VaultGearState.IDENTIFIED)
        {
            // Add Model
            data.getFirstValue(ModGearAttributes.GEAR_MODEL).
                flatMap(modelId -> ModDynamicModels.REGISTRIES.getModel(itemStack.getItem(), modelId)).
                ifPresent(gearModel -> {
                    Item pattern = itemStack.getItem();
                    if (pattern instanceof VaultGearItem)
                    {
                        String name = gearModel.getDisplayName();

                        if (gearModel instanceof ArmorPieceModel modelPiece)
                        {
                            name = modelPiece.getArmorModel().getDisplayName();
                        }

                        builder.appendDescription("**Model:** " + name).appendDescription("\n");
                    }
                });

            // Add Etchings
            data.getFirstValue(ModGearAttributes.ETCHING).
                ifPresent(etchingSet -> {
                    EtchingConfig.Etching etchingConfig = ModConfigs.ETCHING.getEtchingConfig(etchingSet);
                    if (etchingConfig != null)
                    {
                        builder.appendDescription("**Etching:** " + etchingConfig.getName()).appendDescription("\n");
                    }
                });

            // Add Repair text.
            int usedRepairs = data.getUsedRepairSlots();
            int totalRepairs = data.getRepairSlots();

            builder.appendDescription(createRepairText(usedRepairs, totalRepairs)).appendDescription("\n");

            // Add Implicits

            List<VaultGearModifier<?>> implicits = data.getModifiers(VaultGearModifier.AffixType.IMPLICIT);

            if (!implicits.isEmpty())
            {
                VaultItemsHandler.addAffixList(builder, data, VaultGearModifier.AffixType.IMPLICIT, itemStack);
                builder.appendDescription("\n");
            }

            int prefixes = data.getFirstValue(ModGearAttributes.PREFIXES).orElse(0);

            if (prefixes > 0)
            {
                VaultItemsHandler.addAffixList(builder, data, VaultGearModifier.AffixType.PREFIX, itemStack);
                builder.appendDescription("\n");
            }

            int suffixes = data.getFirstValue(ModGearAttributes.SUFFIXES).orElse(0);

            if (suffixes > 0)
            {
                VaultItemsHandler.addAffixList(builder, data, VaultGearModifier.AffixType.SUFFIX, itemStack);
            }
        }
    }


    /**
     * This method adds affixes to the embed builder.
     * @param builder Embed builder that need to be populated.
     * @param data Vault Gear Data.
     * @param type Affix type.
     * @param itemStack Item Stack that is displayed.
     */
    private static void addAffixList(EmbedBuilder builder,
        VaultGearData data,
        VaultGearModifier.AffixType type,
        ItemStack itemStack)
    {
        List<VaultGearModifier<?>> affixes = data.getModifiers(type);

        VaultGearAttribute<Integer> affixAttribute = (type == VaultGearModifier.AffixType.PREFIX) ?
            ModGearAttributes.PREFIXES : ModGearAttributes.SUFFIXES;

        int emptyAffixes = data.getFirstValue(affixAttribute).orElse(0);

        builder.appendDescription("**" + type.getDisplayName() + ":** ");
        builder.appendDescription("\n");
        affixes.forEach(modifier -> VaultItemsHandler.addAffix(builder, modifier, data, type, itemStack));

        if (type != VaultGearModifier.AffixType.IMPLICIT)
        {
            for (int i = 0; i < emptyAffixes - affixes.size(); i++)
            {
                builder.appendDescription(VaultItemsHandler.createEmptyAffix(type));
                builder.appendDescription("\n");
            }
        }
    }


    /**
     * This method adds affix text to given builder.
     * @param builder Embed builder.
     * @param modifier Vault Gear Modifier
     * @param data Vault Gear data
     * @param type Affix Type.
     * @param stack ItemStack of item.
     */
    @SuppressWarnings("rawtypes unchecked")
    private static void addAffix(EmbedBuilder builder,
        VaultGearModifier modifier,
        VaultGearData data,
        VaultGearModifier.AffixType type,
        ItemStack stack)
    {
        Optional.ofNullable(modifier.getAttribute().getReader().getDisplay(modifier, data, type, stack)).
            map(text -> {
                if (!modifier.isLegendary())
                {
                    return text.getString();
                }
                else
                {
                    return VaultItemsHandler.STAR + " " + text.getString();
                }
            }).
            ifPresent(text ->
            {
                MutableComponent tierDisplay = VaultGearTierConfig.getConfig(stack.getItem()).map((tierConfig) ->
                {
                    Object config = tierConfig.getTierConfig(modifier);

                    if (config != null)
                    {
                        return modifier.getAttribute().getGenerator().getConfigDisplay(
                            modifier.getAttribute().getReader(),
                            config);
                    }
                    else
                    {
                        return null;
                    }
                }).orElse(null);

                builder.appendDescription(text);

                if (tierDisplay != null)
                {
                    String legendaryInfo = modifier.isLegendary() ? "**Legendary** " : "";

                    if (tierDisplay.getString().isEmpty())
                    {
                        builder.appendDescription(" (%sT%s)".formatted(legendaryInfo, modifier.getRolledTier() + 1));
                    }
                    else
                    {
                        builder.appendDescription(" (%sT%s: ".formatted(legendaryInfo, modifier.getRolledTier() + 1));
                        builder.appendDescription(tierDisplay.getString());
                        builder.appendDescription(")");
                    }
                }

                builder.appendDescription("\n");
            });
    }


    /**
     * This method creates empty affix of given type.
     * @param type Affix type.
     * @return Empty affix text.
     */
    private static String createEmptyAffix(VaultGearModifier.AffixType type)
    {
        return (SQUARE + " empty %s").formatted(type.name().toLowerCase(Locale.ROOT));
    }


    /**
     * This method creates repair text based on used repairs and total repairs values.
     * @param usedRepairs Number of used repairs.
     * @param totalRepairs Number of total repairs.
     * @return Text for repairs.
     */
    private static String createRepairText(int usedRepairs, int totalRepairs)
    {
        int remaining = totalRepairs - usedRepairs;

        return "**Repairs:** " +
            VaultItemsHandler.createDots(usedRepairs, FULL_CIRCLE) +
            VaultItemsHandler.createDots(remaining, EMPTY_CIRCLE);
    }


    /**
     * This method generates a repair dots for gear.
     * @param amount Amount of dots.
     * @param symbol Dot symbol.
     * @return String that contains number of repairs for gear.
     */
    private static String createDots(int amount, String symbol)
    {
        return (symbol + " ").repeat(Math.max(0, amount));
    }


// ---------------------------------------------------------------------
// Section: Variables
// ---------------------------------------------------------------------


    /**
     * symbol for text fields.
     */
    private static final String EMPTY_CIRCLE = "\u25CB";

    /**
     * symbol for text fields.
     */
    private static final String FULL_CIRCLE = "\u25CF";

    /**
     * symbol for text fields.
     */
    private static final String STAR = "\u2726";

    /**
     * symbol for text fields.
     */
    private static final String SQUARE = "\u25A0";
}
