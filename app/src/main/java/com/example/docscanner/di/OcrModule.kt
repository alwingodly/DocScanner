// di/OcrModule.kt
package com.example.docscanner.di

import com.example.docscanner.data.ocr.extractor.AadhaarBackExtractor
import com.example.docscanner.data.ocr.extractor.AadhaarFrontExtractor
import com.example.docscanner.data.ocr.extractor.DlExtractor
import com.example.docscanner.data.ocr.extractor.DocumentExtractor
import com.example.docscanner.data.ocr.extractor.PanExtractor
import com.example.docscanner.data.ocr.extractor.PassportExtractor
import com.example.docscanner.data.ocr.extractor.VoterIdExtractor
import com.example.docscanner.data.ocr.ner.NerExtractor
import com.example.docscanner.data.ocr.ner.RegexNerExtractor
import com.example.docscanner.data.ocr.ner.TfLiteNerExtractor
import com.example.docscanner.domain.model.DocClassType
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TfLiteNer
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class RegexNer

/** Binds each extractor into a Map<DocClassType, DocumentExtractor>. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {

    @Binds @IntoMap @DocClassTypeKey(DocClassType.AADHAAR_FRONT)
    abstract fun aadhaarFront(e: AadhaarFrontExtractor): DocumentExtractor

    @Binds @IntoMap @DocClassTypeKey(DocClassType.AADHAAR_BACK)
    abstract fun aadhaarBack(e: AadhaarBackExtractor): DocumentExtractor

    @Binds @IntoMap @DocClassTypeKey(DocClassType.PAN)
    abstract fun pan(e: PanExtractor): DocumentExtractor

    @Binds @IntoMap @DocClassTypeKey(DocClassType.PASSPORT)
    abstract fun passport(e: PassportExtractor): DocumentExtractor

    @Binds @IntoMap @DocClassTypeKey(DocClassType.VOTER_ID)
    abstract fun voter(e: VoterIdExtractor): DocumentExtractor

    @Binds @IntoMap @DocClassTypeKey(DocClassType.OTHER)
    abstract fun other(e: DlExtractor): DocumentExtractor
}

@Module
@InstallIn(SingletonComponent::class)
object NerModule {
    /**
     * Default NER binding. Swap this to TfLiteNerExtractor once the model
     * file is shipped in assets/ner/. Keep RegexNerExtractor as a runtime
     * fallback inside TfLiteNerExtractor's init-failure path.
     */
    @Provides
    @Singleton
    fun provideNerExtractor(
        regex: RegexNerExtractor,
        // tflite: TfLiteNerExtractor, // uncomment when model is ready
    ): NerExtractor = regex
}

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class DocClassTypeKey(val value: DocClassType)

// Required import at top: import dagger.MapKey