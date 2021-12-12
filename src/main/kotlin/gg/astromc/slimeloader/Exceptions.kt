package us.phoenixnetwork.slimeloader

sealed class SlimeLoaderException(message: String) : Exception(message)

class UnknownFileTypeException : SlimeLoaderException("File loaded is of an unknown/unsupported type")
class UnsupportedSlimeVersionException : SlimeLoaderException("File loaded is of a known type, however the version of Slime is unsupported.")
class UnsupportedMinecraftVersionException : SlimeLoaderException("File loaded is of a known type, however the MC version is unsupported.")
class CorruptedFileException : SlimeLoaderException("File loaded is corrupted")
