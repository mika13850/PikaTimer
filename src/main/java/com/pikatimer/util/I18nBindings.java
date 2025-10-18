package com.pikatimer.util;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;

/**
 * 
 * - Utility class for binding UI components to i18n resource bundles.
 * 
 * - Provides convenient methods to bind text properties to translated strings.
 */
public class I18nBindings {

    private static final I18nManager i18n = I18nManager.getInstance();

    /**
     * 
     * - Creates a string binding for a resource key
     * - @param key the resource key
     * - @return a StringBinding that updates when locale changes
     */
    public static StringBinding createStringBinding(String key) {
        return Bindings.createStringBinding(() -> i18n.getString(key), i18n.localeProperty());
    }

    /**
     * 
     * - Binds a Label's text to a resource key
     * - @param label the label to bind
     * - @param key the resource key
     */
    public static void bindLabel(Label label, String key) {
        label.textProperty().bind(createStringBinding(key));
    }

    /**
     * 
     * - Binds a Button's text to a resource key
     * - @param button the button to bind
     * - @param key the resource key
     */
    public static void bindButton(Button button, String key) {
        button.textProperty().bind(createStringBinding(key));
    }

    /**
     * 
     * - Binds a MenuItem's text to a resource key
     * - @param menuItem the menu item to bind
     * - @param key the resource key
     */
    public static void bindMenuItem(MenuItem menuItem, String key) {
        menuItem.textProperty().bind(createStringBinding(key));
    }

    /**
     * 
     * - Binds a Tooltip's text to a resource key
     * - @param tooltip the tooltip to bind
     * - @param key the resource key
     */
    public static void bindTooltip(Tooltip tooltip, String key) {
        tooltip.textProperty().bind(createStringBinding(key));
    }

    /**
     * 
     * - Gets a translated string immediately (not bound)
     * - @param key the resource key
     * - @return the translated string
     */
    public static String get(String key) {
        return i18n.getString(key);
    }
}
