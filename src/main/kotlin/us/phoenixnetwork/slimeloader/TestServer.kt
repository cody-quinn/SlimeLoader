package us.phoenixnetwork.slimeloader

import us.phoenixnetwork.slimeloader.source.FileSlimeSource
import us.phoenixnetwork.slimeloader.source.SlimeSource
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import us.phoenixnetwork.slimeloader.loader.SlimeLoader
import java.io.File
import kotlin.system.measureTimeMillis

val slimeDimension: DimensionType = DimensionType
    .builder(NamespaceID.from("slime:testing"))
    .ambientLight(1.0f)
    .build()

fun main() {
    val server = MinecraftServer.init()

    val dimensionTypeManager = MinecraftServer.getDimensionTypeManager()
    dimensionTypeManager.addDimension(slimeDimension)

    val instanceManager = MinecraftServer.getInstanceManager()
    val instanceContainer = instanceManager.createInstanceContainer(slimeDimension)

    val slimeLoader: IChunkLoader

    val file = File(System.getenv("TESTING_SLIME_FILE"))
    val slimeSource: SlimeSource = FileSlimeSource(file)

    val timeToLoad = measureTimeMillis { slimeLoader = SlimeLoader(instanceContainer, slimeSource) }

    println("Took ${timeToLoad}ms to load map.")

    instanceContainer.chunkLoader = slimeLoader

    val globalEventHandler = MinecraftServer.getGlobalEventHandler()
    globalEventHandler.addListener(PlayerLoginEvent::class.java) {
        val player = it.player
        it.setSpawningInstance(instanceContainer)
        player.respawnPoint = Pos(0.0, 80.0, 0.0)
        player.gameMode = GameMode.CREATIVE
        player.isAllowFlying = true
    }

    server.start("0.0.0.0", 25565)
}
