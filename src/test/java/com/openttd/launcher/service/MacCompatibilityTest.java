package com.openttd.launcher.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class MacCompatibilityTest {
    @TempDir Path root;

    @Test void distinguishesNativeRosettaAndLegacy32Bit() throws Exception {
        assertEquals(MacDmgInstaller.Compatibility.ROSETTA, MacDmgInstaller.compatibility("x86_64", true, "15.0"));
        assertEquals(MacDmgInstaller.Compatibility.NATIVE, MacDmgInstaller.compatibility("x86_64 arm64", true, "15.0"));
        assertEquals(MacDmgInstaller.Compatibility.NATIVE, MacDmgInstaller.compatibility("x86_64", false, "15.0"));
        assertEquals(MacDmgInstaller.Compatibility.INTEL_32, MacDmgInstaller.compatibility("i386 ppc", false, "10.14.6"));
        assertThrows(IOException.class, () -> MacDmgInstaller.compatibility("i386 ppc", true, "15.0"));
        assertThrows(IOException.class, () -> MacDmgInstaller.compatibility("i386", false, "10.15"));
        assertThrows(IOException.class, () -> MacDmgInstaller.compatibility("arm64", false, "15.0"));
    }

    @Test void readsThinIntelAndArmMachHeaders() throws Exception {
        for (int cpu : new int[]{0x01000007, 0x0100000c}) {
            Path file = root.resolve("binary-" + cpu);
            try (var out = new DataOutputStream(Files.newOutputStream(file))) {
                out.writeInt(0xcffaedfe); out.writeInt(Integer.reverseBytes(cpu));
            }
            assertEquals(cpu == 0x01000007 ? "x86_64" : "arm64", MacDmgInstaller.architectures(file));
        }
    }

    @Test void readsUniversalArchitectureTable() throws Exception {
        Path file = root.resolve("universal");
        try (var out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(0xcafebabe); out.writeInt(2);
            out.writeInt(0x01000007); out.write(new byte[16]);
            out.writeInt(0x0100000c); out.write(new byte[16]);
        }
        assertEquals("x86_64 arm64", MacDmgInstaller.architectures(file));
    }

    @Test void rejectsTruncatedBinary() throws Exception {
        Path file = Files.write(root.resolve("invalid"), new byte[]{0, 1});
        assertThrows(IOException.class, () -> MacDmgInstaller.architectures(file));
    }
}
