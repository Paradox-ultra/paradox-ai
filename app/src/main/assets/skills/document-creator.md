# Document Creator Skill

## Overview
Generates native, high-quality on-device PDF documents and genuine editable OpenXML PowerPoint presentations (PPTX).

## Capabilities
- **Native PDF Documents**:
  - Uses Android's `PdfDocument` engine with verified multi-page pagination.
  - Formats headers, subheaders, margins, body paragraphs, and styled bullet points.
  - Automatic page numbering ("Page X of Y") and timestamped footer metadata.
  - Real-time `PdfRenderer` verification before returning file handles.
- **Editable OpenXML Presentations (PPTX)**:
  - Generates valid `.pptx` ZIP archives compliant with Microsoft PowerPoint, Google Slides, and Apple Keynote.
  - Native XML shapes, text frames, titles, subtitles, and hierarchical bullet lists.
  - Fully editable on desktop or mobile office suites.

## Limitations
- Pre-existing vector PDF arbitrary layout reflow or complex font replacement is not supported (structured document creation and section appending are supported).
- PPTX macro-enabled (.pptm) or 3D animations are not supported (clean slide structures and vector text frames are supported).
