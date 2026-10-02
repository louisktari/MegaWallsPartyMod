# Source and dependency attribution

The original PartyMod source was supplied as `PartyMod-master.zip`, using
`fr.alexdoru.partymod` and crediting Alexdoru. That namespace and attribution remain.
The input ZIP contains no project licence file. This local modified build does
not assign a new licence to the original author's work or constitute publication.

OneConfig belongs to Polyfrost. The source archive pins the locally cached V0
API `0.2.2-alpha228` and LaunchWrapper loader `1.0.0-beta17` in `libs/` for the
legacy Java 8 build. The production PartyMod JAR contains only the unmodified
OneConfig loader stage-0 classes; OneConfig's implementation is loaded separately.

- [OneConfig legacy source](https://github.com/Polyfrost/OneConfig/tree/legacy)
- [OneConfig loader source](https://github.com/Polyfrost/OneConfigLoader)
- [OneConfig documentation](https://docs.polyfrost.org/oneconfig/)

Consult the upstream licence and additional terms for redistribution. Copies of
the upstream licence texts (legacy OneConfig and main loader branches) are supplied in `licenses/`. These notices do not
relicense third-party dependencies. Forge/MCP, Gradle and JUnit are used by the
build/test toolchain; the wrapper JAR and scripts originate from the supplied
project. Minecraft, Forge runtime libraries and the full OneConfig runtime used
by the isolated client test are not included in the source archive or mod JAR.
