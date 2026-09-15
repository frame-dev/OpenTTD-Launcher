package com.openttd.launcher.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openttd.launcher.service.WindowsCompatibility.Architecture.*;

class WindowsCompatibilityTest {
    @TempDir Path root;

    @Test void allowsNativeAndSupportedWindowsEmulation() throws Exception {
        assertTrue(WindowsCompatibility.description(X86, new WindowsCompatibility.Host(X64, false)).contains("32-bit"));
        assertTrue(WindowsCompatibility.description(X64, new WindowsCompatibility.Host(ARM64, true)).contains("emulation"));
        assertTrue(WindowsCompatibility.description(X86, new WindowsCompatibility.Host(ARM64, false)).contains("32-bit"));
        assertTrue(WindowsCompatibility.description(ARM64, new WindowsCompatibility.Host(ARM64, true)).contains("native"));
        assertThrows(IOException.class, () -> WindowsCompatibility.description(X64, new WindowsCompatibility.Host(ARM64, false)));
        assertThrows(IOException.class, () -> WindowsCompatibility.description(X64, new WindowsCompatibility.Host(X86, false)));
        assertThrows(IOException.class, () -> WindowsCompatibility.description(ARM64, new WindowsCompatibility.Host(X64, false)));
    }

    @Test void readsAllSupportedPeArchitectures() throws Exception {
        int[] machines = {0x014c, 0x8664, 0xaa64};
        var expected = new WindowsCompatibility.Architecture[]{X86, X64, ARM64};
        for (int i = 0; i < machines.length; i++) {
            Path binary = root.resolve("test-" + i + ".exe");
            try (var file = new RandomAccessFile(binary.toFile(), "rw")) {
                file.writeShort(0x4d5a); file.seek(0x3c); file.writeInt(Integer.reverseBytes(64));
                file.seek(64); file.writeInt(0x50450000); file.writeShort(Short.reverseBytes((short) machines[i]));
            }
            assertEquals(expected[i], WindowsCompatibility.executableArchitecture(binary));
        }
    }

    @Test void rejectsCorruptOrTruncatedExecutable() throws Exception {
        Path binary = Files.write(root.resolve("broken.exe"), new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> WindowsCompatibility.executableArchitecture(binary));
    }

    @Test void recognizesNativeArchitectureNames() {
        assertEquals(ARM64, WindowsCompatibility.architecture("ARM64"));
        assertEquals(X64, WindowsCompatibility.architecture("AMD64"));
        assertEquals(X86, WindowsCompatibility.architecture("x86"));
    }
}
