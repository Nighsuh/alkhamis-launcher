# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# R8 will otherwise inline this into Config.load and drop the class. Keep it so a failed
# first-launch theme copy still names BundledTheme in a stack trace.
-keep class com.gathin.tvhome.BundledTheme { *; }
