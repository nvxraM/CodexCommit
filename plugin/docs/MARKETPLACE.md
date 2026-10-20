# Marketplace listing

[Edit CodexCommit](https://plugins.jetbrains.com/plugin/35249-codexcommit/edit).

## Overview

Upload the checked plugin ZIP. The description comes from `src/main/resources/META-INF/plugin.xml`.
Keep **Preserve changes from UI** set to **Use data from plugin.xml**.
Use the brief description, three steps, button picture, requirements, and help links.

The picture uses a pinned public HTTPS PNG. Marketplace removes inline image data and custom HTML IDs.
The enhanced IDE renderer displays the same bundled picture and works offline.
The website and unenhanced HTML renderer can load the public PNG from GitHub.
Click it to open the full-size viewer in PyCharm. The website link opens the full-size PNG.

## Media

Upload these static pictures in order. Click each link to view the original.

1. [Select changes and generate](images/marketplace/01-select-and-generate.png).
2. [Review the draft](images/marketplace/02-review-the-draft.png).

Both pictures are 3840 × 2160 pixels. They show the real IDE with sample files and a local test draft.
The sample draft is not a recorded model response. The gallery supports image zoom.
Keep the gallery pictures static. The button animates during generation; GitHub has the animated guide.

## General information

Use these fields. The IDE uses them for its native Additional Info links.

| Field | Value |
| --- | --- |
| Plugin homepage | https://github.com/nvxraM/CodexCommit |
| Documentation | https://github.com/nvxraM/CodexCommit/blob/main/plugin/docs/USAGE.md |
| Source code | https://github.com/nvxraM/CodexCommit |
| Issue tracker | https://github.com/nvxraM/CodexCommit/issues |
| License | https://github.com/nvxraM/CodexCommit/blob/main/LICENSE |
| Privacy policy | https://github.com/nvxraM/CodexCommit/blob/main/plugin/docs/PRIVACY.md |
| Tags | AI, VCS |

Keep the approved publisher and support contact. Do not add private contact details.
The plugin supplies aligned Documentation and Privacy links when the native links are absent.
Source Code, License, and Report Plugin use the IDE's own links.

## Check after upload

- Open the website Overview and the IDE Overview. Check the picture.
- Resize the IDE settings window. Open the picture, then press Esc.
- Check Documentation, Source Code, License, Privacy, and the issue link.
- Check the current version and its short What's New notes.

A Marketplace token can upload the ZIP through the official update API.
Add it only to the secure `CODEXCOMMIT_MARKETPLACE_TOKEN` environment field for `plugins.jetbrains.com`.
Never put it in this repository or a chat message.
Gallery uploads and general information fields use the Marketplace owner page.
Submission and Marketplace approval are separate from local verification and GitHub publication.
