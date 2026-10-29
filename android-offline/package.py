from pathlib import Path
from zipfile import ZipFile, ZIP_STORED, ZIP_DEFLATED
from collections import Counter
root = Path(__file__).resolve().parent.parent
build = root / 'android-offline/build/output'
dex = (root / 'android-offline/build/dex/classes.dex').read_bytes()
# Compiling only MainActivity.class silently drops the anonymous WebViewClient
# (MainActivity$1.class): the APK then launches without offline request handling.
for symbol in (b'MainActivity$1', b'shouldInterceptRequest', b'shouldOverrideUrlLoading'):
    if symbol not in dex:
        raise SystemExit(f'Incomplete Android dex: missing {symbol.decode()}')
files = sorted((root / 'dist').rglob('*'))
files = [f for f in files if f.is_file()]
with ZipFile(build / 'base.apk') as original, ZipFile(build / 'unsigned.apk', 'w', allowZip64=True) as target:
    for item in original.infolist():
        target.writestr(item, original.read(item.filename))
    target.write(root / 'android-offline/build/dex/classes.dex', 'classes.dex', compress_type=ZIP_DEFLATED)
    for i, f in enumerate(files, 1):
        target.write(f, 'assets/web/' + f.relative_to(root / 'dist').as_posix(), compress_type=ZIP_STORED)
        if i % 10000 == 0: print(f'Packed {i}/{len(files)} assets', flush=True)
print('Packed', len(files), 'assets;', (build / 'unsigned.apk').stat().st_size, 'bytes', flush=True)
