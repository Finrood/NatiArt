package com.portcelana.natiart.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageUploadLifecycleTest {
    @TempDir
    Path root;

    @Test
    void successfulWriteClosesInputAndUsesThePlannedTarget() throws Exception {
        final TrackingInput input = new TrackingInput(false);
        final StorageFileSystem storage = new StorageFileSystem(List.of(root.toString()));
        final var target = storage.uploadTarget(root.toString(), "success");
        assertEquals(
                target, storage.uploadFile(root.toString(), "success", new InputFile(input, "image/webp", "test", 3)));
        assertTrue(input.closed);
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(Path.of(target)));
    }

    @Test
    void partialWriteClosesInputAndRemovesOnlyTheNewIncompleteFile() {
        final TrackingInput input = new TrackingInput(true);
        final StorageFileSystem storage = new StorageFileSystem(List.of(root.toString()));
        assertThrows(
                IllegalStateException.class,
                () -> storage.uploadFile(root.toString(), "failed", new InputFile(input, "image/webp", "test", 3)));
        assertTrue(input.closed);
        assertFalse(Files.exists(root.resolve("failed")));
    }

    @Test
    void existingFileIsNeitherOverwrittenNorDeletedAndInputIsClosed() throws Exception {
        Files.writeString(root.resolve("existing"), "committed bytes");
        final TrackingInput input = new TrackingInput(false);
        final StorageFileSystem storage = new StorageFileSystem(List.of(root.toString()));
        assertThrows(
                IllegalStateException.class,
                () -> storage.uploadFile(root.toString(), "existing", new InputFile(input, "image/webp", "test", 3)));
        assertTrue(input.closed);
        assertEquals("committed bytes", Files.readString(root.resolve("existing")));
    }

    @Test
    void rejectedTargetStillClosesInput() {
        final TrackingInput input = new TrackingInput(false);
        final StorageFileSystem storage = new StorageFileSystem(List.of(root.toString()));
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.uploadFile(root.toString(), "../escape", new InputFile(input, "image/webp", "test", 3)));
        assertTrue(input.closed);
    }

    private static final class TrackingInput extends InputStream {
        private final ByteArrayInputStream bytes = new ByteArrayInputStream(new byte[] {1, 2, 3});
        private final boolean fail;
        private int reads;
        private boolean closed;

        private TrackingInput(boolean fail) {
            this.fail = fail;
        }

        @Override
        public int read() throws IOException {
            if (fail && reads++ > 0) throw new IOException("fixture read failure");
            return bytes.read();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
