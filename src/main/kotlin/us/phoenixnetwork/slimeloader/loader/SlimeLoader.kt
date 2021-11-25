package us.phoenixnetwork.slimeloader.loader

import com.github.luben.zstd.Zstd
import us.phoenixnetwork.slimeloader.UnknownFileTypeException
import us.phoenixnetwork.slimeloader.UnsupportedMinecraftVersionException
import us.phoenixnetwork.slimeloader.UnsupportedSlimeVersionException
import us.phoenixnetwork.slimeloader.source.SlimeSource
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import org.jglrxavpok.hephaistos.nbt.*
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil

class SlimeLoader(
    instance: Instance,
    source: SlimeSource,
    private val readOnly: Boolean = false,
) : IChunkLoader {

    private val depth: Int
    private val width: Int
    private val chunkMinX: Short
    private val chunkMinZ: Short

    private val chunkMask: BitSet

    private var chunks = mutableMapOf<Long, Chunk>()
    private var entities = mutableMapOf<Pos, Entity>()

    init {
        val dataStream = DataInputStream(source.load())

        // Checking some magic numbers
        if (dataStream.readShort() != 0xB10B.toShort()) throw UnknownFileTypeException()
        if (dataStream.readByte() < 0x09.toByte()) throw UnsupportedSlimeVersionException()
        if (dataStream.readByte() < 0x07.toByte()) throw UnsupportedMinecraftVersionException()

        // Loading map size
        chunkMinX = dataStream.readShort()
        chunkMinZ = dataStream.readShort()
        width = dataStream.readUnsignedShort()
        depth = dataStream.readUnsignedShort()

        // Chunks
        val chunkMaskSize = ceil((width * depth) / 8.0).toInt()
        chunkMask = BitSet.valueOf(dataStream.readNBytes(chunkMaskSize))

        // Loading raw data
        val chunkData = loadRawData(dataStream)
        val tileEntitiesData = loadRawData(dataStream)
        val entityData = if (dataStream.readBoolean()) loadRawData(dataStream) else ByteArray(0)
        val extraData = loadRawData(dataStream)

        // Closing the data stream
        dataStream.close()

        // Loading it all
        loadChunks(instance, chunkData, tileEntitiesData)
        loadEntities(entityData)
        loadExtra(instance, extraData)
    }

    private fun loadChunks(instance: Instance, chunkData: ByteArray, tileEntitiesData: ByteArray) {
        val chunkDataStream = DataInputStream(ByteArrayInputStream(chunkData))
        chunks = SlimeDeserializer.readChunks(chunkDataStream, instance, depth, width, chunkMinX, chunkMinZ, chunkMask).toMutableMap()
        SlimeDeserializer.loadTileEntities(tileEntitiesData, chunks)
    }

    private fun loadEntities(entityData: ByteArray) {
        entities = SlimeDeserializer.readEntities(entityData)
    }

    private fun loadExtra(instance: Instance, extraData: ByteArray) {
        val rootTag = readNBTTag<NBTCompound>(extraData)
        instance.setTag(Tag.NBT, rootTag)
    }

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): CompletableFuture<Chunk?> {
        val chunk = chunks[getChunkIndex(chunkX, chunkZ)]

        // Spawning entities
        entities.filterKeys { it.chunkX() == chunkX && it.chunkZ() == chunkZ }
                .forEach { (pos, entity) -> entity.setInstance(instance, pos) }

        return CompletableFuture.completedFuture(chunk)
    }

    override fun saveChunk(chunk: Chunk): CompletableFuture<Void> {
        if (readOnly) return CompletableFuture.completedFuture(null)

        chunks[getChunkIndex(chunk.chunkX, chunk.chunkZ)] = chunk
        //TODO
        return CompletableFuture.completedFuture(null)
    }

    private fun loadRawData(dataStream: DataInputStream): ByteArray {
        val compressedData = ByteArray(dataStream.readInt())
        val uncompressedData = ByteArray(dataStream.readInt())
        dataStream.read(compressedData)
        Zstd.decompress(uncompressedData, compressedData)
        return uncompressedData
    }

}

fun getChunkIndex(x: Int, z: Int): Long = (x.toLong() shl 32) + z

inline fun <reified T : NBT> readNBTTag(bytes: ByteArray): T?
    = NBTReader(ByteArrayInputStream(bytes), false).read() as? T
