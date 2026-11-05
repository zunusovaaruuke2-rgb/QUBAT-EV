"""Small DEMO-only CI previews, retained when artifact storage is not readable by the workspace."""
from pathlib import Path
import base64
for name in ('charging-two-cars-light.png', 'charging-stop-failure.png'):
    path = Path('app/build/reports/charging-preview') / name
    if path.is_file() and path.stat().st_size < 512_000:
        data = base64.b64encode(path.read_bytes()).decode('ascii')
        print('QUBAT_PNG_BEGIN ' + name, flush=True)
        for offset in range(0, len(data), 4000):
            print(data[offset:offset+4000], flush=True)
        print('QUBAT_PNG_END', flush=True)
