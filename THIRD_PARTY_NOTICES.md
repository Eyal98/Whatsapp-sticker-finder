# Third-party notices

Sticker Finder bundles the models and libraries below. The app shows the same notices under
**About → Open-source licenses**, together with a list generated at build time of every library
in the build and the license its Maven POM declares (`app/build.gradle.kts`, `dependencyNotices`).

## Models

| Model | By | Used for | License | Source |
|---|---|---|---|---|
| SigLIP 2 base, patch 16, 224 px (fp16 LiteRT) | Google | Picture tags | Apache License 2.0 | https://huggingface.co/litert-community/SigLIP2-base-patch16-224 |
| Granite Embedding 311M Multilingual R2 (int8 LiteRT-LM) | IBM | Search by meaning | Apache License 2.0 | https://huggingface.co/litert-community/granite-embedding-311m-multilingual-r2 |
| SFace face recognition, converted to LiteRT by this project | OpenCV Zoo | Telling people apart (People) | Apache License 2.0 | https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface |
| ML Kit face detection (bundled model) | Google | Finding faces (People) | ML Kit Terms of Service | https://developers.google.com/ml-kit/terms |
| Tesseract `tessdata_fast` language data (heb, eng) | Tesseract OCR | Reading printed text | Apache License 2.0 | https://github.com/tesseract-ocr/tessdata_fast |

Every model file is downloaded when the app is built and checked against a pinned SHA-256
(`core/vision/siglip.properties`, `core/vision/faces.properties`, `core/embed/granite.properties`,
`core/ocr/tessdata.properties`). The picture-tag label list (`tools/siglip/labels.tsv`) and its
vectors are this project's own.

**SFace training data.** The SFace weights are Apache-2.0, but face-recognition models are
usually trained on research face datasets whose terms may restrict commercial use. That is fine
for a free app; check the model's training data terms before any paid or commercial release.

## Native code inside libraries

Tesseract4Android bundles native builds of these, which its Maven license doesn't list:

| Component | License | Source |
|---|---|---|
| Tesseract OCR engine | Apache License 2.0 | https://github.com/tesseract-ocr/tesseract |
| Leptonica | BSD 2-Clause | http://www.leptonica.org |
| libjpeg-turbo | IJG License / BSD 3-Clause | https://libjpeg-turbo.org |
| libpng | PNG Reference Library License | http://www.libpng.org |

## Libraries

AndroidX, Jetpack Compose, Room, WorkManager, Kotlin, kotlinx.coroutines, LiteRT and LiteRT-LM
are under the Apache License 2.0. ML Kit and the Google Play services libraries it depends on
are under Google's own terms, listed per library in the app. The full Apache License 2.0 text is
in `app/src/main/assets/licenses/Apache-2.0.txt`.
