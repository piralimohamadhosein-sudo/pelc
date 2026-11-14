package ir.pelc.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.pelc.core.export.ExportOrchestrator
import ir.pelc.core.render.PelcRenderer
import ir.pelc.core.shaping.Shaper
import ir.pelc.core.shaping.TextLayouter
import ir.pelc.core.shaping.VariantDetector
import ir.pelc.feature.fonts.FontRepository
import javax.inject.Singleton

/**
 * The shaping/render/export engines are plain Kotlin classes (no framework
 * annotations in core modules); the DI graph wires them here.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideShaper(): Shaper = Shaper()

    @Provides
    @Singleton
    fun provideTextLayouter(shaper: Shaper, fonts: FontRepository): TextLayouter =
        TextLayouter(shaper, fonts)

    @Provides
    @Singleton
    fun provideRenderer(layouter: TextLayouter): PelcRenderer =
        PelcRenderer(layouter)

    @Provides
    @Singleton
    fun provideVariantDetector(shaper: Shaper): VariantDetector =
        VariantDetector(shaper)

    @Provides
    @Singleton
    fun provideExportOrchestrator(
        renderer: PelcRenderer,
        layouter: TextLayouter,
        fonts: FontRepository,
    ): ExportOrchestrator = ExportOrchestrator(renderer, layouter, fonts)
}
