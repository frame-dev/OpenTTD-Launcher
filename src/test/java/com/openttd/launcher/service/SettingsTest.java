package com.openttd.launcher.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @TempDir Path root;
    @Test void preferencesSurviveRestartAndKeepInstallation() {
        Path file=root.resolve("settings.properties"); Settings settings=new Settings(file);
        settings.installRoot(root.resolve("games"));
        var preferences=new Settings.Preferences("Light","#ba34ef",125,false,true,false,true,500,"-r\n1280x720\n-c\nC:\\My Games\\settings.cfg");
        settings.preferences(preferences);
        Settings reloaded=new Settings(file);
        assertEquals(preferences,reloaded.preferences());
        assertEquals(root.resolve("games"),reloaded.installRoot());
        reloaded.preferences(Settings.defaults());
        assertEquals(root.resolve("games"),new Settings(file).installRoot());
    }
    @Test void corruptPreferencesFallBackToUsableDefaults() throws Exception {
        Path file=root.resolve("settings.properties"); Files.writeString(file,"scale=broken\ntheme=Other\n");
        assertEquals(Settings.defaults(),new Settings(file).preferences());
    }
    @Test void failedSaveKeepsOldPreferences() throws Exception {
        Path parent=Files.writeString(root.resolve("not-a-directory"),"file");
        Settings settings=new Settings(parent.resolve("settings"));
        var changed=new Settings.Preferences("Light","#ffffff",100,false,false,false,false,100,"");
        assertThrows(java.io.UncheckedIOException.class,()->settings.preferences(changed));
        assertEquals(Settings.defaults(),settings.preferences());
    }
    @Test void argumentsKeepSpacesAndNeverInvokeAShell() {
        var p=new Settings.Preferences("Dark","#5bb1ff",100,true,false,true,false,100,"-c\n /a folder/game.cfg \n\n; echo test");
        assertEquals(List.of("-c","/a folder/game.cfg","; echo test"),p.argumentList());
        Path exe=root.resolve("openttd.exe");
        assertEquals(List.of(exe.toString(),"-c","/a folder/game.cfg","; echo test"),MacDmgInstaller.launchCommand(exe,p.argumentList()));
    }
    @Test void macArgumentsArePassedToTheGameBundle() {
        Path executable=root.resolve("OpenTTD.app/Contents/MacOS/openttd");
        assertEquals(List.of("/usr/bin/open","-W","-n",root.resolve("OpenTTD.app").toString(),"--args","-r","1280x720"),
            MacDmgInstaller.launchCommand(executable,List.of("-r","1280x720")));
        assertFalse(MacDmgInstaller.launchCommand(executable,List.of()).contains("--args"));
    }
    @Test void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new Settings.Preferences("Dark","#123456",500,true,false,true,false,100,""));
    }
}
