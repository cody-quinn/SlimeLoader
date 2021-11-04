# 🗺️ SlimeLoader

Slime loader is a map loader & saver for the file format Slime as specified [here](https://github.com/PhoenixNetwork/SlimeLoader/blob/master/SLIME_FORMAT.txt) implemented in Minestom.

Features:
- [x] World loading
  - [x] Blocks
  - [x] TileEntities
  - [x] Entities   (Incomplete)
- [ ] World saving
  - [ ] Blocks
  - [ ] TileEntities
  - [ ] Entities
- [ ] Async

## Installation

Add the following to your `build.gradle.kts`

```kotlin
repositories { 
  maven("https://repo.phoenixnetwork.us/repository/maven-public/")
}

dependencies { 
  implementation("us.phoenixnetwork:SlimeLoader:1.0.0-SNAPSHOT")
}
```

## Usage

The library is quite simple to use. If you need to get your slime world from somewhere else (ex. AWS S3) you can implement the `SlimeSource` interface. 

```kotlin
val instanceManager = MinecraftServer.getInstanceManager()
val instanceContainer = instanceManager.createInstanceContainer()

val file = File("Slime file goes here")
val slimeSource: SlimeSource = FileSlimeSource(file)
val slimeLoader: IChunkLoader = SlimeLoader(instanceContainer, slimeSource)

instanceContainer.chunkLoader = slimeLoader
```

## License

SlimeLoader is licensed under the GNU GPL-3 license

###### Written by [Cody](https://github.com/CatDevz) for Phoenix Network
