/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.Desktop;
import java.net.URI;

// Jar's Main-Class (see build.gradle.kts jar.manifest): this is what runs when someone
// double-clicks the addon jar directly instead of dropping it in their mods folder. Meteor's
// own Main (meteordevelopment.meteorclient.Main) does the same thing for its jar, but its
// classes aren't on the classpath here - this is a plain "java -jar" launch, before Fabric/
// Meteor ever loads - so we can't call into it, only mirror the pattern with java.awt.Desktop
// instead of Meteor's hand-rolled per-OS Runtime.exec.
public class Main {

    private static final String RELEASES_URL = "https://github.com/Leonn170709/THM-Addons/releases/latest";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        Object[] options = {"Download", "No thanks"};
        int choice = JOptionPane.showOptionDialog(null,
            "This is a Meteor Client addon, not a program you run directly.\nPut it in your mods folder next to Meteor Client and Fabric.",
            "THM Addon",
            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
            options, options[0]);

        if (choice == 0) {
            try {
                Desktop.getDesktop().browse(new URI(RELEASES_URL));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
