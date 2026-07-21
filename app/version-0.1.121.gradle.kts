val androidExtension121 = extensions.getByName("android")
val defaultConfig121 = androidExtension121.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension121)
defaultConfig121.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig121, "0.1.121")
