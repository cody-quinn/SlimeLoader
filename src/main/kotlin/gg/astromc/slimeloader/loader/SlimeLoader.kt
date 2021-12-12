package gg.astromc.slimeloader.loader

import com.github.luben.zstd.Zstd
import gg.astromc.slimeloader.UnknownFileTypeException
import gg.astromc.slimeloader.UnsupportedMinecraftVersionException
import gg.astromc.slimeloader.UnsupportedSlimeVersionException
import gg.astromc.slimeloader.source.SlimeSource
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import org.jglrxavpok.hephaistos.nbt.*
import gg.astromc.slimeloader.helpers.ChunkHelpers.getChunkIndex
import gg.astromc.slimeloader.helpers.NBTHelpers.readNBTTag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil

class SlimeLoader(
    instance: Instance,
    private val slimeSource: SlimeSource,
    private val readOnly: Boolean = false,
) : IChunkLoader {

    private val depth: Int
    private val width: Int
    private val chunkMinX: Short
    private val chunkMinZ: Short
    private val chunkMask: BitSet

    private var chunks = mutableMapOf<Long, Chunk>()

    init {
        val dataStream = DataInputStream(slimeSource.load())

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
        if (dataStream.readBoolean()) loadRawData(dataStream) else ByteArray(0) // Skipping past entity data
        val extraData = loadRawData(dataStream)

        // Closing the data stream
        dataStream.close()

        // Loading it all
        loadExtra(instance, extraData)
        loadChunks(instance, chunkData, tileEntitiesData)
    }

    private fun loadChunks(instance: Instance, chunkData: ByteArray, tileEntityData: ByteArray) {
        val deserializer = SlimeDeserializer(instance, chunkData, tileEntityData, depth, width, chunkMinX, chunkMinZ, chunkMask)
        chunks = deserializer.readChunks().toMutableMap()
    }

    private fun loadExtra(instance: Instance, extraData: ByteArray) {
        val rootTag = readNBTTag<NBTCompound>(extraData)
        instance.setTag(Tag.NBT("Data"), rootTag)
    }

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): CompletableFuture<Chunk?> {
        val chunk = chunks[getChunkIndex(chunkX, chunkZ)]
        return CompletableFuture.completedFuture(chunk)
    }

    override fun saveChunk(chunk: Chunk): CompletableFuture<Void> {
        if (readOnly) return CompletableFuture.completedFuture(null)
        chunks[getChunkIndex(chunk.chunkX, chunk.chunkZ)] = chunk
        return CompletableFuture.completedFuture(null)
    }

    override fun saveInstance(instance: Instance): CompletableFuture<Void> {
        if (readOnly) return CompletableFuture.completedFuture(null)

        val outputStream = slimeSource.save()
        val dataOutputStream = DataOutputStream(outputStream)

        val serializer = SlimeSerializer()
        serializer.serialize(dataOutputStream, instance, chunks.values.toList())

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
