import 'package:archive/archive_io.dart';

/// Reads ZIP Central Directory file headers only (no local headers, no file bytes).
///
/// Logic mirrors [ZipDirectory.read] from package `archive` but **stops before**
/// `readLocalFileHeader`, so no compressed payload is loaded.
/// See: `archive` package `ZipDirectory` (brendan-duncan/archive).
///
/// If [maxCentralDirectoryBytes] is set and the directory is larger, throws
/// [ArchiveException] (DoS / zip bomb on directory metadata).
List<ZipFileHeader> readZipCentralDirectoryHeadersOnly(
  InputStreamBase input, {
  int? maxCentralDirectoryBytes,
}) {
  const eocdLocatorSignature = 0x06054b50;
  const zip64EocdLocatorSignature = 0x07064b50;
  const zip64EocdLocatorSize = 20;
  const zip64EocdSignature = 0x06054b50;

  var filePosition = _findEocdrSignature(input, eocdLocatorSignature);
  input.position = filePosition;
  input.readUint32(); // EOCD signature
  var numberOfThisDisk = input.readUint16();
  final diskWithTheStartOfTheCentralDirectory = input.readUint16();
  var totalCentralDirectoryEntriesOnThisDisk = input.readUint16();
  input.readUint16(); // total central directory entries (redundant with parsed headers)
  var centralDirectorySize = input.readUint32();
  var centralDirectoryOffset = input.readUint32();

  final commentLen = input.readUint16();
  if (commentLen > 0) {
    input.readString(size: commentLen, utf8: false);
  }

  if (centralDirectoryOffset == 0xffffffff ||
      centralDirectorySize == 0xffffffff ||
      totalCentralDirectoryEntriesOnThisDisk == 0xffff ||
      numberOfThisDisk == 0xffff) {
    final zip64 = _readZip64Eocd(
      input,
      filePosition,
      zip64EocdLocatorSignature,
      zip64EocdLocatorSize,
      zip64EocdSignature,
    );
    if (zip64 != null) {
      numberOfThisDisk = zip64.numberOfThisDisk;
      totalCentralDirectoryEntriesOnThisDisk = zip64.entriesOnDisk;
      centralDirectorySize = zip64.centralDirectorySize;
      centralDirectoryOffset = zip64.centralDirectoryOffset;
    }
  }

  if (diskWithTheStartOfTheCentralDirectory != 0 || numberOfThisDisk != 0) {
    throw ArchiveException('Multi-disk ZIP archives are not supported');
  }

  if (maxCentralDirectoryBytes != null &&
      centralDirectorySize > maxCentralDirectoryBytes) {
    throw ArchiveException('Central directory too large');
  }

  final dirContent = input.subset(centralDirectoryOffset, centralDirectorySize);
  final dirStream = InputStream(dirContent.toUint8List());
  final headers = <ZipFileHeader>[];
  while (!dirStream.isEOS) {
    final fileSig = dirStream.readUint32();
    if (fileSig != ZipFileHeader.SIGNATURE) {
      break;
    }
    headers.add(ZipFileHeader(dirStream));
  }

  return headers;
}

int _findEocdrSignature(InputStreamBase input, int eocdLocatorSignature) {
  final pos = input.position;
  final length = input.length;
  for (var ip = length - 5; ip >= 0; --ip) {
    input.position = ip;
    final sig = input.readUint32();
    if (sig == eocdLocatorSignature) {
      input.position = pos;
      return ip;
    }
  }
  throw ArchiveException('Could not find End of Central Directory Record');
}

class _Zip64Eocd {
  _Zip64Eocd({
    required this.numberOfThisDisk,
    required this.entriesOnDisk,
    required this.entriesTotal,
    required this.centralDirectorySize,
    required this.centralDirectoryOffset,
  });

  final int numberOfThisDisk;
  final int entriesOnDisk;
  final int entriesTotal;
  final int centralDirectorySize;
  final int centralDirectoryOffset;
}

_Zip64Eocd? _readZip64Eocd(
  InputStreamBase input,
  int filePosition,
  int zip64EocdLocatorSignature,
  int zip64EocdLocatorSize,
  int zip64EocdSignature,
) {
  final ip = input.position;
  final locPos = filePosition - zip64EocdLocatorSize;
  if (locPos < 0) {
    return null;
  }
  final zip64 = input.subset(locPos, zip64EocdLocatorSize);

  var sig = zip64.readUint32();
  if (sig != zip64EocdLocatorSignature) {
    input.position = ip;
    return null;
  }

  zip64.readUint32(); // start disk of zip64 EOCD
  final zip64DirOffset = zip64.readUint64();
  zip64.readUint32(); // total disks

  input.position = zip64DirOffset;

  sig = input.readUint32();
  if (sig != zip64EocdSignature) {
    input.position = ip;
    return null;
  }

  input.readUint64(); // zip64 EOCD size
  input.readUint16(); // version made by
  input.readUint16(); // version needed
  final zip64DiskNumber = input.readUint32();
  input.readUint32(); // disk with start of central directory
  final zip64NumEntriesOnDisk = input.readUint64();
  final zip64NumEntries = input.readUint64();
  final dirSize = input.readUint64();
  final dirOffset = input.readUint64();

  input.position = ip;

  return _Zip64Eocd(
    numberOfThisDisk: zip64DiskNumber,
    entriesOnDisk: zip64NumEntriesOnDisk,
    entriesTotal: zip64NumEntries,
    centralDirectorySize: dirSize,
    centralDirectoryOffset: dirOffset,
  );
}

/// Opens a ZIP file for header-only parsing without loading file contents.
InputFileStream openZipInputStream(String zipPath) => InputFileStream(zipPath);
