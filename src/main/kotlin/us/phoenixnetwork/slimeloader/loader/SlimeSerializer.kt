package us.phoenixnetwork.slimeloader.loader


import com.github.luben.zstd.Zstd
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.Section
import net.minestom.server.instance.palette.Palette
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTString
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*
import kotlin.collections.HashMap


internal class SlimeSerializer {

    /**
     * Serializes the slime world feeding the raw data into the provided outputDataStream
     *
     * @param outputDataStream DataOutputStream that serialized data will be fed to
     * @param instance Instance that will be serialized, will be used primary for "extra" data
     * @param chunks List of chunks that will be serialized
     */
    fun serialize(
        outputDataStream: DataOutputStream,
        instance: Instance,
        chunks: List<Chunk>
    ) {
        // Filtering the provided chunks
        val filteredChunks = chunks
            .filter { !it.isReadOnly } // Removing chunks that are reaed only
            .map { DynamicChunk(it.instance, it.chunkX, it.chunkZ) }
            .filter {
                ((((it.getPrivateProperty("sections") as? Array<Section>)
                    ?.getPrivateProperty("blockPalette") as? Palette)
                    ?.getPrivateProperty("values") as? LongArray)?.size != 0)
            }

        // Writing header info
        outputDataStream.writeShort(0xB10B)
        outputDataStream.writeByte(0x09)
        outputDataStream.writeByte(0x07)

        val chunkMinX = filteredChunks.minOf { it.chunkX }
        val chunkMaxX = filteredChunks.maxOf { it.chunkX }
        val chunkMinZ = filteredChunks.minOf { it.chunkZ }
        val chunkMaxZ = filteredChunks.maxOf { it.chunkZ }

        val width = chunkMaxX - chunkMinX + 1
        val depth = chunkMaxZ - chunkMinZ + 1

        // Writing the chunk sizing
        outputDataStream.writeShort(chunkMinX)
        outputDataStream.writeShort(chunkMinZ)
        outputDataStream.writeShort(width)
        outputDataStream.writeShort(depth)

        // Calculating the chunk mask & writing it
        val chunkMaskBitSet = BitSet(width * depth)
        for (chunk in filteredChunks) {
            val bitsetIndex = (chunk.chunkZ - chunkMinZ) * width + (chunk.chunkX - chunkMinX)
            chunkMaskBitSet[bitsetIndex] = true
        }

        outputDataStream.write(chunkMaskBitSet.toByteArray())

        // Serializing the chunk data
        val chunkOutputStream = ByteArrayOutputStream()
        for (chunk in filteredChunks) chunkOutputStream.write(serializeChunk(chunk))
        val chunkData = chunkOutputStream.toByteArray()

        // Serializing the tile entities
        val tileEntityOutputStream = ByteArrayOutputStream()
        for (chunk in filteredChunks) tileEntityOutputStream.write(serializeTileEntities(chunk))
        val tileEntityData = tileEntityOutputStream.toByteArray()

        // Writing the data to the output stream, compressed with their size prefixed
        writeCompressedDataSized(outputDataStream, chunkData)
        writeCompressedDataSized(outputDataStream, tileEntityData)
        outputDataStream.writeBoolean(false)
        writeCompressedDataSized(outputDataStream, ByteArray(0)) // TODO: Serialize entities
        writeCompressedDataSized(outputDataStream, ByteArray(0)) // TODO: Serialize extra
        writeCompressedDataSized(outputDataStream, ByteArray(0)) // TODO: Serialize map data
    }

    private fun serializeChunk(chunk: Chunk): ByteArray {
        val chunkOutputStream = ByteArrayOutputStream()
        val chunkDataOutputStream = DataOutputStream(chunkOutputStream)

        chunkDataOutputStream.writeInt(0) // TODO: Heightmaps
        chunkDataOutputStream.writeInt(0) // TODO: Biomes

        val chunkX = chunk.chunkX
        val chunkZ = chunk.chunkZ

        // Determining the bit set for the chunk mask
        val sectionsBitSet = BitSet()
        var sections = (chunk.getPrivateProperty("sections") as Array<Section>)
        var map: HashMap<Int, Section> = HashMap();

        var int = 0;
        for (section in sections) {
            map[int] = section
            int++;
        }

        for ((index, section) in map) {
            val palette: Palette = (section.getPrivateProperty("blockPalette") as Palette)
            if (palette.data().isEmpty()) continue

            // Setting the bit set to true
            sectionsBitSet[index] = true
        }
        chunkDataOutputStream.write(sectionsBitSet.toByteArray())

        // Serializing all chunk sections
        for ((index, section) in map) {
            // Serializing the section data and writing it
            val serializedChunkSection = serializeChunkSection(chunk, index)
            chunkDataOutputStream.write(serializedChunkSection)
        }

        return chunkOutputStream.toByteArray()
    }

    private fun serializeChunkSection(chunk: Chunk, section: Int): ByteArray {
        val chunkSectionOutputStream = ByteArrayOutputStream()
        val chunkSectionDataOutputStream = DataOutputStream(chunkSectionOutputStream)

        val yOffset = section * 16
        val chunkSection = chunk.getSection(section)

        // Writing the block light of the section
        chunkSectionDataOutputStream.writeBoolean(true)
        chunkSectionDataOutputStream.write(chunkSection.blockLight)

        // Writing the palette data
        val paletteBlocks = ((chunkSection.getPrivateProperty("blockPalette") as Palette).getPrivateProperty("longs") as LongArray)
        for (i in paletteBlocks) chunkSectionDataOutputStream.writeLong(i)

        // Writing the block data
        val paletteList = mutableListOf(NBTCompound().set("Name", NBTString("minecraft:air")))

        for (x in 0 until 16) {
            for (y in yOffset until yOffset + 16) {
                for (z in 0 until 16) {
                    val block = chunk.getBlock(x, y, z)
                }
            }
        }

        return chunkSectionOutputStream.toByteArray()
    }

    private fun serializeTileEntities(chunk: Chunk): ByteArray {
        return ByteArray(0)
    }

    private fun writeCompressedDataSized(
        dataOutputStream: DataOutputStream,
        uncompressedData: ByteArray
    ) {
        val compressedData = Zstd.compress(uncompressedData)
        dataOutputStream.writeInt(compressedData.size)
        dataOutputStream.writeInt(uncompressedData.size)
        dataOutputStream.write(compressedData)
    }

    private fun <T : Any> T.getPrivateProperty(variableName: String): Any? {
        return javaClass.getDeclaredField(variableName).let { field ->
            field.isAccessible = true
            return@let field.get(this)
        }
    }

}
