"""Builds the SigLIP2 label embeddings the app uses to tag stickers.

The phone runs only SigLIP2's image tower (a LiteRT .tflite the user imports). Its text tower
isn't available on the phone, so the text side runs here, once, over the fixed label list in
labels.tsv, and the app ships the resulting vectors.

Before writing anything, this checks that the .tflite image tower and the open_clip model used
for the text side produce the same image embeddings (otherwise the two halves wouldn't be in the
same space), and prints the top labels for a few emoji images to help tune tagging thresholds.

Output file format (little-endian):
    magic b"SLB1", uint32 count, uint32 dim, float32 logit_scale, float32 logit_bias,
    32 bytes SHA-256 of the prompt list, then count * dim float16 values (L2-normalized rows).
The prompt-list hash is SHA-256 of the prompt phrases joined with "\n" (UTF-8), in file order;
the app computes the same from its copy of labels.tsv to make sure rows and labels line up.
"""

import argparse
import hashlib
import io
import struct
import sys
import urllib.request

import numpy as np
import open_clip
import torch
from PIL import Image

MODEL = "ViT-B-16-SigLIP2"
PRETRAINED = "webli"
TEMPLATES = ["a sticker of {}.", "a cartoon of {}.", "This is a photo of {}."]
TEST_EMOJI = {
    "tears of joy": "1f602", "angry": "1f621", "cat face": "1f431", "birthday cake": "1f382",
    "thumbs up": "1f44d", "red heart": "2764", "sleeping": "1f634", "party popper": "1f389",
    "facepalm": "1f926", "dog face": "1f436", "coffee": "2615", "pizza": "1f355",
}
EMOJI_URL = "https://raw.githubusercontent.com/googlefonts/noto-emoji/main/png/512/emoji_u{}.png"


def read_prompts(path):
    prompts = []
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        cols = line.split("\t")
        if len(cols) != 3:
            sys.exit(f"bad line (need 3 tab-separated columns): {line!r}")
        prompts.append(cols[0].strip())
    if len(set(prompts)) != len(prompts):
        sys.exit("duplicate prompt phrases in labels.tsv")
    return prompts


def flatten(image):
    """Stickers are transparent; the app draws them on white before encoding. Same here."""
    image = image.convert("RGBA")
    background = Image.new("RGBA", image.size, (255, 255, 255, 255))
    return Image.alpha_composite(background, image).convert("RGB")


def tflite_input(image):
    """Matches the app: bilinear resize to 224, RGB scaled to [-1, 1], NCHW."""
    pixels = np.asarray(image.resize((224, 224), Image.BILINEAR), dtype=np.float32)
    pixels = (pixels / 255.0 - 0.5) / 0.5
    return pixels.transpose(2, 0, 1)[None]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--labels", required=True)
    parser.add_argument("--tflite", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    prompts = read_prompts(args.labels)
    prompt_hash = hashlib.sha256("\n".join(prompts).encode("utf-8")).digest()
    print(f"{len(prompts)} labels, prompt hash {prompt_hash.hex()}")

    model, _, preprocess = open_clip.create_model_and_transforms(MODEL, pretrained=PRETRAINED)
    tokenizer = open_clip.get_tokenizer(MODEL)
    model.eval()
    scale = float(model.logit_scale.exp())
    bias = float(model.logit_bias)
    print(f"logit_scale {scale:.4f}, logit_bias {bias:.4f}")

    from ai_edge_litert.interpreter import Interpreter
    interpreter = Interpreter(model_path=args.tflite, num_threads=4)
    interpreter.allocate_tensors()
    inp = interpreter.get_input_details()[0]
    out = interpreter.get_output_details()[0]
    print(f"tflite input {inp['shape']} {inp['dtype']}, output {out['shape']} {out['dtype']}")

    def encode_tflite(image):
        interpreter.set_tensor(inp["index"], tflite_input(image))
        interpreter.invoke()
        v = interpreter.get_tensor(out["index"])[0].astype(np.float32)
        return v / np.linalg.norm(v)

    def encode_torch(image):
        # Same preprocessing as the app/tflite path, so only the networks are compared.
        with torch.no_grad():
            v = model.encode_image(torch.from_numpy(tflite_input(image)))[0].numpy()
        return v / np.linalg.norm(v)

    with torch.no_grad():
        rows = []
        for i in range(0, len(prompts), 64):
            chunk = prompts[i:i + 64]
            per_template = []
            for template in TEMPLATES:
                tokens = tokenizer([template.format(p) for p in chunk])
                e = model.encode_text(tokens)
                per_template.append(e / e.norm(dim=-1, keepdim=True))
            mean = torch.stack(per_template).mean(0)
            rows.append(mean / mean.norm(dim=-1, keepdim=True))
        labels = torch.cat(rows).numpy().astype(np.float32)
    print(f"label embeddings {labels.shape}")

    worst = 1.0
    for name, code in TEST_EMOJI.items():
        with urllib.request.urlopen(EMOJI_URL.format(code)) as r:
            image = flatten(Image.open(io.BytesIO(r.read())))
        a, b = encode_tflite(image), encode_torch(image)
        agreement = float(a @ b)
        worst = min(worst, agreement)
        cos = labels @ a
        prob = 1 / (1 + np.exp(-(scale * cos + bias)))
        top = np.argsort(-cos)[:8]
        print(f"\n{name}: tflite/torch cosine {agreement:.4f}")
        for j in top:
            print(f"  {cos[j]:.3f}  p={prob[j]:.4f}  {prompts[j]}")
    print(f"\nworst tflite/torch agreement {worst:.4f}")
    if worst < 0.98:
        sys.exit("The .tflite image tower does not match the open_clip model; label vectors would be wrong.")

    with open(args.out, "wb") as f:
        f.write(b"SLB1")
        f.write(struct.pack("<IIff", len(prompts), labels.shape[1], scale, bias))
        f.write(prompt_hash)
        f.write(labels.astype("<f2").tobytes())
    print(f"wrote {args.out} sha256 {hashlib.sha256(open(args.out, 'rb').read()).hexdigest()}")


if __name__ == "__main__":
    main()
