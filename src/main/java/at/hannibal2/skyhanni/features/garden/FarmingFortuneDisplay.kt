package at.hannibal2.skyhanni.features.garden

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.CropAccessoryData
import at.hannibal2.skyhanni.data.GardenCropMilestones
import at.hannibal2.skyhanni.data.GardenCropMilestones.Companion.getCounter
import at.hannibal2.skyhanni.data.GardenCropUpgrades.Companion.getUpgradeLevel
import at.hannibal2.skyhanni.events.*
import at.hannibal2.skyhanni.features.garden.CropType.Companion.getTurboCrop
import at.hannibal2.skyhanni.features.garden.GardenAPI.addCropIcon
import at.hannibal2.skyhanni.features.garden.GardenAPI.getCropType
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.addAsSingletonList
import at.hannibal2.skyhanni.utils.RenderUtils.renderString
import at.hannibal2.skyhanni.utils.RenderUtils.renderStringsAndItems
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getEnchantments
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getHoeCounter
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.floor
import kotlin.math.log10

class FarmingFortuneDisplay {
    private val tabFortunePattern = " Farming Fortune: §r§6☘(\\d+)".toRegex()

    private var display = listOf<List<Any>>()
    private var accessoryProgressDisplay = ""

    private var lastToolSwitch: Long = 0
    private var ticks: Int = 0

    @SubscribeEvent
    fun onTabListUpdate(event: TabListUpdateEvent) {
        if (!GardenAPI.inGarden()) return
        tabFortune = event.tabList.firstNotNullOfOrNull {
            tabFortunePattern.matchEntire(it)?.groups?.get(1)?.value?.toDoubleOrNull()
        } ?: tabFortune
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onInventoryUpdate(event: OwnInventorItemUpdateEvent) {
        if (!GardenAPI.inGarden()) return
        if (event.itemStack.getCropType() == null) return
        updateToolFortune(event.itemStack)
    }

    @SubscribeEvent
    fun onBlockBreak(event: CropClickEvent) {
        val cropBroken = event.crop
        if (cropBroken != currentCrop) {
            currentCrop = cropBroken
            updateToolFortune(event.itemInHand)
        }
    }

    @SubscribeEvent
    fun onGardenToolChange(event: GardenToolChangeEvent) {
        lastToolSwitch = System.currentTimeMillis()
        val heldTool = event.toolItem
        currentCrop = event.crop ?: currentCrop
        updateToolFortune(heldTool)
    }

    @SubscribeEvent
    fun onRenderOverlay(event: GuiRenderEvent.GameOverlayRenderEvent) {
        if (!isEnabled()) return
        config.farmingFortunePos.renderStringsAndItems(display, posLabel = "True Farming Fortune")
    }

    @SubscribeEvent
    fun onRenderOverlay(event: GuiRenderEvent.ChestBackgroundRenderEvent) {
        if (!isEnabled()) return
        if (!CropAccessoryData.isLoadingAccessories) return
        SkyHanniMod.feature.misc.inventoryLoadPos.renderString(
            accessoryProgressDisplay,
            posLabel = "Load Accessory Bags"
        )
    }

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START || ticks++ % 5 != 0) return
        val displayCrop = currentCrop ?: return

        val updatedDisplay = mutableListOf<List<Any>>()
        updatedDisplay.add(mutableListOf<Any>().also {
            it.addCropIcon(displayCrop)
            val recentlySwitchedTool = System.currentTimeMillis() < lastToolSwitch + 1000
            it.add(
                "§6Farming Fortune§7: §e" + if (!recentlySwitchedTool) {
                    LorenzUtils.formatDouble(getCurrentFarmingFortune(), 0)
                } else "?"
            )
            if (GardenAPI.toolInHand != null) {
                latestTrueFarmingFortune[displayCrop] = getCurrentFarmingFortune(true)
            }
        })

        if (upgradeFortune == null) {
            updatedDisplay.addAsSingletonList("§cOpen §e/cropupgrades§c for more exact data!")
        }
        if (accessoryFortune == null) {
            updatedDisplay.addAsSingletonList("§cOpen Accessory Bag for more exact data!")
            if (CropAccessoryData.isLoadingAccessories) {
                accessoryProgressDisplay =
                    "§e${CropAccessoryData.pagesLoaded}/${CropAccessoryData.accessoryBagPageCount} pages viewed"
            }
        } else {
            accessoryProgressDisplay = ""
        }

