package com.openttd.launcher.service;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

/** Downloads a separate 7-Zip command-line program; never installs it system-wide. */
final class TtdArchiveTool {
    private static final Map<String,String> HASHES = Map.of(
        "7z2603.exe","0f6ec2eda1f8c5dc4c267ee761c0dad8a9d5e8863e0c84b7ac026bc9625a1560",
        "7z2603-x64.exe","0859c524b8a63551848f0c246abddcb1d0b7b656b0fbfe879f8d85e61a9e6edd",
        "7z2603-arm64.exe","e22ce71c11dcf503c448fe51e56f41830eb4e1344fa5c7731ae63bce533a8e8e",
        "7z2603-mac.tar.xz","5ca87677072c59f5602e5c49baa27d4694bacd2259b4e507f0094249d4281480",
        "7z2603-linux-x64.tar.xz","dc99eff5008f1ab79bd7084c68513701547a808a89502bf4133683535ab3c695",
        "7z2603-linux-arm64.tar.xz","2389ba20e4d8295e8709c20b6263b69bd1ec4972fe38a04ad7a1badbf595b996",
        "7z2603-linux-x86.tar.xz","1ac4d68723f457ce000a069f5453051aa5381765ce5c77b1d5a02ea8cfba9af7");

    static String packageName(String os, String architecture) throws IOException {
        String arch=architecture.toLowerCase(Locale.ROOT);
        boolean arm=arch.equals("aarch64") || arch.equals("arm64");
        boolean x64=arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64");
        boolean x86=arch.equals("x86") || arch.matches("i[3-6]86");
        if (os.startsWith("Windows") && (arm || x64 || x86)) return "7z2603"+(arm ? "-arm64" : x64 ? "-x64" : "")+".exe";
        if (os.startsWith("Mac") && (arm || x64)) return "7z2603-mac.tar.xz";
        if (os.startsWith("Linux") && (arm || x64 || x86)) return "7z2603-linux-"+(arm ? "arm64" : x64 ? "x64" : "x86")+".tar.xz";
        throw new IOException("Automatic extraction is unavailable on this platform. Use Import from folder instead.");
    }
    static Path prepare(Path root, InstallService.ProgressListener progress) throws Exception {
        String name=packageName(System.getProperty("os.name"),System.getProperty("os.arch"));
        Path directory=root.resolve("tools/7zip-26.03-"+name);
        String binary=name.endsWith(".exe") ? "7z.exe" : "7zz";
        Path executable=directory.resolve(binary);
        if (Files.isRegularFile(directory.resolve("ready")) && Files.isRegularFile(executable)) return executable;
        Files.createDirectories(directory.getParent());
        Path stage=Files.createTempDirectory(directory.getParent(),".7zip-");
        try {
            Path archive=stage.resolve("download");
            progress.update("Downloading the archive extraction tool",0,-1);
            TtdDataService.download(URI.create("https://github.com/ip7z/7zip/releases/download/26.03/"+name),archive,16*1024*1024);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)));
            if (!HASHES.get(name).equals(hash)) throw new IOException("Archive tool checksum mismatch");
            Path output=Files.createDirectory(stage.resolve("tool"));
            Set<String> wanted=Set.of(binary,"7z.dll","License.txt","readme.txt","History.txt");
            if (name.endsWith(".exe")) {
                Path bootstrap=stage.resolve("7zr.exe");
                TtdDataService.download(URI.create("https://github.com/ip7z/7zip/releases/download/26.03/7zr.exe"),bootstrap,4*1024*1024);
                String bootstrapHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(bootstrap)));
                if(!bootstrapHash.equals("ad4c82fadcbdf93c03b4fc440f300509c7d60c5c2f4d183e35d9d70d6957037d")) throw new IOException("Archive bootstrap checksum mismatch");
                var command=new ArrayList<>(List.of(bootstrap.toAbsolutePath().toString(),"e","-y","-o"+output.toAbsolutePath(),archive.toAbsolutePath().toString()));
                command.addAll(wanted);
                run(command,output);
            } else {
                try(var tar=new TarArchiveInputStream(new XZCompressorInputStream(Files.newInputStream(archive)))) {
                    var entry=tar.getNextEntry();
                    while(entry!=null) {
                        if(entry.isFile() && wanted.contains(entry.getName())) {
                            if(entry.getSize()>16*1024*1024) throw new IOException("Archive tool file is too large");
                            Files.copy(tar,output.resolve(entry.getName()));
                        }
                        entry=tar.getNextEntry();
                    }
                }
            }
            if(!Files.isRegularFile(output.resolve(binary))) throw new IOException("Archive tool executable is missing");
            if(!name.endsWith(".exe") && !output.resolve(binary).toFile().setExecutable(true,true)) throw new IOException("Cannot make archive tool executable");
            Files.createDirectories(directory);
            try(var files=Files.list(output)) { for(Path file:files.toList()) Files.move(file,directory.resolve(file.getFileName()),StandardCopyOption.REPLACE_EXISTING); }
            Files.writeString(directory.resolve("ready"),HASHES.get(name));
            return executable;
        } finally { TtdDataService.deleteTemporary(stage); }
    }
    static void extract(Path tool,Path archive,Path output) throws Exception {
        var command=new ArrayList<>(List.of(tool.toAbsolutePath().toString(),"e","-y","-bd","-bb0","-r","-ssc-","-o"+output.toAbsolutePath(),archive.toAbsolutePath().toString()));
        command.addAll(TtdDataService.REQUIRED);
        run(command,output);
    }
    private static void run(List<String> command, Path output) throws Exception {
        Process process=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(2);
            while(!process.waitFor(200,TimeUnit.MILLISECONDS)) {
                if(System.nanoTime()>deadline) throw new IOException("TTD archive extraction timed out");
                try(var files=Files.list(output)) {
                    long size=0;for(Path file:files.toList()) size+=Files.size(file);
                    if(size>128L*1024*1024) throw new IOException("Extracted TTD files exceed the size limit");
                }
            }
            if(process.exitValue()!=0) throw new IOException("Could not extract the TTD archive (exit "+process.exitValue()+")");
        } finally { if(process.isAlive()) { process.destroyForcibly(); process.waitFor(5,TimeUnit.SECONDS); } }
    }
}
