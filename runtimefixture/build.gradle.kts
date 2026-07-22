plugins {
    id("com.android.application")
}

android {
    namespace = "br.com.mapeiaia.rotacerta.runtimefixture"
    compileSdk = 35

    defaultConfig {
        // Usa um pacote real monitorado apenas no emulador do CI. Assim o teste
        // prova que dois enderecos, sem modelo cadastrado, continuam cinza.
        applicationId = "sinet.startup.indriver"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
