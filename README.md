# KMapGen2

[MapGen2](https://github.com/amitp/mapgen2), just in Kotlin and a little reorganized.

Some classes like Edges have been converted from Arrays of Structs to Structs of Arrays for better GC performance and lower memory overhead.

This project uses [RemsEngine](https://github.com/AntonioNoack/RemsEngine),
but effectively just [JOML](https://github.com/JOML-CI/JOML), and [Primitive Collections](https://github.com/Speiger/Primitive-Collections) from Speiger.
