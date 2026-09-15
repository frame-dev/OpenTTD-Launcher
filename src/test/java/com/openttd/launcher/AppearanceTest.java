package com.openttd.launcher;
import com.openttd.launcher.service.Settings;
import javax.swing.*;
import java.awt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AppearanceTest {
    @Test void repeatedThemeChangesDoNotCompoundTextSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel=new JPanel(); JButton button=new JButton("Launch"); button.setFont(new Font("Dialog",Font.PLAIN,12));
            button.putClientProperty("primary",true); panel.add(button);
            var light=new Settings.Preferences("Light","#ffffff",150,true,false,true,false,1000,"");
            Appearance.apply(panel,light); Appearance.apply(panel,light);
            assertEquals(18,button.getFont().getSize()); assertEquals(Color.WHITE,button.getBackground());
            assertTrue(button.getForeground().getRed()<100);
            Appearance.apply(panel,Settings.defaults()); assertEquals(12,button.getFont().getSize());
        });
    }
}
