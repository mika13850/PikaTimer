/* 
 * Copyright (C) 2024 John Garner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.pikatimer;

import com.pikatimer.event.Event;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import java.util.Locale;
import com.pikatimer.util.I18nManager;

/**
 * FXML Controller class
 *
 * @author jcgarner
 */
public class FXMLpikaController {

    private final Event event = Event.getInstance();
    @FXML
    private Label eventName;
    @FXML
    private Label eventDate;
    @FXML
    private TabPane mainTabPane;
    @FXML
    private MenuItem englishMenuItem;
    @FXML
    private MenuItem frenchMenuItem;

    /**
     * Initializes the controller class.
     */
    @FXML
    protected void initialize() {
        // TODO
        eventName.textProperty().bind(Bindings.concat("PikaTimer: ").concat(event.eventNameProperty()));
        eventDate.textProperty().bind(event.eventDateStringProperty());
        event.setMainTabPane(mainTabPane);
        
        // Initialize i18n manager
        I18nManager i18n = I18nManager.getInstance();

        // Bind menu items to show checkmark for current language
        englishMenuItem.textProperty().bind(
            Bindings.createStringBinding(() -> {
                if (i18n.getLocale().equals(Locale.ENGLISH)) {
                    return "✓ " + i18n.getString("label.english");
                }
                return i18n.getString("label.english");
            }, i18n.localeProperty())
        );
        
        frenchMenuItem.textProperty().bind(
            Bindings.createStringBinding(() -> {
                if (i18n.getLocale().equals(Locale.FRENCH)) {
                    return "✓ " + i18n.getString("label.french");
                }
                return i18n.getString("label.french");
            }, i18n.localeProperty())
        );
    }
    
    /**
     * Switches the application language to English
     */
    @FXML
    private void switchToEnglish() {
        I18nManager.getInstance().setLocale(Locale.ENGLISH);
    }
    
    /**
     * Switches the application language to French
     */
    @FXML
    private void switchToFrench() {
        I18nManager.getInstance().setLocale(Locale.FRENCH);
    }
    
}
