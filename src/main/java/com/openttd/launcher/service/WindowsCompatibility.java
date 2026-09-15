package com.openttd.launcher.service;

import java.io.*;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Reads Windows executable headers and accounts for the native OS architecture. */
public final class WindowsCompatibility {
    enum Architecture { X86, X64, ARM64 }
    record Host(Architecture architecture, boolean x64Emulation) {}
    private static class Current { static final Host HOST = detectHost(); }

    static Host detectHost() {
        String nativeArch = System.getenv("PROCESSOR_ARCHITEW6432");
        if (nativeArch == null || nativeArch.isBlank()) nativeArch = System.getenv("PROCESSOR_ARCHITECTURE");
        if (nativeArch == null || nativeArch.isBlank()) nativeArch = System.getProperty("os.arch", "");
        Architecture architecture = architecture(nativeArch);
        boolean x64Emulation = architecture == Architecture.ARM64 && windows11OrLater();
        return new Host(architecture, x64Emulation);
    }

    static Architecture architecture(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "arm64", "aarch64" -> Architecture.ARM64;
            case "amd64", "x86_64", "x64" -> Architecture.X64;
            default -> Architecture.X86;
        };
    }

    private static boolean windows11OrLater() {
        if (System.getProperty("os.name", "").contains("Windows 11")) return true;
        Process process = null;
        try {
            process = new ProcessBuilder("reg.exe", "query", "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "/v", "CurrentBuildNumber")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) return false;
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.Charset.defaultCharset());
            var matcher = Pattern.compile("CurrentBuildNumber\\s+REG_SZ\\s+(\\d+)").matcher(output);
            return process.exitValue() == 0 && matcher.find() && Integer.parseInt(matcher.group(1)) >= 22000;
        } catch (IOException ex) { return false; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); return false; }
        finally { if (process != null && process.isAlive()) process.destroyForcibly(); }
    }

    static String platform() {
        Host host = Current.HOST;
        return switch (host.architecture()) {
            case X86 -> "windows-win32";
            case X64 -> "windows-win64";
            case ARM64 -> host.x64Emulation() ? "windows-arm64" : "windows-arm64-win10";
        };
    }

    static Architecture executableArchitecture(Path executable) throws IOException {
        try (var file = new RandomAccessFile(executable.toFile(), "r")) {
            if (file.length() < 64 || file.readUnsignedShort() != 0x4d5a) throw new IOException("Not a valid Windows executable");
            file.seek(0x3c);
            long offset = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
            if (offset < 64 || offset > file.length() - 6) throw new IOException("Invalid Windows executable header");
            file.seek(offset);
            if (file.readInt() != 0x50450000) throw new IOException("This is not a supported Windows PE executable");
            int machine = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
            return switch (machine) {
                case 0x014c -> Architecture.X86;
                case 0x8664 -> Architecture.X64;
                case 0xaa64 -> Architecture.ARM64;
                default -> throw new IOException("Unsupported Windows executable architecture: " + Integer.toHexString(machine));
            };
        }
    }

    static String description(Architecture binary, Host host) throws IOException {
        if (binary == host.architecture()) return "Launching the native Windows " + binary + " build";
        if (binary == Architecture.X86) return "Launching the Windows x86 (32-bit) build through Windows compatibility support";
        if (binary == Architecture.X64 && host.architecture() == Architecture.ARM64 && host.x64Emulation()) {
            return "Launching the Windows x64 build through Windows 11 ARM emulation";
        }
        if (binary == Architecture.X64 && host.architecture() == Architecture.ARM64) {
            throw new IOException("Windows 10 on ARM supports x86 emulation, but not x64. Install an ARM64 or x86 (32-bit) build.");
        }
        throw new IOException("This " + binary + " build cannot run on Windows " + host.architecture() + ". Install a compatible build.");
    }

    public static String launchCompatibility(Path executable) throws IOException {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return null;
        return description(executableArchitecture(executable), Current.HOST);
    }
}
