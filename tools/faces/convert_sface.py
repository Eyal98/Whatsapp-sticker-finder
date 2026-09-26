"""Converts OpenCV Zoo's SFace face-recognition model (Apache-2.0) to TFLite for the app.

SFace maps an aligned 112x112 face to a 128-d vector; the same person's faces have close vectors
(OpenCV's cosine threshold for "same person" is 0.363). The app groups sticker faces with it.

Steps: download the ONNX file, convert with onnx2tf (NCHW -> NHWC input), then check that the
TFLite model gives the same vectors as the ONNX one on several inputs, and that LiteRT 1.4 (the
app's interpreter) runs it. Writes out/sface_fp16.tflite.
"""

import hashlib
import subprocess
import sys
import urllib.request
from pathlib import Path

import numpy as np

URLS = [
    "https://huggingface.co/opencv/face_recognition_sface/resolve/main/face_recognition_sface_2021dec.onnx",
    "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx",
    "https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx",
]


def download(path: Path) -> None:
    for url in URLS:
        try:
            data = urllib.request.urlopen(url, timeout=120).read()
        except Exception as e:  # noqa: BLE001 - try the next mirror
            print(f"{url}: {e}")
            continue
        # A Git LFS pointer is a few hundred bytes of text, not the model.
        if len(data) < 1_000_000:
            print(f"{url}: only {len(data)} bytes, skipped")
            continue
        path.write_bytes(data)
        print(f"downloaded {url} ({len(data)} bytes, sha256 {hashlib.sha256(data).hexdigest()})")
        return
    sys.exit("SFace ONNX model could not be downloaded")


def main() -> None:
    work = Path("work")
    work.mkdir(exist_ok=True)
    onnx_path = work / "sface.onnx"
    download(onnx_path)

    subprocess.run(["onnx2tf", "-i", str(onnx_path), "-o", str(work / "tf"), "-n"], check=True)
    fp32 = work / "tf" / "sface_float32.tflite"
    fp16 = work / "tf" / "sface_float16.tflite"
    for p in (fp32, fp16):
        if not p.exists():
            sys.exit(f"onnx2tf did not write {p}; got {sorted(x.name for x in (work / 'tf').iterdir())}")

    import onnxruntime as ort
    from ai_edge_litert.interpreter import Interpreter

    session = ort.InferenceSession(str(onnx_path))
    onnx_input = session.get_inputs()[0]
    print(f"onnx input {onnx_input.name} {onnx_input.shape}")

    interp = Interpreter(model_path=str(fp16), num_threads=4)
    interp.allocate_tensors()
    tin = interp.get_input_details()[0]
    tout = interp.get_output_details()[0]
    print(f"tflite input {tin['shape']} {tin['dtype']}, output {tout['shape']}")
    if list(tin["shape"]) != [1, 112, 112, 3]:
        sys.exit(f"unexpected TFLite input shape {tin['shape']}")

    rng = np.random.default_rng(0)
    yy, xx = np.mgrid[0:112, 0:112].astype(np.float32)
    inputs = [
        rng.uniform(0, 255, (112, 112, 3)).astype(np.float32),
        np.stack([xx * 2.2, yy * 2.2, (xx + yy) * 1.1], axis=-1),
        np.clip(128 + 60 * np.sin(xx / 7)[..., None] * np.cos(yy / 5)[..., None] * np.ones(3), 0, 255).astype(np.float32),
    ]
    worst = 1.0
    for image in inputs:
        # SFace takes RGB, 0-255, no normalization (OpenCV: blobFromImage(swapRB=True)).
        ref = session.run(None, {onnx_input.name: image.transpose(2, 0, 1)[None]})[0].reshape(-1)
        interp.set_tensor(tin["index"], image[None])
        interp.invoke()
        got = interp.get_tensor(tout["index"]).reshape(-1)
        cos = float(ref @ got / (np.linalg.norm(ref) * np.linalg.norm(got)))
        worst = min(worst, cos)
        print(f"onnx/tflite cosine {cos:.5f}, dim {got.size}")
    if worst < 0.995:
        sys.exit(f"TFLite model doesn't match the ONNX one (worst cosine {worst:.4f})")

    out = Path("out")
    out.mkdir(exist_ok=True)
    target = out / "sface_fp16.tflite"
    target.write_bytes(fp16.read_bytes())
    print(f"wrote {target} sha256 {hashlib.sha256(target.read_bytes()).hexdigest()} size {target.stat().st_size}")


if __name__ == "__main__":
    main()
