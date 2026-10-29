#!/usr/bin/env python3
"""Link the local Vite build into the install-time asset pack before Gradle."""
import os
import shutil
from pathlib import Path

root = Path(__file__).resolve().parent.parent
source = root / 'dist'
target = root / 'android-offline/webassets/src/main/assets/web'
if not (source / 'index.html').is_file():
    raise SystemExit('First run: ATLAS_BASE=/assets/ npm run build')
expected = {f.relative_to(source) for f in source.rglob('*') if f.is_file()}
existing = {f.relative_to(target) for f in target.rglob('*') if f.is_file()} if target.exists() else set()
stale = existing - expected
if stale:
    raise SystemExit(f'{len(stale)} stale files in {target}; inspect and clear generated web/ before rebuilding')
for item in source.rglob('*'):
    if item.is_symlink():
        raise SystemExit(f'Symlinks are not permitted in dist: {item}')
    destination = target / item.relative_to(source)
    if item.is_dir():
        destination.mkdir(parents=True, exist_ok=True)
    elif item.is_file():
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            if destination.stat().st_size == item.stat().st_size and destination.stat().st_mtime_ns == item.stat().st_mtime_ns:
                continue
            destination.unlink()
        try:
            os.link(item, destination)
        except OSError:
            shutil.copy2(item, destination)
print(f'Prepared {sum(1 for f in source.rglob("*") if f.is_file())} offline assets in {target}')
