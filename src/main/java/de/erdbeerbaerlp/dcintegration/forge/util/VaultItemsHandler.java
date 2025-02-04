//
// Created by BONNe
// Copyright - 2022
//


package de.erdbeerbaerlp.dcintegration.forge.util;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Predicate;

import iskallia.vault.client.gui.helper.UIHelper;
import iskallia.vault.config.EtchingConfig;
import iskallia.vault.config.TrinketConfig;
import iskallia.vault.config.gear.VaultGearTierConfig;
import iskallia.vault.core.data.key.ThemeKey;
import iskallia.vault.core.vault.VaultRegistry;
import iskallia.vault.core.world.generator.layout.ArchitectRoomEntry;
import iskallia.vault.core.world.generator.layout.DIYRoomEntry;
import iskallia.vault.core.world.roll.IntRoll;
import iskallia.vault.dynamodel.DynamicModel;
import iskallia.vault.dynamodel.model.armor.ArmorPieceModel;
import iskallia.vault.dynamodel.model.item.PlainItemModel;
import iskallia.vault.dynamodel.registry.DynamicModelRegistry;
import iskallia.vault.gear.VaultGearState;
import iskallia.vault.gear.attribute.VaultGearAttribute;
import iskallia.vault.gear.attribute.VaultGearModifier;
import iskallia.vault.gear.data.AttributeGearData;
import iskallia.vault.gear.data.VaultGearData;
import iskallia.vault.gear.item.VaultGearItem;
import iskallia.vault.gear.trinket.TrinketEffect;
import iskallia.vault.init.ModConfigs;
import iskallia.vault.init.ModDynamicModels;
import iskallia.vault.init.ModGearAttributes;
import iskallia.vault.init.ModRelics;
import iskallia.vault.item.*;
import iskallia.vault.item.crystal.CrystalData;
import iskallia.vault.item.crystal.CrystalModifiers;
import iskallia.vault.item.crystal.layout.*;
import iskallia.vault.item.crystal.objective.*;
import iskallia.vault.item.crystal.theme.CrystalTheme;
import iskallia.vault.item.crystal.theme.NullCrystalTheme;
import iskallia.vault.item.crystal.theme.PoolCrystalTheme;
import iskallia.vault.item.crystal.theme.ValueCrystalTheme;
import iskallia.vault.item.crystal.time.CrystalTime;
import iskallia.vault.item.crystal.time.NullCrystalTime;
import iskallia.vault.item.crystal.time.PoolCrystalTime;
import iskallia.vault.item.crystal.time.ValueCrystalTime;
import iskallia.vault.item.data.InscriptionData;
import iskallia.vault.item.tool.PaxelItem;
import iskallia.vault.util.MiscUtils;
import iskallia.vault.world.vault.modifier.VaultModifierStack;
import iskallia.vault.world.vault.modifier.registry.VaultModifierRegistry;
import iskallia.vault.world.vault.modifier.spi.VaultModifier;
import net.dv8tion.jda.api.EmbedBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
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

            int maxPrefixes = data.getFirstValue(ModGearAttributes.PREFIXES).orElse(0);
            List<VaultGearModifier<?>> prefixes = data.getModifiers(VaultGearModifier.AffixType.PREFIX);

            if (maxPrefixes > 0 || !prefixes.isEmpty())
            {
                VaultItemsHandler.addAffixList(builder, data, VaultGearModifier.AffixType.PREFIX, itemStack);
                builder.appendDescription("\n");
            }

            int maxSuffixes = data.getFirstValue(ModGearAttributes.SUFFIXES).orElse(0);
            List<VaultGearModifier<?>> suffixes = data.getModifiers(VaultGearModifier.AffixType.SUFFIX);

            if (maxSuffixes > 0 || !suffixes.isEmpty())
            {
                VaultItemsHandler.addAffixList(builder, data, VaultGearModifier.AffixType.SUFFIX, itemStack);
            }
        }
    }


    /**
     * This method parses doll tooltip into discord chat.
     * @param builder Embed Builder.
     * @param itemTag Vault Doll Item Tag.
     */
    public static void handleDollTooltip(EmbedBuilder builder, CompoundTag itemTag)
    {
        String owner = itemTag.getCompound("playerProfile").getString("Name");

        builder.appendDescription("**Owner:**").
            appendDescription(" ").
            appendDescription(owner).
            appendDescription("").
            appendDescription("\n");

        int lootPercent = (int) (itemTag.getFloat("lootPercent") * 100.0F);

        builder.appendDescription("**Loot Efficiency:**").
            appendDescription(" ").
            appendDescription(String.format("%d", lootPercent) + "%").
            appendDescription("\n");

        int xpPercent = (int) (itemTag.getFloat("xpPercent") * 100.0F);

        builder.appendDescription("**Experience Efficiency:**").
            appendDescription(" ").
            appendDescription(String.format("%d", xpPercent) + "%").
            appendDescription("\n");

        if (itemTag.contains("vaultUUID"))
        {
            builder.appendDescription("**Ready to be released!**");
        }
        else
        {
            builder.appendDescription("**Ready for a vault!**");
        }
    }


    /**
     * This method parses paxel item tooltip into discord chat.
     * @param builder Embed Builder.
     * @param itemStack Vault Paxel Item Stack.
     * @param paxelItem Vault Paxel Item.
     */
    public static void handlePaxelTooltip(EmbedBuilder builder, ItemStack itemStack, PaxelItem paxelItem)
    {
        int durability = paxelItem.getMaxDamage(itemStack);
        float miningSpeed = PaxelItem.getUsableStat(itemStack, PaxelItem.Stat.MINING_SPEED);
        float reach = PaxelItem.getUsableStat(itemStack, PaxelItem.Stat.REACH);
        float copiously = PaxelItem.getUsableStat(itemStack, PaxelItem.Stat.COPIOUSLY);

        // Generic information.

        builder.appendDescription("**D:** " + FORMAT.format(durability));

        if (reach > 0)
        {
            builder.appendDescription(" **R:** " + FORMAT.format(reach));
        }

        builder.appendDescription(" **S:** " + FORMAT.format(miningSpeed));

        if (copiously > 0)
        {
            builder.appendDescription(" **C:** " + FORMAT.format(copiously) + "%");
        }

        builder.appendDescription("\n");

        // Perks

        List<PaxelItem.Perk> perks = PaxelItem.getPerks(itemStack);

        if (perks.size() > 0)
        {
            builder.appendDescription("**Perks:**");
            perks.forEach(perk -> {
                builder.appendDescription("\n");
                builder.appendDescription("  " + perk.getSerializedName());
            });

            builder.appendDescription("\n");
        }

        // Main information

        int level = PaxelItem.getPaxelLevel(itemStack);
        builder.appendDescription("**Level:** "  + level).appendDescription("\n");

        builder.appendDescription(VaultItemsHandler.createRepairText(
            PaxelItem.getUsedRepairSlots(itemStack),
            PaxelItem.getMaxRepairSlots(itemStack))).
            appendDescription("\n");

        int sockets = PaxelItem.getSockets(itemStack);

        if (sockets != 0)
        {
            builder.appendDescription("**Sockets:** ").
                appendDescription(VaultItemsHandler.createDots(sockets, EMPTY_CIRCLE)).
                appendDescription("\n");
        }

        builder.appendDescription("\n");

        PaxelItem.Stat[] stats = PaxelItem.Stat.values();

        for (int index = 0; index < stats.length; ++index)
        {
            PaxelItem.Stat stat = stats[index];

            float value = PaxelItem.getStatUpgrade(itemStack, stat);

            if (value != 0.0F)
            {
                builder.appendDescription("**" + stat.getReadableName() + "**");
                builder.appendDescription(value > 0.0F ? " +" : " ");
                builder.appendDescription("" + ModConfigs.PAXEL_CONFIGS.getUpgrade(stat).formatValue(value));
                builder.appendDescription("\n");
            }
        }
    }


    /**
     * This method parses Etching item tooltip into discord chat.
     * @param builder Embed Builder.
     * @param itemStack Vault Etching Item Stack.
     */
    public static void handleEtchingTooltip(EmbedBuilder builder, ItemStack itemStack)
    {
        AttributeGearData data = AttributeGearData.read(itemStack);

        if (data.getFirstValue(ModGearAttributes.STATE).orElse(VaultGearState.UNIDENTIFIED) == VaultGearState.IDENTIFIED)
        {
            data.getFirstValue(ModGearAttributes.ETCHING).ifPresent((etchingSet) ->
            {
                EtchingConfig.Etching config = ModConfigs.ETCHING.getEtchingConfig(etchingSet);

                if (config != null)
                {
                    builder.appendDescription("**Etching:** ").appendDescription(config.getName());

                    for (TextComponent cmp : MiscUtils.splitDescriptionText(config.getEffectText()))
                    {
                        builder.appendDescription("\n");
                        builder.appendDescription(cmp.getString());
                    }
                }
            });
        }
    }


    /**
     * This method parses VaultRune item tooltip into discord chat.
     * @param builder Embed Builder.
     * @param itemStack Vault Rune Item Stack.
     */
    public static void handleRuneTooltip(EmbedBuilder builder, ItemStack itemStack)
    {
        VaultRuneItem.getEntries(itemStack).forEach(diyRoomEntry -> {
            int count = diyRoomEntry.get(DIYRoomEntry.COUNT);

            builder.appendDescription("- Has ").
                appendDescription(String.valueOf(count)).
                appendDescription(" ").
                appendDescription(diyRoomEntry.getName().getString()).
                appendDescription(" ").
                appendDescription(count > 1 ? "Rooms" : "Room");
            builder.appendDescription("\n");
        });
    }


    /**
     * This method parses Vault Inscription item tooltip into discord chat.
     * @param builder Embed Builder.
     * @param itemStack Vault Rune Item Stack.
     */
    public static void handleInscriptionTooltip(EmbedBuilder builder, ItemStack itemStack)
    {
        InscriptionData data = InscriptionData.from(itemStack);

        CompoundTag compoundTag = data.serializeNBT();

        builder.appendDescription("**Completion:** ").
            appendDescription(Math.round(compoundTag.getFloat("completion") * 100.0F) + "%").
            appendDescription("\n");
        builder.appendDescription("**Time:** ").
            appendDescription(formatTimeString(compoundTag.getInt("time"))).
            appendDescription("\n");
        builder.appendDescription("**Instability:** ").
            appendDescription(Math.round(compoundTag.getInt("instability") * 100.0F) + "%").
            appendDescription("\n");

        for (InscriptionData.Entry entry : data.getEntries())
        {
            String roomStr = entry.count > 1 ? "Rooms" : "Room";

            builder.appendDescription(" \u2022 ").
                appendDescription(String.valueOf(entry.count)).
                appendDescription(" ").
                appendDescription(entry.toRoomEntry().has(ArchitectRoomEntry.TYPE) ?
                    entry.toRoomEntry().get(ArchitectRoomEntry.TYPE).getName() : "Unknown").
                appendDescription(" ").
                appendDescription(roomStr).
                appendDescription("\n");
        }
    }


    /**
     * This method parses Vault Crystal item tooltip into discord chat.
     * @param builder Embed Builder.
     * @param crystalData Vault Crystal Data.
     */
    public static void handleVaultCrystalTooltip(EmbedBuilder builder, CrystalData crystalData)
    {
        builder.appendDescription("**Level:** " + crystalData.getLevel()).appendDescription("\n");

        // Objective
        builder.appendDescription("**Objective:** ").
            appendDescription(parseObjectiveName(crystalData.getObjective()));
        builder.appendDescription("\n");

        // Vault Theme
        builder.appendDescription("**Theme:** ").
            appendDescription(parseThemeName(crystalData.getTheme()));
        builder.appendDescription("\n");

        // Vault Layout
        builder.appendDescription("**Layout:** ").
            appendDescription(parseLayoutName(crystalData.getLayout()));
        builder.appendDescription("\n");

        // Vault Time
        String time = parseTime(crystalData.getTime());

        if (!time.isBlank())
        {
            builder.appendDescription(time);
            builder.appendDescription("\n");
        }

        // Instability
        float instability = crystalData.getInstability();

        if (instability > 0.0F)
        {
            builder.appendDescription("**Instability:** ").
                appendDescription(Math.round(crystalData.getInstability() * 100.0F) + "%").
                appendDescription("\n");
        }

        // Unmodifiable
        if (crystalData.isUnmodifiable())
        {
            builder.appendDescription("**Unmodifiable**").appendDescription("\n");
        }

        // Modifiers
        VaultItemsHandler.parseModifiers(builder, crystalData.getModifiers());
    }


    /**
     * This method parses VaultTrinket item tooltip into discord chat.
     * @param builder Embed Builder.
     * @param itemStack Vault Trinket Item Stack.
     * @param itemTag The item tag data.
     */
    public static void handleTrinketTooltip(EmbedBuilder builder, ItemStack itemStack, CompoundTag itemTag)
    {
        // I do not want to include Botania in dependencies. This is workaround.

        AttributeGearData data = AttributeGearData.read(itemStack);

        if (data.getFirstValue(ModGearAttributes.STATE).orElse(VaultGearState.UNIDENTIFIED) == VaultGearState.IDENTIFIED)
        {
            int totalUses = itemTag.getInt("vaultUses");
            int used = itemTag.getList("usedVaults", 10).size();
            int remaining = Math.max(totalUses - used, 0);

            builder.appendDescription("**Uses:** ").appendDescription(String.valueOf(remaining)).appendDescription("\n");

            data.getFirstValue(ModGearAttributes.CRAFTED_BY).ifPresent(crafter ->
                builder.appendDescription("**Crafted by:** ").appendDescription(crafter).appendDescription("\n"));

            data.getFirstValue(ModGearAttributes.TRINKET_EFFECT).ifPresent(effect -> {
                TrinketConfig.Trinket trinket = effect.getTrinketConfig();
                builder.appendDescription(trinket.getEffectText()).appendDescription("\n");
            });

            data.getFirstValue(ModGearAttributes.TRINKET_EFFECT).
                map(TrinketEffect::getConfig).
                filter(TrinketEffect.Config::hasCuriosSlot).
                map(TrinketEffect.Config::getCuriosSlot).
                ifPresent(slot -> {
                    MutableComponent slotTranslation = new TranslatableComponent("curios.slot").append(": ");
                    MutableComponent slotType = new TranslatableComponent("curios.identifier." + slot);

                    builder.appendDescription("\n").
                        appendDescription(slotTranslation.getString()).
                        appendDescription(slotType.getString());
                });
        }
    }


    /**
     * This method parses Vault Relic Fragment item tooltip into discord chat.
     * @param builder Embed Builder.
     * @param itemStack Vault Trinket Item Stack.
     * @param relic The item.
     */
    public static void handleRelicFragmentTooltip(EmbedBuilder builder, ItemStack itemStack, RelicFragmentItem relic)
    {
        Optional<ResourceLocation> resourceLocation = relic.getDynamicModelId(itemStack);
        DynamicModelRegistry<PlainItemModel> fragmentRegistry = ModDynamicModels.Relics.FRAGMENT_REGISTRY;

        resourceLocation = resourceLocation.
            flatMap(fragmentRegistry::get).
            map(DynamicModel::getId).
            flatMap(ModRelics::getRelicOfFragment).
            map(ModRelics.RelicRecipe::getResultingRelic);

        fragmentRegistry = ModDynamicModels.Relics.RELIC_REGISTRY;

        resourceLocation.flatMap(fragmentRegistry::get).ifPresent(relicModel ->
            builder.appendDescription("**Assembles:** ").appendDescription(relicModel.getDisplayName()));
    }


    /**
     * This method parses Vault Catalyst item tooltip into discord chat.
     * @param builder Embed Builder.
     * @param itemStack Vault Catalyst Item Stack.
     */
    public static void handleCatalystTooltip(EmbedBuilder builder, ItemStack itemStack)
    {
        List<ResourceLocation> modifierIdList = VaultCatalystInfusedItem.getModifiers(itemStack);

        if (!modifierIdList.isEmpty())
        {
            builder.appendDescription("\n");
            builder.appendDescription(new TranslatableComponent(modifierIdList.size() <= 1 ?
                "tooltip.the_vault.vault_catalyst.modifier.singular" :
                "tooltip.the_vault.vault_catalyst.modifier.plural").getString());
            builder.appendDescription("\n");

            modifierIdList.forEach(modifierId ->
                VaultModifierRegistry.getOpt(modifierId).ifPresent(vaultModifier ->
                    builder.appendDescription(vaultModifier.getDisplayName()).appendDescription("\n")));
        }
    }


    /**
     * This method adds Vault Artifact image to the discord chat.
     * @param builder Embed Builder.
     * @param itemTag Vault artifact tag.
     */
    public static void handleVaultArtifactTooltip(EmbedBuilder builder, CompoundTag itemTag)
    {
         int customModelData = Math.max(itemTag.getInt("CustomModelData"), 1);
         String name = "vault_artifact_" + customModelData + ".png";
         builder.setImage("https://bonne.id.lv/assets/img/" + name);
    }


    /**
     * This method adds Vault Augment Item description the discord chat.
     * @param builder Embed Builder.
     * @param itemStack Vault augment item.
     */
    public static void handleAugmentTooltip(EmbedBuilder builder, ItemStack itemStack)
    {
        builder.appendDescription("**Theme:** ");
        AugmentItem.getTheme(itemStack).ifPresentOrElse(
            (key) -> builder.appendDescription(key.getName()),
            () -> builder.appendDescription("???"));
    }


