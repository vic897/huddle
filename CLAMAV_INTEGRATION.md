# ClamAV Integration Documentation

## Implementation Verification

### ClamAV INSTREAM Protocol Implementation

**Status: VERIFIED CORRECT**

The `ClamAVFileScanner` implements the ClamAV INSTREAM protocol correctly:

1. **Command Format**: Sends `zINSTREAM\0` (line 54) - Correct
2. **Chunk Framing**: Uses 4-byte big-endian chunk size followed by chunk data (lines 114-119) - Correct
3. **Termination**: Sends 0 as end-of-stream marker (line 123) - Correct
4. **Response Parsing**: Reads full response and parses for OK, FOUND, ERROR (lines 127-158) - Correct

### Socket Connection and Timeouts

**Status: VERIFIED CORRECT**

- Connection timeout: Configurable via `scanner.clamav.timeout` (default 30s) - line 100
- Socket read timeout: Set via `setSoTimeout()` (line 101) - Correct
- Both timeouts use same configuration value - Appropriate for consistency

### Maximum File Size Enforcement

**Status: VERIFIED CORRECT**

- File size checked before scanning (lines 82-91)
- Returns `BLOCKED_PENDING_REVIEW` for oversized files
- Configurable via `scanner.clamav.max-file-size` (default 250MB)

### Response Handling

**Status: VERIFIED CORRECT**

- **OK response**: Returns `CLEARED` (lines 151-154)
- **FOUND response**: Returns `CONFIRMED_MALICIOUS`, extracts virus name (lines 139-144)
- **ERROR response**: Returns `BLOCKED_PENDING_REVIEW` (lines 146-149)
- **Empty response**: Returns `BLOCKED_PENDING_REVIEW` (lines 134-137)
- **Unknown response**: Returns `BLOCKED_PENDING_REVIEW` (lines 156-158)

### Fail-Closed Behavior

**Status: VERIFIED CORRECT**

- **Connection failure**: Returns `PENDING_ASSESSMENT` (lines 160-162)
- **Timeout**: Returns `PENDING_ASSESSMENT` (lines 163-165)
- **IO error**: Returns `PENDING_ASSESSMENT` (lines 166-168)
- **Unexpected error**: Returns `BLOCKED_PENDING_REVIEW` (lines 169-171)
- **File size check error**: Returns `PENDING_ASSESSMENT` (lines 88-90)

### Scanner Selection

**Status: VERIFIED CORRECT**

- `ClamAVFileScanner`: Active when `scanner.clamav.enabled=true` (line 48)
- `StubFileScanner`: Active when `scanner.clamav.enabled=false` or not set (line 30)
- Uses Spring's `@ConditionalOnProperty` for mutual exclusion

### Concurrent Upload Behavior

**Status: VERIFIED CORRECT**

- Each scan creates a new socket connection (line 99)
- No shared state between concurrent scans
- Socket properly closed in finally block (lines 173-189)
- Thread-safe implementation

### Historical Version and Replacement Scanning

**Status: VERIFIED CORRECT**

- `FileService.uploadFile()` calls `fileScanner.scanFile()` for new uploads
- `FileService.replaceFile()` calls `fileScanner.scanFile()` for replacements
- Both set safety state to scan result
- FileVersion entities inherit safety state from current file during replacement

## Infrastructure Requirements

### Docker Deployment (Recommended)

```bash
# Start ClamAV daemon
docker run -d -p 3310:3310 clamav/clamav

# Or with custom configuration
docker run -d \
  -p 3310:3310 \
  -v /path/to/clamav.conf:/etc/clamav/clamd.conf \
  clamav/clamav
```

### Linux Installation

```bash
# Install ClamAV
sudo apt update
sudo apt install -y clamav clamav-daemon

# Update signatures
sudo freshclam

# Start daemon
sudo systemctl enable --now clamav-daemon
sudo systemctl status clamav-daemon
```

### Windows Installation

1. Download ClamAV for Windows from https://www.clamav.net/downloads
2. Install and configure `clamd.conf`
3. Start ClamAV service via Windows Services

### Configuration

**Environment Variables:**
- `SCANNER_CLAMAV_ENABLED=true` - Enable ClamAV scanner
- `SCANNER_CLAMAV_HOST=localhost` - ClamAV daemon host
- `SCANNER_CLAMAV_PORT=3310` - ClamAV daemon port
- `SCANNER_CLAMAV_TIMEOUT=30` - Connection timeout in seconds
- `SCANNER_CLAMAV_MAX_FILE_SIZE=262144000` - Max file size in bytes (250MB)

### Signature Updates

**Critical for Production:**

ClamAV requires regular signature updates to detect new threats. Configure `freshclam`:

```bash
# Configure freshclam
sudo nano /etc/clamav/freshclam.conf

# Set update schedule (cron example)
0 */6 * * * /usr/bin/freshclam --quiet
```

### Health Monitoring

**Recommended Monitoring:**
- ClamAV daemon process status
- Port 3310 connectivity
- Signature update timestamps
- Scan queue depth
- Scan latency metrics

### Testing with EICAR

The EICAR test file is a harmless test signature detected by all antivirus scanners:

```bash
# Download EICAR test file
curl -O https://www.eicar.org/download-anti-malware-testfile/eicar.com.txt

# Expected behavior:
# - ClamAV should detect it as "Eicar-Test-Signature"
# - Scanner should return CONFIRMED_MALICIOUS
# - File should remain inaccessible
```

### Production Deployment Checklist

- [ ] ClamAV daemon installed and running
- [ ] Daemon configured to listen on configured port (default 3310)
- [ ] Firewall allows access to ClamAV port
- [ ] Signature updates configured and automated
- [ ] Health monitoring configured
- [ ] Environment variables set for scanner configuration
- [ ] Load testing performed with concurrent uploads
- [ ] Timeout values tuned for expected file sizes
- [ ] Log aggregation configured for scan results
- [ ] Incident response procedure for malware detections

## Integration Test Results

**Status: DOCKER UNAVAILABLE - Integration tests deferred**

Docker is not available in the current development environment. The following integration tests require a running ClamAV daemon:

1. **Clean file scan** - Verify clean files return CLEARED
2. **EICAR detection** - Verify EICAR test file returns CONFIRMED_MALICIOUS
3. **Daemon failure** - Verify daemon unavailability returns PENDING_ASSESSMENT
4. **Timeout handling** - Verify timeout returns PENDING_ASSESSMENT
5. **Concurrent scans** - Verify thread-safe concurrent scanning
6. **Large file handling** - Verify streaming for files near size limit

**Unit Tests**: All 8 unit tests in `ClamAVFileScannerTest.java` pass without ClamAV daemon.

## Known Limitations

1. **No retry logic** - Transient failures immediately return PENDING_ASSESSMENT
2. **No scan queue** - Each scan creates a new socket connection
3. **No scan caching** - Same file rescanned on each upload/replacement
4. **No async scanning** - Scanning blocks the upload thread

These limitations are acceptable for the current use case but may need enhancement for high-volume deployments.
