# Receiver POC — accept a 10 GB streamed file on ~100 MB RAM

The counterpart to the **sender** POC. Exposes one endpoint that accepts a very large
file streamed over plain HTTP and writes it straight to disk, **without ever holding
the file in memory**. Heap can stay around 100 MB.

## How it works

```
Sender ──POST /upload (application/octet-stream, streamed)──> [Receiver :8081]
                                                                   │
                                            request InputStream ──[64 KB buffer]──> disk
```

- **One endpoint:** `POST /upload`.
- Reads the **raw request body** via `HttpServletRequest.getInputStream()` and copies
  it to a file in a fixed **64 KB** buffer. Nothing accumulates in memory, so a 10 GB
  body uses only a few KB of heap.
- **No multipart** (multipart would buffer each part first and can blow the RAM budget).
- Writes to a temporary `*.part` file, then renames to the final name on success.

## Contract with the sender

| Aspect | Value |
|--------|-------|
| Endpoint | `POST /upload` |
| Body | raw bytes, `Content-Type: application/octet-stream` |
| Header | `X-File-Name` — original file name (sanitized to prevent path traversal) |
| Response | `201 Created` with `{ status, bytesReceived, storedAs, elapsedMs }` |

This matches the sender's default `receiverUrl = http://localhost:8081/upload`.

## Key configuration (why a big upload doesn't fail)

See `application.properties`:

| Setting | Purpose |
|---------|---------|
| `spring.servlet.multipart.enabled=false` | Don't let Spring buffer/parse the body. |
| `server.tomcat.max-swallow-size=-1` | Don't cap how many body bytes the container will read. |
| `server.tomcat.max-http-form-post-size=-1` | Remove form-size cap. |
| `server.tomcat.connection-timeout=600000` | Don't kill the socket mid-stream on a long transfer. |
| `receiver.output-dir` | Where files are written (needs free space ≥ file size). |

## Run

```bash
# build the Maven wrapper (or copy mvnw/mvnw.cmd/.mvn from the sender repo)
mvn spring-boot:run        # starts on :8081

# files land in ./received
```

## Full round-trip test with the sender

```bash
# 1. generate a test file
mkfile 10g /tmp/10gb.bin                 # macOS
# dd if=/dev/zero of=/tmp/10gb.bin bs=1m count=10240   # portable

# 2. start receiver (this app) on :8081, then start sender on :8080

# 3. trigger the transfer via the sender
curl -X POST "http://localhost:8080/send?filePath=/tmp/10gb.bin"

# the file appears in receiver's ./received directory
```

## Memory proof

Run the receiver with a small heap to demonstrate the constraint is respected:

```bash
java -Xmx128m -jar target/receiver-0.0.1-SNAPSHOT.jar
```

A 10 GB upload still succeeds because the body is streamed to disk, never buffered.

## Java 17 → 25

Built on Spring Boot 4.1 / Java 17, runs through Java 25. The streaming read uses only
the Servlet API (`getInputStream()`), which is portable across all those versions.

## Project layout

```
receiver/
  pom.xml
  src/main/java/com/example/receiver/
    ReceiverApplication.java   # Spring Boot entry point
    UploadController.java      # POST /upload — streams body to disk
    UploadResult.java          # response payload (record)
  src/main/resources/
    application.properties     # port, output dir, size/timeout config
  src/test/java/com/example/receiver/
    ReceiverApplicationTests.java
```

