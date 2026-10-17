from pathlib import Path
import re
import struct
import zipfile


ARTIFACT_GLOB = "coreai-*.jar"
PLUGIN_PACKAGE = "be/stealingdapenta/coreai/"
RELOCATED_ROOT = PLUGIN_PACKAGE + "internal/"
BYTECODE_MAJOR = 65
FORBIDDEN_PREFIXES = (
    "org/bukkit/",
    "io/papermc/",
    "com/destroystokyo/paper/",
    "net/minecraft/",
    "net/kyori/",
    "com/squareup/",
    "okhttp3/",
    "okio/",
    "kotlin/",
    "org/jetbrains/",
    "org/intellij/",
    "org/jspecify/",
)
REQUIRED_ENTRIES = (
    "plugin.yml",
    "config.yml",
    "META-INF/THIRD_PARTY_NOTICES.txt",
    "META-INF/LICENSE-APACHE-2.0.txt",
    "META-INF/LICENSE-MIT.txt",
)
RELOCATED_CLASSES = (
    RELOCATED_ROOT + "kyori/adventure/text/Component.class",
    RELOCATED_ROOT + "kyori/adventure/text/serializer/plain/PlainTextComponentSerializer.class",
    RELOCATED_ROOT + "kyori/adventure/text/serializer/legacy/LegacyComponentSerializer.class",
    RELOCATED_ROOT + "okhttp3/OkHttpClient.class",
    RELOCATED_ROOT + "moshi/Moshi.class",
    RELOCATED_ROOT + "jspecify/annotations/Nullable.class",
)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


artifacts = [
    path
    for path in Path("target").glob(ARTIFACT_GLOB)
    if not path.name.startswith("original-")
    and not path.stem.endswith(("-sources", "-javadoc", "-tests"))
]
require(len(artifacts) == 1, f"Expected one deployable JAR, found {artifacts}")
artifact = artifacts[0]
with zipfile.ZipFile(artifact) as jar:
    entries = set(jar.namelist())
    for entry in REQUIRED_ENTRIES:
        require(entry in entries, f"Missing required resource: {entry}")
    for entry in RELOCATED_CLASSES:
        require(entry in entries, f"Missing relocated class: {entry}")

    own_classes = 0
    for entry in entries:
        if not entry.endswith(".class"):
            continue
        require(not entry.endswith("module-info.class"), f"Bundled module descriptor: {entry}")
        require(not entry.startswith(FORBIDDEN_PREFIXES), f"Bundled server or unrelocated class: {entry}")
        if entry.startswith(PLUGIN_PACKAGE) and not entry.startswith(RELOCATED_ROOT):
            own_classes += 1
            major = struct.unpack(">H", jar.read(entry)[6:8])[0]
            require(major == BYTECODE_MAJOR, f"Unexpected bytecode {major}: {entry}")
    require(own_classes > 0, "No plugin classes found")

    metadata = jar.read("plugin.yml").decode("utf-8")
    require(re.search(r"(?m)^api-version:\s*['\"]?26\.3['\"]?\s*$", metadata), "api-version is not 26.3")
    notices = jar.read("META-INF/THIRD_PARTY_NOTICES.txt").decode("utf-8")
    for marker in (
        "com.squareup.okhttp3:okhttp-jvm:5.5.0",
        "com.squareup.moshi:moshi:1.15.2",
        "net.kyori:adventure-api:5.2.0",
        "net.kyori:adventure-text-serializer-legacy:5.2.0",
        "org.junit.jupiter:junit-jupiter:6.1.3",
    ):
        require(marker in notices, f"Missing third-party notice: {marker}")
    require(
        "Apache License" in jar.read("META-INF/LICENSE-APACHE-2.0.txt").decode("utf-8"),
        "Bundled Apache-2.0 license text is invalid",
    )
    require(
        "MIT License" in jar.read("META-INF/LICENSE-MIT.txt").decode("utf-8"),
        "Bundled MIT license text is invalid",
    )

print(f"Verified deployable artifact: {artifact} ({artifact.stat().st_size:,} bytes)")