        display = updatedDisplay
    }

    private fun updateToolFortune(tool: ItemStack?) {
        val cropMatchesTool = currentCrop == tool?.getCropType()
        val toolCounterFortune = if (cropMatchesTool) {
            getToolFortune(tool) + getCounterFortune(tool) + getCollectionFortune(tool)
        } else 0.0
        toolFortune =
            toolCounterFortune + getTurboCropFortune(tool, currentCrop) + getDedicationFortune(tool, currentCrop)
    }

    private fun isEnabled(): Boolean = GardenAPI.inGarden() && config.farmingFortuneDisplay


    companion object {
        private val config get() = SkyHanniMod.feature.garden
        private val hidden get() = SkyHanniMod.feature.hidden
        private val latestTrueFarmingFortune: MutableMap<CropType, Double> get() = hidden.gardenLatestTrueFarmingFortune

        private var currentCrop: CropType? = null

        private var tabFortune: Double = 0.0
        private var toolFortune: Double = 0.0
        private val baseFortune: Double get() = if (config.farmingFortuneDropMultiplier) 100.0 else 0.0
        private val upgradeFortune: Double? get() = currentCrop?.getUpgradeLevel()?.let { it * 5.0 }
        private val accessoryFortune: Double?
            get() = currentCrop?.let {
                CropAccessoryData.cropAccessory?.getFortune(it)
            }

        private val collectionPattern = "§7You have §6\\+([\\d]{1,3})☘ Farming Fortune".toRegex()

        fun getToolFortune(tool: ItemStack?): Double {
            val internalName = tool?.getInternalName() ?: return 0.0
            return if (internalName.startsWith("THEORETICAL_HOE")) {
                listOf(10.0, 25.0, 50.0)[internalName.last().digitToInt() - 1]
            } else when (internalName) {
                "FUNGI_CUTTER" -> 30.0
                "COCO_CHOPPER" -> 20.0
                else -> 0.0
            }
        }

        fun getTurboCropFortune(tool: ItemStack?, cropType: CropType?): Double {
            val crop = cropType ?: return 0.0
            return tool?.getEnchantments()?.get(crop.getTurboCrop())?.let { it * 5.0 } ?: 0.0
        }

        fun getCollectionFortune(tool: ItemStack?): Double {
            val lore = tool?.getLore() ?: return 0.0
            var hasCollectionAbility = false
            return lore.firstNotNullOfOrNull {
                if (hasCollectionAbility || it == "§6Collection Analysis") {
                    hasCollectionAbility = true
                    collectionPattern.matchEntire(it)?.groups?.get(1)?.value?.toDoubleOrNull()
                } else null
            } ?: 0.0
        }

        fun getCounterFortune(tool: ItemStack?): Double {
            val counter = tool?.getHoeCounter() ?: return 0.0
            val digits = floor(log10(counter.toDouble()))
            return (16 * digits - 48).takeIf { it > 0.0 } ?: 0.0
        }

        fun getDedicationFortune(tool: ItemStack?, cropType: CropType?): Double {
            val dedicationLevel = tool?.getEnchantments()?.get("dedication") ?: 0
            val dedicationMultiplier = listOf(0.0, 0.5, 0.75, 1.0, 2.0)[dedicationLevel]
            val cropMilestone = GardenCropMilestones.getTierForCrops(
                cropType?.getCounter() ?: 0
            )
            return dedicationMultiplier * cropMilestone
        }

        fun getCurrentFarmingFortune(alwaysBaseFortune: Boolean = false): Double {
            val upgradeFortune = upgradeFortune ?: 0.0
            val accessoryFortune = accessoryFortune ?: 0.0

            val baseFortune = if (alwaysBaseFortune) 100.0 else baseFortune
            return baseFortune + upgradeFortune + tabFortune + toolFortune + accessoryFortune
        }

        fun CropType.getLatestTrueFarmingFortune() = latestTrueFarmingFortune[this]
    }
}