// ---------------------------------------------------------------------
// Section: Private processing methods
// ---------------------------------------------------------------------


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

        builder.appendDescription("**" + (affixes.size() != 1 ? type.getPlural() : type.getSingular()) + ":** ");
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
                if (modifier.getCategory() != VaultGearModifier.AffixCategory.LEGENDARY)
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
                    String legendaryInfo = modifier.getCategory() == VaultGearModifier.AffixCategory.LEGENDARY ? "**Legendary** " : "";

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


    /**
     * This method adds crystal modifier data to the discord embed based on given filter.
     * @param builder Builder that need to be populated.
     * @param data Crystal Data object.
     * @param header Header of elements.
     * @param filter Filter for modifiers.
     */
    private static void populateCatalystInformation(EmbedBuilder builder,
        List<VaultModifierStack> data,
        String header,
        Predicate<VaultModifierStack> filter)
    {
        List<VaultModifierStack> modifierList = data.stream().filter(filter).toList();

        if (!modifierList.isEmpty())
        {
            builder.appendDescription(header).appendDescription("\n");

            for (VaultModifierStack modifierStack : modifierList)
            {
                VaultModifier<?> vaultModifier = modifierStack.getModifier();
                String formattedName = vaultModifier.getDisplayNameFormatted(modifierStack.getSize());

                builder.appendDescription("  ").
                    appendDescription("%dx".formatted(modifierStack.getSize())).
                    appendDescription(formattedName).
                    appendDescription("\n");
            }
        }
    }


    /**
     * Returns Crystal Objective name from instance of CrystalObjective.
     * @param objective class.
     * @return Name of the objective.
     */
    private static String parseObjectiveName(CrystalObjective objective)
    {
        if (objective instanceof BossCrystalObjective)
        {
            return "Hunt the Guardians";
        }
        else if (objective instanceof CakeCrystalObjective)
        {
            return "Cake Hunt";
        }
        else if (objective instanceof ElixirCrystalObjective)
        {
            return "Elixir Rush";
        }
        else if (objective instanceof EmptyCrystalObjective)
        {
            return "None";
        }
        else if (objective instanceof MonolithCrystalObjective)
        {
            return "Light the Monoliths";
        }
        else if (objective instanceof NullCrystalObjective)
        {
            return "???";
        }
        else if (objective instanceof ScavengerCrystalObjective)
        {
            return "Scavenger Hunt";
        }
        else if (objective instanceof SpeedrunCrystalObjective)
        {
            return "Speedrun";
        }
        else
        {
            return "???";
        }
    }


    /**
     * Returns Crystal Theme name from instance of CrystalTheme.
     * @param theme class.
     * @return Name of the theme.
     */
    private static String parseThemeName(CrystalTheme theme)
    {
        if (theme instanceof PoolCrystalTheme)
        {
            return "???";
        }
        else if (theme instanceof ValueCrystalTheme)
        {
            ThemeKey themeKey = VaultRegistry.THEME.getKey(theme.serializeNBT().getString("id"));

            if (themeKey == null)
            {
                return "Unknown";
            }
            else
            {
                return themeKey.getName();
            }
        }
        else if (theme instanceof NullCrystalTheme)
        {
            return "???";
        }
        else
        {
            return "???";
        }
    }


    /**
     * Returns Crystal Layout name from instance of CrystalLayout.
     * @param layout class.
     * @return Name of the layout.
     */
    private static String parseLayoutName(CrystalLayout layout)
    {
        if (layout instanceof ArchitectCrystalLayout)
        {
            StringBuilder builder = new StringBuilder();
            builder.append("Architect").append("\n");

            Optional<JsonObject> jsonObject = layout.writeJson();

            jsonObject.ifPresent(json -> {
                JsonArray entries = json.getAsJsonArray("entries");

                entries.forEach(entry -> {
                    ArchitectRoomEntry architectRoomEntry = ArchitectRoomEntry.fromJson((JsonObject) entry);
                    Component roomName = architectRoomEntry.getName();

                    if (roomName != null)
                    {
                        int count = architectRoomEntry.get(ArchitectRoomEntry.COUNT);

                        builder.append("-Has ").
                            append(String.valueOf(count)).
                            append(" *").
                            append(roomName.getString()).
                            append("* ").
                            append(count > 1 ? "Rooms" : "Room").
                            append("\n");
                    }
                });
            });

            return builder.toString();
        }
        else if (layout instanceof ClassicCircleCrystalLayout)
        {
            return "Circle";
        }
        else if (layout instanceof ClassicPolygonCrystalLayout)
        {
            return "Polygon";
        }
        else if (layout instanceof ClassicSpiralCrystalLayout)
        {
            return "Spiral";
        }
        else if (layout instanceof ClassicInfiniteCrystalLayout)
        {
            return "Infinite";
        }
        else if (layout instanceof NullCrystalLayout)
        {
            return "???";
        }
        else
        {
            return "???";
        }
    }


    /**
     * Returns Crystal Time name from instance of CrystalTime.
     * @param time class.
     * @return Name of the time.
     */
    private static String parseTime(CrystalTime time)
    {
        if (time instanceof PoolCrystalTime)
        {
            return "";
        }
        else if (time instanceof ValueCrystalTime vaultTime)
        {
            int min = IntRoll.getMin(vaultTime.getRoll());
            int max = IntRoll.getMax(vaultTime.getRoll());
            String text = formatTimeString(min);
            if (min != max) {
                text = text + " - " + formatTimeString(max);
            }

            return "**Time:** " + text;
        }
        else if (time instanceof NullCrystalTime)
        {
            return "";
        }
        else
        {
            return "";
        }
    }


    /**
     * This method parses crystal modifiers.
     * @param builder Builder that need to be populated.
     * @param modifiers The object that contains all crystal modifiers.
     */
    private static void parseModifiers(EmbedBuilder builder, CrystalModifiers modifiers)
    {
        if (modifiers.hasClarity()) {
            builder.appendDescription("*Clarity*\n");
        }

        List<VaultModifierStack> modifierList = new ArrayList<>();

        for (VaultModifierStack modifier : modifiers)
        {
            modifierList.add(modifier);
        }

        int curseCount = modifiers.getCurseCount();

        if (curseCount > 0)
        {
            if (modifiers.hasClarity())
            {
                VaultItemsHandler.populateCatalystInformation(builder,
                    modifierList,
                    "**Cursed:**",
                    catalyst -> ModConfigs.VAULT_CRYSTAL_CATALYST.isCurse(catalyst.getModifierId()));
            }
            else
            {
                builder.appendDescription("**Cursed** ").
                    appendDescription(CURSE.repeat(curseCount)).
                    appendDescription("\n");
            }
        }

        VaultItemsHandler.populateCatalystInformation(builder,
            modifierList,
            "**Positive Modifiers:**",
            catalyst -> ModConfigs.VAULT_CRYSTAL_CATALYST.isGood(catalyst.getModifierId()));

        VaultItemsHandler.populateCatalystInformation(builder,
            modifierList,
            "**Negative Modifiers:**",
            catalyst -> ModConfigs.VAULT_CRYSTAL_CATALYST.isBad(catalyst.getModifierId()));

        VaultItemsHandler.populateCatalystInformation(builder,
            modifierList,
            "**Other Modifiers:**",
            catalyst -> ModConfigs.VAULT_CRYSTAL_CATALYST.isUnlisted(catalyst.getModifierId()));
    }


    /**
     * Time parser
     * @param remainingTicks how many ticks remaining
     * @return remaining ticks parsed as string.
     */
    private static String formatTimeString(int remainingTicks)
    {
        long seconds = remainingTicks / 20 % 60;
        long minutes = remainingTicks / 20 / 60 % 60;
        long hours = remainingTicks / 20 / 60 / 60;
        return hours > 0L ? String.format("%02d:%02d:%02d", hours, minutes, seconds) :
            String.format("%02d:%02d", minutes, seconds);
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

    /**
     * Symbol for text fields.
     */
    private static final String CURSE = "\u2620";

    /**
     * Variable format for numbers.
     */
    private static final DecimalFormat FORMAT = new DecimalFormat("0.##");
}
