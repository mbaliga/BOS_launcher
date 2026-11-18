#!/usr/bin/env python3
"""Wave 28 (commsLayout): side-by-side and overlay of a Comms screenshot against the owner's mock-up (docs/design/comms/).

usage: python3 -I tools/comms_diff.py <mock.png> <shot.png> <out.png>
Writes mock | shot | overlay (mock in red, shot in cyan; white where both have ink). Both are expected at 1 px = 1 device px (411 dp, 352 dpi).
"""
import sys
from PIL import Image
import numpy as np

def main(mock, shot, out):
    a = Image.open(mock).convert('RGB'); b = Image.open(shot).convert('RGB')
    h = min(a.height, b.height); w = min(a.width, b.width)
    a = a.crop((0, 0, w, h)); b = b.crop((0, 0, w, h))
    A = np.asarray(a).astype(int); B = np.asarray(b).astype(int)
    bgA = A[h // 2, 5]; bgB = B[h // 2, 5]
    ia = np.abs(A - bgA).sum(axis=2) > 60; ib = np.abs(B - bgB).sum(axis=2) > 60
    ov = np.zeros((h, w, 3), dtype=np.uint8)
    ov[ia & ~ib] = (255, 60, 60); ov[ib & ~ia] = (60, 220, 255); ov[ia & ib] = (255, 255, 255)
    canvas = Image.new('RGB', (w * 3 + 20, h), (40, 40, 40))
    canvas.paste(a, (0, 0)); canvas.paste(b, (w + 10, 0)); canvas.paste(Image.fromarray(ov), (2 * w + 20, 0))
    canvas.save(out)

if __name__ == '__main__':
    main(*sys.argv[1:4])
