val androidExtension122 = extensions.getByName("android")
val defaultConfig122 = androidExtension122.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension122)
defaultConfig122.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig122, "0.1.122")
