package com.midnightdancer.core.designsystem

/**
 * Typographic scale names + sizes (sp). Font file binding is platform work (C-22 / C-23).
 */
enum class TypeWeight {
    Regular,
    Medium,
    SemiBold,
    Bold,
}

/**
 * One named step on the type scale.
 *
 * @property name token id for platform theme maps
 * @property sizeSp font size in scalable pixels
 * @property lineHeightSp preferred line height in sp
 * @property weight nominal weight; platforms map to FontWeight / UIFont.Weight
 */
data class TypeScaleToken(
    val name: String,
    val sizeSp: Float,
    val lineHeightSp: Float,
    val weight: TypeWeight,
)

object TypographyTokens {
    /** Platform display face token id (map to bundled or system display font). */
    const val FontFamilyDisplay: String = "MidnightDisplay"

    /** Platform body face token id (Flutter used Inter; platforms may use system). */
    const val FontFamilyBody: String = "MidnightBody"

    val DisplayLarge =
        TypeScaleToken(
            name = "display_large",
            sizeSp = 34f,
            lineHeightSp = 40f,
            weight = TypeWeight.Bold,
        )

    val TitleLarge =
        TypeScaleToken(
            name = "title_large",
            sizeSp = 22f,
            lineHeightSp = 28f,
            weight = TypeWeight.SemiBold,
        )

    val TitleMedium =
        TypeScaleToken(
            name = "title_medium",
            sizeSp = 18f,
            lineHeightSp = 24f,
            weight = TypeWeight.SemiBold,
        )

    val BodyLarge =
        TypeScaleToken(
            name = "body_large",
            sizeSp = 16f,
            lineHeightSp = 24f,
            weight = TypeWeight.Regular,
        )

    val BodyMedium =
        TypeScaleToken(
            name = "body_medium",
            sizeSp = 14f,
            lineHeightSp = 20f,
            weight = TypeWeight.Regular,
        )

    val LabelSmall =
        TypeScaleToken(
            name = "label_small",
            sizeSp = 12f,
            lineHeightSp = 16f,
            weight = TypeWeight.Medium,
        )

    val TabLabel =
        TypeScaleToken(
            name = "tab_label",
            sizeSp = 11f,
            lineHeightSp = 14f,
            weight = TypeWeight.Medium,
        )

    /** All scale steps for iteration / smoke. */
    val All: List<TypeScaleToken> =
        listOf(
            DisplayLarge,
            TitleLarge,
            TitleMedium,
            BodyLarge,
            BodyMedium,
            LabelSmall,
            TabLabel,
        )
}
