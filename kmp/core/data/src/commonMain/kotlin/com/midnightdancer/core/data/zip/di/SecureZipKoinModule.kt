package com.midnightdancer.core.data.zip.di

import com.midnightdancer.core.data.backup.DefaultMergeImport
import com.midnightdancer.core.data.zip.DefaultImportSecureZip
import com.midnightdancer.core.data.zip.DefaultValidateSecureZip
import com.midnightdancer.core.data.zip.SecureZipMetadataReader
import com.midnightdancer.core.data.zip.SecureZipWhitelist
import com.midnightdancer.core.data.zip.ZipArchiveInspector
import com.midnightdancer.core.data.zip.platformZipArchiveInspector
import com.midnightdancer.core.domain.usecase.ImportSecureZip
import com.midnightdancer.core.domain.usecase.MergeImport
import com.midnightdancer.core.domain.usecase.ValidateSecureZip
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * C-15/C-16 secure zip / merge DI.
 *
 * @param metadataReader production: sandbox-extracted metadata only; tests: [com.midnightdancer.core.data.zip.MapSecureZipMetadataReader]
 * @param whitelist **S-12:** bind explicitly per import mode —
 *   [SecureZipWhitelist.DefaultUserSpec] for user packages,
 *   [SecureZipWhitelist.MidnightDancerFullBackup] for full backup.
 *   Default stays fail-closed (no audio / no manifest). Never ship a global "allow all" override in release.
 */
fun secureZipKoinModule(
    metadataReader: SecureZipMetadataReader,
    whitelist: SecureZipWhitelist = SecureZipWhitelist.DefaultUserSpec,
    inspector: ZipArchiveInspector = platformZipArchiveInspector(),
): Module =
    module {
        single<ZipArchiveInspector> { inspector }
        single<MergeImport> { DefaultMergeImport() }
        single<ValidateSecureZip> {
            DefaultValidateSecureZip(inspector = get(), whitelist = whitelist)
        }
        single<ImportSecureZip> {
            DefaultImportSecureZip(
                validate = get(),
                mergeImport = get(),
                metadataReader = metadataReader,
            )
        }
    }