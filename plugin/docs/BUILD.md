# Build and test

Use a full JDK 25 for the current IDE SDK. This release targets Java 25.
It does not establish compatibility with older IDEs.

Open `plugin/` as the Gradle project in your IDE.
Run build commands from that directory.
The build can download pinned build tools and the selected IDE SDK.

From the repository root, on Windows:

```powershell
cd plugin
.\gradlew.bat check buildPlugin
```

From the repository root, on Linux or macOS:

```sh
cd plugin
./gradlew check buildPlugin
```

To use an installed IDE SDK, set `CODEXCOMMIT_IDE_HOME` to its installation directory before the build.
On macOS, use the application bundle's `Contents` directory.
Do not put a personal installation path in a tracked file.

The offline helper uses an installed IDE SDK and its JDK. It downloads nothing.
Keep `plugin/` as your working directory for these commands.

Windows:

```powershell
.\tools\build-local.ps1 -IdeHome 'PATH_TO_YOUR_IDE'
```

Linux or macOS:

```sh
sh tools/build-local.sh /path/to/your/IDE
```

The offline helper compiles the source and does the local regression tests.
It puts a new plugin ZIP in `build/local/`.
It does not install the plugin or launch the IDE.
Build tests do not replace native UI acceptance tests.
