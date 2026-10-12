# iOS image strategy (C-23 / Stage 2 R4)

## Decision

**iOS does not use Coil / Coil Multiplatform in Stage 3.**  
`isCoilImageLoaderFactoryAvailable()` stays `false` on Kotlin/Native — that is intentional, not an unfinished stub.

Images load through a **native SwiftUI path**:

| Piece | Role |
|-------|------|
| `MdImageCacheConfig` | Mirrors C-18 `ImageCacheConfig.Default` (128 MiB disk, 25% memory, dir `image_cache`) |
| `MdImageLoader` | `URLSession` + shared `URLCache` |
| `MdRemoteImage` | SwiftUI view for shells / future feature UI |

No Compose Multiplatform / Skia image stack on iOS.

## Why not Coil MP here

- Stage 3 shells only need placeholder remote/local thumbs.
- Avoids pulling Coil KMP into the iOS framework for a thin shell.
- Cache **limits** stay aligned with Android Coil via the same numeric defaults as C-18.

## Later (optional)

If product needs Coil parity (GIF, complex transforms), Stage 4+ may add Coil KMP and flip `isCoilImageLoaderFactoryAvailable()` — until then the Swift path is the documented iOS factory.
