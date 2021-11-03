package us.phoenixnetwork.slimeloader.source

import java.io.*

class FileSlimeSource(
    private val file: File
) : SlimeSource {

    override fun load(): InputStream {
        return FileInputStream(file)
    }

    override fun save(): OutputStream {
        return FileOutputStream(file)
    }

}