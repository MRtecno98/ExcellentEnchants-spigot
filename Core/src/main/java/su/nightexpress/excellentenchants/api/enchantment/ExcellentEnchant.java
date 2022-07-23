package su.nightexpress.excellentenchants.api.enchantment;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nexmedia.engine.api.config.JYML;
import su.nexmedia.engine.api.manager.IListener;
import su.nexmedia.engine.lang.LangManager;
import su.nexmedia.engine.manager.leveling.Scaler;
import su.nexmedia.engine.utils.*;
import su.nexmedia.engine.utils.random.Rnd;
import su.nightexpress.excellentenchants.ExcellentEnchants;
import su.nightexpress.excellentenchants.config.Config;
import su.nightexpress.excellentenchants.config.Lang;
import su.nightexpress.excellentenchants.manager.EnchantManager;
import su.nightexpress.excellentenchants.manager.object.EnchantScaler;
import su.nightexpress.excellentenchants.manager.object.EnchantTier;
import su.nightexpress.excellentenchants.manager.type.FitItemType;
import su.nightexpress.excellentenchants.manager.type.ObtainType;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ExcellentEnchant extends Enchantment implements IListener {

    public static final String PLACEHOLDER_NAME           = "%enchantment_name%";
    public static final String PLACEHOLDER_NAME_FORMATTED = "%enchantment_name_formatted%";
    public static final String PLACEHOLDER_DESCRIPTION    = "%enchantment_description%";
    public static final String PLACEHOLDER_LEVEL                         = "%enchantment_level%";
    public static final String PLACEHOLDER_LEVEL_MIN                     = "%enchantment_level_min%";
    public static final String PLACEHOLDER_LEVEL_MAX                     = "%enchantment_level_max%";
    public static final String PLACEHOLDER_CONFLICTS                     = "%enchantment_conflicts%";
    public static final String PLACEHOLDER_TARGET                        = "%enchantment_target%";
    public static final String PLACEHOLDER_TIER                          = "%enchantment_tier%";
    public static final String PLACEHOLDER_FIT_ITEM_TYPES                = "%enchantment_fit_item_types%";
    public static final String PLACEHOLDER_OBTAIN_CHANCE_ENCHANTING      = "%enchantment_obtain_chance_enchanting%";
    public static final String PLACEHOLDER_OBTAIN_CHANCE_VILLAGER        = "%enchantment_obtain_chance_villager%";
    public static final String PLACEHOLDER_OBTAIN_CHANCE_LOOT_GENERATION = "%enchantment_obtain_chance_loot_generation%";
    public static final String PLACEHOLDER_OBTAIN_CHANCE_FISHING = "%enchantment_obtain_chance_fishing%";
    public static final String PLACEHOLDER_OBTAIN_CHANCE_MOB_SPAWNING = "%enchantment_obtain_chance_mob_spawning%";
    public static final String PLACEHOLDER_COST_ITEM       = "%enchantment_cost_item%";

    protected final ExcellentEnchants plugin;
    protected final JYML              cfg;
    protected final String            id;
    protected final EnchantPriority priority;

    protected String       displayName;
    protected EnchantTier  tier;
    protected List<String> description;

    private final Set<Enchantment>        conflicts;
    protected     boolean                 isTreasure;
    protected     int                     levelMin;
    protected     int                     levelMax;
    protected     Scaler                  levelByEnchantCost;
    protected     Scaler                  anvilMergeCost;
    protected     Map<ObtainType, Double> obtainChance;
    protected ItemStack                   costItem;
    protected boolean                     costEnabled;

    public ExcellentEnchant(@NotNull ExcellentEnchants plugin, @NotNull JYML cfg, @NotNull EnchantPriority priority) {
        super(NamespacedKey.minecraft(cfg.getFile().getName().replace(".yml", "").toLowerCase()));
        this.plugin = plugin;
        this.id = this.getKey().getKey();
        this.cfg = cfg;
        this.updateConfig();
        this.cfg.saveChanges();
        this.priority = priority;

        this.displayName = StringUtil.color(cfg.getString("Name", this.getId()));
        this.tier = EnchantManager.getTierById(cfg.getString("Tier", Placeholders.DEFAULT));
        if (this.tier == null) {
            throw new IllegalStateException("Invalid tier provided for the '" + id + "' enchantment!");
        }
        this.tier.getEnchants().add(this);
        this.description = StringUtil.color(cfg.getStringList("Description"));

        this.conflicts = new HashSet<>();
        this.isTreasure = cfg.getBoolean("Is_Treasure");
        this.levelMin = cfg.getInt("Level.Min");
        this.levelMax = cfg.getInt("Level.Max");
        this.levelByEnchantCost = new EnchantScaler(this, ObtainType.ENCHANTING.getPathName() + ".Level_By_Exp_Cost");
        this.anvilMergeCost = new EnchantScaler(this, "Anvil.Merge_Cost");

        this.obtainChance = new HashMap<>();
        for (ObtainType obtainType : ObtainType.values()) {
            double obtainChance = cfg.getDouble(obtainType.getPathName() + ".Chance");
            this.obtainChance.put(obtainType, obtainChance);
        }

        this.costEnabled = cfg.getBoolean("Settings.Cost.Enabled");
        this.costItem = cfg.getItem("Settings.Cost.Item");
    }

    protected void updateConfig() {
        cfg.addMissing("Is_Treasure", false);
        cfg.addMissing("Settings.Cost.Enabled", false);
        cfg.addMissing("Settings.Cost.Item.Material", Material.AIR.name());
        cfg.addMissing("Settings.Cost.Item.Amount", 1);

        if (cfg.contains("Enchantment_Table")) {
            String path = ObtainType.ENCHANTING.getPathName() + ".";
            cfg.addMissing(path + "Chance", cfg.getDouble("Enchantment_Table.Chance"));
            cfg.addMissing(path + "Level_By_Exp_Cost", cfg.getString("Enchantment_Table.Level_By_Exp_Cost", "30"));
            cfg.remove("Enchantment_Table");
        }

        for (ObtainType obtainType : ObtainType.values()) {
            cfg.addMissing(obtainType.getPathName() + ".Chance", 25D);
        }

        /*String scalabe = "Scalable. Placeholder: " + PLACEHOLDER_LEVEL + ". See: http://77.222.60.131:8080/plugin/engine/config/formats";
        cfg.setComments("Is_Treasure", Arrays.asList("Defines if this enchantment is a treasure enchantment.", "Treasure enchantments can only be received via looting, trading, or fishing."));
        cfg.setComments("Name", Arrays.asList("Enchantment display name. This name will be displayed in item lore and in enchantments list GUI."));
        cfg.setComments("Tier", Arrays.asList("Enchantment tier. Must be a valid tier from the 'config.yml'. Enchantments with invalid tier won't be loaded."));
        cfg.setComments("Description", Arrays.asList("Enchantment description. Will be displayed in item lore (if not disabled in the main config.yml) and in enchantments list GUI.", "You can use multiple lines here.", "You can use 'Enchantment' placeholders: http://77.222.60.131:8080/plugin/excellentenchants/utils/placeholders"));
        cfg.setComments("Level", Arrays.asList("Enchantment level settings."));
        cfg.setComments("Level.Min", Arrays.asList("Minimal (start) enchantment level. Can not be smaller then 1."));
        cfg.setComments("Level.Max", Arrays.asList("Maximal (final) enchantment level.", "Keep in mind that while you can enchant items with bypass max. enchantment level, all enchantment 'Scalable' option values will not exceed the max. enchantment level."));
        cfg.setComments("Anvil", Arrays.asList("Enchantment settings for Anvil."));
        cfg.setComments("Anvil.Merge_Cost", Arrays.asList("Defines the exp cost to merge this enchantment on other items on anvil.", scalabe));
        cfg.setComments("Enchanting_Table", Arrays.asList("Enchantment settings for Enchanting Table."));
        cfg.setComments("Enchanting_Table.Level_By_Exp_Cost", Arrays.asList("Defines which enchantment level will be generated in Enchanting Table depends on the enchanting cost.", "Example: expression '9 * %enchantment_level%' for enchantment levels 1-3 will result in I = 9+ Levels, II = 18+ Levels, III = 27+ Levels.", scalabe));
        cfg.setComments("Enchanting_Table.Chance", Arrays.asList("A chance that this enchantment will be appeared in Enchanting Table."));
        cfg.setComments("Villagers.Chance", Arrays.asList("A chance that this enchantment will be populated on items in Villager trades."));
        cfg.setComments("Loot_Generation.Chance", Arrays.asList("A chance that this enchantment will be populated on items in cave/dungeon/castle chests/minecarts and other containers."));
        cfg.setComments("Fishing.Chance", Arrays.asList("A chance that this enchantment will be populated on items received from fishing."));
        cfg.setComments("Mob_Spawning.Chance", Arrays.asList("A chance that this enchantment will be populated on items equipped on mob on spawning."));
        cfg.setComments("Settings", Arrays.asList("Individual enchantment settings."));
        cfg.setComments("Settings.Trigger_Chance", Arrays.asList("A chance that this enchantment will be triggered.", scalabe));
        cfg.setComments("Settings.Cost", Arrays.asList("A cost a player will have to pay to have this enchantment triggered."));
        cfg.setComments("Settings.Cost.Enabled", Arrays.asList("Enables/Disables cost feature."));
        cfg.setComments("Settings.Cost.Item", Arrays.asList("A (custom) item that player must have in his inventory, that will be consumed to trigger the enchantment effect.", "See http://77.222.60.131:8080/plugin/engine/config/formats for item options."));
        cfg.setComments("Settings.Potion_Effect", Arrays.asList("Enchantment settings for the Potion Effect applied to a wearer or victim."));
        cfg.setComments("Settings.Potion_Effect.Level", Arrays.asList("Potion effect level (amplifier).", scalabe));
        cfg.setComments("Settings.Potion_Effect.Duration", Arrays.asList("Potion effect duration (in seconds). Keep in mind that settings this to a low value (smaller than Passive Task Interval in the config.yml) will result in effect reappear delay.", scalabe));
        cfg.setComments("Settings.Particle", Arrays.asList("Particle effect that will be played on enchantment trigger."));
        cfg.setComments("Settings.Particle.Name", Arrays.asList("Particle name. Set this to empty '' or 'NONE' to disable.", "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Particle.html"));
        cfg.setComments("Settings.Particle.Data", Arrays.asList("Particle data (additional settings).", "- BLOCK_DUST, BLOCK_MARKER, BLOCK_CRACK, ITEM_CRACK, FALLING_DUST: Use https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html", "- REDSTONE: Use RGB (like 255,255,255)"));
        cfg.setComments("Settings.Sound", Arrays.asList("Sound that will be played on enchantment trigger.", "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html"));
        cfg.setComments("Settings.Arrow", Arrays.asList("Enchantment arrow settings."));
        cfg.setComments("Settings.Arrow.Trail", Arrays.asList("A particle effect to play as an arrow trail."));
        cfg.setComments("Settings.Arrow.Trail.Name", Arrays.asList("Particle name. Set this to empty '' or 'NONE' to disable.", "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Particle.html"));
        cfg.setComments("Settings.Arrow.Trail.Data", Arrays.asList("Particle data (additional settings).", "- BLOCK_DUST, BLOCK_MARKER, BLOCK_CRACK, ITEM_CRACK, FALLING_DUST: Use https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html", "- REDSTONE: Use RGB (like 255,255,255)"));

        List<String> placeholders = new ArrayList<>();
        placeholders.add("Additional placeholders:");
        for (Field field : Reflex.getFields(this.getClass())) {
            if (field.getType() != String.class) continue;
            if (!field.getName().startsWith("PLACEHOLDER")) continue;
            if (field.getDeclaringClass().equals(ExcellentEnchant.class)) continue;

            String value = (String) Reflex.getFieldValue(this, field.getName());
            String name = StringUtil.capitalizeFully(value.replace("%", "").replace("_", " "));
            placeholders.add("- " + value + ": " + name.trim());
        }
        cfg.options().setHeader(placeholders);*/
    }

    @NotNull
    public UnaryOperator<String> replacePlaceholders(int level) {
        String conflicts = this.getConflicts().isEmpty() ? plugin.getMessage(Lang.OTHER_NONE).getLocalized() : this.getConflicts().stream().filter(Objects::nonNull).map(LangManager::getEnchantment).collect(Collectors.joining("\n"));

        return str -> str
            .replace(PLACEHOLDER_NAME, this.getDisplayName())
            .replace(PLACEHOLDER_NAME_FORMATTED, this.getNameFormatted(level))
            .replace(PLACEHOLDER_LEVEL, NumberUtil.toRoman(level))
            .replace(PLACEHOLDER_LEVEL_MIN, String.valueOf(this.getStartLevel()))
            .replace(PLACEHOLDER_LEVEL_MAX, String.valueOf(this.getMaxLevel()))
            .replace(PLACEHOLDER_TARGET, plugin.getLangManager().getEnum(this.getItemTarget()))
            .replace(PLACEHOLDER_TIER, this.getTier().getName())
            .replace(PLACEHOLDER_CONFLICTS, conflicts)
            .replace(PLACEHOLDER_FIT_ITEM_TYPES, String.join(", ", Stream.of(this.getFitItemTypes()).map(type -> plugin.getLangManager().getEnum(type)).toList()))
            .replace(PLACEHOLDER_OBTAIN_CHANCE_ENCHANTING, NumberUtil.format(this.getObtainChance(ObtainType.ENCHANTING)))
            .replace(PLACEHOLDER_OBTAIN_CHANCE_VILLAGER, NumberUtil.format(this.getObtainChance(ObtainType.VILLAGER)))
            .replace(PLACEHOLDER_OBTAIN_CHANCE_LOOT_GENERATION, NumberUtil.format(this.getObtainChance(ObtainType.LOOT_GENERATION)))
            .replace(PLACEHOLDER_OBTAIN_CHANCE_FISHING, NumberUtil.format(this.getObtainChance(ObtainType.FISHING)))
            .replace(PLACEHOLDER_OBTAIN_CHANCE_MOB_SPAWNING, NumberUtil.format(this.getObtainChance(ObtainType.MOB_SPAWNING)))
            .replace(PLACEHOLDER_COST_ITEM, this.hasCostItem() ? ItemUtil.getItemName(this.costItem) : plugin.getMessage(Lang.OTHER_NONE).getLocalized())
            ;
    }

    @NotNull
    public UnaryOperator<String> formatString(int level) {
        return str -> this.replacePlaceholders(level).apply(str
            .replace(PLACEHOLDER_DESCRIPTION, String.join("\n", Config.formatDescription(this.getDescription())))
        );
    }

    @NotNull
    public FitItemType[] getFitItemTypes() {
        FitItemType itemType = FitItemType.getByEnchantmentTarget(this.getItemTarget());
        return itemType == null ? new FitItemType[0] : new FitItemType[]{itemType};
    }

    protected void addConflicts() {

    }

    protected void addConflict(@NotNull Enchantment enchantment) {
        this.conflicts.add(enchantment);
    }

    public boolean hasCostItem() {
        return this.costEnabled && !this.costItem.getType().isAir();
    }

    public boolean takeCostItem(@NotNull LivingEntity livingEntity) {
        if (!this.hasCostItem()) return true;
        if (!(livingEntity instanceof Player player)) return true;

        if (PlayerUtil.countItem(player, this.costItem) < this.costItem.getAmount()) return false;
        return PlayerUtil.takeItem(player, this.costItem, this.costItem.getAmount());
    }

    public boolean isEnchantmentAvailable(@NotNull LivingEntity entity) {
        return !Config.isEnchantmentDisabled(this, entity.getWorld().getName());
    }

    @Override
    public void registerListeners() {
    	if(Config.ENCHANTMENTS_AUTO_CONFLICT)
    		this.addConflicts();
        this.plugin.getPluginManager().registerEvents(this, plugin);
    }

    @NotNull
    public JYML getConfig() {
        return this.cfg;
    }

    @NotNull
    public String getId() {
        return this.id;
    }

    @NotNull
    public EnchantPriority getPriority() {
        return priority;
    }

    @NotNull
    @Override
    public String getName() {
        return getId().toUpperCase();
    }

    @NotNull
    public String getDisplayName() {
        return this.displayName;
    }

    @NotNull
    public String getNameFormatted(int level) {
        return this.getTier().getColor() + this.getDisplayName() + " " + NumberUtil.toRoman(level);
    }

    @NotNull
    public List<String> getDescription() {
        return this.description;
    }

    @NotNull
    public List<String> getDescription(int level) {
        List<String> description = new ArrayList<>(this.description);
        description.replaceAll(this.replacePlaceholders(level));
        return description;
    }

    @NotNull
    public Set<Enchantment> getConflicts() {
        return conflicts;
    }

    @NotNull
    public EnchantTier getTier() {
        return this.tier;
    }

    @Override
    public int getMaxLevel() {
        return this.levelMax;
    }

    @Override
    public int getStartLevel() {
        return this.levelMin;
    }

    public int getLevelByEnchantCost(int expLevel) {
        Optional<Map.Entry<Integer, Double>> opt = this.levelByEnchantCost.getValues().entrySet().stream()
            .filter(en -> expLevel >= en.getValue().intValue()).max(Comparator.comparingInt(Map.Entry::getKey));
        return opt.isPresent() ? opt.get().getKey() : Rnd.get(this.getStartLevel(), this.getMaxLevel());
    }

    public double getObtainChance(@NotNull ObtainType obtainType) {
        return this.obtainChance.getOrDefault(obtainType, 0D);
    }

    public int getAnvilMergeCost(int level) {
        return (int) this.anvilMergeCost.getValue(level);
    }

    @Override
    public final boolean conflictsWith(@NotNull Enchantment enchantment) {
        return this.conflicts.contains(enchantment);
    }

    @Override
    public final boolean canEnchantItem(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (EnchantManager.getItemEnchants(item).keySet().stream().anyMatch(e -> e.conflictsWith(this) || this.conflictsWith(e))) return false;
        if (!item.containsEnchantment(this) && EnchantManager.getItemCustomEnchantsAmount(item) >= Config.ENCHANTMENTS_ITEM_CUSTOM_MAX) {
            return false;
        }
        if (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK) {
            return true;
        }
        return Stream.of(this.getFitItemTypes()).anyMatch(fitItemType -> fitItemType.isIncluded(item));
    }

    /*protected boolean isFitItemType(@NotNull ItemStack item) {
        EnchantmentTarget target = this.getItemTarget();
        return switch (target) {
            case ARMOR -> ItemUtil.isArmor(item);
            case ARMOR_FEET -> ItemUtil.isBoots(item);
            case ARMOR_LEGS -> ItemUtil.isLeggings(item);
            case ARMOR_TORSO -> ItemUtil.isChestplate(item) || (Config.ENCHANTMENTS_ITEM_ELYTRA_AS_CHESTPLATE && item.getType() == Material.ELYTRA);
            case ARMOR_HEAD -> ItemUtil.isHelmet(item);
            case WEAPON -> ItemUtil.isSword(item) || (Config.ENCHANTMENTS_ITEM_AXES_AS_SWORDS && ItemUtil.isAxe(item));
            case TOOL -> ItemUtil.isTool(item);
            case BOW -> item.getType() == Material.BOW || (Config.ENCHANTMENTS_ITEM_CROSSBOWS_AS_BOWS && ItemUtil.isBow(item));
            case FISHING_ROD -> item.getType() == Material.FISHING_ROD;
            case BREAKABLE -> true;
            case WEARABLE -> EnchantManager.isEnchantable(item);
            case TRIDENT -> ItemUtil.isTrident(item);
            case CROSSBOW -> item.getType() == Material.CROSSBOW;
            default -> false;
        };
    }*/

    @Override
    public boolean isCursed() {
        return false;
    }

    @Override
    public final boolean isTreasure() {
        return this.isTreasure;
    }
}
