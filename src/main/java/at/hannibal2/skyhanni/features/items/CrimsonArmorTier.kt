package at.hannibal2.skyhanni.features.items

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.events.GuiRenderItemEvent
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.utils.ItemUtils.name
import at.hannibal2.skyhanni.utils.LorenzUtils
import net.minecraft.client.renderer.GlStateManager
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.regex.Pattern

class CrimsonArmorTier {

    private val armors = mutableListOf<String>()
    private val tiers = mutableMapOf<String, Int>()
    private val STAR_FIND_PATCHER = Pattern.compile("(.*)§.✪(.*)")
    private val armorParts = listOf("Helmet", "Chestplate", "Leggings", "Boots")

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onTooltip(event: ItemTooltipEvent) {
        if (!LorenzUtils.inSkyblock) return
        if (!SkyHanniMod.feature.inventory.crimsonArmorStars) return

        val stack = event.itemStack ?: return
        if (stack.stackSize != 1) return
        val number = getCrimsonNumber(stack.name ?: return)

        if (number > 0) {
            var name = stack.name!!
            while (STAR_FIND_PATCHER.matcher(name).matches()) {
                name = name.replaceFirst("§.✪".toRegex(), "")
            }
            name = name.trim()
            event.toolTip[0] = "$name §c$number✪"
        }
    }

    @SubscribeEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        try {
            val items = event.getConstant("Items")!!
            if (items.has("crimson_armors")) {
                armors.clear()
                armors.addAll(items.getAsJsonArray("crimson_armors").map { it.asString })
            }

            tiers.clear()
            if (items.has("crimson_tiers")) {
                items.getAsJsonObject("crimson_tiers").entrySet().forEach {
                    tiers[it.key] = it.value.asInt
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            LorenzUtils.error("error in RepositoryReloadEvent")
        }
    }

    @SubscribeEvent
    fun onRenderItemOverlayPost(event: GuiRenderItemEvent.RenderOverlayEvent.Post) {
        if (!LorenzUtils.inSkyblock) return
        if (!SkyHanniMod.feature.inventory.itemNumberAsStackSize.contains(6)) return

        val stack = event.stack ?: return
        if (stack.stackSize != 1) return
        val number = getCrimsonNumber(stack.name ?: return)
        val stackTip = if (number == -1) "" else number.toString()

        if (stackTip.isNotEmpty()) {
            GlStateManager.disableLighting()
            GlStateManager.disableDepth()
            GlStateManager.disableBlend()
            event.fontRenderer.drawStringWithShadow(
                stackTip,
                (event.x + 17 - event.fontRenderer.getStringWidth(stackTip)).toFloat(),
                (event.y + 9).toFloat(),
                16777215
            )
            GlStateManager.enableLighting()
            GlStateManager.enableDepth()
        }
    }

    private fun getCrimsonNumber(fullName: String): Int {
        var name = fullName
        if (armors.any { name.contains(it) } && armorParts.any { name.contains(it) }) {
            var gold = 0
            var pink = 0
            var aqua = 0
            while (name.contains("§6✪")) {
                name = name.replaceFirst("§6✪", "")
                gold++
            }
            while (name.contains("§d✪")) {
                name = name.replaceFirst("§d✪", "")
                pink++
            }
            while (name.contains("§b✪")) {
                name = name.replaceFirst("§b✪", "")
                aqua++
            }
            return (tiers.entries.find { name.contains(it.key) }?.value ?: 0) + if (aqua > 0) {
                10 + aqua
            } else if (pink > 0) {
                5 + pink
            } else {
                gold
            }
        }
        return -1
    }
}