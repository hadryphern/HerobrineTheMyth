package net.herobrinemyth.client;

/**
 * Old Minecraft had very few sounds: steps, digging, mobs, fire, water, rain, doors, the cave sounds.
 * Everything added later (biome ambience, new mobs, new blocks, combat sounds, new music)
 * is muted so the world feels empty again, and his sounds stand out.
 */
final class ClassicSounds {
    private static final String[] MODERN = {
            // ambience added in 1.16+ (everything "ambient." except the old cave sounds)
            "ambient.basalt", "ambient.crimson", "ambient.nether", "ambient.soul", "ambient.warped", "ambient.underwater",
            // music added later (the old C418 "music.game" stays)
            "music.overworld.", "music.nether.", "music.under_water", "music.dragon", "music.end",
            // combat sounds from 1.9
            "entity.player.attack.", "item.shield.", "item.armor.equip_", "item.trident.", "item.crossbow.",
            "entity.player.levelup", "item.totem.", "item.elytra.", "item.spyglass.", "item.bundle.",
            "item.brush.", "item.goat_horn", "item.honey", "item.bone_meal", "item.axe.", "item.hoe.", "item.shovel.",
            // new mobs
            "entity.warden.", "entity.allay.", "entity.axolotl.", "entity.bee.", "entity.frog.", "entity.tadpole.",
            "entity.goat.", "entity.fox.", "entity.panda.", "entity.parrot.", "entity.dolphin.", "entity.camel.",
            "entity.sniffer.", "entity.turtle.", "entity.cod.", "entity.salmon.", "entity.pufferfish.", "entity.tropical_fish.",
            "entity.glow_squid.", "entity.polar_bear.", "entity.llama.", "entity.mule.", "entity.donkey.", "entity.cat.",
            "entity.ocelot.", "entity.rabbit.", "entity.bat.", "entity.phantom.", "entity.pillager.", "entity.vindicator.",
            "entity.evoker.", "entity.ravager.", "entity.illusioner.", "entity.wandering_trader.", "entity.strider.",
            "entity.hoglin.", "entity.zoglin.", "entity.piglin.", "entity.piglin_brute.", "entity.shulker.", "entity.vex.",
            "entity.guardian.", "entity.elder_guardian.", "entity.drowned.", "entity.husk.", "entity.stray.", "entity.endermite.",
            "entity.villager.work_", "entity.villager.trade", "entity.villager.celebrate", "entity.villager.yes",
            "entity.zombie_villager.converted", "entity.witch.", "entity.iron_golem.", "entity.snow_golem.", "entity.puffer_fish.",
            "entity.squid.", "entity.armadillo.", "entity.breeze.", "entity.bogged.",
            // new blocks
            "block.amethyst", "block.sculk", "block.beehive", "block.pointed_dripstone", "block.dripstone", "block.cave_vines",
            "block.big_dripleaf", "block.small_dripleaf", "block.azalea", "block.moss", "block.bubble_column", "block.beacon",
            "block.conduit", "block.respawn_anchor", "block.copper", "block.suspicious", "block.cherry", "block.bamboo",
            "block.decorated_pot", "block.chiseled_bookshelf", "block.hanging_sign", "block.hanging_roots", "block.mangrove",
            "block.mud", "block.froglight", "block.frogspawn", "block.powder_snow", "block.calcite", "block.tuff", "block.deepslate",
            "block.polished_deepslate", "block.nether_", "block.netherite", "block.ancient_debris", "block.basalt", "block.blackstone",
            "block.bone_block", "block.chain", "block.lodestone", "block.shroomlight", "block.soul_soil", "block.stem", "block.nylium",
            "block.wart_block", "block.weeping_vines", "block.vine", "block.honey_block", "block.lantern", "block.smithing_table",
            "block.grindstone", "block.composter", "block.bell", "block.barrel", "block.campfire", "block.smoker", "block.blastfurnace",
            "block.loom", "block.cartography", "block.lectern", "block.scaffolding", "block.sweet_berry", "block.crop.break",
            "block.enchantment_table", "block.anvil", "block.brewing_stand", "block.shulker_box", "block.end_portal",
            "block.end_gateway", "block.sculk_sensor", "block.sculk_shrieker", "block.sniffer_egg", "block.trial_spawner", "block.vault",
            "weather.rain.above",
            // animals talking all the time: quiet, like nobody is there
            "entity.cow.ambient", "entity.pig.ambient", "entity.sheep.ambient", "entity.chicken.ambient", "entity.chicken.egg",
            "entity.horse.ambient", "entity.horse.breathe", "entity.wolf.ambient", "entity.wolf.pant", "entity.villager.ambient",
            "entity.generic.swim", "entity.player.swim", "entity.player.splash", "block.water.ambient", "block.lava.ambient",
            "entity.item_frame.", "block.note_block.", "entity.minecart.inside", "block.fire.ambient"
    };

    static boolean isModern(String path) {
        for (String m : MODERN) {
            if (path.startsWith(m)) return true;
        }
        return false;
    }

    private ClassicSounds() {}
}
