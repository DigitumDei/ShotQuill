# OpenAI Provider Smoke Test

Use this manual check after configuring an OpenAI API key in local settings.
Automated tests use fake transports and do not require network access.

1. Save a valid OpenAI API key in the app settings screen.
2. Import a PNG or JPEG photo that satisfies the app upload limits.
3. Run vision description generation and confirm a concise image description is returned.
4. Generate caption and alt text from the same draft and confirm caption, short caption, hashtags, and plain alt text are populated.
5. Run a photo edit and confirm a new edited image asset is returned.
6. Inspect prompt history or logs and verify the API key and full image payloads are not present.

Expected provider endpoints:

- `POST https://api.openai.com/v1/chat/completions` for vision, captions, and alt text.
- `POST https://api.openai.com/v1/images/edits` with multipart image upload for photo edits.
