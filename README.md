# KMapGen2

[MapGen2](https://github.com/amitp/mapgen2), just in Kotlin and a little reorganized.

Some classes like Edges have been converted from Arrays of Structs to Structs of Arrays for better GC performance and lower memory overhead.

This project uses [RemsEngine](https://github.com/AntonioNoack/RemsEngine),
but effectively just [JOML](https://github.com/JOML-CI/JOML), and [Primitive Collections](https://github.com/Speiger/Primitive-Collections) from Speiger.

The packages have been named after the original author to honor them.
The project is split into the following parts:

- graphbuilder: generates a list of cells, edges and their connections to each other
- islandshapes: given a normalized x/z coordinate, decide whether the position should be in water or on land
- pointselector: for VoronoiGraphBuilder, decides how the cell centers are distributed
- structures: data structures for storing the map, and helpers
- 'main': how you primarily interface with this library