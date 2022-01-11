package gg.astromc.slimeloader.helpers


import org.jglrxavpok.hephaistos.nbt.CompressedProcesser
import org.jglrxavpok.hephaistos.nbt.NBT
import org.jglrxavpok.hephaistos.nbt.NBTReader
import org.jglrxavpok.hephaistos.nbt.NBTWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object NBTHelpers {

    inline fun <reified T : NBT> readNBTTag(bytes: ByteArray): T?
            = NBTReader(ByteArrayInputStream(bytes), CompressedProcesser.NONE).read() as? T

    inline fun <reified T : NBT> writeNBTTag(tag: T): ByteArray
            = ByteArrayOutputStream().also { NBTWriter(it).writeRaw(tag) }.toByteArray()

}