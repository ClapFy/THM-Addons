package xyz.thm.addon.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.screens.settings.base.CollectionListSettingScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.GenericSetting;

public class StringMultiSelectScreen extends CollectionListSettingScreen<String> {
    public StringMultiSelectScreen(GuiTheme theme, StringMultiSelect value, GenericSetting<StringMultiSelect> setting) {
        super(theme, value.screenTitle(), setting, value.selected(), value.candidates());
    }

    @Override
    protected WWidget getValueWidget(String value) {
        return theme.label(value);
    }

    @Override
    protected String[] getValueNames(String value) {
        return new String[] { value };
    }
}
