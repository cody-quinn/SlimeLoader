package us.phoenixnetwork.slimeloader.source

import java.io.InputStream
import java.io.OutputStream

interface SlimeSource {

    /**
     * Provides SlimeLoader an input stream to get the map data with
     *
     * @see us.phoenixnetwork.slimeloader.loader.SlimeLoader
     * @return Returns an InputStream containing the map's data
     */
    fun load(): InputStream

    /**
     * Provides SlimeLoader an output stream to feed map data to
     *
     * @see us.phoenixnetwork.slimeloader.loader.SlimeLoader
     * @return Returns an OutputStream
     */
    fun save(): OutputStream

}