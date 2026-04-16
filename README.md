# AI Document and Multimedia Q&A Backend (Spring Boot)

This project implements backend APIs for uploading PDF/audio/video files, generating summaries, asking questions, and returning topic timestamps/play links.

## Tech Stack

- Java 17
- Spring Boot
- Spring Data JPA
- PostgreSQL (main runtime)
- H2 (tests)

## Configuration

Main runtime config is in `src/main/resources/application.properties`.

Current database values are set to:

- URL: `jdbc:postgresql://localhost:5432/pansc_ca`
- Username: `postgres`
- Password: `root`

Optional OpenAI key for real LLM responses:

- Environment variable: `OPENAI_API_KEY`

If `OPENAI_API_KEY` is missing, the backend uses deterministic fallback summaries/answers.

## API Endpoints

Base path: `/api/assets`

- `POST /upload` - Upload one file (multipart field name: `file`)
- `GET /` - List uploaded files
- `GET /{id}` - Get one uploaded file metadata
- `GET /{id}/summary` - Summarize extracted content
- `POST /{id}/ask` - Ask a question from file context
- `GET /{id}/timestamps?topic=...` - Get relevant timestamps
- `GET /{id}/play-link?startSeconds=...` - Get playable stream URL for media
- `GET /{id}/stream?startSeconds=...` - Stream stored file

### Example ask payload

```json
{
  "question": "What are the key points?"
}
```

## Run Tests

```powershell
./mvnw.cmd test
```

## Run Application

```powershell
./mvnw.cmd spring-boot:run
```

## Notes

- PDF text extraction uses Apache PDFBox.
- Audio/video transcription is scaffolded with placeholder logic in `TextExtractionService`; integrate Whisper/Deepgram next.
- Uploaded files are stored under `app.storage.path` (default: `uploads`).

