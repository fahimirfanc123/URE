package core.disk;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Persistent transport for the URE native read-only helper.
 *
 * Protocol: SIZE, READ offset length, QUIT.
 * READ responses contain a DATA length line followed by exactly that
 * many raw bytes. Access is serialized to protect protocol framing.
 */
public final class NativeReadTransport implements Closeable {

    private static final int MAX_READ_BYTES = 1024 * 1024;

    private final Process process;
    private final InputStream input;
    private final OutputStream output;
    private boolean closed;

    public NativeReadTransport(ProcessBuilder builder) throws IOException {
        Objects.requireNonNull(builder, "builder");
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = builder.start();
        input = new BufferedInputStream(process.getInputStream());
        output = new BufferedOutputStream(process.getOutputStream());
    }

    public synchronized long size() throws IOException {
        ensureOpen();
        sendLine("SIZE");
        String response = readLine();

        if (!response.startsWith("SIZE ")) {
            throw protocolError("Expected SIZE response", response);
        }

        try {
            long value = Long.parseLong(response.substring(5));
            if (value <= 0) {
                throw new IOException("Helper reported invalid capacity");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IOException("Malformed SIZE response: " + response, e);
        }
    }

    public synchronized byte[] read(long offset, int length, long capacity)
            throws IOException {
        ensureOpen();

        if (offset < 0 || length <= 0 || length > MAX_READ_BYTES
                || offset > capacity || length > capacity - offset) {
            throw new IllegalArgumentException("Read range is out of bounds");
        }

        sendLine("READ " + offset + " " + length);
        String response = readLine();

        if (!response.equals("DATA " + length)) {
            throw protocolError("Expected DATA response", response);
        }

        byte[] data = input.readNBytes(length);
        if (data.length != length) {
            throw new IOException("Helper ended before returning all requested bytes");
        }
        return data;
    }

    private void sendLine(String line) throws IOException {
        output.write(line.getBytes(StandardCharsets.US_ASCII));
        output.write('\n');
        output.flush();
    }

    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        while (line.length() < 128) {
            int value = input.read();
            if (value == -1) {
                throw new IOException("Native helper closed its output");
            }
            if (value == '\n') {
                return line.toString();
            }
            if (value != '\r') {
                line.append((char) value);
            }
        }
        throw new IOException("Native helper response line is too long");
    }

    private IOException protocolError(String message, String response) {
        if (response.startsWith("ERR ")) {
            return new IOException("Native helper rejected request: " + response);
        }
        return new IOException(message + ": " + response);
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Native read transport is closed");
        }
        if (!process.isAlive()) {
            throw new IOException("Native helper process is no longer running");
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;

        IOException failure = null;
        try {
            output.write("QUIT\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            input.close();
            output.close();
        } catch (IOException e) {
            failure = e;
        }

        process.destroy();
        try {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            IOException interrupted = new IOException(
                    "Interrupted while closing native helper", e);
            if (failure == null) failure = interrupted;
            else failure.addSuppressed(interrupted);
        }

        if (failure != null) throw failure;
    }
}
