package gg.astromc.slimeloader.loader

import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.world.biomes.Biome
import org.jglrxavpok.hephaistos.mca.unpack
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTString
import gg.astromc.slimeloader.helpers.ChunkHelpers.getChunkIndex
import gg.astromc.slimeloader.helpers.NBTHelpers.readNBTTag
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.*
import kotlin.math.floor

internal class SlimeDeserializer(
    private val instance: Instance,
    private val chunkData: ByteArray,
    private val tileEntityData: ByteArray,
    private val depth: Int,
    private val width: Int,
    private val chunkMinX: Short,
    private val chunkMinZ: Short,
    private val chunkMask: BitSet
) {

    fun readChunks(): Map<Long, Chunk> {
        val chunkDataByteStream = ByteArrayInputStream(chunkData)
        val chunkDataStream = DataInputStream(chunkDataByteStream)
        
        val tempChunks = mutableMapOf<Long, Chunk>()
        for (chunkZ in 0 until depth) {
            for (chunkX in 0 until width) {
                val bitsetIndex = chunkZ * width + chunkX

                val realChunkX = chunkX + chunkMinX
                val realChunkZ = chunkZ + chunkMinZ

                if (chunkMask[bitsetIndex]) {
                    val chunk = readChunk(chunkDataStream, instance, realChunkX, realChunkZ)
                    val chunkIndex = getChunkIndex(realChunkX, realChunkZ)
                    tempChunks[chunkIndex] = chunk
                }
            }
        }

        loadTileEntities(tempChunks)
        return tempChunks
    }

    private fun readChunk(
        chunkDataStream: DataInputStream,
        instance: Instance,
        chunkX: Int,
        chunkZ: Int
    ): Chunk {
        // Getting the heightmap
        val heightMapSize = chunkDataStream.readInt()
        val heightMap = ByteArray(heightMapSize)
        chunkDataStream.read(heightMap)
        val heightMapNBT = readNBTTag(heightMap) ?: NBTCompound()

        // Getting the biomes
        val biomesLength = chunkDataStream.readInt()
        val biomes = IntArray(biomesLength)
        for (i in biomes.indices) {
            biomes[i] = chunkDataStream.readInt()
        }

        // Creating the chunk
        // TODO: Read biomes lol
        val chunk = DynamicChunk(instance, arrayOfNulls<Biome>(1024).apply { fill(Biome.PLAINS) }, chunkX, chunkZ)
        readChunkSections(chunkDataStream, chunk)

        return chunk
    }

    private fun readChunkSections(chunkDataStream: DataInputStream, chunk: Chunk) {
        val chunkSectionsByteArray = ByteArray(2)
        chunkDataStream.read(chunkSectionsByteArray)
        val chunkSectionsMask: BitSet = BitSet.valueOf(chunkSectionsByteArray)

        for (chunkSection in 0..15) {
            val yOffset = chunkSection * 16

            if (chunkSectionsMask[chunkSection]) {
                // Light Data
                val hasBlockLight = chunkDataStream.readBoolean()
                val blockLight = if (hasBlockLight) { chunkDataStream.readNBytes(2048) } else null

                // Palette Data
                val paletteLength = chunkDataStream.readInt()
                val paletteList = mutableListOf<NBTCompound>()

                for (i in 0 until paletteLength) {
                    val nbtLength = chunkDataStream.readInt()
                    val nbtRaw = chunkDataStream.readNBytes(nbtLength)
                    val nbtCompound = readNBTTag<NBTCompound>(nbtRaw) ?: continue
                    paletteList.add(nbtCompound)
                }

                // Block States
                val blockStatesLength = chunkDataStream.readInt()
                val compactedBlockStates = LongArray(blockStatesLength)

                for (i in 0 until blockStatesLength) {
                    compactedBlockStates[i] = chunkDataStream.readLong()
                }

                val sizeInBits = compactedBlockStates.size*64 / 4096
                val blockStates = unpack(compactedBlockStates, sizeInBits).sliceArray(0 until 4096)

                for (y in 0 until 16) {
                    for (z in 0 until 16) {
                        for (x in 0 until 16) {
                            val pos = y * 16 * 16 + z * 16 + x
                            val value = paletteList[blockStates[pos]]
                            val block = getBlockFromCompound(value) ?: continue
                            chunk.setBlock(x, yOffset + y, z, block)
                        }
                    }
                }

                // Skylight
                val hasSkyLight = chunkDataStream.readBoolean()
                val skyLight = if (hasSkyLight) { chunkDataStream.readNBytes(2048) } else null

                chunk.getSection(chunkSection).skyLight = skyLight
                chunk.getSection(chunkSection).blockLight = blockLight
            }
        }
    }

    private fun getBlockFromCompound(compound: NBTCompound): Block? {
        val name = compound.getString("Name") ?: return null
        if (name == "minecraft:air") return null
        val properties = compound.getCompound("Properties") ?: NBTCompound()

        val newProps = mutableMapOf<String, String>()
        for ((key, rawValue) in properties) {
            newProps[key] = (rawValue as NBTString).value
        }

        return Block.fromNamespaceId(name)?.withProperties(newProps)
    }

    private fun loadTileEntities(chunks: Map<Long, Chunk>) {
        val tileEntitiesCompound = readNBTTag<NBTCompound>(tileEntityData) ?: return
        val tileEntities = tileEntitiesCompound.getList<NBTCompound>("tiles") ?: return
        for (tileEntity in tileEntities) {
            val x = tileEntity.getInt("x") ?: continue
            val y = tileEntity.getInt("y") ?: continue
            val z = tileEntity.getInt("z") ?: continue

            val localX = x % 16 + (if(x < 0) 16 else 0)
            val localZ = z % 16 + (if(z < 0) 16 else 0)

            val chunkX = floor(x / 16.0).toInt()
            val chunkZ = floor(z / 16.0).toInt()

            val chunk = chunks[getChunkIndex(chunkX, chunkZ)] ?: continue
            var block = chunk.getBlock(localX, y, localZ)

            val id: String? = tileEntity.getString("id")
            if (id != null) {
                val blockHandler = MinecraftServer.getBlockManager().getHandler(id)
                if (blockHandler != null) {
                    block = block.withHandler(blockHandler)
                }
            }

            tileEntity.removeTag("x").removeTag("y").removeTag("z")
                .removeTag("id").removeTag("keepPacked")

            if (tileEntity.size > 0) {
                block = block.withNbt(tileEntity)
            }

            chunk.setBlock(localX, y, localZ, block)
        }
    }

}