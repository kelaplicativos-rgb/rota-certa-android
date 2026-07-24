val androidExtension134 = extensions.getByName("android")
val defaultConfig134 = androidExtension134.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension134)
defaultConfig134.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig134, "0.1.134")
